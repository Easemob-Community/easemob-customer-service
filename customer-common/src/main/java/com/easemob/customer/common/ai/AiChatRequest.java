package com.easemob.customer.common.ai;

import java.util.List;

public record AiChatRequest(String sessionId, String userId, String question, List<AiMessage> history) {
}
