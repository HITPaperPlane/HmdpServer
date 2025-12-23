package com.hmdp.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pay.alipay")
public class AlipayProperties {
    private String appId;
    private String appPrivateKey;
    private String alipayPublicKey;
    private String gatewayUrl;
    private String charset = "utf-8";
    private String signType = "RSA2";
    private String notifyUrl;
    private String returnUrlDefault;
}

