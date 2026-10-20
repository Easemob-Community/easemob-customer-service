package com.easemob.customer.conversation.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class QueueConsumeEvent {

    private final Long sessionId;
    private final Long skillGroupId;
}
