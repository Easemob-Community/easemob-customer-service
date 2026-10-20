package com.easemob.customer.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easemob.customer.ai.entity.CsAiProvider;
import com.easemob.customer.ai.entity.CsTransferWord;
import com.easemob.customer.ai.mapper.AiProviderMapper;
import com.easemob.customer.ai.mapper.TransferWordMapper;
import com.easemob.customer.ai.service.AiRouteService;
import com.easemob.customer.common.ai.AiAnswer;
import com.easemob.customer.common.ai.AiChatRequest;
import com.easemob.customer.common.exception.BizException;
import com.easemob.customer.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiRouteService aiRouteService;
    private final AiProviderMapper aiProviderMapper;
    private final TransferWordMapper transferWordMapper;

    @GetMapping("/providers")
    public R<List<CsAiProvider>> listProviders() {
        List<CsAiProvider> list = aiProviderMapper.selectList(
                new QueryWrapper<CsAiProvider>().orderByDesc("weight"));
        list.forEach(p -> p.setAppKey(mask(p.getAppKey())));
        return R.ok(list);
    }

    @PostMapping("/providers")
    public R<Void> createProvider(@RequestBody CsAiProvider provider) {
        validate(provider);
        provider.setId(null);
        provider.setStatus(provider.getStatus() == null ? 1 : provider.getStatus());
        provider.setWeight(provider.getWeight() == null ? 100 : provider.getWeight());
        provider.setTimeoutMs(provider.getTimeoutMs() == null ? 30000 : provider.getTimeoutMs());
        provider.setRetryTimes(provider.getRetryTimes() == null ? 1 : provider.getRetryTimes());
        provider.setCreateTime(LocalDateTime.now());
        aiProviderMapper.insert(provider);
        return R.ok();
    }

    @PutMapping("/providers/{id}")
    public R<Void> updateProvider(@PathVariable Long id, @RequestBody CsAiProvider provider) {
        CsAiProvider existing = aiProviderMapper.selectById(id);
        if (existing == null) {
            throw new BizException("厂家配置不存在");
        }
        validate(provider);
        provider.setId(id);
        if (!StringUtils.hasText(provider.getAppKey())) {
            provider.setAppKey(existing.getAppKey());
        }
        provider.setCreateTime(null);
        aiProviderMapper.updateById(provider);
        return R.ok();
    }

    @DeleteMapping("/providers/{id}")
    public R<Void> deleteProvider(@PathVariable Long id) {
        if (aiProviderMapper.selectById(id) == null) {
            throw new BizException("厂家配置不存在");
        }
        aiProviderMapper.deleteById(id);
        return R.ok();
    }

    @PostMapping("/provider/test")
    public R<Map<String, Object>> testProvider(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String question = body.getOrDefault("question", "你好").toString();
        CsAiProvider provider = aiProviderMapper.selectById(id);
        if (provider == null) {
            throw new BizException("厂家配置不存在");
        }
        long start = System.currentTimeMillis();
        try {
            AiAnswer answer = aiRouteService.chatWith(provider,
                    new AiChatRequest("test", "test", question, List.of()));
            long latency = System.currentTimeMillis() - start;
            return R.ok(Map.of(
                    "ok", StringUtils.hasText(answer.answer()),
                    "latencyMs", latency,
                    "answerSample", answer.answer() != null ? answer.answer() : ""));
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return R.ok(Map.of(
                    "ok", false,
                    "latencyMs", latency,
                    "answerSample", "调用失败: " + e.getMessage()));
        }
    }

    @GetMapping("/transfer-words")
    public R<List<String>> getTransferWords() {
        return R.ok(transferWordMapper.selectEnabledWords());
    }

    @PutMapping("/transfer-words")
    @Transactional
    public R<Void> updateTransferWords(@RequestBody List<String> words) {
        transferWordMapper.delete(new QueryWrapper<>());
        List<String> distinct = words.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        for (String w : distinct) {
            CsTransferWord word = new CsTransferWord();
            word.setWord(w);
            word.setStatus(1);
            word.setCreateTime(LocalDateTime.now());
            transferWordMapper.insert(word);
        }
        return R.ok();
    }

    @GetMapping("/templates")
    public R<Map<String, String>> getTemplates() {
        return R.ok(Map.of(
                "welcome", "您好，我是智能客服小环～",
                "fallback", "抱歉，我不太明白，您可以尝试转人工客服。",
                "queue", "正在为您排队，请稍候～",
                "close", "本次会话已结束，感谢您的咨询。"));
    }

    @PutMapping("/templates")
    public R<Void> updateTemplates(@RequestBody Map<String, String> templates) {
        return R.ok();
    }

    private static void validate(CsAiProvider provider) {
        if (!StringUtils.hasText(provider.getProviderCode())
                || !StringUtils.hasText(provider.getName())
                || !StringUtils.hasText(provider.getBaseUrl())) {
            throw new BizException("厂家类型、名称、地址不能为空");
        }
    }

    private static String mask(String key) {
        if (!StringUtils.hasText(key)) {
            return key;
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
    }
}
