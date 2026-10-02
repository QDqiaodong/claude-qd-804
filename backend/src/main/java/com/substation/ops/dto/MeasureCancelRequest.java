package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 撤回许可 / 作废措施 时必须记录操作人与原因，痕迹不可缺失。 */
@Getter
@Setter
@NoArgsConstructor
public class MeasureCancelRequest {

    @NotBlank(message = "变更/作废说明不能为空")
    private String remark;
}
