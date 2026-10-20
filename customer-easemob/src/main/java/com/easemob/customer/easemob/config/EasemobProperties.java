package com.easemob.customer.easemob.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "easemob")
public class EasemobProperties {

    private String orgName;
    private String appName;
    private String clientId;
    private String clientSecret;
    private String baseUrl;
    private String callbackSecret;

    public String appKey() {
        return orgName + "#" + appName;
    }

    public String apiBase() {
        return baseUrl + "/" + orgName + "/" + appName;
    }
}
