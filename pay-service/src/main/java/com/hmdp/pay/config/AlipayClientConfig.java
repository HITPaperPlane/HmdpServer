package com.hmdp.pay.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(name = "pay.mock.enabled", havingValue = "false", matchIfMissing = true)
public class AlipayClientConfig {

    @Bean
    public AlipayClient alipayClient(AlipayProperties properties) {
        if (!StringUtils.hasText(properties.getGatewayUrl())) {
            throw new IllegalStateException("pay.alipay.gateway-url is required");
        }
        if (!StringUtils.hasText(properties.getAppId())) {
            throw new IllegalStateException("pay.alipay.app-id is required");
        }
        if (!StringUtils.hasText(properties.getAppPrivateKey())) {
            throw new IllegalStateException("pay.alipay.app-private-key is required");
        }
        if (!StringUtils.hasText(properties.getAlipayPublicKey())) {
            throw new IllegalStateException("pay.alipay.alipay-public-key is required");
        }
        return new DefaultAlipayClient(
                properties.getGatewayUrl(),
                properties.getAppId(),
                properties.getAppPrivateKey(),
                "json",
                properties.getCharset(),
                properties.getAlipayPublicKey(),
                properties.getSignType()
        );
    }
}

