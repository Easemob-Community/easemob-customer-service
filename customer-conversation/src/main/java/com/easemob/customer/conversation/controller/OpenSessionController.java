package com.easemob.customer.conversation.controller;

import com.easemob.customer.common.exception.BizException;
import com.easemob.customer.common.result.R;
import com.easemob.customer.conversation.dto.StartSessionReq;
import com.easemob.customer.conversation.dto.StartSessionResp;
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
public class OpenSessionController {

    private final SessionService sessionService;

    @PostMapping("/start")
    public R<StartSessionResp> start(@RequestBody @Valid StartSessionReq req) {
        return R.ok(sessionService.start(req));
    }
}
