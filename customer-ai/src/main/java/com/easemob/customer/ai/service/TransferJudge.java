package com.easemob.customer.ai.service;

import com.easemob.customer.ai.mapper.TransferWordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferJudge {

    private final TransferWordMapper transferWordMapper;

    public boolean hitTransferWords(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        List<String> words = transferWordMapper.selectEnabledWords();
        return words.stream().anyMatch(w -> w != null && !w.isBlank()
                && lower.contains(w.toLowerCase()));
    }
}
