package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 撤回工作票许可：已许可的票不能直接删改已确认措施，
 * 必须先由许可人撤回，新增/变更项回到待执行，原确认痕迹保留。
 */
@Getter
@Setter
@NoArgsConstructor
public class WithdrawRequest {

    @NotBlank(message = "撤回人不能为空")
    private String person;

    @NotBlank(message = "撤回原因不能为空")
    private String reason;
}
