package com.easemob.customer.assignment.queue;

import com.easemob.customer.common.constant.RedisKeyConstant;
import com.easemob.customer.conversation.entity.CsSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitQueueService {

    private final StringRedisTemplate redis;

    public void enqueue(CsSession session) {
        Long skillGroupId = session.getSkillGroupId();
        if (skillGroupId == null) {
            skillGroupId = 0L;
        }
        String key = RedisKeyConstant.queueKey(skillGroupId);
        redis.opsForZSet().add(key, String.valueOf(session.getId()), System.currentTimeMillis());
        log.info("Enqueue session={}, group={}", session.getId(), skillGroupId);
    }

    public Long dequeue(Long skillGroupId) {
        String key = RedisKeyConstant.queueKey(skillGroupId);
        Set<String> ids = redis.opsForZSet().range(key, 0, 0);
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        String id = ids.iterator().next();
        redis.opsForZSet().remove(key, id);
        return Long.valueOf(id);
    }

    public Long position(CsSession session) {
        Long skillGroupId = session.getSkillGroupId();
        if (skillGroupId == null) {
            skillGroupId = 0L;
        }
        String key = RedisKeyConstant.queueKey(skillGroupId);
        Long rank = redis.opsForZSet().rank(key, String.valueOf(session.getId()));
        return rank != null ? rank + 1 : 0L;
    }

    public Long size(Long skillGroupId) {
        String key = RedisKeyConstant.queueKey(skillGroupId);
        Long size = redis.opsForZSet().size(key);
        return size != null ? size : 0L;
    }
}
