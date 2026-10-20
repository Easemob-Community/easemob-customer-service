package com.easemob.customer.account.service;

import com.easemob.customer.account.entity.CsAccount;
import com.easemob.customer.account.entity.CsFamiliarRelation;
import com.easemob.customer.account.mapper.AccountMapper;
import com.easemob.customer.account.mapper.FamiliarRelationMapper;
import com.easemob.customer.common.constant.RedisKeyConstant;
import com.easemob.customer.common.enums.AccountType;
import com.easemob.customer.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountMapper accountMapper;
    private final FamiliarRelationMapper familiarRelationMapper;
    private final StringRedisTemplate redis;

    private static final String ONLINE = RedisKeyConstant.AGENT_ONLINE;
    private static final String LOAD = RedisKeyConstant.AGENT_LOAD;
    private static final String LAST_ASSIGN = RedisKeyConstant.AGENT_LAST_ASSIGN;

    public CsAccount pickAiAccount(Long aiGroupId) {
        List<CsAccount> ais = accountMapper.selectEnabledByGroup(aiGroupId);
        if (ais.isEmpty()) {
            throw new BizException(4001, "AI 账号池为空");
        }
        String key = RedisKeyConstant.aiRoundRobinKey(aiGroupId);
        Long idx = redis.opsForValue().increment(key, 1);
        return ais.get((int) ((idx != null ? idx : 0) - 1) % ais.size());
    }

    public void agentOnline(String agentId) {
        redis.opsForSet().add(ONLINE, agentId);
        redis.opsForZSet().addIfAbsent(LOAD, agentId, 0);
        log.info("Agent online: {}", agentId);
    }

    public void agentOffline(String agentId) {
        redis.opsForSet().remove(ONLINE, agentId);
        redis.opsForZSet().remove(LOAD, agentId);
        redis.opsForZSet().remove(LAST_ASSIGN, agentId);
        log.info("Agent offline: {}", agentId);
    }

    public Optional<CsAccount> findFamiliarAgent(String userId, int validDays) {
        List<CsAccount> agents = accountMapper.selectAgents(null);
        for (CsAccount agent : agents) {
            CsFamiliarRelation relation = familiarRelationMapper.selectValid(userId, agent.getEasemobId(), validDays);
            if (relation != null) {
                return Optional.of(agent);
            }
        }
        return Optional.empty();
    }

    public void incrLoad(String agentId) {
        redis.opsForZSet().incrementScore(LOAD, agentId, 1);
        redis.opsForZSet().add(LAST_ASSIGN, agentId, System.currentTimeMillis());
    }

    public void decrLoad(String agentId) {
        redis.opsForZSet().incrementScore(LOAD, agentId, -1);
    }

    public int currentLoad(String agentId) {
        Double score = redis.opsForZSet().score(LOAD, agentId);
        return score != null ? score.intValue() : 0;
    }

    public Set<String> onlineAgentIds() {
        return redis.opsForSet().members(ONLINE);
    }

    public Optional<CsAccount> findAgent(String agentId) {
        return Optional.ofNullable(accountMapper.selectByEasemobId(agentId));
    }

    @Transactional
    public void upsertFamiliar(String userId, String agentId) {
        CsFamiliarRelation relation = new CsFamiliarRelation();
        relation.setUserId(userId);
        relation.setAgentId(agentId);
        relation.setServeCount(1);
        relation.setLastServeTime(LocalDateTime.now());

        int updated = familiarRelationMapper.incrementServeCount(userId, agentId);
        if (updated == 0) {
            familiarRelationMapper.insert(relation);
        }
    }
}
