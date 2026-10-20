package com.easemob.customer.assignment.strategy;

import com.easemob.customer.account.entity.CsAccount;
import com.easemob.customer.common.enums.StrategyType;

import java.util.Optional;

public interface AssignmentStrategy {

    StrategyType type();

    Optional<CsAccount> assign(AssignContext ctx);
}
