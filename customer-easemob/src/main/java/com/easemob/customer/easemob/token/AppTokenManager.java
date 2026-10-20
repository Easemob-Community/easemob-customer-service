package com.easemob.customer.easemob.token;

import com.easemob.customer.common.constant.RedisKeyConstant;
import com.easemob.customer.common.util.JsonUtil;
import com.easemob.customer.easemob.config.EasemobProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppTokenManager {

    private final EasemobProperties props;
    private final StringRedisTemplate redis;
    private final OkHttpClient http;

    public String getToken() {
        String cached = redis.opsForValue().get(RedisKeyConstant.EASEMOB_TOKEN);
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = redis.opsForValue().get(RedisKeyConstant.EASEMOB_TOKEN);
            if (cached != null) {
                return cached;
            }
            TokenResp resp = fetchTokenFromEasemob();
            long ttl = Math.max(resp.expiresIn() - 300, 60);
            redis.opsForValue().set(RedisKeyConstant.EASEMOB_TOKEN, resp.accessToken(), ttl, TimeUnit.SECONDS);
            return resp.accessToken();
        }
    }

    private TokenResp fetchTokenFromEasemob() {
        String url = props.apiBase() + "/token";
        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "client_id", props.getClientId(),
                "client_secret", props.getClientSecret());
        RequestBody requestBody = RequestBody.create(JsonUtil.toJson(body), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("Easemob token fetch failed: " + response);
            }
            Map<String, Object> map = JsonUtil.fromJson(response.body().string(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            String accessToken = (String) map.get("access_token");
            Number expires = (Number) map.get("expires_in");
            return new TokenResp(accessToken, expires != null ? expires.longValue() : 7200L);
        } catch (IOException e) {
            log.error("fetchTokenFromEasemob error", e);
            throw new RuntimeException("Easemob token fetch error", e);
        }
    }

    private record TokenResp(String accessToken, long expiresIn) {
    }
}
