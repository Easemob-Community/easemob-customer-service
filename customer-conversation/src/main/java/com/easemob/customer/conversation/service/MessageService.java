package com.easemob.customer.conversation.service;

import com.easemob.customer.common.ai.AiAnswer;
import com.easemob.customer.common.constant.RedisKeyConstant;
import com.easemob.customer.common.enums.MsgSenderType;
import com.easemob.customer.common.enums.MsgType;
import com.easemob.customer.conversation.entity.CsMessage;
import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.mapper.MessageMapper;
import com.easemob.customer.conversation.mapper.SessionMapper;
import com.easemob.customer.easemob.callback.CallbackPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;
    private final SessionMapper sessionMapper;
    private final StringRedisTemplate redis;

    @Transactional
    public CsMessage saveUserMessage(CsSession session, CallbackPayload payload) {
        CsMessage message = new CsMessage();
        message.setSessionId(session.getId());
        message.setMsgId(payload.getMsgId());
        message.setSenderId(payload.getFrom());
        message.setSenderType(MsgSenderType.USER.name());
        message.setMsgType(MsgType.TXT.name());
        message.setContent(payload.getText());
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);

        session.setLastMsgTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return message;
    }

    @Transactional
    public void saveAiMessage(CsSession session, AiAnswer answer) {
        CsMessage message = new CsMessage();
        message.setSessionId(session.getId());
        message.setMsgId("ai-" + System.currentTimeMillis());
        message.setSenderId(session.getAiAccountId());
        message.setSenderType(MsgSenderType.AI.name());
        message.setMsgType(MsgType.TXT.name());
        message.setContent(answer.answer());
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);

        session.setLastMsgTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    public boolean tryMarkProcessed(String msgId) {
        Boolean ok = redis.opsForValue()
                .setIfAbsent(RedisKeyConstant.msgIdemKey(msgId), "1", 24, TimeUnit.HOURS);
        return Boolean.TRUE.equals(ok);
    }

    public List<CsMessage> recentHistory(Long sessionId, int limit) {
        return messageMapper.selectRecentBySession(sessionId, limit);
    }

    public CsSession loadSessionByGroup(String groupId) {
        return sessionMapper.selectByGroupId(groupId);
    }
}
