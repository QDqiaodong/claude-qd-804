package com.substation.ops.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.substation.ops.common.BizException;
import com.substation.ops.common.Labels;
import com.substation.ops.domain.Bay;
import com.substation.ops.domain.Defect;
import com.substation.ops.domain.OperationStep;
import com.substation.ops.domain.OperationTicket;
import com.substation.ops.domain.SafetyMeasure;
import com.substation.ops.domain.WorkTicket;
import com.substation.ops.domain.WorkTicketLog;
import com.substation.ops.dto.MeasureCancelRequest;
import com.substation.ops.dto.MeasureConfirmRequest;
import com.substation.ops.dto.MeasureRequest;
import com.substation.ops.dto.TicketStatusCount;
import com.substation.ops.dto.WorkTicketCreateRequest;
import com.substation.ops.repository.BayRepository;
import com.substation.ops.repository.DefectRepository;
import com.substation.ops.repository.OperationStepRepository;
import com.substation.ops.repository.OperationTicketRepository;
import com.substation.ops.repository.SafetyMeasureRepository;
import com.substation.ops.repository.WorkTicketLogRepository;
import com.substation.ops.repository.WorkTicketRepository;

/**
 * 模块四：工作票。三审（签发 / 许可 / 终结）之外，安全措施是逐项可确认的执行闭环：
 *
 * 1. 每项措施区分接地线 / 接地刀闸 / 遮栏 / 标示牌，记录间隔、位置、要求动作、执行人与时间、当前阶段；
 * 2. 签发后、许可前，要求装设/合上的措施必须全部确认，且同间隔关联操作票对应步骤也已执行，
 *    任一侧未完成都逐项指出卡点并禁止许可；
 * 3. 已许可后不得删改已确认措施：现场变更必须先撤回许可，新增/变更项回到待执行，旧痕迹保留；
 * 4. 终结前需要拆除的措施必须全部拆除，关联恢复操作票的对应恢复步骤也要执行完毕；
 * 5. 行锁 + 措施乐观版本号：两人同时确认/许可只有一次有效推进，后提交者看到最新结果。
 */
@Service
public class WorkTicketService {

    private static final List<String> MEASURE_TYPES = List.of(
            SafetyMeasure.TYPE_GROUND_WIRE,
            SafetyMeasure.TYPE_EARTH_SWITCH,
            SafetyMeasure.TYPE_BARRIER,
            SafetyMeasure.TYPE_SIGNBOARD);

    private final WorkTicketRepository workTicketRepository;
    private final BayRepository bayRepository;
    private final DefectRepository defectRepository;
    private final SafetyMeasureRepository measureRepository;
    private final WorkTicketLogRepository logRepository;
    private final OperationTicketRepository operationTicketRepository;
    private final OperationStepRepository operationStepRepository;

