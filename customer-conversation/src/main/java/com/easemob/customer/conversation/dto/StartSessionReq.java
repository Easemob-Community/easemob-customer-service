package com.easemob.customer.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class StartSessionReq {

    @NotBlank
    private String userId;
    private String channel;
    private Long aiGroupId;
    private Long skillGroupId;
    private Map<String, Object> metadata;

    public Long getAiGroupIdOrDefault() {
        return aiGroupId != null ? aiGroupId : 1L;
    }
}
