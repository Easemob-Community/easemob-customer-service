package com.easemob.customer.conversation.dto;

import com.easemob.customer.conversation.entity.CsSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartSessionResp {

    private Long sessionId;
    private String groupId;
    private String status;
    private String handlerType;
    private String welcome;
    private String aiAccountId;
    private String aiNickname;

    public static StartSessionResp of(CsSession session) {
        return StartSessionResp.builder()
                .sessionId(session.getId())
                .groupId(session.getGroupId())
                .status(session.getStatus())
                .handlerType(session.getCurrentHandlerType())
                .aiAccountId(session.getAiAccountId())
                .welcome("您好，我是智能客服小环～")
                .build();
    }

    public String getAiAccountId() {
        return aiAccountId;
    }

    public String getAiNickname() {
        return aiNickname;
    }
}
