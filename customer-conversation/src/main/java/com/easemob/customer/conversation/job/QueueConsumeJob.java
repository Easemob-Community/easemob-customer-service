package com.easemob.customer.conversation.job;

import com.easemob.customer.conversation.dto.QueueConsumeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueConsumeJob {

    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 5_000)
    public void consumeWaitQueue() {
        log.debug("QueueConsumeJob start");
        eventPublisher.publishEvent(new QueueConsumeEvent(0L, 0L));
    }
}
