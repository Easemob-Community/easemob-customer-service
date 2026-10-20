package com.easemob.customer.easemob.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackDispatchService {

    private final List<MessageCallbackHandler> handlers;

    @Async("callbackExecutor")
    public void dispatchAsync(CallbackPayload payload) {
        log.info("[CallbackDispatch] received payload: msgId={}, from={}, groupId={}, text={}",
                payload.getMsgId(), payload.getFrom(), payload.getGroupId(), payload.getText());
        for (MessageCallbackHandler handler : handlers) {
            try {
                handler.handle(payload);
            } catch (Exception e) {
                log.error("[CallbackDispatch] handler {} failed, msgId={}",
                        handler.getClass().getSimpleName(), payload.getMsgId(), e);
            }
        }
    }
}
