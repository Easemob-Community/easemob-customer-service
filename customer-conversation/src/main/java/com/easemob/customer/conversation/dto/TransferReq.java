package com.easemob.customer.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferReq {

    @NotNull
    private Long sessionId;

    @NotBlank
    private String userId;

    private String reason;
}
