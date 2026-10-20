package com.easemob.customer.conversation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TransferToHumanEvent extends ApplicationEvent {

    private final Long sessionId;
    private final String reason;

    public TransferToHumanEvent(Object source, Long sessionId, String reason) {
        super(source);
        this.sessionId = sessionId;
        this.reason = reason;
    }
}
