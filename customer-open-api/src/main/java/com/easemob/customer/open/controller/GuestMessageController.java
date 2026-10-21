package com.easemob.customer.open.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.easemob.customer.ai.service.AiChatService;
import com.easemob.customer.common.enums.MsgSenderType;
import com.easemob.customer.common.enums.MsgType;
import com.easemob.customer.common.enums.SessionStatus;
import com.easemob.customer.common.exception.BizException;
import com.easemob.customer.common.result.R;
import com.easemob.customer.conversation.entity.CsMessage;
import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.mapper.MessageMapper;
import com.easemob.customer.conversation.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/open/message")
@RequiredArgsConstructor
public class GuestMessageController {

    private static final int MAX_CONTENT_LENGTH = 100;

    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final AiChatService aiChatService;

    @PostMapping("/record")
    public R<Void> record(@RequestBody Map<String, Object> body) {
        String userId = StpUtil.getLoginId().toString();
        Long sessionId = Long.valueOf(body.getOrDefault("sessionId", "0").toString());
        String content = body.getOrDefault("content", "").toString();
        log.info("收到访客消息, userId={}, sessionId={}, content={}",
                userId, sessionId, truncate(content));
        CsSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserAccountId().equals(userId)) {
            throw new BizException(4003, "会话不存在或不匹配");
        }
        if (SessionStatus.CLOSED.name().equals(session.getStatus())
                || SessionStatus.EVALUATED.name().equals(session.getStatus())) {
            throw new BizException(4002, "会话已结束");
        }

        CsMessage message = new CsMessage();
        message.setSessionId(sessionId);
        String msgId = body.getOrDefault("msgId", "").toString();
        message.setMsgId(msgId.isBlank() ? "user-" + System.currentTimeMillis() : msgId);
        message.setSenderId(userId);
        message.setSenderType(MsgSenderType.USER.name());
        message.setMsgType(body.getOrDefault("type", MsgType.TXT.name()).toString());
        message.setContent(content);
        messageMapper.insert(message);

        session.setLastMsgTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        // 本地开发时环信回调到不了 localhost，由 record 直接驱动 AI 应答
        // （生产环境若配置了环信回调，AiChatService 内部按内容去重，不会重复回复）
        aiChatService.handleUserMessage(session, message.getContent());
        return R.ok();
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_CONTENT_LENGTH ? text.substring(0, MAX_CONTENT_LENGTH) + "..." : text;
    }
}