    public WorkTicketService(WorkTicketRepository workTicketRepository,
                             BayRepository bayRepository,
                             DefectRepository defectRepository,
                             SafetyMeasureRepository measureRepository,
                             WorkTicketLogRepository logRepository,
                             OperationTicketRepository operationTicketRepository,
                             OperationStepRepository operationStepRepository) {
        this.workTicketRepository = workTicketRepository;
        this.bayRepository = bayRepository;
        this.defectRepository = defectRepository;
        this.measureRepository = measureRepository;
        this.logRepository = logRepository;
        this.operationTicketRepository = operationTicketRepository;
        this.operationStepRepository = operationStepRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOfDay(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<WorkTicket> tickets = workTicketRepository.findByIssueDateOrderByIdAsc(day);
        Map<Long, Bay> bayMap = loadBayMap();
        List<Map<String, Object>> result = new ArrayList<>();
        for (WorkTicket t : tickets) {
            List<SafetyMeasure> measures = measureRepository.findByTicketIdOrderBySeqNoAscIdAsc(t.getId());
            result.add(toMap(t, bayMap.get(t.getBayId()), measures, false));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        WorkTicket t = workTicketRepository.findById(id)
                .orElseThrow(() -> new BizException("工作票不存在"));
        Bay bay = t.getBayId() == null ? null : bayRepository.findById(t.getBayId()).orElse(null);
        return toMap(t, bay, measureRepository.findByTicketIdOrderBySeqNoAscIdAsc(t.getId()), true);
    }

    /**
     * 新建工作票：票号交由存储过程生成后回写。
     */
    @Transactional
    public Map<String, Object> create(WorkTicketCreateRequest request) {
        Bay bay = bayRepository.findById(request.getBayId())
                .orElseThrow(() -> new BizException("所选间隔不存在"));

        // 规则一：运行中的间隔不允许下达检修工作票
        if (Bay.STATUS_RUNNING.equals(bay.getStatus())) {
            throw new BizException("间隔【" + bay.getName() + "】当前处于运行状态，不允许下达检修工作票，请先将间隔转冷备或检修");
        }

        LocalDate issueDate = request.getIssueDate() == null ? LocalDate.now() : request.getIssueDate();

        // 规则二：同一张间隔当天不能有两张并行的工作票
        boolean alreadyWorking = workTicketRepository.findByIssueDateOrderByIdAsc(issueDate).stream()
                .anyMatch(t -> t.getBayId().equals(bay.getId())
                        && (WorkTicket.STATUS_PERMITTED.equals(t.getStatus())
                        || WorkTicket.STATUS_ISSUED.equals(t.getStatus())
                        || WorkTicket.STATUS_DRAFT.equals(t.getStatus())));
        if (alreadyWorking) {
            throw new BizException("间隔【" + bay.getName() + "】在 " + issueDate + " 已有未终结的工作票，不能重复开票");
        }

        String issueDateText = issueDate.toString();
        String ticketNo = workTicketRepository.nextTicketNo(bay.getId(), issueDateText);

        WorkTicket ticket = new WorkTicket();
        ticket.setBayId(bay.getId());
        ticket.setIssueDate(issueDate);
        ticket.setTicketNo(ticketNo);
        ticket.setWorkLeader(request.getWorkLeader());
        ticket.setTeamMembers(request.getTeamMembers());
        ticket.setWorkContent(request.getWorkContent());
        ticket.setPowerCutRange(request.getPowerCutRange());
        ticket.setSafetyMeasures(request.getSafetyMeasures());
        ticket.setPlanStartTime(request.getPlanStartTime());
        ticket.setPlanEndTime(request.getPlanEndTime());
        ticket.setStatus(WorkTicket.STATUS_DRAFT);

        WorkTicket saved = workTicketRepository.saveAndFlush(ticket);
        return toMap(saved, bay, List.of(), true);
    }

    // ============================ 安全措施：逐项闭环 ============================

    /** 新增一条措施：待签发阶段编排安措；撤回许可后也可增补（新增项为待执行）。 */
    @Transactional
    public Map<String, Object> addMeasure(Long ticketId, MeasureRequest req) {
        WorkTicket ticket = lockTicket(ticketId);
        String status = ticket.getStatus();
        if (WorkTicket.STATUS_PERMITTED.equals(status)) {
            throw new BizException("工作票已许可，不能直接新增安全措施；确有现场变更请先撤回许可");
        }
        if (WorkTicket.STATUS_TERMINATED.equals(status) || WorkTicket.STATUS_CANCELED.equals(status)) {
            throw new BizException("工作票已" + Labels.workStatus(status) + "，不能再新增安全措施");
        }
        validateMeasureRequest(req);
        validateLinkSteps(req);

        List<SafetyMeasure> measures = measureRepository.findByTicketIdOrderBySeqNoAscIdAsc(ticketId);
        int nextSeq = measures.stream().map(SafetyMeasure::getSeqNo).reduce(0, Integer::max) + 1;

        SafetyMeasure m = new SafetyMeasure();
        m.setTicketId(ticketId);
        m.setSeqNo(nextSeq);
        m.setMeasureType(req.getMeasureType());
        m.setBayId(req.getBayId());
        m.setLocation(req.getLocation());
        m.setRequiredAction(req.getRequiredAction());
        m.setSignboardText(req.getSignboardText());
        m.setOpTicketId(req.getOpTicketId());
        m.setOpStepNo(req.getOpStepNo());
        m.setRestoreTicketId(req.getRestoreTicketId());
        m.setRestoreStepNo(req.getRestoreStepNo());
        m.setStatus(SafetyMeasure.STATUS_PENDING);
        m.setVersion(0);
        m.setCreatedAt(LocalDateTime.now());
        measureRepository.saveAndFlush(m);

        writeLog(ticketId, WorkTicketLog.ACTION_MEASURE_ADD, m.getId(), null,
                "新增第 " + nextSeq + " 项措施【" + Labels.measureType(m.getMeasureType())
                        + "｜" + m.getLocation() + "｜" + m.getRequiredAction() + "】，阶段：待执行");
        return detail(ticketId);
    }

    /** 变更一条尚未确认的措施内容（关联步骤等）。已确认项不得直接改。 */
    @Transactional
    public Map<String, Object> updateMeasure(Long ticketId, Long measureId, MeasureRequest req) {
        WorkTicket ticket = lockTicket(ticketId);
        String status = ticket.getStatus();
        if (WorkTicket.STATUS_PERMITTED.equals(status)) {
            throw new BizException("工作票已许可，已确认措施不能直接修改；请先撤回许可，再对变更项重新执行");
        }
        if (WorkTicket.STATUS_TERMINATED.equals(status) || WorkTicket.STATUS_CANCELED.equals(status)) {
            throw new BizException("工作票已" + Labels.workStatus(status) + "，不能再变更安全措施");
        }
        SafetyMeasure m = lockMeasureOf(ticketId, measureId);
        if (SafetyMeasure.STATUS_CANCELED.equals(m.getStatus())) {
            throw new BizException("该措施已作废（现场变更痕迹），不能再修改");
        }
        if (hasConfirmed(m)) {
            throw new BizException("该项已有装设/拆除确认记录，不能直接修改；请先撤回许可并通过「作废 + 新增」变更，以保留原确认痕迹");
        }
        validateMeasureRequest(req);
        validateLinkSteps(req);

        m.setMeasureType(req.getMeasureType());
        m.setBayId(req.getBayId());
        m.setLocation(req.getLocation());
        m.setRequiredAction(req.getRequiredAction());
        m.setSignboardText(req.getSignboardText());
        m.setOpTicketId(req.getOpTicketId());
        m.setOpStepNo(req.getOpStepNo());
        m.setRestoreTicketId(req.getRestoreTicketId());
        m.setRestoreStepNo(req.getRestoreStepNo());
        measureRepository.saveAndFlush(m);

        writeLog(ticketId, WorkTicketLog.ACTION_MEASURE_UPDATE, m.getId(), null,
                "变更第 " + m.getSeqNo() + " 项措施【" + Labels.measureType(m.getMeasureType())
                        + "｜" + m.getLocation() + "｜" + m.getRequiredAction() + "】");
        return detail(ticketId);
    }

    /**
     * 装设 / 合上确认：待执行 → 已执行。只能在「签发后、许可前」（含撤回许可后）进行；
     * 许可后新增/变更项必须先撤回许可，才能重新确认。
     */
    @Transactional
    public Map<String, Object> executeMeasure(Long ticketId, Long measureId, MeasureConfirmRequest req) {
        WorkTicket ticket = lockTicket(ticketId);
        String ticketStatus = ticket.getStatus();
        if (!WorkTicket.STATUS_ISSUED.equals(ticketStatus)) {
            throw new BizException("装设确认只能在工作票签发后、许可前进行；当前状态为「"
                    + Labels.workStatus(ticketStatus) + "」"
                    + (WorkTicket.STATUS_PERMITTED.equals(ticketStatus)
                    ? "，现场变更请先撤回许可" : ""));
        }
        SafetyMeasure m;
        try {
            m = lockMeasureOf(ticketId, measureId);
            if (SafetyMeasure.STATUS_EXECUTED.equals(m.getStatus())
                    || SafetyMeasure.STATUS_PENDING_REMOVE.equals(m.getStatus())) {
                // 幂等：同一值班员重复点击 / 并发后到者 —— 不产生第二条执行记录
                throw new BizException("第 " + m.getSeqNo() + " 项【" + Labels.measureType(m.getMeasureType())
                        + "】已由 " + m.getExecutePerson() + " 于 " + fmt(m.getExecuteTime())
                        + " 确认装设，请勿重复确认（页面已刷新为最新结果）");
            }
            if (SafetyMeasure.STATUS_REMOVED.equals(m.getStatus())) {
                if (WorkTicket.STATUS_ISSUED.equals(ticketStatus)) {
                    // 撤回许可后或许可前发现误拆：允许重新装设；旧的拆除记录已留在操作痕迹中
                } else {
                    throw new BizException("第 " + m.getSeqNo() + " 项已拆除，不能再确认装设");
                }
            }
            if (SafetyMeasure.STATUS_CANCELED.equals(m.getStatus())) {
                throw new BizException("第 " + m.getSeqNo() + " 项已随现场变更作废，不能确认装设");
            }
            if (req.getVersion() != null && !req.getVersion().equals(m.getVersion())) {
                throw new BizException("第 " + m.getSeqNo() + " 项措施刚被其他值班员更新过，请刷新后查看最新结果再操作");
            }
            LocalDateTime now = LocalDateTime.now();
            m.setStatus(SafetyMeasure.STATUS_EXECUTED);
            m.setExecutePerson(req.getPerson());
            m.setExecuteTime(now);
            m.setVersion((m.getVersion() == null ? 0 : m.getVersion()) + 1);
            measureRepository.saveAndFlush(m);
        } catch (org.springframework.dao.CannotAcquireLockException e) {
            throw new BizException("第 " + measureId + " 项措施正由另一名值班员确认，本次提交未生效，请刷新查看最新结果");
        }

        writeLog(ticketId, WorkTicketLog.ACTION_MEASURE_EXECUTE, m.getId(), req.getPerson(),
                "第 " + m.getSeqNo() + " 项【" + Labels.measureType(m.getMeasureType()) + "｜" + m.getLocation()
                        + "】" + m.getRequiredAction() + " 已装设确认");
        return detail(ticketId);
    }

    /**
     * 拆除 / 拉开确认：在装（已执行/待拆除）→ 已拆除。
     * 许可后正常作业结束拆除；撤回许可（回到已签发）后也允许拆除在装措施，
     * 以便现场恢复后作废或重新编排。
     */
    @Transactional
    public Map<String, Object> removeMeasure(Long ticketId, Long measureId, MeasureConfirmRequest req) {
        WorkTicket ticket = lockTicket(ticketId);
        String ticketStatus = ticket.getStatus();
        if (!WorkTicket.STATUS_PERMITTED.equals(ticketStatus)
                && !WorkTicket.STATUS_ISSUED.equals(ticketStatus)) {
            throw new BizException("当前票面状态为「" + Labels.workStatus(ticketStatus)
                    + "」，不能确认拆除；应在已许可作业结束后，或撤回许可后拆除");
        }
        SafetyMeasure m;
        try {
            m = lockMeasureOf(ticketId, measureId);
            if (SafetyMeasure.STATUS_REMOVED.equals(m.getStatus())) {
                throw new BizException("第 " + m.getSeqNo() + " 项【" + Labels.measureType(m.getMeasureType())
                        + "】已由 " + m.getRemovePerson() + " 于 " + fmt(m.getRemoveTime())
                        + " 确认拆除，请勿重复确认（页面已刷新为最新结果）");
            }
            if (SafetyMeasure.STATUS_PENDING.equals(m.getStatus())) {
                throw new BizException("第 " + m.getSeqNo() + " 项尚未装设确认，不能拆除");
            }
            if (SafetyMeasure.STATUS_CANCELED.equals(m.getStatus())) {
                throw new BizException("第 " + m.getSeqNo() + " 项已作废，无需拆除");
            }
            if (req.getVersion() != null && !req.getVersion().equals(m.getVersion())) {
                throw new BizException("第 " + m.getSeqNo() + " 项措施刚被其他值班员更新过，请刷新后查看最新结果再操作");
            }
            m.setStatus(SafetyMeasure.STATUS_REMOVED);
            m.setRemovePerson(req.getPerson());
            m.setRemoveTime(LocalDateTime.now());
            m.setVersion((m.getVersion() == null ? 0 : m.getVersion()) + 1);
            measureRepository.saveAndFlush(m);
        } catch (org.springframework.dao.CannotAcquireLockException e) {
            throw new BizException("第 " + measureId + " 项措施正由另一名值班员操作，本次提交未生效，请刷新查看最新结果");
        }

        writeLog(ticketId, WorkTicketLog.ACTION_MEASURE_REMOVE, m.getId(), req.getPerson(),
                "第 " + m.getSeqNo() + " 项【" + Labels.measureType(m.getMeasureType()) + "｜" + m.getLocation()
                        + "】已拆除确认");
        return detail(ticketId);
    }

    /**
     * 现场变更：作废一项措施。必须先撤回许可；原执行人/执行时间等确认痕迹原样保留，
     * 作废项不再参与许可/终结核对。
     */
    @Transactional
    public Map<String, Object> cancelMeasure(Long ticketId, Long measureId, MeasureCancelRequest req) {
        WorkTicket ticket = lockTicket(ticketId);
        if (WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus())) {
            throw new BizException("工作票仍处于许可状态，不能作废措施；请先撤回许可，再进行现场变更");
        }
        if (WorkTicket.STATUS_TERMINATED.equals(ticket.getStatus()) || WorkTicket.STATUS_CANCELED.equals(ticket.getStatus())) {
            throw new BizException("工作票已" + Labels.workStatus(ticket.getStatus()) + "，不能再作废措施");
        }
        SafetyMeasure m = lockMeasureOf(ticketId, measureId);
        if (SafetyMeasure.STATUS_CANCELED.equals(m.getStatus())) {
            throw new BizException("该项已作废，请勿重复操作");
        }
        m.setStatus(SafetyMeasure.STATUS_CANCELED);
        m.setChangeRemark(req.getRemark());
        m.setVersion((m.getVersion() == null ? 0 : m.getVersion()) + 1);
        measureRepository.saveAndFlush(m);

        String trace = m.getExecutePerson() == null
                ? "该项原处于待执行，无确认痕迹"
                : "该项原由 " + m.getExecutePerson() + " 于 " + fmt(m.getExecuteTime()) + " 装设确认，原记录保留";
        writeLog(ticketId, WorkTicketLog.ACTION_MEASURE_CANCEL, m.getId(), null,
                "第 " + m.getSeqNo() + " 项【" + Labels.measureType(m.getMeasureType()) + "｜" + m.getLocation()
                        + "】现场变更作废：" + req.getRemark() + "（" + trace + "）");
        return detail(ticketId);
    }

    // ============================ 三审 + 撤回 + 作废 ============================

    /**
     * 三审签字：ISSUE → PERMIT → TERMINATE 必须依次点亮。
     * 许可、终结是双侧闸道：安全措施侧 + 关联操作票侧都核对，缺一不可。
     */
    @Transactional
    public Map<String, Object> sign(Long id, String stage, String person) {
        WorkTicket ticket = lockTicket(id);

        switch (stage) {
            case WorkTicket.STAGE_ISSUE -> {
                if (!WorkTicket.STATUS_DRAFT.equals(ticket.getStatus())) {
                    throw new BizException("该票已签发，不能再由签发人签字（最新状态："
                            + Labels.workStatus(ticket.getStatus()) + "，请刷新查看）");
                }
                ticket.setIssuer(person);
                ticket.setIssueTime(LocalDateTime.now());
                ticket.setStatus(WorkTicket.STATUS_ISSUED);
                workTicketRepository.saveAndFlush(ticket);
                writeLog(id, WorkTicketLog.ACTION_ISSUE, null, person, "工作票签发");
            }
            case WorkTicket.STAGE_PERMIT -> {
                if (WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus())) {
                    // 两人同时发起许可：后提交者拿到的已是最新状态，只提示、不重复推进
                    throw new BizException("该票已由 " + ticket.getPermittor() + " 于 " + fmt(ticket.getPermitTime())
                            + " 许可，无需重复许可（请刷新查看最新票面）");
                }
                if (!WorkTicket.STATUS_ISSUED.equals(ticket.getStatus())) {
                    throw new BizException("尚未经签发人签发，许可人不能先签字");
                }
                // 双侧闸道：① 安措侧 ② 同间隔关联操作票步骤侧
                GateCheck gate = evaluatePermitGate(ticket);
                if (!gate.ok) {
                    throw new BizException(buildGateMessage("许可", gate));
                }
                ticket.setPermittor(person);
                ticket.setPermitTime(LocalDateTime.now());
                ticket.setStatus(WorkTicket.STATUS_PERMITTED);
                // 许可时所有已装设确认的措施在票面上进入「待拆除」阶段
                for (SafetyMeasure m : measureRepository.findByTicketIdOrderBySeqNoAscIdAsc(id)) {
                    if (SafetyMeasure.STATUS_EXECUTED.equals(m.getStatus())) {
                        m.setStatus(SafetyMeasure.STATUS_PENDING_REMOVE);
                        m.setVersion((m.getVersion() == null ? 0 : m.getVersion()) + 1);
                        measureRepository.save(m);
                    }
                }
                workTicketRepository.saveAndFlush(ticket);
                if (!Bay.STATUS_MAINTENANCE.equals(bayStatus(ticket.getBayId()))) {
                    bayRepository.findById(ticket.getBayId()).ifPresent(bay -> {
                        bay.setStatus(Bay.STATUS_MAINTENANCE);
                        bayRepository.save(bay);
                    });
                }
                writeLog(id, WorkTicketLog.ACTION_PERMIT, null, person,
                        "许可开工：安全措施与关联操作票装设步骤已逐项核对一致");
            }
            case WorkTicket.STAGE_TERMINATE -> {
                if (!WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus())) {
                    throw new BizException("尚未许可开工，终结人不能先签字（当前状态："
                            + Labels.workStatus(ticket.getStatus()) + "）");
                }
                List<Defect> overdue = defectRepository.findByBayIdOrderByDueDateAsc(ticket.getBayId()).stream()
                        .filter(d -> !Defect.STATUS_ACCEPTED.equals(d.getStatus()))
                        .filter(d -> d.getDueDate() != null && LocalDate.now().isAfter(d.getDueDate()))
                        .toList();
                if (!overdue.isEmpty()) {
                    Defect worst = overdue.get(0);
                    throw new BizException("该间隔尚有 " + overdue.size() + " 条超期未消缺（如 " + worst.getDefectNo()
                            + " 已超期 " + java.time.temporal.ChronoUnit.DAYS.between(worst.getDueDate(), LocalDate.now())
                            + " 天），消缺闭环前不允许终结工作票");
                }
                GateCheck gate = evaluateTerminateGate(ticket);
                if (!gate.ok) {
                    throw new BizException(buildGateMessage("终结", gate));
                }
                ticket.setTerminator(person);
                ticket.setTerminateTime(LocalDateTime.now());
                ticket.setStatus(WorkTicket.STATUS_TERMINATED);
                workTicketRepository.saveAndFlush(ticket);
                writeLog(id, WorkTicketLog.ACTION_TERMINATE, null, person,
                        "工作票终结：应拆除措施与关联恢复操作票步骤已逐项核对一致");
            }
            default -> throw new BizException("未知的签字环节：" + stage);
        }

