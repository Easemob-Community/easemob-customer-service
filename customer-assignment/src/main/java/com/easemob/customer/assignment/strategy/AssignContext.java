package com.easemob.customer.assignment.strategy;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssignContext {

    private String userId;
    private Long skillGroupId;
    private String specifyAgentId;
    private int familiarValidDays;
}
