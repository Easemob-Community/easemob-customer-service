package com.easemob.customer.common.ai;

import java.util.List;

public record AiAnswer(String answer, double confidence, boolean transfer, List<String> suggests) {

    public static AiAnswer empty() {
        return new AiAnswer("", 0.0, false, List.of());
    }

    public static AiAnswer of(String answer) {
        return new AiAnswer(answer, 1.0, false, List.of());
    }
}
