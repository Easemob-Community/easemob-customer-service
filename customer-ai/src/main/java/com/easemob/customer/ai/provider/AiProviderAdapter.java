package com.easemob.customer.ai.provider;

import com.easemob.customer.ai.entity.CsAiProvider;
import com.easemob.customer.common.ai.AiAnswer;
import com.easemob.customer.common.ai.AiChatRequest;

public interface AiProviderAdapter {

    String providerCode();

    AiAnswer chat(AiChatRequest req, CsAiProvider cfg);
}
