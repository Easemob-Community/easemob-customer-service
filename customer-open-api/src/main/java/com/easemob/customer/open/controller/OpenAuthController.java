package com.easemob.customer.open.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.easemob.customer.common.result.R;
import com.easemob.customer.easemob.client.EasemobUserTokenClient;
import com.easemob.customer.open.dto.GuestTokenResp;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/open/auth")
@RequiredArgsConstructor
public class OpenAuthController {

    private final EasemobUserTokenClient easemobUserTokenClient;

    @PostMapping("/guest-token")
    public R<GuestTokenResp> guestToken(@RequestBody Map<String, Object> body) {
        Object userIdObj = body.get("userId");
        String userId = (userIdObj == null || userIdObj.toString().isBlank())
                ? "guest_" + UUID.randomUUID().toString().replace("-", "")
                : userIdObj.toString();
        String easemobToken = easemobUserTokenClient.fetchUserToken(userId, "1");
        StpUtil.login(userId, 7200);
        GuestTokenResp resp = GuestTokenResp.builder()
                .token(StpUtil.getTokenValue())
                .userId(userId)
                .easemobToken(easemobToken)
                .expiresIn(7200L)
                .build();
        return R.ok(resp);
    }
}
