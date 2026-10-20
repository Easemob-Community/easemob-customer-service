package com.easemob.customer.ai.service;

import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.service.MessageService;
import com.easemob.customer.easemob.callback.CallbackPayload;
import com.easemob.customer.easemob.callback.MessageCallbackHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 环信回调中的用户消息：落库后交给 AI 编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMessageCallbackHandler implements MessageCallbackHandler {

    private final MessageService messageService;
    private final AiChatService aiChatService;

    @Override
    public void handle(CallbackPayload p) {
        if (p.getMsgId() == null || p.getGroupId() == null) {
            return;
        }
        // 幂等：重复回调直接忽略
        if (!messageService.tryMarkProcessed(p.getMsgId())) {
            return;
        }
        CsSession session = messageService.loadSessionByGroup(p.getGroupId());
        if (session == null || !Objects.equals(p.getFrom(), session.getUserAccountId())) {
            return;
        }
        messageService.saveUserMessage(session, p);
        aiChatService.handleUserMessage(session, p.getText());
    }
}
