package com.easemob.customer.assignment.strategy;

import com.easemob.customer.account.entity.CsAccount;
import com.easemob.customer.account.service.AccountService;
import com.easemob.customer.common.constant.RedisKeyConstant;
import com.easemob.customer.common.enums.StrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SpecifyStrategy implements AssignmentStrategy {

    private final AccountService accountService;
    private final RoundRobinStrategy roundRobinStrategy;
    private final StringRedisTemplate redis;

    @Override
    public StrategyType type() {
        return StrategyType.SPECIFY;
    }

    @Override
    public Optional<CsAccount> assign(AssignContext ctx) {
        if (!StringUtils.hasText(ctx.getSpecifyAgentId())) {
            return roundRobinStrategy.assign(ctx);
        }
        return accountService.findAgent(ctx.getSpecifyAgentId())
                .filter(this::isAssignable)
                .or(() -> roundRobinStrategy.assign(ctx));
    }

    private boolean isAssignable(CsAccount agent) {
        Set<String> online = redis.opsForSet().members(RedisKeyConstant.AGENT_ONLINE);
        if (!online.contains(agent.getEasemobId())) {
            return false;
        }
        Double load = redis.opsForZSet().score(RedisKeyConstant.AGENT_LOAD, agent.getEasemobId());
        return (load != null ? load.intValue() : 0) < agent.getMaxSessions();
    }
}
