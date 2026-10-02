package com.substation.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 作废工作票。 */
@Getter
@Setter
@NoArgsConstructor
public class CancelTicketRequest {

    @NotBlank(message = "操作人不能为空")
    private String person;

    @NotBlank(message = "作废原因不能为空")
    private String reason;
}
