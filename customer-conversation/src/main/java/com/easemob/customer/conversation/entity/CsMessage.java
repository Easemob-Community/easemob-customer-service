package com.easemob.customer.conversation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_message")
public class CsMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String msgId;
    private String senderId;
    private String senderType;
    private String msgType;
    private String content;
    private String bizType;
    private LocalDateTime createTime;
}
