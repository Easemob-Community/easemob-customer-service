package com.easemob.customer.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_ai_provider")
public class CsAiProvider {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String providerCode;
    private String name;
    private String baseUrl;
    private String appKey;
    private String appSecret;
    private Integer timeoutMs;
    private Integer retryTimes;
    private Integer weight;
    private Integer status;
    private String extraJson;
    private LocalDateTime createTime;
}
