package com.substation.ops;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.substation.ops.common.BizException;
import com.substation.ops.config.ProcedureInitializer;
import com.substation.ops.domain.OperationStep;
import com.substation.ops.domain.OperationTicket;
import com.substation.ops.domain.SafetyMeasure;
import com.substation.ops.domain.WorkTicket;
import com.substation.ops.domain.WorkTicketLog;
import com.substation.ops.dto.MeasureConfirmRequest;
import com.substation.ops.dto.MeasureRequest;
import com.substation.ops.repository.SafetyMeasureRepository;
import com.substation.ops.repository.WorkTicketLogRepository;
import com.substation.ops.service.OperationTicketService;
import com.substation.ops.service.WorkTicketService;

/**
 * 安全措施执行闭环的端到端业务测试（H2/MySQL 兼容模式）：
 * 双侧闸道、撤回变更留痕、终结核对、幂等与并发单次推进。
 */
@SpringBootTest
@ActiveProfiles("test")
class SafetyMeasureLoopIntegrationTest {

    @Autowired
    private WorkTicketService workTicketService;
    @Autowired
    private OperationTicketService operationTicketService;
    @Autowired
    private SafetyMeasureRepository measureRepository;
    @Autowired
    private WorkTicketLogRepository logRepository;
    @Autowired
    private JdbcTemplate jdbc;

    /** 存储过程初始化器在测试中不执行（H2 无 MySQL 存储过程） */
    @MockBean
    private ProcedureInitializer procedureInitializer;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM work_ticket_log");
        jdbc.update("DELETE FROM safety_measure");
        jdbc.update("DELETE FROM operation_step");
        jdbc.update("DELETE FROM operation_ticket");
        jdbc.update("DELETE FROM work_ticket");
        jdbc.update("DELETE FROM defect");
        jdbc.update("DELETE FROM equipment");
        jdbc.update("DELETE FROM bay");
        jdbc.update("DELETE FROM substation");

