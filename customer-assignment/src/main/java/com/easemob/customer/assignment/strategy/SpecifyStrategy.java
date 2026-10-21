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
            log.info("指定坐席策略跳过, 未指定坐席, userId={}, 降级为轮询", ctx.getUserId());
            return roundRobinStrategy.assign(ctx);
        }
        Optional<CsAccount> specified = accountService.findAgent(ctx.getSpecifyAgentId());
        if (specified.isPresent() && isAssignable(specified.get())) {
            return specified;
        }
        log.info("指定坐席不可用, specifyAgentId={}, userId={}, 降级为轮询",
                ctx.getSpecifyAgentId(), ctx.getUserId());
        return roundRobinStrategy.assign(ctx);
    }

    private boolean isAssignable(CsAccount agent) {
        Set<String> online = redis.opsForSet().members(RedisKeyConstant.AGENT_ONLINE);
        if (!online.contains(agent.getEasemobId())) {
            log.info("坐席不在线, agentId={}", agent.getEasemobId());
            return false;
        }
        Double load = redis.opsForZSet().score(RedisKeyConstant.AGENT_LOAD, agent.getEasemobId());
        boolean assignable = (load != null ? load.intValue() : 0) < agent.getMaxSessions();
        if (!assignable) {
            log.info("坐席已满负荷, agentId={}, load={}, maxSessions={}",
                    agent.getEasemobId(), load != null ? load.intValue() : 0, agent.getMaxSessions());
        }
        return assignable;
    }
}
