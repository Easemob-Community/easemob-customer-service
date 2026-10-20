package com.easemob.customer.ai.provider;

import com.easemob.customer.ai.entity.CsAiProvider;
import com.easemob.customer.common.ai.AiAnswer;
import com.easemob.customer.common.ai.AiChatRequest;
import com.easemob.customer.common.ai.AiMessage;
import com.easemob.customer.common.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容协议适配器（/chat/completions）。
 * 适用于 OpenAI 官方及 DeepSeek、Moonshot、通义兼容模式等遵循同一协议的厂商，
 * baseUrl 形如 https://api.openai.com/v1，模型与系统提示词在 extraJson 中配置：
 * {"model": "gpt-4o-mini", "systemPrompt": "..."}
 */
@Slf4j
@Component
public class OpenAiAdapter implements AiProviderAdapter {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    @Override
    public String providerCode() {
        return "OPENAI";
    }

    @Override
    public AiAnswer chat(AiChatRequest req, CsAiProvider cfg) {
        Map<String, String> extra = parseExtra(cfg.getExtraJson());
        String model = extra.getOrDefault("model", DEFAULT_MODEL);
        String systemPrompt = extra.getOrDefault("systemPrompt",
                "你是电商平台的智能客服助手，回答简洁友好，不确定时引导用户转人工。");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        if (req.history() != null) {
            for (AiMessage m : req.history()) {
                messages.add(Map.of("role", toOpenAiRole(m.role()), "content", m.content()));
            }
        }
        messages.add(Map.of("role", "user", "content", req.question()));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);

        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(cfg.getTimeoutMs() != null ? cfg.getTimeoutMs() : 30000,
                        TimeUnit.MILLISECONDS)
                .build();
        Request request = new Request.Builder()
                .url(chatCompletionsUrl(cfg.getBaseUrl()))
                .header("Authorization", "Bearer " + cfg.getAppKey())
                .post(RequestBody.create(JsonUtil.toJson(body), MediaType.parse("application/json")))
                .build();
        try (Response response = http.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("OpenAI API error: HTTP " + response.code() + " " + respBody);
            }
            Map<String, Object> map = JsonUtil.fromJson(respBody, new TypeReference<>() {
            });
            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("OpenAI API returned no choices: " + respBody);
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = message != null ? String.valueOf(message.get("content")) : "";
            return AiAnswer.of(content);
        } catch (IOException e) {
            log.error("OpenAI chat error", e);
            throw new RuntimeException("OpenAI chat error: " + e.getMessage(), e);
        }
    }

    private static String chatCompletionsUrl(String baseUrl) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return url.endsWith("/chat/completions") ? url : url + "/chat/completions";
    }

    private static String toOpenAiRole(String role) {
        if (role == null) {
            return "user";
        }
        return switch (role.toLowerCase()) {
            case "ai", "agent", "assistant" -> "assistant";
            case "system" -> "system";
            default -> "user";
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseExtra(String extraJson) {
        if (!StringUtils.hasText(extraJson)) {
            return Map.of();
        }
        try {
            return JsonUtil.fromJson(extraJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Invalid provider extraJson: {}", extraJson);
            return Map.of();
        }
    }
}
