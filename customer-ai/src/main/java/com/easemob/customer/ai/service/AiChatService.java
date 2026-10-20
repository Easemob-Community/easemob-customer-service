package com.easemob.customer.ai.service;

import com.easemob.customer.assignment.service.AssignmentService;
import com.easemob.customer.common.ai.AiAnswer;
import com.easemob.customer.common.ai.AiChatRequest;
import com.easemob.customer.common.ai.AiMessage;
import com.easemob.customer.common.enums.SessionStatus;
import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.service.MessageService;
import com.easemob.customer.easemob.client.EasemobMessageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiRouteService routeService;
    private final EasemobMessageClient messageClient;
    private final MessageService messageService;
    private final AssignmentService assignmentService;
    private final TransferJudge transferJudge;
    private final StringRedisTemplate redis;

    /**
     * 用户消息编排入口（环信回调与 /open/message/record 共用）：
     * 仅 AI 服务中的会话需要处理；命中转人工词表则转人工，否则 AI 应答。
     */
    @Async("aiExecutor")
    public void handleUserMessage(CsSession session, String question) {
        if (!SessionStatus.AI_SERVING.name().equals(session.getStatus())) {
            return;
        }
        // 去重：回调与 record 可能同时到达（生产环境），10s 内同内容只处理一次
        String dedupKey = "cs:msg:ai:dedup:" + session.getId() + ":"
                + DigestUtils.md5DigestAsHex(question.getBytes(StandardCharsets.UTF_8));
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(dedupKey, "1", 10, TimeUnit.SECONDS))) {
            log.info("duplicate user message skipped, sessionId={}", session.getId());
            return;
        }
        try {
            if (transferJudge.hitTransferWords(question)) {
                assignmentService.transferToHuman(session, "KEYWORD");
                return;
            }
            reply(session, question);
        } catch (Exception e) {
            log.error("handleUserMessage failed, sessionId={}", session.getId(), e);
        }
    }

    @Async("aiExecutor")
    public void reply(CsSession session, String question) {
        List<AiMessage> history = messageService.recentHistory(session.getId(), 10).stream()
                .map(m -> new AiMessage(m.getSenderType().toLowerCase(), m.getContent()))
                .toList();
        AiAnswer answer = routeService.chatWithFailover(
                new AiChatRequest(session.getId().toString(), session.getUserAccountId(), question, history));
        if (answer.transfer()) {
            assignmentService.transferToHuman(session, "AI_TRANSFER");
            return;
        }
        if (StringUtils.hasText(answer.answer())) {
            messageClient.sendGroupText(session.getAiAccountId(), session.getGroupId(), answer.answer());
            messageService.saveAiMessage(session, answer);
            session.resetFailCount();
        } else {
            handleAiFailure(session);
        }
    }

    private void handleAiFailure(CsSession session) {
        int count = session.getFailCount() != null ? session.getFailCount() : 0;
        session.setFailCount(count + 1);
        if (session.getFailCount() >= 3) {
            assignmentService.transferToHuman(session, "AI_FAILOVER");
        }
    }
}
