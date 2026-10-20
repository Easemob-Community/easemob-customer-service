package com.easemob.customer.assignment.strategy;

import com.easemob.customer.account.entity.CsAccount;
import com.easemob.customer.account.service.AccountService;
import com.easemob.customer.common.constant.RedisKeyConstant;
import com.easemob.customer.common.enums.StrategyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class FamiliarFirstStrategy implements AssignmentStrategy {

    private final AccountService accountService;
    private final RoundRobinStrategy roundRobinStrategy;
    private final StringRedisTemplate redis;

    @Override
    public StrategyType type() {
        return StrategyType.FAMILIAR_FIRST;
    }

    @Override
    public Optional<CsAccount> assign(AssignContext ctx) {
        Optional<CsAccount> familiar = accountService.findFamiliarAgent(ctx.getUserId(), ctx.getFamiliarValidDays());
        log.info("FamiliarFirst userId={}, familiar={}", ctx.getUserId(), familiar.map(CsAccount::getEasemobId).orElse("none"));
        if (familiar.isPresent() && isAssignable(familiar.get())) {
            return familiar;
        }
        return roundRobinStrategy.assign(ctx);
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
