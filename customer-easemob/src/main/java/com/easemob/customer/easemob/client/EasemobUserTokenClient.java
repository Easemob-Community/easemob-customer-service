package com.easemob.customer.easemob.client;

import com.easemob.customer.common.util.JsonUtil;
import com.easemob.customer.easemob.config.EasemobProperties;
import com.easemob.customer.easemob.token.AppTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EasemobUserTokenClient {

    private final EasemobProperties props;
    private final OkHttpClient http;
    private final AppTokenManager appTokenManager;

    public String fetchUserToken(String username, String password) {
        String token = doFetchUserToken(username, password);
        if (token == null) {
            registerUser(username, password);
            token = doFetchUserToken(username, password);
        }
        if (token == null) {
            throw new RuntimeException("Easemob user token fetch failed after registration: " + username);
        }
        return token;
    }

    private String doFetchUserToken(String username, String password) {
        String url = props.apiBase() + "/token";
        Map<String, String> body = Map.of(
                "grant_type", "password",
                "username", username,
                "password", password);
        RequestBody requestBody = RequestBody.create(JsonUtil.toJson(body), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build();
        try (Response response = http.newCall(request).execute()) {
            String respBody = response.body() == null ? null : response.body().string();
            if (!response.isSuccessful()) {
                log.warn("获取环信用户token失败(将尝试注册), username={}, status={}, responseBody={}",
                        username, response.code(), respBody);
                return null;
            }
            Map<String, Object> map = JsonUtil.fromJson(respBody,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            String accessToken = (String) map.get("access_token");
            if (accessToken == null || accessToken.isEmpty()) {
                log.warn("获取环信用户token失败, 响应缺少access_token, username={}, responseBody={}", username, respBody);
                return null;
            }
            log.info("获取环信用户token成功, username={}", username);
            return accessToken;
        } catch (IOException e) {
            log.error("获取环信用户token异常, username={}", username, e);
            return null;
        }
    }

    private void registerUser(String username, String password) {
        String url = props.apiBase() + "/users";
        Map<String, String> body = Map.of("username", username, "password", password);
        RequestBody requestBody = RequestBody.create(JsonUtil.toJson(body), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Authorization", "Bearer " + appTokenManager.getToken())
                .header("Content-Type", "application/json")
                .build();
        try (Response response = http.newCall(request).execute()) {
            String respBody = response.body() == null ? null : response.body().string();
            if (!response.isSuccessful()) {
                log.error("注册环信用户失败, username={}, status={}, responseBody={}", username, response.code(), respBody);
                throw new RuntimeException("Easemob register user failed: " + response.code() + " " + respBody);
            }
            log.info("注册环信用户成功, username={}", username);
        } catch (IOException e) {
            log.error("注册环信用户异常, username={}", username, e);
            throw new RuntimeException("Easemob register user error", e);
        }
    }
}
