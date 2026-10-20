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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EasemobGroupClient {

    private final EasemobProperties props;
    private final AppTokenManager tokenManager;
    private final OkHttpClient http;

    public String createGroup(String ownerUserId, List<String> members, String groupName) {
        String url = props.apiBase() + "/chatgroups";
        Map<String, Object> body = new HashMap<>();
        body.put("groupname", groupName);
        body.put("desc", "customer-service-session");
        body.put("public", false);
        body.put("members_only", true);
        body.put("allowinvites", false);
        body.put("maxusers", 10);
        body.put("owner", ownerUserId);
        body.put("members", members);
        log.info("create easemob group, url={}, owner={}, members={}, groupName={}", url, ownerUserId, members, groupName);
        JsonNode resp = postJson(url, body);
        String groupId = resp.path("data").path("groupid").asText();
        log.info("create easemob group success, groupId={}, resp={}", groupId, resp);
        return groupId;
    }

    public void addMember(String groupId, String agentId) {
        String url = props.apiBase() + "/chatgroups/" + groupId + "/users/" + agentId;
        postJson(url, Map.of());
    }

    public void dismissGroup(String groupId) {
        String url = props.apiBase() + "/chatgroups/" + groupId;
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + tokenManager.getToken())
                .delete()
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Easemob dismiss group error: " + response);
            }
        } catch (IOException e) {
            log.error("Easemob dismiss group error, groupId={}", groupId, e);
            throw new RuntimeException("Easemob dismiss group error", e);
        }
    }

    private JsonNode postJson(String url, Map<String, Object> body) {
        RequestBody requestBody = RequestBody.create(JsonUtil.toJson(body), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + tokenManager.getToken())
                .post(requestBody)
                .build();
        try (Response response = http.newCall(request).execute()) {
            String respBody = response.body() == null ? null : response.body().string();
            if (!response.isSuccessful()) {
                log.error("Easemob API error, url={}, requestBody={}, status={}, responseBody={}",
                        url, JsonUtil.toJson(body), response.code(), respBody);
                throw new RuntimeException("Easemob API error: " + response.code() + " " + respBody);
            }
            return JsonUtil.fromJson(respBody, JsonNode.class);
        } catch (IOException e) {
            log.error("Easemob API error, url={}", url, e);
            throw new RuntimeException("Easemob API error", e);
        }
    }
}
