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
import com.substation.ops.domain.WorkTicket;
import com.substation.ops.dto.TicketStatusCount;
import com.substation.ops.dto.WorkTicketCreateRequest;
import com.substation.ops.repository.BayRepository;
import com.substation.ops.repository.DefectRepository;
import com.substation.ops.repository.WorkTicketRepository;

/**
 * 模块四：工作票。核心是三审（签发 / 许可 / 终结）与几条硬约束。
 */
@Service
public class WorkTicketService {

    private final WorkTicketRepository workTicketRepository;
    private final BayRepository bayRepository;
    private final DefectRepository defectRepository;

    public WorkTicketService(WorkTicketRepository workTicketRepository,
                             BayRepository bayRepository,
                             DefectRepository defectRepository) {
        this.workTicketRepository = workTicketRepository;
        this.bayRepository = bayRepository;
        this.defectRepository = defectRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOfDay(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<WorkTicket> tickets = workTicketRepository.findByIssueDateOrderByIdAsc(day);
        Map<Long, Bay> bayMap = new HashMap<>();
        for (Bay b : bayRepository.findAll()) {
            bayMap.put(b.getId(), b);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (WorkTicket t : tickets) {
            result.add(toMap(t, bayMap.get(t.getBayId())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        WorkTicket t = workTicketRepository.findById(id)
                .orElseThrow(() -> new BizException("工作票不存在"));
        Bay bay = t.getBayId() == null ? null : bayRepository.findById(t.getBayId()).orElse(null);
        return toMap(t, bay);
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
        return toMap(saved, bay);
    }

    /**
     * 三审签字：ISSUE → PERMIT → TERMINATE 必须依次点亮。
     */
    @Transactional
    public Map<String, Object> sign(Long id, String stage, String person) {
        WorkTicket ticket = workTicketRepository.findById(id)
                .orElseThrow(() -> new BizException("工作票不存在"));
        Bay bay = ticket.getBayId() == null ? null : bayRepository.findById(ticket.getBayId()).orElse(null);

        switch (stage) {
            case WorkTicket.STAGE_ISSUE -> {
                if (!WorkTicket.STATUS_DRAFT.equals(ticket.getStatus())) {
                    throw new BizException("该票已签发，不能再由签发人签字");
                }
                ticket.setIssuer(person);
                ticket.setIssueTime(LocalDateTime.now());
                ticket.setStatus(WorkTicket.STATUS_ISSUED);
            }
            case WorkTicket.STAGE_PERMIT -> {
                if (!WorkTicket.STATUS_ISSUED.equals(ticket.getStatus())) {
                    throw new BizException("尚未经签发人签发，许可人不能先签字");
                }
                ticket.setPermittor(person);
                ticket.setPermitTime(LocalDateTime.now());
                ticket.setStatus(WorkTicket.STATUS_PERMITTED);
            }
            case WorkTicket.STAGE_TERMINATE -> {
                if (!WorkTicket.STATUS_PERMITTED.equals(ticket.getStatus())) {
                    throw new BizException("尚未许可开工，终结人不能先签字");
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
                ticket.setTerminator(person);
                ticket.setTerminateTime(LocalDateTime.now());
                ticket.setStatus(WorkTicket.STATUS_TERMINATED);
            }
            default -> throw new BizException("未知的签字环节：" + stage);
        }

        // 规则三：签发 / 许可 / 终结三人不得为同一人
        ensureDifferentPersons(ticket);

        if (bay != null && !Bay.STATUS_MAINTENANCE.equals(bay.getStatus())
                && WorkTicket.STAGE_PERMIT.equals(stage)) {
            bay.setStatus(Bay.STATUS_MAINTENANCE);
            bayRepository.save(bay);
        }

        WorkTicket saved = workTicketRepository.saveAndFlush(ticket);
        return toMap(saved, bay == null ? null : bayRepository.findById(saved.getBayId()).orElse(null));
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

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
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

    private Map<String, Object> toMap(WorkTicket t, Bay bay) {
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

        String nextStage;
        String waiting = "";
        switch (t.getStatus()) {
            case WorkTicket.STATUS_DRAFT -> {
                nextStage = WorkTicket.STAGE_ISSUE;
                waiting = "待签发人签字";
            }
            case WorkTicket.STATUS_ISSUED -> {
                nextStage = WorkTicket.STAGE_PERMIT;
                waiting = "待许可人签字";
            }
            case WorkTicket.STATUS_PERMITTED -> {
                nextStage = WorkTicket.STAGE_TERMINATE;
                waiting = "待终结人签字";
            }
            default -> {
                nextStage = "";
                waiting = "三审完成";
            }
        }
        map.put("nextStage", nextStage);
        map.put("waitingText", waiting);
        map.put("signedCount", countSigned(t));
        return map;
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
}
