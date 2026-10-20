package com.easemob.customer.conversation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_transfer_record")
public class CsTransferRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String fromType;
    private String toAgentId;
    private String trigger;
    private String result;
    private LocalDateTime createTime;
}
