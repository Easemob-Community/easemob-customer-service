package com.easemob.customer.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.easemob.customer.common.enums.AccountType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_account")
public class CsAccount {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String easemobId;
    private AccountType type;
    private Long groupId;
    private String nickname;
    private String avatar;
    private Integer maxSessions;
    private Integer status;
    private String extJson;
    private LocalDateTime createTime;
}
