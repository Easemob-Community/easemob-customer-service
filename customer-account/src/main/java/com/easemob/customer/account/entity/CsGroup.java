package com.easemob.customer.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.easemob.customer.common.enums.AccountType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_group")
public class CsGroup {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private AccountType type;
    private Long parentId;
    private Integer status;
    private LocalDateTime createTime;
}
