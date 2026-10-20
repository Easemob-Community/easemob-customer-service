package com.easemob.customer.conversation.controller;

import com.easemob.customer.common.result.PageResult;
import com.easemob.customer.common.result.R;
import com.easemob.customer.conversation.dto.EvaluateReq;
import com.easemob.customer.conversation.entity.CsMessage;
import com.easemob.customer.conversation.service.EvaluateService;
import com.easemob.customer.conversation.service.MessageService;
import com.easemob.customer.conversation.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/open")
@RequiredArgsConstructor
public class OpenMessageController {

    private final MessageService messageService;
    private final SessionService sessionService;
    private final EvaluateService evaluateService;

    @GetMapping("/message/history")
    public R<PageResult<CsMessage>> history(@RequestParam Long sessionId,
                                              @RequestParam(required = false) String beforeMsgId,
                                              @RequestParam(defaultValue = "20") int size) {
        List<CsMessage> list = messageService.recentHistory(sessionId, Math.min(size, 100));
        PageResult<CsMessage> result = new PageResult<>(list.size(), 1L, size, list);
        return R.ok(result);
    }

    @PostMapping("/session/close")
    public R<Void> close(@RequestParam Long sessionId,
                         @RequestParam(required = false) String reason) {
        sessionService.close(sessionId, "USER", reason != null ? reason : "USER_CLOSE");
        return R.ok();
    }

    @PostMapping("/session/evaluate")
    public R<Void> evaluate(@RequestBody @Valid EvaluateReq req) {
        evaluateService.evaluate(req);
        return R.ok();
    }
}
