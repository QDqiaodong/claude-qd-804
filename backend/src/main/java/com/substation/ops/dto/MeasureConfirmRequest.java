package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 装设 / 拆除一项安全措施时的确认请求。
 * version 为页面上该措施当前的乐观锁版本：两人同时确认时，
 * 后提交者版本号过期，服务端拒绝并要求其刷新查看最新结果。
 */
@Getter
@Setter
@NoArgsConstructor
public class MeasureConfirmRequest {

    @NotBlank(message = "执行人不能为空")
    private String person;

    /** 前端持有的版本号；为空时按服务端最新状态判定（幂等友好） */
    private Integer version;
}
