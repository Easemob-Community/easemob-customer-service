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
        log.debug("收到排队消费事件, sessionId={}, skillGroupId={}",
                event.getSessionId(), event.getSkillGroupId());
        Long sessionId = waitQueueService.dequeue(event.getSkillGroupId());
        if (sessionId == null) {
            log.debug("排队消费无待分配会话, skillGroupId={}", event.getSkillGroupId());
            return;
        }
        CsSession session = sessionService.getById(sessionId);
        if (session == null) {
            log.warn("排队消费会话不存在, sessionId={}", sessionId);
            return;
        }
        log.info("排队消费开始重试分配, sessionId={}", sessionId);
        assignmentService.transferToHuman(session, "QUEUE_ASSIGN");
        log.info("排队消费重试分配结束, sessionId={}, status={}", sessionId, session.getStatus());
    }
}
