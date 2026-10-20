package com.easemob.customer.assignment.strategy;

import com.easemob.customer.account.entity.CsAccount;
import com.easemob.customer.account.mapper.AccountMapper;
import com.easemob.customer.common.constant.RedisKeyConstant;
import com.easemob.customer.common.enums.StrategyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoundRobinStrategy implements AssignmentStrategy {

    private final AccountMapper accountMapper;
    private final StringRedisTemplate redis;

    @Override
    public StrategyType type() {
        return StrategyType.ROUND_ROBIN;
    }

    @Override
    public Optional<CsAccount> assign(AssignContext ctx) {
        Set<String> online = redis.opsForSet().members(RedisKeyConstant.AGENT_ONLINE);
        List<CsAccount> agents = accountMapper.selectAgents(ctx.getSkillGroupId());
        log.info("RoundRobin agents={}, online={}, skillGroupId={}", agents.size(), online, ctx.getSkillGroupId());
        List<CsAccount> candidates = agents.stream()
                .filter(a -> online.contains(a.getEasemobId()))
                .filter(a -> currentLoad(a.getEasemobId()) < a.getMaxSessions())
                .peek(a -> log.info("RoundRobin candidate={}, load={}, max={}", a.getEasemobId(), currentLoad(a.getEasemobId()), a.getMaxSessions()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .min(Comparator
                        .comparingInt((CsAccount a) -> currentLoad(a.getEasemobId()))
                        .thenComparing(a -> lastAssignTime(a.getEasemobId())));
    }

    private int currentLoad(String agentId) {
        Double score = redis.opsForZSet().score(RedisKeyConstant.AGENT_LOAD, agentId);
        return score != null ? score.intValue() : 0;
    }

    private long lastAssignTime(String agentId) {
        Double score = redis.opsForZSet().score(RedisKeyConstant.AGENT_LAST_ASSIGN, agentId);
        return score != null ? score.longValue() : 0L;
    }
}
