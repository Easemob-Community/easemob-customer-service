package com.easemob.customer.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_transfer_word")
public class CsTransferWord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    private Integer status;
    private LocalDateTime createTime;
}
