package com.easemob.customer.conversation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_session")
public class CsSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String groupId;
    private String userAccountId;
    private String aiAccountId;
    private String agentAccountId;
    private String status;
    private String currentHandlerType;
    private String channel;
    private Long skillGroupId;
    private String transferReason;
    private Integer failCount;
    private Integer firstResponseMs;
    private LocalDateTime lastMsgTime;
    private String closeReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public void resetFailCount() {
        this.failCount = 0;
    }
}
