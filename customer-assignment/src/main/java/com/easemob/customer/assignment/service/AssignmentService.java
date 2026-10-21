package com.easemob.customer.assignment.service;

import com.easemob.customer.account.entity.CsAccount;
import com.easemob.customer.account.service.AccountService;
import com.easemob.customer.assignment.queue.WaitQueueService;
import com.easemob.customer.assignment.strategy.AssignContext;
import com.easemob.customer.assignment.strategy.AssignmentStrategy;
import com.easemob.customer.common.constant.RedisKeyConstant;
import com.easemob.customer.common.enums.SessionStatus;
import com.easemob.customer.common.enums.StrategyType;
import com.easemob.customer.common.exception.BizException;
import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.service.SessionService;
import com.easemob.customer.easemob.client.EasemobGroupClient;
import com.easemob.customer.easemob.client.EasemobMessageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final List<AssignmentStrategy> strategies;
    private final WaitQueueService waitQueueService;
    private final SessionService sessionService;
    private final EasemobGroupClient groupClient;
    private final EasemobMessageClient messageClient;
    private final AccountService accountService;
    private final RedissonClient redisson;

    @Transactional
    public void transferToHuman(CsSession session, String reason) {
        log.info("转人工开始, sessionId={}, userId={}, transferType={}", session.getId(), session.getUserAccountId(), reason);
        if (session.getUserAccountId() == null) {
            throw new BizException(4002, "会话用户信息缺失");
        }
        RLock lock = redisson.getLock(RedisKeyConstant.assignLockKey(session.getUserAccountId()));
        try {
            boolean locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("转人工获取锁失败, sessionId={}, transferType={}", session.getId(), reason);
                return;
            }
            try {
                if (!SessionStatus.AI_SERVING.name().equals(session.getStatus())) {
                    log.info("转人工跳过, 会话状态非AI服务中, sessionId={}, status={}, transferType={}",
                            session.getId(), session.getStatus(), reason);
                    return;
                }
                AssignContext ctx = buildContext(session);
                log.info("转人工上下文, sessionId={}, userId={}, skillGroupId={}", session.getId(), ctx.getUserId(), ctx.getSkillGroupId());
                Optional<CsAccount> agent = Optional.empty();
                for (AssignmentStrategy s : strategyChain()) {
                    Optional<CsAccount> r = s.assign(ctx);
                    if (r.isPresent()) {
                        log.info("转人工策略命中, strategy={}, agentId={}, sessionId={}", s.type(), r.get().getEasemobId(), session.getId());
                        agent = r;
                        break;
                    }
                    log.info("转人工策略未命中, strategy={}, sessionId={}", s.type(), session.getId());
                }
                if (agent.isEmpty()) {
                    sessionService.markWaiting(session, reason);
                    waitQueueService.enqueue(session);
                    int position = waitQueueService.position(session).intValue();
                    log.info("转人工无可用坐席, 进入排队, sessionId={}, queuePosition={}, transferType={}",
                            session.getId(), position, reason);
                    messageClient.sendGroupText(session.getAiAccountId(), session.getGroupId(),
                            "正在为您排队，当前第 " + position + " 位，请稍候～");
                    return;
                }
                groupClient.addMember(session.getGroupId(), agent.get().getEasemobId());
                sessionService.switchToHuman(session, agent.get(), reason);
                accountService.incrLoad(agent.get().getEasemobId());
                accountService.upsertFamiliar(session.getUserAccountId(), agent.get().getEasemobId());
                Map<String, Object> ext = Map.of(
                        "bizType", "TRANSFERRED",
                        "agent", Map.of("id", agent.get().getEasemobId(), "nickname", agent.get().getNickname())
                );
                messageClient.sendGroupText(agent.get().getEasemobId(), session.getGroupId(),
                        "客服 " + agent.get().getNickname() + " 为您服务，请问有什么可以帮您？", ext);
                log.info("转人工完成, sessionId={}, agentId={}, transferType={}",
                        session.getId(), agent.get().getEasemobId(), reason);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "转人工被中断");
        }
    }

    private List<AssignmentStrategy> strategyChain() {
        return strategies.stream()
                .sorted(Comparator.comparingInt(s -> switch (s.type()) {
                    case SPECIFY -> 0;
                    case FAMILIAR_FIRST -> 1;
                    case ROUND_ROBIN -> 2;
                }))
                .toList();
    }

    private AssignContext buildContext(CsSession session) {
        return AssignContext.builder()
                .userId(session.getUserAccountId())
                .skillGroupId(session.getSkillGroupId())
                .familiarValidDays(30)
                .build();
    }
}
