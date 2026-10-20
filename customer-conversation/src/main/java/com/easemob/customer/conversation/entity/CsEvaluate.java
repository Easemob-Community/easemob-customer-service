package com.easemob.customer.conversation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_evaluate")
public class CsEvaluate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer score;
    private String tags;
    private String comment;
    private LocalDateTime createTime;
}
