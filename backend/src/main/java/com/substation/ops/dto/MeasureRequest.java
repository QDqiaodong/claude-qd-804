package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 新增 / 变更一条安全措施的请求体。
 * 许可前核对「装设/合上」对应步骤（opTicketId+opStepNo），
 * 终结前核对「拆除/拉开」对应步骤（restoreTicketId+restoreStepNo），均可暂不关联。
 */
@Getter
@Setter
@NoArgsConstructor
public class MeasureRequest {

    @NotBlank(message = "措施类型不能为空")
    private String measureType;

    @NotNull(message = "所在间隔不能为空")
    private Long bayId;

    @NotBlank(message = "具体位置不能为空")
    private String location;

    @NotBlank(message = "要求动作不能为空")
    private String requiredAction;

    /** 标示牌文字：标示牌类型必填，其余类型可空 */
    private String signboardText;

    private Long opTicketId;

    private Integer opStepNo;

    private Long restoreTicketId;

    private Integer restoreStepNo;
}
