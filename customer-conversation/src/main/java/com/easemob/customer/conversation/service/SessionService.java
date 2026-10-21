package com.easemob.customer.conversation.service;

import com.easemob.customer.account.entity.CsAccount;
import com.easemob.customer.account.service.AccountService;
import com.easemob.customer.common.constant.RedisKeyConstant;
import com.easemob.customer.common.enums.HandlerType;
import com.easemob.customer.common.enums.SessionStatus;
import com.easemob.customer.common.exception.BizException;
import com.easemob.customer.common.util.IdUtil;
import com.easemob.customer.conversation.dto.StartSessionReq;
import com.easemob.customer.conversation.dto.StartSessionResp;
import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.mapper.SessionMapper;
import com.easemob.customer.easemob.client.EasemobGroupClient;
import com.easemob.customer.easemob.client.EasemobMessageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionMapper sessionMapper;
    private final AccountService accountService;
    private final EasemobGroupClient groupClient;
    private final EasemobMessageClient messageClient;
    private final StringRedisTemplate redis;

    @Transactional
    public StartSessionResp start(StartSessionReq req) {
        log.info("开始会话, userId={}, channel={}, skillGroupId={}, aiGroupId={}",
                req.getUserId(), req.getChannel(), req.getSkillGroupId(), req.getAiGroupIdOrDefault());
        CsSession existing = sessionMapper.selectActiveByUser(req.getUserId());
        if (existing != null) {
            log.info("复用已有会话, sessionId={}, status={}", existing.getId(), existing.getStatus());
            return StartSessionResp.of(existing);
        }
        CsAccount ai = accountService.pickAiAccount(req.getAiGroupIdOrDefault());
        log.info("已选取AI坐席, userId={}, pickedAi={}", req.getUserId(), ai.getEasemobId());
        String groupId = groupClient.createGroup(req.getUserId(), List.of(ai.getEasemobId()),
                "cs-" + IdUtil.fastSimpleUUID());
        CsSession session = new CsSession();
        session.setGroupId(groupId);
        session.setUserAccountId(req.getUserId());
        session.setAiAccountId(ai.getEasemobId());
        session.setStatus(SessionStatus.AI_SERVING.name());
        session.setCurrentHandlerType(HandlerType.AI.name());
        session.setChannel(req.getChannel());
        session.setSkillGroupId(req.getSkillGroupId());
        session.setFailCount(0);
        session.setLastMsgTime(LocalDateTime.now());
        sessionMapper.insert(session);
        redis.opsForValue().set(RedisKeyConstant.currentSessionKey(req.getUserId()), String.valueOf(session.getId()));
        messageClient.sendGroupText(ai.getEasemobId(), groupId, "您好，我是智能客服小环～");
        log.info("创建会话成功, sessionId={}, groupId={}, aiAccountId={}, status={}, handlerType={}",
                session.getId(), session.getGroupId(), ai.getEasemobId(), session.getStatus(), session.getCurrentHandlerType());
        return StartSessionResp.builder()
                .sessionId(session.getId())
                .groupId(session.getGroupId())
                .status(session.getStatus())
                .handlerType(session.getCurrentHandlerType())
                .aiAccountId(ai.getEasemobId())
                .aiNickname(ai.getNickname())
                .welcome("您好，我是智能客服小环～")
                .build();
    }

    @Transactional
    public void close(Long sessionId, String by, String reason) {
        log.info("关闭会话, sessionId={}, by={}, reason={}", sessionId, by, reason);
        CsSession s = sessionMapper.selectById(sessionId);
        if (s == null || SessionStatus.CLOSED.name().equals(s.getStatus())) {
            log.info("关闭会话跳过, 会话不存在或已关闭, sessionId={}", sessionId);
            return;
        }
        sessionMapper.updateStatusClosed(sessionId, reason);
        if (HandlerType.HUMAN.name().equals(s.getCurrentHandlerType()) && s.getAgentAccountId() != null) {
            accountService.decrLoad(s.getAgentAccountId());
        }
        redis.delete(RedisKeyConstant.currentSessionKey(s.getUserAccountId()));
        try {
            messageClient.sendGroupText(s.getAiAccountId(), s.getGroupId(),
                    "本次会话已结束，感谢您的咨询。欢迎为本次服务评分～",
                    java.util.Map.of("bizType", "CLOSED"));
        } catch (Exception e) {
            log.warn("send close message failed, sessionId={}", sessionId, e);
        }
        try {
            groupClient.dismissGroup(s.getGroupId());
        } catch (Exception e) {
            log.warn("dismiss group failed, sessionId={}, groupId={}", sessionId, s.getGroupId(), e);
        }
    }

    @Transactional
    public void switchToHuman(CsSession session, CsAccount agent, String reason) {
        sessionMapper.updateToHuman(session.getId(), SessionStatus.HUMAN_SERVING.name(),
                agent.getEasemobId(), reason);
        session.setStatus(SessionStatus.HUMAN_SERVING.name());
        session.setAgentAccountId(agent.getEasemobId());
        session.setCurrentHandlerType(HandlerType.HUMAN.name());
        session.setTransferReason(reason);
        log.info("会话切换为人工服务, sessionId={}, agentId={}, reason={}",
                session.getId(), agent.getEasemobId(), reason);
    }

    @Transactional
    public void markWaiting(CsSession session, String reason) {
        sessionMapper.updateWaiting(session.getId(), reason);
        session.setCurrentHandlerType(HandlerType.HUMAN.name());
        session.setTransferReason(reason);
        log.info("会话标记为等待人工, sessionId={}, reason={}", session.getId(), reason);
    }

    public CsSession selectActiveByUser(String userId) {
        return sessionMapper.selectActiveByUser(userId);
    }

    public CsSession getById(Long sessionId) {
        return sessionMapper.selectById(sessionId);
    }
}
