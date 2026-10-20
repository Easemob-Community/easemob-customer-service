package com.easemob.customer.easemob.callback;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/easemob/callback")
@RequiredArgsConstructor
public class CallbackController {

    private final CallbackDispatchService dispatchService;

    @PostMapping("/message")
    public Map<String, String> onMessage(@RequestBody CallbackPayload payload,
                                         HttpServletRequest req) {
        log.info("[EasemobCallback] from={}, msgId={}", req.getRemoteAddr(), payload.getMsgId());
        dispatchService.dispatchAsync(payload);
        return Map.of("status", "ok");
    }
}
