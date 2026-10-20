package com.easemob.customer.assignment.queue;

import com.easemob.customer.assignment.service.AssignmentService;
import com.easemob.customer.conversation.dto.QueueConsumeEvent;
import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueConsumeEventListener {

    private final WaitQueueService waitQueueService;
    private final AssignmentService assignmentService;
    private final SessionService sessionService;

    @EventListener
    public void onQueueConsume(QueueConsumeEvent event) {
        log.info("Queue consume event received, sessionId={}, skillGroupId={}",
                event.getSessionId(), event.getSkillGroupId());
        Long sessionId = waitQueueService.dequeue(event.getSkillGroupId());
        if (sessionId == null) {
            return;
        }
        CsSession session = sessionService.getById(sessionId);
        if (session == null) {
            return;
        }
        log.info("Queue consume assigning sessionId={}", sessionId);
        assignmentService.transferToHuman(session, "QUEUE_ASSIGN");
    }
}