        jdbc.update("INSERT INTO substation(id,code,name) VALUES (1,'CD','城东变电站')");
        jdbc.update("INSERT INTO bay(id,substation_id,code,name,bay_type,voltage_level,status) "
                + "VALUES (6,1,'CD-BT','110kV母联间隔','BUS_TIE','110kV','COLD_STANDBY')");
    }

    // ---------- 数据构造助手 ----------

    private void issuedTicket(long id, long bayId) {
        jdbc.update("INSERT INTO work_ticket(id,bay_id,ticket_no,issue_date,work_leader,work_content,status,issuer,issue_time) "
                + "VALUES (?,?,?,?,?,?, 'ISSUED','朱明',?)",
                id, bayId, "GZP-T" + id, LocalDate.now(), "张伟", "母联检修", LocalDateTime.now());
    }

    private void opTicketWithSteps(long id, long bayId, String no, List<Integer> stepNos, List<String> descs,
                                   List<Boolean> done) {
        long doneCount = done.stream().filter(Boolean::booleanValue).count();
        String ticketStatus = doneCount == 0 ? OperationTicket.STATUS_PENDING
                : doneCount == done.size() ? OperationTicket.STATUS_FINISHED
                : OperationTicket.STATUS_RUNNING;
        jdbc.update("INSERT INTO operation_ticket(id,bay_id,ticket_no,issue_date,task_name,status) "
                + "VALUES (?,?,?,?,?,?)", id, bayId, no, LocalDate.now(), "母联转检修/恢复", ticketStatus);
        for (int i = 0; i < stepNos.size(); i++) {
            jdbc.update("INSERT INTO operation_step(id,ticket_id,step_no,action_desc,execute_person,execute_time,status) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    id * 100 + stepNos.get(i), id, stepNos.get(i), descs.get(i),
                    done.get(i) ? "张伟" : null, done.get(i) ? LocalDateTime.now() : null,
                    done.get(i) ? OperationStep.STATUS_DONE : OperationStep.STATUS_PENDING);
        }
    }

    private void setStepDone(long ticketId, int stepNo) {
        jdbc.update("UPDATE operation_step SET status='DONE', execute_person='张伟', execute_time=? "
                + "WHERE ticket_id=? AND step_no=?", LocalDateTime.now(), ticketId, stepNo);
    }

    private long measure(long ticketId, int seq, String type, long bayId, String location, String action,
                         String status, Long opTicket, Integer opStep, Long restoreTicket, Integer restoreStep) {
        LocalDateTime executedAt = status.equals("PENDING") ? null : LocalDateTime.now();
        jdbc.update("INSERT INTO safety_measure(ticket_id,seq_no,measure_type,bay_id,location,required_action,"
                        + "op_ticket_id,op_step_no,restore_ticket_id,restore_step_no,status,execute_person,execute_time,version,created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                ticketId, seq, type, bayId, location, action, opTicket, opStep, restoreTicket, restoreStep,
                status, executedAt == null ? null : "张伟", executedAt,
                status.equals("REMOVED") ? 2 : (status.equals("PENDING") ? 0 : 1), LocalDateTime.now());
        return jdbc.queryForObject("SELECT id FROM safety_measure WHERE ticket_id=? AND seq_no=?",
                Long.class, ticketId, seq);
    }

    private MeasureConfirmRequest person(String name) {
        MeasureConfirmRequest r = new MeasureConfirmRequest();
        r.setPerson(name);
        return r;
    }

    private long permitLogCount(long ticketId) {
        return logRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId).stream()
                .filter(l -> WorkTicketLog.ACTION_PERMIT.equals(l.getAction())).count();
    }

    // ---------- 用例 ----------

    /** 许可前：安措侧有待确认项、关联操作票步骤未执行，任一侧未完成都禁止许可并逐项指出卡点。 */
    @Test
    void permitBlockedUntilBothSidesClosed() {
        issuedTicket(100, 6);
        opTicketWithSteps(10, 6, "CZP-10",
                List.of(6, 7, 8),
                List.of("合上217接地刀闸", "装设遮栏", "悬挂标示牌"),
                List.of(false, false, false));

        long m1 = measure(100, 1, "EARTH_SWITCH", 6, "217接地刀闸", "合上接地刀闸", "EXECUTED", 10L, 6, null, null);
        long m2 = measure(100, 2, "BARRIER", 6, "母联间隔", "装设遮栏", "PENDING", 10L, 7, null, null);
        long m3 = measure(100, 3, "SIGNBOARD", 6, "操作把手", "悬挂标示牌", "PENDING", 10L, 8, null, null);

        BizException ex = assertThrows(BizException.class,
                () -> workTicketService.sign(100L, WorkTicket.STAGE_PERMIT, "刘玉兰"));
        String msg = ex.getMessage();
        assertTrue(msg.contains("禁止许可"), msg);
        assertTrue(msg.contains("安全措施侧"), "应指出安措侧卡点：" + msg);
        assertTrue(msg.contains("关联操作票侧"), "应指出操作票侧卡点：" + msg);
        assertTrue(msg.contains("尚未装设确认"), msg);
        assertTrue(msg.contains("尚未执行"), msg);
        assertEquals("ISSUED", jdbc.queryForObject("SELECT status FROM work_ticket WHERE id=100", String.class));
        assertEquals(0, permitLogCount(100));

        // 补齐安措侧
        workTicketService.executeMeasure(100L, m2, person("张伟"));
        workTicketService.executeMeasure(100L, m3, person("张伟"));
        // 仅安措完成、操作票侧仍未执行 —— 仍应被拦
        BizException ex2 = assertThrows(BizException.class,
                () -> workTicketService.sign(100L, WorkTicket.STAGE_PERMIT, "刘玉兰"));
        assertTrue(ex2.getMessage().contains("关联操作票侧"), ex2.getMessage());
        assertTrue(!ex2.getMessage().contains("尚未装设确认"), "安措侧应已清空：" + ex2.getMessage());

        // 补齐操作票侧
        setStepDone(10, 6);
        setStepDone(10, 7);
        setStepDone(10, 8);
        Map<String, Object> result = workTicketService.sign(100L, WorkTicket.STAGE_PERMIT, "刘玉兰");
        assertEquals("PERMITTED", result.get("status"));
        assertEquals(1, permitLogCount(100));
        // 许可后措施呈现为「待拆除」
        for (SafetyMeasure m : measureRepository.findByTicketIdOrderBySeqNoAscIdAsc(100L)) {
            assertEquals(SafetyMeasure.STATUS_PENDING_REMOVE, m.getStatus());
            assertNotNull(m.getExecutePerson());
        }
    }

    /** 已许可后不能直接删改；撤回 → 作废旧项（痕迹保留）→ 新增项待执行 → 重新闭环再许可。 */
    @Test
    void withdrawKeepsTracesAndReturnsChangesToPending() {
        issuedTicket(100, 6);
        opTicketWithSteps(10, 6, "CZP-10",
                List.of(6, 7, 8),
                List.of("合上217接地刀闸", "装设遮栏", "悬挂标示牌"),
                List.of(true, true, true));
        long m1 = measure(100, 1, "EARTH_SWITCH", 6, "217接地刀闸", "合上接地刀闸", "EXECUTED", 10L, 6, null, null);
        long m2 = measure(100, 2, "BARRIER", 6, "母联间隔", "装设遮栏", "EXECUTED", 10L, 7, null, null);
        long m3 = measure(100, 3, "SIGNBOARD", 6, "操作把手", "悬挂标示牌", "EXECUTED", 10L, 8, null, null);

        workTicketService.sign(100L, WorkTicket.STAGE_PERMIT, "刘玉兰");
        assertEquals(SafetyMeasure.STATUS_PENDING_REMOVE, measureRepository.findById(m1).orElseThrow().getStatus());

        // 已许可后禁止直接新增 / 作废
        MeasureRequest req = new MeasureRequest();
        req.setMeasureType("GROUND_WIRE");
        req.setBayId(6L);
        req.setLocation("新接地点");
        req.setRequiredAction("装设接地线 1 组");
        req.setOpTicketId(10L);
        req.setOpStepNo(6);
        assertThrows(BizException.class, () -> workTicketService.addMeasure(100L, req));

        // 撤回许可
        Map<String, Object> withdrawn = workTicketService.withdrawPermit(100L, "刘玉兰", "现场核位变更接地点");
        assertEquals("ISSUED", withdrawn.get("status"));
        // 待拆除还原为已执行，执行人/时间保留
        SafetyMeasure m1after = measureRepository.findById(m1).orElseThrow();
        assertEquals(SafetyMeasure.STATUS_EXECUTED, m1after.getStatus());
        assertNotNull(m1after.getExecutePerson());
        assertNotNull(m1after.getExecuteTime());

        // 作废旧项：痕迹保留
        com.substation.ops.dto.MeasureCancelRequest cr = new com.substation.ops.dto.MeasureCancelRequest();
        cr.setRemark("桩头锈蚀改点");
        workTicketService.cancelMeasure(100L, m1, cr);
        SafetyMeasure m1canceled = measureRepository.findById(m1).orElseThrow();
        assertEquals(SafetyMeasure.STATUS_CANCELED, m1canceled.getStatus());
        assertNotNull(m1canceled.getExecutePerson(), "原装设确认痕迹不能被抹掉");
        WorkTicketLog cancelLog = logRepository.findByTicketIdOrderByCreatedAtAscIdAsc(100L).stream()
                .filter(l -> WorkTicketLog.ACTION_MEASURE_CANCEL.equals(l.getAction())).findFirst().orElseThrow();
        assertTrue(cancelLog.getDetail().contains("张伟"), "作废痕迹应保留原执行人：" + cancelLog.getDetail());

        // 新增项回到待执行；此时重新许可被拦
        workTicketService.addMeasure(100L, req);
        SafetyMeasure added = measureRepository.findByTicketIdOrderBySeqNoAscIdAsc(100L).stream()
                .filter(m -> "新接地点".equals(m.getLocation())).findFirst().orElseThrow();
        assertEquals(SafetyMeasure.STATUS_PENDING, added.getStatus());
        assertThrows(BizException.class, () -> workTicketService.sign(100L, WorkTicket.STAGE_PERMIT, "刘玉兰"));

        workTicketService.executeMeasure(100L, added.getId(), person("孙磊"));
        Map<String, Object> repermitted = workTicketService.sign(100L, WorkTicket.STAGE_PERMIT, "刘玉兰");
        assertEquals("PERMITTED", repermitted.get("status"));
        assertEquals(2, permitLogCount(100), "两次许可都应有痕迹");
        assertTrue(logRepository.findByTicketIdOrderByCreatedAtAscIdAsc(100L).stream()
                .anyMatch(l -> WorkTicketLog.ACTION_WITHDRAW.equals(l.getAction())));
    }

    /** 终结前：措施全部拆除 ＋ 关联恢复操作票恢复步骤完成，缺一不可。 */
    @Test
    void terminateBlockedUntilRemovedAndRestoreStepsDone() {
        issuedTicket(100, 6);
        opTicketWithSteps(10, 6, "CZP-10", List.of(6, 7), List.of("合上接地刀闸", "装设遮栏"), List.of(true, true));
        opTicketWithSteps(11, 6, "CZF-11", List.of(3, 4), List.of("拉开接地刀闸", "拆除遮栏"), List.of(false, false));

        long m1 = measure(100, 1, "EARTH_SWITCH", 6, "217", "合上", "EXECUTED", 10L, 6, 11L, 3);
        long m2 = measure(100, 2, "BARRIER", 6, "通道", "装设遮栏", "EXECUTED", 10L, 7, 11L, 4);
        workTicketService.sign(100L, WorkTicket.STAGE_PERMIT, "刘玉兰");

        assertThrows(BizException.class,
                () -> workTicketService.sign(100L, WorkTicket.STAGE_TERMINATE, "黄志强"));

        workTicketService.removeMeasure(100L, m1, person("张伟"));
        // 只拆了一项、恢复步骤未执行：仍被拦，且两侧都有卡点
        BizException ex = assertThrows(BizException.class,
                () -> workTicketService.sign(100L, WorkTicket.STAGE_TERMINATE, "黄志强"));
        assertTrue(ex.getMessage().contains("尚未拆除确认"), ex.getMessage());
        assertTrue(ex.getMessage().contains("恢复"), ex.getMessage());

        workTicketService.removeMeasure(100L, m2, person("张伟"));
        assertThrows(BizException.class,
                () -> workTicketService.sign(100L, WorkTicket.STAGE_TERMINATE, "黄志强"));

        setStepDone(11, 3);
        setStepDone(11, 4);
        Map<String, Object> result = workTicketService.sign(100L, WorkTicket.STAGE_TERMINATE, "黄志强");
        assertEquals("TERMINATED", result.get("status"));
        assertEquals("黄志强", result.get("terminator"));
    }

    /** 重复装设确认不产生第二条执行记录，版本号只推进一次。 */
    @Test
    void duplicateExecuteIsIdempotent() {
        issuedTicket(102, 6);
        opTicketWithSteps(20, 6, "CZP-20", List.of(1), List.of("合上接地刀闸"), List.of(true));
        long m = measure(102, 1, "EARTH_SWITCH", 6, "217", "合上", "PENDING", 20L, 1, null, null);

        var r1 = new MeasureConfirmRequest();
        r1.setPerson("张伟");
        r1.setVersion(0);
        workTicketService.executeMeasure(102L, m, r1);
        assertThrows(BizException.class, () -> workTicketService.executeMeasure(102L, m, person("张伟")));

        SafetyMeasure done = measureRepository.findById(m).orElseThrow();
        assertEquals("EXECUTED", done.getStatus());
        assertEquals(1, done.getVersion());
        long execLogs = logRepository.findByTicketIdOrderByCreatedAtAscIdAsc(102L).stream()
                .filter(l -> WorkTicketLog.ACTION_MEASURE_EXECUTE.equals(l.getAction())).count();
        assertEquals(1, execLogs);
    }

    /** 两人同时发起许可：只有一次有效推进，后提交者看到最新结果。 */
    @Test
    void concurrentPermitAdvancesOnce() throws Exception {
        issuedTicket(103, 6);
        opTicketWithSteps(30, 6, "CZP-30", List.of(1), List.of("合上接地刀闸"), List.of(true));
        measure(103, 1, "EARTH_SWITCH", 6, "217", "合上", "EXECUTED", 30L, 1, null, null);

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Runnable permit = () -> {
            try {
                workTicketService.sign(103L, WorkTicket.STAGE_PERMIT, "刘玉兰");
                ok.incrementAndGet();
            } catch (BizException e) {
                rejected.incrementAndGet();
            }
        };
        Thread a = new Thread(permit);
        Thread b = new Thread(permit);
        a.start();
        b.start();
        a.join(10000);
        b.join(10000);

        assertEquals(1, ok.get(), "恰好一次许可成功");
        assertEquals(1, rejected.get(), "后提交者被拒并可见最新状态");
        assertEquals("PERMITTED", jdbc.queryForObject("SELECT status FROM work_ticket WHERE id=103", String.class));
        assertEquals(1, permitLogCount(103), "不得生成重复许可记录");
    }

    /** 两人同时勾选同一倒闸步骤：只有一次有效执行（票仍执行中时的重复提交被拒，不留重复记录）。 */
    @Test
    void concurrentOperationStepExecutesOnce() {
        opTicketWithSteps(40, 6, "CZP-40", List.of(1, 2),
                List.of("核对调度令", "拉开断路器"), List.of(false, false));
        assertDoesNotThrow(() -> operationTicketService.executeStep(40L, 1, "张伟"));
        BizException ex = assertThrows(BizException.class,
                () -> operationTicketService.executeStep(40L, 1, "李雪"));
        assertTrue(ex.getMessage().contains("已由"), ex.getMessage());
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_step WHERE ticket_id=40 AND step_no=1 AND status='DONE'", Integer.class);
        assertEquals(1, count);
        String person = jdbc.queryForObject(
                "SELECT execute_person FROM operation_step WHERE ticket_id=40 AND step_no=1", String.class);
        assertEquals("张伟", person, "先提交者的执行记录不应被覆盖");
    }

    /** 接地线/接地刀闸未关联同间隔操作票步骤时禁止许可；遮栏/标示牌允许无倒闸步骤。 */
    @Test
    void groundMeasuresRequireLinkedStepBarrierDoesNot() {
        issuedTicket(104, 6);
        // 遮栏无关联票
        measure(104, 1, "BARRIER", 6, "作业区", "装设围栏", "EXECUTED", null, null, null, null);
        Map<String, Object> permitted = workTicketService.sign(104L, WorkTicket.STAGE_PERMIT, "刘玉兰");
        assertEquals("PERMITTED", permitted.get("status"));

        // 接地线无关联票 —— 撤回后新增，许可必须被拦
        workTicketService.withdrawPermit(104L, "刘玉兰", "补接地");
        MeasureRequest gw = new MeasureRequest();
        gw.setMeasureType("GROUND_WIRE");
        gw.setBayId(6L);
        gw.setLocation("线路侧");
        gw.setRequiredAction("装设接地线 1 组");
        workTicketService.addMeasure(104L, gw);
        long gwId = measureRepository.findByTicketIdOrderBySeqNoAscIdAsc(104L).stream()
                .filter(m -> "线路侧".equals(m.getLocation())).findFirst().orElseThrow().getId();
        BizException ex = assertThrows(BizException.class,
                () -> workTicketService.sign(104L, WorkTicket.STAGE_PERMIT, "刘玉兰"));
        assertTrue(ex.getMessage().contains("必须关联同间隔操作票"), ex.getMessage());
        assertNull(ex.getCause());

        // 关联一张跨间隔票也要被拒
        jdbc.update("INSERT INTO bay(id,substation_id,code,name,bay_type,voltage_level,status) "
                + "VALUES (8,1,'HX-1L','35kV河西线','OUTGOING','35kV','COLD_STANDBY')");
        opTicketWithSteps(50, 8, "CZP-50", List.of(4), List.of("挂接地线"), List.of(true));
        MeasureRequest gw2 = new MeasureRequest();
        gw2.setMeasureType("GROUND_WIRE");
        gw2.setBayId(6L);
        gw2.setLocation("线路侧2");
        gw2.setRequiredAction("装设接地线 1 组");
        gw2.setOpTicketId(50L);
        gw2.setOpStepNo(4);
        assertThrows(BizException.class, () -> workTicketService.addMeasure(104L, gw2));
    }
}
