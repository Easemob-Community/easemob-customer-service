package com.easemob.customer.ai.provider;

import com.easemob.customer.ai.entity.CsAiProvider;
import com.easemob.customer.common.ai.AiAnswer;
import com.easemob.customer.common.ai.AiChatRequest;
import com.easemob.customer.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DifyAdapter implements AiProviderAdapter {

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @Override
    public String providerCode() {
        return "DIFY";
    }

    @Override
    public AiAnswer chat(AiChatRequest req, CsAiProvider cfg) {
        String url = cfg.getBaseUrl() + "/chat-messages";
        Map<String, Object> body = Map.of(
                "inputs", Map.of(),
                "query", req.question(),
                "user", req.userId(),
                "response_mode", "blocking",
                "conversation_id", "");
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + cfg.getAppKey())
                .post(RequestBody.create(JsonUtil.toJson(body), MediaType.parse("application/json")))
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("Dify API error: " + response);
            }
            Map<String, Object> map = JsonUtil.fromJson(response.body().string(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            String answer = map.getOrDefault("answer", "").toString();
            return AiAnswer.of(answer);
        } catch (IOException e) {
            log.error("Dify chat error", e);
            throw new RuntimeException("Dify chat error", e);
        }
    }
}
