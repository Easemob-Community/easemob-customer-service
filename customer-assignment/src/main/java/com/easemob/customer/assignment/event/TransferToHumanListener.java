package com.easemob.customer.assignment.event;

import com.easemob.customer.assignment.service.AssignmentService;
import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.event.TransferToHumanEvent;
import com.easemob.customer.conversation.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferToHumanListener {

    private final AssignmentService assignmentService;
    private final SessionService sessionService;

    @EventListener
    public void onTransfer(TransferToHumanEvent event) {
        CsSession session = sessionService.getById(event.getSessionId());
        if (session == null) {
            return;
        }
        assignmentService.transferToHuman(session, event.getReason());
    }
}
