package com.substation.ops.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 新建工作票请求体：票号由存储过程自动生成并回写，不需要前端给。
 */
@Getter
@Setter
@NoArgsConstructor
public class WorkTicketCreateRequest {

    @NotNull(message = "间隔不能为空")
    private Long bayId;

    @NotNull(message = "业务日期不能为空")
    private LocalDate issueDate;

    @NotBlank(message = "工作负责人不能为空")
    private String workLeader;

    private String teamMembers;

    @NotBlank(message = "工作内容不能为空")
    private String workContent;

    private String powerCutRange;

    private String safetyMeasures;

    private LocalDateTime planStartTime;

    private LocalDateTime planEndTime;
}
