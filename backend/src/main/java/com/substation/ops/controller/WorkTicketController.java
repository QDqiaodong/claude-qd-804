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
import com.substation.ops.dto.SignRequest;
import com.substation.ops.dto.WorkTicketCreateRequest;
import com.substation.ops.service.WorkTicketService;

import jakarta.validation.Valid;

/**
 * 模块四：工作票（三审）。
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
}
