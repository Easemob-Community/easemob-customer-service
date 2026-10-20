package com.easemob.customer.easemob.client;

import com.easemob.customer.common.util.JsonUtil;
import com.easemob.customer.easemob.config.EasemobProperties;
import com.easemob.customer.easemob.token.AppTokenManager;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EasemobMessageClient {

    private final EasemobProperties props;
    private final AppTokenManager tokenManager;
    private final OkHttpClient http;

    public void sendGroupText(String from, String groupId, String text) {
        sendGroupText(from, groupId, text, null);
    }

    public void sendGroupText(String from, String groupId, String text, Map<String, Object> ext) {
        String url = props.apiBase() + "/messages/chatgroups";
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("from", from);
        body.put("to", List.of(groupId));
        body.put("type", "txt");
        body.put("target_type", "chatgroups");
        body.put("body", Map.of("msg", text));
        if (ext != null && !ext.isEmpty()) {
            body.put("ext", ext);
        }
        postJson(url, body);
    }

    public void sendUserText(String from, String toUserId, String text) {
        String url = props.apiBase() + "/messages/users";
        Map<String, Object> body = Map.of(
                "from", from,
                "to", List.of(toUserId),
                "type", "txt",
                "target_type", "users",
                "body", Map.of("msg", text));
        postJson(url, body);
    }

    private JsonNode postJson(String url, Map<String, Object> body) {
        RequestBody requestBody = RequestBody.create(JsonUtil.toJson(body), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + tokenManager.getToken())
                .post(requestBody)
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("Easemob message send error: " + response);
            }
            return JsonUtil.fromJson(response.body().string(), JsonNode.class);
        } catch (IOException e) {
            log.error("Easemob message send error, url={}", url, e);
            throw new RuntimeException("Easemob message send error", e);
        }
    }
}
