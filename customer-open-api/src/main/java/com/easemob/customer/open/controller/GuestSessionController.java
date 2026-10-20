package com.easemob.customer.open.controller;

import com.easemob.customer.assignment.service.AssignmentService;
import com.easemob.customer.common.exception.BizException;
import com.easemob.customer.common.result.R;
import com.easemob.customer.conversation.dto.TransferReq;
import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/session")
@RequiredArgsConstructor
public class GuestSessionController {

    private final SessionService sessionService;
    private final AssignmentService assignmentService;

    @PostMapping("/transfer")
    public R<Void> transfer(@RequestBody @Valid TransferReq req) {
        CsSession session = sessionService.getById(req.getSessionId());
        if (session == null) {
            throw new BizException(4003, "会话不存在");
        }
        if (!session.getUserAccountId().equals(req.getUserId())) {
            throw new BizException(4003, "会话用户不匹配");
        }
        assignmentService.transferToHuman(session, req.getReason());
        return R.ok();
    }
}
