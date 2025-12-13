package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useClusterServers()
                .addNodeAddress("redis://123.56.100.212:6379",
                        "redis://39.97.193.168:6379",
                        "redis://115.190.193.236:6379")
                .setPassword("123456");
        return Redisson.create(config);
    }
}
