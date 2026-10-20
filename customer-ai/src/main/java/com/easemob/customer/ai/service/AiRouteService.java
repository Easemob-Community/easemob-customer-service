package com.easemob.customer.ai.service;

import com.easemob.customer.ai.entity.CsAiProvider;
import com.easemob.customer.ai.mapper.AiProviderMapper;
import com.easemob.customer.ai.provider.AiProviderAdapter;
import com.easemob.customer.common.ai.AiAnswer;
import com.easemob.customer.common.ai.AiChatRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRouteService {

    private final AiProviderMapper aiProviderMapper;
    private final List<AiProviderAdapter> adapterList;

    private Map<String, AiProviderAdapter> adapters;

    @PostConstruct
    void init() {
        adapters = adapterList.stream()
                .collect(Collectors.toMap(AiProviderAdapter::providerCode, Function.identity()));
        log.info("AI provider adapters registered: {}", adapters.keySet());
    }

    public AiAnswer chatWithFailover(AiChatRequest req) {
        List<CsAiProvider> chain = aiProviderMapper.selectEnabledByWeight();
        for (CsAiProvider cfg : chain) {
            AiProviderAdapter adapter = adapters.get(cfg.getProviderCode());
            if (adapter == null) {
                log.warn("No adapter for provider {}", cfg.getProviderCode());
                continue;
            }
            for (int i = 0; i <= cfg.getRetryTimes(); i++) {
                try {
                    return adapter.chat(req, cfg);
                } catch (Exception e) {
                    log.warn("AI provider {} failed, retry={}", cfg.getProviderCode(), i, e);
                }
            }
        }
        return AiAnswer.empty();
    }

    public AiAnswer chatWith(CsAiProvider cfg, AiChatRequest req) {
        AiProviderAdapter adapter = adapters.get(cfg.getProviderCode());
        if (adapter == null) {
            throw new IllegalArgumentException("不支持的厂家类型: " + cfg.getProviderCode());
        }
        return adapter.chat(req, cfg);
    }
}
