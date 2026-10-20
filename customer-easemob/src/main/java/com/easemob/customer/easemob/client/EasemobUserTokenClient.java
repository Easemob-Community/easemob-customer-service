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
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Easemob user token fetch failed (will try register): {}", response);
                return null;
            }
            Map<String, Object> map = JsonUtil.fromJson(response.body().string(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            String accessToken = (String) map.get("access_token");
            if (accessToken == null || accessToken.isEmpty()) {
                return null;
            }
            return accessToken;
        } catch (IOException e) {
            log.error("fetchUserToken error", e);
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
            if (!response.isSuccessful() || response.body() == null) {
                String bodyStr = response.body() != null ? response.body().string() : "";
                log.error("Easemob register user failed: {} {}", response, bodyStr);
                throw new RuntimeException("Easemob register user failed: " + response + " " + bodyStr);
            }
            log.info("Easemob user registered: {}", username);
        } catch (IOException e) {
            log.error("registerUser error", e);
            throw new RuntimeException("Easemob register user error", e);
        }
    }
}
