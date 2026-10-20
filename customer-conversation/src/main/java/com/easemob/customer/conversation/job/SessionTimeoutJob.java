package com.easemob.customer.conversation.job;

import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionTimeoutJob {

    private final SessionService sessionService;

    @Scheduled(fixedDelay = 60_000)
    public void closeTimeoutSessions() {
        log.info("SessionTimeoutJob start");
    }
}
