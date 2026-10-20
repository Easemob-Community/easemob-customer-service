package com.easemob.customer.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.easemob.customer.common.result.R;
import com.easemob.customer.easemob.client.EasemobUserTokenClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EasemobUserTokenClient easemobUserTokenClient;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "agent");
        String password = body.getOrDefault("password", "1");
        String role = username.equals("admin") ? "admin" : (username.equals("leader") ? "leader" : "agent");
        String nickname = role.equals("admin") ? "管理员" : (role.equals("leader") ? "组长" : "坐席001");
        String easemobToken = easemobUserTokenClient.fetchUserToken(username, password);
        StpUtil.login(username, 7200);
        Map<String, Object> data = Map.of(
                "token", StpUtil.getTokenValue(),
                "userId", username,
                "nickname", nickname,
                "role", role,
                "permissions", List.of("workbench", "account", "assignment", "ai", "dashboard"),
                "easemobToken", easemobToken
        );
        return R.ok(data);
    }
}
