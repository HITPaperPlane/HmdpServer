package com.hmdp.relay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "canal")
@Data
public class CanalProperties {
    private String host;
    private Integer port = 11111;
    private String destination = "example";
    private String username;
    private String password;
    private String subscribe = "hmdp\\.tb_shop,hmdp\\.tb_blog";
    private Integer batchSize = 1000;
}
