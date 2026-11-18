package com.easemob.customer.easemob.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 环信消息回调报文。当前环信控制台下发的新版格式为下划线字段：
 * msg_id / group_id / chat_type，文本在 payload.bodies[0].msg。
 */
@Data
public class CallbackPayload {

    @JsonProperty("msg_id")
    private String msgId;
    private String from;
    private String to;
    @JsonProperty("group_id")
    private String groupId;
    @JsonProperty("chat_type")
    private String chatType;
    private Long timestamp;
    private Payload payload;

    /** 取第一条消息体文本；非文本消息返回 null */
    public String getText() {
        if (payload == null || payload.getBodies() == null || payload.getBodies().isEmpty()) {
            return null;
        }
        return payload.getBodies().get(0).getMsg();
    }

    @Data
    public static class Payload {
        private List<Body> bodies;
    }

    @Data
    public static class Body {
        private String msg;
        private String type;
    }
}
