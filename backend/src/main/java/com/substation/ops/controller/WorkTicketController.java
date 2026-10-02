package com.substation.ops.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.substation.ops.common.ApiResponse;
import com.substation.ops.dto.CancelTicketRequest;
import com.substation.ops.dto.MeasureCancelRequest;
import com.substation.ops.dto.MeasureConfirmRequest;
import com.substation.ops.dto.MeasureRequest;
import com.substation.ops.dto.SignRequest;
import com.substation.ops.dto.WithdrawRequest;
import com.substation.ops.dto.WorkTicketCreateRequest;
import com.substation.ops.service.WorkTicketService;

import jakarta.validation.Valid;

/**
 * 模块四：工作票（三审 + 安全措施逐项确认闭环）。
 */
@RestController
@RequestMapping("/api/work-tickets")
public class WorkTicketController {

    private final WorkTicketService workTicketService;

    public WorkTicketController(WorkTicketService workTicketService) {
        this.workTicketService = workTicketService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(workTicketService.listOfDay(date));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(workTicketService.detail(id));
    }

    /** 【手写 JPQL】两票状态统计 */
    @GetMapping("/status-stats")
    public ApiResponse<List<Map<String, Object>>> statusStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success("工作票票面状态统计", workTicketService.statusStats(date));
    }

    /** 票号由存储过程生成 */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody WorkTicketCreateRequest request) {
        return ApiResponse.success("票号由存储过程生成并回写", workTicketService.create(request));
    }

    /** 三审签字：ISSUE / PERMIT / TERMINATE */
    @PostMapping("/{id}/sign")
    public ApiResponse<Map<String, Object>> sign(@PathVariable Long id, @Valid @RequestBody SignRequest request) {
        return ApiResponse.success("签字完成", workTicketService.sign(id, request.getStage(), request.getPerson()));
    }

    /** 撤回许可：已许可 → 已签发，措施确认痕迹保留，新增/变更项回到待执行 */
    @PostMapping("/{id}/withdraw")
    public ApiResponse<Map<String, Object>> withdraw(@PathVariable Long id,
                                                     @Valid @RequestBody WithdrawRequest request) {
        return ApiResponse.success("许可已撤回",
                workTicketService.withdrawPermit(id, request.getPerson(), request.getReason()));
    }

    /** 作废工作票（已签发且现场有在装安措时同样被双侧闸道拦住） */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id,
                                                   @Valid @RequestBody CancelTicketRequest request) {
        return ApiResponse.success("工作票已作废",
                workTicketService.cancelTicket(id, request.getPerson(), request.getReason()));
    }

    // ---------------- 安全措施逐项闭环 ----------------

    /** 新增一项安全措施（接地线/接地刀闸/遮栏/标示牌） */
    @PostMapping("/{id}/measures")
    public ApiResponse<Map<String, Object>> addMeasure(@PathVariable Long id,
                                                       @Valid @RequestBody MeasureRequest request) {
        return ApiResponse.success("安全措施已新增（待执行）", workTicketService.addMeasure(id, request));
    }

    /** 变更一项尚未确认的措施内容 */
    @PostMapping("/{id}/measures/{measureId}/update")
    public ApiResponse<Map<String, Object>> updateMeasure(@PathVariable Long id,
                                                          @PathVariable Long measureId,
                                                          @Valid @RequestBody MeasureRequest request) {
        return ApiResponse.success("安全措施已变更", workTicketService.updateMeasure(id, measureId, request));
    }

    /** 装设 / 合上确认（签发后、许可前） */
    @PostMapping("/{id}/measures/{measureId}/execute")
    public ApiResponse<Map<String, Object>> executeMeasure(@PathVariable Long id,
                                                           @PathVariable Long measureId,
                                                           @Valid @RequestBody MeasureConfirmRequest request) {
        return ApiResponse.success("装设确认完成", workTicketService.executeMeasure(id, measureId, request));
    }

    /** 拆除 / 拉开确认（许可后、终结前） */
    @PostMapping("/{id}/measures/{measureId}/remove")
    public ApiResponse<Map<String, Object>> removeMeasure(@PathVariable Long id,
                                                          @PathVariable Long measureId,
                                                          @Valid @RequestBody MeasureConfirmRequest request) {
        return ApiResponse.success("拆除确认完成", workTicketService.removeMeasure(id, measureId, request));
    }

    /** 现场变更作废旧措施（须先撤回许可，原确认痕迹保留） */
    @PostMapping("/{id}/measures/{measureId}/cancel")
    public ApiResponse<Map<String, Object>> cancelMeasure(@PathVariable Long id,
                                                          @PathVariable Long measureId,
                                                          @Valid @RequestBody MeasureCancelRequest request) {
        return ApiResponse.success("该措施已作废并保留原痕迹", workTicketService.cancelMeasure(id, measureId, request));
    }
}