        // 规则三：签发 / 许可 / 终结三人不得为同一人
        ensureDifferentPersons(ticket);
        return detail(id);
    }

    /**
     * 撤回许可：已许可 → 已签发。已确认措施的执行人/时间原样保留；
     * 之后新增项回到待执行，作废项留痕，再次许可必须重新走双侧闸道。
     */
    @Transactional
    public Map<String, Object> withdrawPermit(Long id, String person, String reason) {
        WorkTicket ticket = workTicketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("工作票不存在"));
        if (!WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus())) {
            throw new BizException("只有已许可的工作票才能撤回许可，当前状态为「"
                    + Labels.workStatus(ticket.getStatus()) + "」");
        }
        ticket.setStatus(WorkTicket.STATUS_ISSUED);
        // 许可人签字与许可时间保留为历史痕迹；清空会抹掉已发生记录，这里不动。
        workTicketRepository.saveAndFlush(ticket);
        // 票面上在装措施由「待拆除」还原为「已执行」：现场仍装着，但已退出许可作业态；
        // 执行人/执行时间一律保留。新增项保持待执行，重新许可须再走双侧闸道。
        for (SafetyMeasure m : measureRepository.findByTicketIdOrderBySeqNoAscIdAsc(id)) {
            if (SafetyMeasure.STATUS_PENDING_REMOVE.equals(m.getStatus())) {
                m.setStatus(SafetyMeasure.STATUS_EXECUTED);
                m.setVersion((m.getVersion() == null ? 0 : m.getVersion()) + 1);
                measureRepository.save(m);
            }
        }
        writeLog(id, WorkTicketLog.ACTION_WITHDRAW, null, person,
                "许可人撤回许可，原因：" + reason + "。已确认措施的执行人/时间保留，新增或变更项回到待执行，重新许可须再次核对双侧步骤");
        return detail(id);
    }

    /**
     * 作废工作票：待签发可直接作废；已签发的必须确认没有尚未拆除的在装措施
     * （现场已装设的安措必须拆除并完成关联恢复步骤后才能作废）。
     */
    @Transactional
    public Map<String, Object> cancelTicket(Long id, String person, String reason) {
        WorkTicket ticket = workTicketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("工作票不存在"));
        String status = ticket.getStatus();
        if (WorkTicket.STATUS_TERMINATED.equals(status) || WorkTicket.STATUS_CANCELED.equals(status)) {
            throw new BizException("工作票已" + Labels.workStatus(status) + "，不能作废");
        }
        if (WorkTicket.STATUS_PERMITTED.equals(status)) {
            throw new BizException("工作票已许可，不能直接作废；请先撤回许可，待现场安措全部拆除、恢复步骤完成后再作废");
        }
        if (WorkTicket.STATUS_ISSUED.equals(status)) {
            GateCheck gate = evaluateTerminateGate(ticket);
            if (!gate.ok) {
                throw new BizException("已签发票上仍有在装安全措施或关联恢复步骤未完成，不能作废：\n"
                        + buildGateMessage("作废", gate));
            }
        }
        ticket.setStatus(WorkTicket.STATUS_CANCELED);
        workTicketRepository.saveAndFlush(ticket);
        writeLog(id, WorkTicketLog.ACTION_CANCEL, null, person, "工作票作废：" + reason);
        return detail(id);
    }

    // ============================ 双侧闸道 ============================

    /** 许可前核对：① 要求装设/合上的有效措施全部已确认；② 各措施关联的装设步骤已执行。 */
    private GateCheck evaluatePermitGate(WorkTicket ticket) {
        GateCheck gate = new GateCheck();
        List<SafetyMeasure> measures = measureRepository.findByTicketIdOrderBySeqNoAscIdAsc(ticket.getId());
        List<SafetyMeasure> active = measures.stream()
                .filter(m -> !SafetyMeasure.STATUS_CANCELED.equals(m.getStatus()))
                .toList();
        if (active.isEmpty()) {
            gate.measureBlockers.add("本票尚未编排任何有效安全措施，请先逐项补充接地线/接地刀闸/遮栏/标示牌后再许可");
            return gate;
        }
        for (SafetyMeasure m : active) {
            String label = "第 " + m.getSeqNo() + " 项【" + Labels.measureType(m.getMeasureType())
                    + "｜" + bayNameOf(m.getBayId()) + "｜" + m.getLocation() + "】";
            if (SafetyMeasure.STATUS_PENDING.equals(m.getStatus())) {
                gate.measureBlockers.add(label + "尚未装设确认（" + m.getRequiredAction() + "），需现场执行人确认");
            } else if (SafetyMeasure.STATUS_REMOVED.equals(m.getStatus())) {
                gate.measureBlockers.add(label + "已被拆除，许可前须重新装设确认");
            }
            // 措施侧还要求：同间隔关联操作票对应步骤已执行
            checkLinkedStep(m, true, gate);
        }
        gate.ok = gate.measureBlockers.isEmpty() && gate.operationBlockers.isEmpty();
        return gate;
    }

    /** 终结前核对：① 需要拆除的有效措施全部已拆除；② 关联恢复操作票对应恢复步骤已执行。 */
    private GateCheck evaluateTerminateGate(WorkTicket ticket) {
        GateCheck gate = new GateCheck();
        List<SafetyMeasure> active = measureRepository.findByTicketIdOrderBySeqNoAscIdAsc(ticket.getId()).stream()
                .filter(m -> !SafetyMeasure.STATUS_CANCELED.equals(m.getStatus()))
                .toList();
        for (SafetyMeasure m : active) {
            String label = "第 " + m.getSeqNo() + " 项【" + Labels.measureType(m.getMeasureType())
                    + "｜" + bayNameOf(m.getBayId()) + "｜" + m.getLocation() + "】";
            if (!SafetyMeasure.STATUS_REMOVED.equals(m.getStatus())) {
                gate.measureBlockers.add(label + "尚未拆除确认（当前阶段："
                        + Labels.measureStatus(m.getStatus()) + "），终结前必须全部拆除");
            }
            checkLinkedStep(m, false, gate);
        }
        gate.ok = gate.measureBlockers.isEmpty() && gate.operationBlockers.isEmpty();
        return gate;
    }

    /** 核对措施关联的操作票步骤；side=true 许可前（装设步骤），false 终结前（恢复步骤）。 */
    private void checkLinkedStep(SafetyMeasure m, boolean installSide, GateCheck gate) {
        Long ticketIdRef = installSide ? m.getOpTicketId() : m.getRestoreTicketId();
        Integer stepNoRef = installSide ? m.getOpStepNo() : m.getRestoreStepNo();
        String label = "第 " + m.getSeqNo() + " 项【" + Labels.measureType(m.getMeasureType())
                + "｜" + m.getLocation() + "】";
        if (installSide) {
            // 接地线 / 接地刀闸必然来自倒闸操作，必须指明同间隔操作票的装设步骤，否则无法核验「两票一致」；
            // 遮栏 / 标示牌（如不停电作业）允许没有对应倒闸步骤，但一旦关联就必须已执行。
            boolean mustLink = SafetyMeasure.TYPE_GROUND_WIRE.equals(m.getMeasureType())
                    || SafetyMeasure.TYPE_EARTH_SWITCH.equals(m.getMeasureType());
            if (mustLink && (ticketIdRef == null || stepNoRef == null)) {
                gate.operationBlockers.add(label + "为" + Labels.measureType(m.getMeasureType())
                        + "，必须关联同间隔操作票的装设（合上）步骤，无法核验倒闸操作是否一致");
                return;
            }
            if (ticketIdRef == null || stepNoRef == null) {
                return;
            }
        } else {
            // 恢复步骤允许不关联（如带电作业票本无倒闸恢复）；一旦关联就必须执行到位
            if (ticketIdRef == null || stepNoRef == null) {
                return;
            }
        }
        OperationTicket op = operationTicketRepository.findById(ticketIdRef).orElse(null);
        if (op == null) {
            gate.operationBlockers.add(label + "关联的操作票不存在，请重新选择");
            return;
        }
        if (!op.getBayId().equals(m.getBayId())) {
            gate.operationBlockers.add(label + "关联操作票【" + op.getTicketNo() + "】不在同一间隔，不能作为两票核对依据");
            return;
        }
        if (OperationTicket.STATUS_CANCELED.equals(op.getStatus())) {
            gate.operationBlockers.add(label + "关联操作票【" + op.getTicketNo() + "】已作废，对应"
                    + (installSide ? "装设" : "恢复") + "步骤无法核验");
            return;
        }
        OperationStep step = operationStepRepository.findByTicketIdOrderByStepNoAsc(ticketIdRef).stream()
                .filter(s -> s.getStepNo().equals(stepNoRef))
                .findFirst().orElse(null);
        if (step == null) {
            gate.operationBlockers.add(label + "关联操作票【" + op.getTicketNo() + "】第 " + stepNoRef + " 步不存在");
            return;
        }
        if (!OperationStep.STATUS_DONE.equals(step.getStatus())) {
            gate.operationBlockers.add(label + "关联" + (installSide ? "装设" : "恢复")
                    + "操作票【" + op.getTicketNo() + "】第 " + stepNoRef
                    + " 步「" + step.getActionDesc() + "」尚未执行"
                    + (OperationTicket.STATUS_FINISHED.equals(op.getStatus()) ? "" : "（操作票状态："
                    + Labels.opStatus(op.getStatus()) + "），")
                    + (installSide ? "许可前必须先完成该装设步骤" : "终结前必须先完成该恢复步骤"));
        }
    }

    private String buildGateMessage(String action, GateCheck gate) {
        StringBuilder sb = new StringBuilder("禁止").append(action).append("，尚有 ").append(
                        gate.measureBlockers.size() + gate.operationBlockers.size())
                .append(" 项未闭环：\n");
        if (!gate.measureBlockers.isEmpty()) {
            sb.append("【安全措施侧】\n");
            gate.measureBlockers.forEach(b -> sb.append("· ").append(b).append('\n'));
        }
        if (!gate.operationBlockers.isEmpty()) {
            sb.append("【关联操作票侧】\n");
            gate.operationBlockers.forEach(b -> sb.append("· ").append(b).append('\n'));
        }
        sb.append(action).append("后下一步：先完成上述现场确认与倒闸步骤，再发起").append(action).append("。");
        return sb.toString();
    }

    // ============================ 校验 / 组装视图 ============================

    private void validateMeasureRequest(MeasureRequest req) {
        if (!MEASURE_TYPES.contains(req.getMeasureType())) {
            throw new BizException("措施类型只能是：接地线 / 接地刀闸 / 遮栏 / 标示牌");
        }
        if (SafetyMeasure.TYPE_SIGNBOARD.equals(req.getMeasureType())
                && (req.getSignboardText() == null || req.getSignboardText().isBlank())) {
            throw new BizException("标示牌类措施必须填写牌上文字（如「禁止合闸，有人工作」）");
        }
        bayRepository.findById(req.getBayId())
                .orElseThrow(() -> new BizException("措施所在间隔不存在"));
    }

    /** 编辑措施时校验关联步骤：步骤必须真实存在于该操作票，且间隔一致。 */
    private void validateLinkSteps(MeasureRequest req) {
        validateOneLink(req.getOpTicketId(), req.getOpStepNo(), req.getBayId(), "装设步骤");
        validateOneLink(req.getRestoreTicketId(), req.getRestoreStepNo(), req.getBayId(), "恢复步骤");
    }

    private void validateOneLink(Long opTicketId, Integer stepNo, Long bayId, String what) {
        if (opTicketId == null && stepNo == null) {
            return;
        }
        if (opTicketId == null || stepNo == null) {
            throw new BizException("关联" + what + "必须同时选择操作票与步骤序号");
        }
        OperationTicket op = operationTicketRepository.findById(opTicketId)
                .orElseThrow(() -> new BizException("关联" + what + "的操作票不存在"));
        if (!op.getBayId().equals(bayId)) {
            throw new BizException("关联" + what + "必须与措施在同一间隔（操作票【" + op.getTicketNo() + "】不属于该间隔）");
        }
        boolean exists = operationStepRepository.findByTicketIdOrderByStepNoAsc(opTicketId).stream()
                .anyMatch(s -> s.getStepNo().equals(stepNo));
        if (!exists) {
            throw new BizException("操作票【" + op.getTicketNo() + "】不存在第 " + stepNo + " 步");
        }
    }

    private void ensureDifferentPersons(WorkTicket ticket) {
        String issuer = ticket.getIssuer();
        String permittor = ticket.getPermittor();
        String terminator = ticket.getTerminator();
        if (notBlank(issuer) && issuer.equals(permittor)) {
            throw new BizException("签发人与许可人不得为同一人（" + issuer + "）");
        }
        if (notBlank(issuer) && issuer.equals(terminator)) {
            throw new BizException("签发人与终结人不得为同一人（" + issuer + "）");
        }
        if (notBlank(permittor) && permittor.equals(terminator)) {
            throw new BizException("许可人与终结人不得为同一人（" + permittor + "）");
        }
    }

    /**
     * 【手写 JPQL】两票状态统计（指定日期的工作票票面状态分布）。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> statusStats(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<TicketStatusCount> rows = workTicketRepository.statByStatus(day, day);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TicketStatusCount row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("status", row.getStatus());
            item.put("statusName", Labels.workStatus(row.getStatus()));
            item.put("count", row.getCount());
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> toMap(WorkTicket t, Bay bay, List<SafetyMeasure> measures, boolean full) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", t.getId());
        map.put("ticketNo", t.getTicketNo());
        map.put("bayId", t.getBayId());
        map.put("bayName", bay == null ? "" : bay.getName());
        map.put("bayStatus", bay == null ? "" : bay.getStatus());
        map.put("bayStatusName", bay == null ? "" : Labels.bayStatus(bay.getStatus()));
        map.put("voltageLevel", bay == null ? "" : bay.getVoltageLevel());
        map.put("issueDate", t.getIssueDate());
        map.put("workLeader", t.getWorkLeader());
        map.put("teamMembers", t.getTeamMembers());
        map.put("workContent", t.getWorkContent());
        map.put("powerCutRange", t.getPowerCutRange());
        map.put("safetyMeasures", t.getSafetyMeasures());
        map.put("planStartTime", t.getPlanStartTime());
        map.put("planEndTime", t.getPlanEndTime());
        map.put("issuer", t.getIssuer());
        map.put("permittor", t.getPermittor());
        map.put("terminator", t.getTerminator());
        map.put("issueTime", t.getIssueTime());
        map.put("permitTime", t.getPermitTime());
        map.put("terminateTime", t.getTerminateTime());
        map.put("status", t.getStatus());
        map.put("statusName", Labels.workStatus(t.getStatus()));
        map.put("remark", t.getRemark());

        // 措施四阶段计数
        long total = measures.size();
        long executed = measures.stream().filter(m -> SafetyMeasure.STATUS_EXECUTED.equals(m.getStatus())
                || SafetyMeasure.STATUS_PENDING_REMOVE.equals(m.getStatus())
                || SafetyMeasure.STATUS_REMOVED.equals(m.getStatus())).count();
        long waitingRemove = measures.stream().filter(m ->
                SafetyMeasure.STATUS_EXECUTED.equals(m.getStatus())
                        || SafetyMeasure.STATUS_PENDING_REMOVE.equals(m.getStatus())).count();
        long removed = measures.stream().filter(m -> SafetyMeasure.STATUS_REMOVED.equals(m.getStatus())).count();
        long pending = measures.stream().filter(m -> SafetyMeasure.STATUS_PENDING.equals(m.getStatus())).count();
        long canceled = measures.stream().filter(m -> SafetyMeasure.STATUS_CANCELED.equals(m.getStatus())).count();
        map.put("measureTotal", total);
        map.put("measureExecuted", executed);
        map.put("measurePending", pending);
        map.put("measureWaitingRemove", waitingRemove);
        map.put("measureRemoved", removed);
        map.put("measureCanceled", canceled);

        if (full) {
            Map<Long, Bay> bayMap = loadBayMap();
            List<Map<String, Object>> measureList = new ArrayList<>();
            for (SafetyMeasure m : measures) {
                measureList.add(measureToMap(m, bayMap));
            }
            map.put("measures", measureList);
            map.put("permitGate", gateToMap(evaluatePermitGate(t, measures)));
            map.put("terminateGate", gateToMap(evaluateTerminateGate(t, measures)));
            map.put("logs", logsToMap(logRepository.findByTicketIdOrderByCreatedAtAscIdAsc(t.getId())));
            map.put("canEditMeasures", canEditMeasures(t.getStatus()));
        }

        String nextStage;
        String waiting;
        switch (t.getStatus()) {
            case WorkTicket.STATUS_DRAFT -> {
                nextStage = WorkTicket.STAGE_ISSUE;
                waiting = "待签发人签字";
            }
            case WorkTicket.STATUS_ISSUED -> {
                nextStage = WorkTicket.STAGE_PERMIT;
                GateCheck gate = full ? evaluatePermitGate(t, measures) : null;
                if (gate != null && !gate.ok) {
                    waiting = "安措/操作票未闭环，禁止许可";
                } else {
                    waiting = "待许可人签字";
                }
            }
            case WorkTicket.STATUS_PERMITTED -> {
                nextStage = WorkTicket.STAGE_TERMINATE;
                GateCheck gate = full ? evaluateTerminateGate(t, measures) : null;
                waiting = gate != null && gate.ok ? "安措已拆除，待终结人签字" : "作业进行中，待拆除安措并终结";
            }
            default -> {
                nextStage = "";
                waiting = WorkTicket.STATUS_CANCELED.equals(t.getStatus()) ? "本票已作废" : "三审完成";
            }
        }
        map.put("nextStage", nextStage);
        map.put("waitingText", waiting);
        map.put("signedCount", countSigned(t));
        return map;
    }

    private boolean canEditMeasures(String status) {
        return WorkTicket.STATUS_DRAFT.equals(status)
                || WorkTicket.STATUS_ISSUED.equals(status);
    }

    private Map<String, Object> measureToMap(SafetyMeasure m, Map<Long, Bay> bayMap) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", m.getId());
        item.put("seqNo", m.getSeqNo());
        item.put("measureType", m.getMeasureType());
        item.put("measureTypeName", Labels.measureType(m.getMeasureType()));
        item.put("bayId", m.getBayId());
        Bay b = bayMap.get(m.getBayId());
        item.put("bayName", b == null ? "" : b.getName());
        item.put("location", m.getLocation());
        item.put("requiredAction", m.getRequiredAction());
        item.put("signboardText", m.getSignboardText());
        item.put("opTicketId", m.getOpTicketId());
        item.put("opStepNo", m.getOpStepNo());
        item.put("restoreTicketId", m.getRestoreTicketId());
        item.put("restoreStepNo", m.getRestoreStepNo());
        item.put("linkedOpText", linkedText(m.getOpTicketId(), m.getOpStepNo()));
        item.put("linkedRestoreText", linkedText(m.getRestoreTicketId(), m.getRestoreStepNo()));
        item.put("status", m.getStatus());
        item.put("statusName", Labels.measureStatus(m.getStatus()));
        item.put("executePerson", m.getExecutePerson());
        item.put("executeTime", m.getExecuteTime());
        item.put("removePerson", m.getRemovePerson());
        item.put("removeTime", m.getRemoveTime());
        item.put("changeRemark", m.getChangeRemark());
        item.put("version", m.getVersion());
        item.put("confirmed", hasConfirmed(m));
        return item;
    }

    private String linkedText(Long ticketId, Integer stepNo) {
        if (ticketId == null || stepNo == null) {
            return "未关联";
        }
        OperationTicket op = operationTicketRepository.findById(ticketId).orElse(null);
        if (op == null) {
            return "关联票已不存在";
        }
        return op.getTicketNo() + " 第" + stepNo + "步";
    }

    private Map<String, Object> gateToMap(GateCheck gate) {
        Map<String, Object> map = new HashMap<>();
        map.put("ok", gate.ok);
        map.put("measureBlockers", gate.measureBlockers);
        map.put("operationBlockers", gate.operationBlockers);
        return map;
    }

    private List<Map<String, Object>> logsToMap(List<WorkTicketLog> logs) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (WorkTicketLog l : logs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", l.getId());
            item.put("action", l.getAction());
            item.put("actionName", Labels.ticketAction(l.getAction()));
            item.put("measureId", l.getMeasureId());
            item.put("person", l.getPerson());
            item.put("detail", l.getDetail());
            item.put("createdAt", l.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    // ---- 闸道的无副作用重载（供视图组装使用，避免重复查表） ----
    private GateCheck evaluatePermitGate(WorkTicket ticket, List<SafetyMeasure> measures) {
        GateCheck gate = new GateCheck();
        List<SafetyMeasure> active = measures.stream()
                .filter(m -> !SafetyMeasure.STATUS_CANCELED.equals(m.getStatus())).toList();
        if (active.isEmpty()) {
            gate.measureBlockers.add("本票尚未编排任何有效安全措施，请先逐项补充接地线/接地刀闸/遮栏/标示牌后再许可");
        }
        for (SafetyMeasure m : active) {
            String label = "第 " + m.getSeqNo() + " 项【" + Labels.measureType(m.getMeasureType())
                    + "｜" + m.getLocation() + "】";
            if (SafetyMeasure.STATUS_PENDING.equals(m.getStatus())) {
                gate.measureBlockers.add(label + "尚未装设确认（" + m.getRequiredAction() + "），需现场执行人确认");
            } else if (SafetyMeasure.STATUS_REMOVED.equals(m.getStatus())) {
                gate.measureBlockers.add(label + "已被拆除，许可前须重新装设确认");
            }
            checkLinkedStep(m, true, gate);
        }
        gate.ok = gate.measureBlockers.isEmpty() && gate.operationBlockers.isEmpty();
        return gate;
    }

    private GateCheck evaluateTerminateGate(WorkTicket ticket, List<SafetyMeasure> measures) {
        GateCheck gate = new GateCheck();
        for (SafetyMeasure m : measures) {
            if (SafetyMeasure.STATUS_CANCELED.equals(m.getStatus())) {
                continue;
            }
            String label = "第 " + m.getSeqNo() + " 项【" + Labels.measureType(m.getMeasureType())
                    + "｜" + m.getLocation() + "】";
            if (!SafetyMeasure.STATUS_REMOVED.equals(m.getStatus())) {
                gate.measureBlockers.add(label + "尚未拆除确认（当前阶段："
                        + Labels.measureStatus(m.getStatus()) + "），终结前必须全部拆除");
            }
            checkLinkedStep(m, false, gate);
        }
        gate.ok = gate.measureBlockers.isEmpty() && gate.operationBlockers.isEmpty();
        return gate;
    }

    private WorkTicket lockTicket(Long id) {
        return workTicketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("工作票不存在"));
    }

    private SafetyMeasure lockMeasureOf(Long ticketId, Long measureId) {
        SafetyMeasure m = measureRepository.findByIdForUpdate(measureId)
                .orElseThrow(() -> new BizException("安全措施不存在"));
        if (!m.getTicketId().equals(ticketId)) {
            throw new BizException("该安全措施不属于当前工作票");
        }
        return m;
    }

    private void writeLog(Long ticketId, String action, Long measureId, String person, String detail) {
        WorkTicketLog log = new WorkTicketLog();
        log.setTicketId(ticketId);
        log.setAction(action);
        log.setMeasureId(measureId);
        log.setPerson(person);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);
    }

    private Map<Long, Bay> loadBayMap() {
        Map<Long, Bay> map = new HashMap<>();
        for (Bay b : bayRepository.findAll()) {
            map.put(b.getId(), b);
        }
        return map;
    }

    private String bayStatus(Long bayId) {
        if (bayId == null) {
            return "";
        }
        return bayRepository.findById(bayId).map(Bay::getStatus).orElse("");
    }

    private String bayNameOf(Long bayId) {
        if (bayId == null) {
            return "";
        }
        return bayRepository.findById(bayId).map(Bay::getName).orElse("");
    }

    private boolean hasConfirmed(SafetyMeasure m) {
        return m.getExecutePerson() != null || m.getRemovePerson() != null;
    }

    private int countSigned(WorkTicket t) {
        int count = 0;
        if (notBlank(t.getIssuer())) {
            count++;
        }
        if (notBlank(t.getPermittor())) {
            count++;
        }
        if (notBlank(t.getTerminator())) {
            count++;
        }
        return count;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String fmt(LocalDateTime time) {
        return time == null ? "" : time.toString().replace('T', ' ');
    }

    /** 闸道核对结果：两侧分别列出卡住的具体项目。 */
    private static class GateCheck {
        boolean ok = true;
        List<String> measureBlockers = new ArrayList<>();
        List<String> operationBlockers = new ArrayList<>();
    }
}
