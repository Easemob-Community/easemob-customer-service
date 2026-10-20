package com.easemob.customer.easemob.callback;

import lombok.Data;

@Data
public class CallbackPayload {

    private String msgId;
    private String from;
    private String to;
    private String groupId;
    private String chatType;
    private String type;
    private String text;
    private Long timestamp;
}
