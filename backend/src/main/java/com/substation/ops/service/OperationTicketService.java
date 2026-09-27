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
import com.substation.ops.domain.OperationStep;
import com.substation.ops.domain.OperationTicket;
import com.substation.ops.repository.BayRepository;
import com.substation.ops.repository.OperationStepRepository;
import com.substation.ops.repository.OperationTicketRepository;

/**
 * 模块五：操作票与倒闸操作。硬约束 —— 步骤必须按序号逐条执行，不能跳步。
 */
@Service
public class OperationTicketService {

    private final OperationTicketRepository ticketRepository;
    private final OperationStepRepository stepRepository;
    private final BayRepository bayRepository;

    public OperationTicketService(OperationTicketRepository ticketRepository,
                                  OperationStepRepository stepRepository,
                                  BayRepository bayRepository) {
        this.ticketRepository = ticketRepository;
        this.stepRepository = stepRepository;
        this.bayRepository = bayRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOfDay(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<Map<String, Object>> result = new ArrayList<>();
        for (OperationTicket t : ticketRepository.findByIssueDateOrderByIdAsc(day)) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", t.getId());
            map.put("ticketNo", t.getTicketNo());
            map.put("issueDate", t.getIssueDate());
            map.put("taskName", t.getTaskName());
            map.put("operatorName", t.getOperatorName());
            map.put("guardian", t.getGuardian());
            map.put("dispatcher", t.getDispatcher());
            map.put("status", t.getStatus());
            map.put("statusName", Labels.opStatus(t.getStatus()));
            Bay bay = t.getBayId() == null ? null : bayRepository.findById(t.getBayId()).orElse(null);
            map.put("bayName", bay == null ? "" : bay.getName());

            List<OperationStep> steps = stepRepository.findByTicketIdOrderByStepNoAsc(t.getId());
            map.put("totalSteps", steps.size());
            map.put("doneSteps", steps.stream().filter(s -> OperationStep.STATUS_DONE.equals(s.getStatus())).count());
            result.add(map);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        OperationTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new BizException("操作票不存在"));
        Bay bay = ticket.getBayId() == null ? null : bayRepository.findById(ticket.getBayId()).orElse(null);

        Map<String, Object> map = new HashMap<>();
        map.put("id", ticket.getId());
        map.put("ticketNo", ticket.getTicketNo());
        map.put("issueDate", ticket.getIssueDate());
        map.put("taskName", ticket.getTaskName());
        map.put("operatorName", ticket.getOperatorName());
        map.put("guardian", ticket.getGuardian());
        map.put("dispatcher", ticket.getDispatcher());
        map.put("status", ticket.getStatus());
        map.put("statusName", Labels.opStatus(ticket.getStatus()));
        map.put("remark", ticket.getRemark());
        map.put("bayId", ticket.getBayId());
        map.put("bayName", bay == null ? "" : bay.getName());

        List<OperationStep> steps = stepRepository.findByTicketIdOrderByStepNoAsc(ticket.getId());
        List<Map<String, Object>> stepList = new ArrayList<>();
        OperationStep nextStep = null;
        for (OperationStep s : steps) {
            Map<String, Object> item = new HashMap<>();
            item.put("stepNo", s.getStepNo());
            item.put("actionDesc", s.getActionDesc());
            item.put("executePerson", s.getExecutePerson());
            item.put("executeTime", s.getExecuteTime());
            item.put("status", s.getStatus());
            item.put("statusName", Labels.opStatus(s.getStatus()));
            boolean done = OperationStep.STATUS_DONE.equals(s.getStatus());
            item.put("done", done);
            item.put("executable", !done && nextStep == null);
            item.put("blockedHint", done ? "" : (nextStep == null ? "" : "请先执行第 " + nextStep.getStepNo() + " 步"));
            stepList.add(item);
            if (!done && nextStep == null) {
                nextStep = s;
            }
        }
        map.put("steps", stepList);
        map.put("totalSteps", steps.size());
        map.put("doneSteps", steps.stream().filter(s -> OperationStep.STATUS_DONE.equals(s.getStatus())).count());
        map.put("nextStepNo", nextStep == null ? null : nextStep.getStepNo());
        return map;
    }

    /**
     * 执行某一步：必须按顺序执行，跳步直接报错。
     */
    @Transactional
    public Map<String, Object> executeStep(Long id, Integer stepNo, String person) {
        OperationTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new BizException("操作票不存在"));

        if (OperationTicket.STATUS_FINISHED.equals(ticket.getStatus())) {
            throw new BizException("该操作票已全部执行完毕");
        }
        if (OperationTicket.STATUS_CANCELED.equals(ticket.getStatus())) {
            throw new BizException("该操作票已作废，不能继续操作");
        }

        List<OperationStep> steps = stepRepository.findByTicketIdOrderByStepNoAsc(ticket.getId());
        if (steps.isEmpty()) {
            throw new BizException("该操作票没有编排操作步骤，无法执行");
        }

        OperationStep target = steps.stream()
                .filter(s -> s.getStepNo().equals(stepNo))
                .findFirst()
                .orElseThrow(() -> new BizException("第 " + stepNo + " 步不存在"));

        if (OperationStep.STATUS_DONE.equals(target.getStatus())) {
            throw new BizException("第 " + stepNo + " 步已执行，请勿重复勾选");
        }

        OperationStep firstUndone = steps.stream()
                .filter(s -> !OperationStep.STATUS_DONE.equals(s.getStatus()))
                .findFirst()
                .orElseThrow(() -> new BizException("全部步骤已执行完毕"));

        if (!firstUndone.getStepNo().equals(stepNo)) {
            throw new BizException("倒闸操作不得跳步：应先执行第 " + firstUndone.getStepNo() + " 步，当前不要执行第 " + stepNo + " 步");
        }

        target.setStatus(OperationStep.STATUS_DONE);
        target.setExecutePerson(person);
        target.setExecuteTime(LocalDateTime.now());
        stepRepository.save(target);

        long doneCount = steps.stream().filter(s -> OperationStep.STATUS_DONE.equals(s.getStatus())).count() + 1;
        if (doneCount >= steps.size()) {
            ticket.setStatus(OperationTicket.STATUS_FINISHED);
        } else {
            ticket.setStatus(OperationTicket.STATUS_RUNNING);
        }
        ticketRepository.save(ticket);

        return detail(id);
    }
}
