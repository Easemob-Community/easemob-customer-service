package com.easemob.customer.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_familiar_relation")
public class CsFamiliarRelation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String agentId;
    private Integer serveCount;
    private LocalDateTime lastServeTime;
}
