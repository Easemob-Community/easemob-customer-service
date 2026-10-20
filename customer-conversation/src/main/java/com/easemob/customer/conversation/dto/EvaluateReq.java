package com.easemob.customer.conversation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class EvaluateReq {

    @NotNull
    private Long sessionId;
    @Min(1)
    @Max(5)
    private Integer score;
    private List<String> tags;
    private String comment;
}
