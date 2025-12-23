package com.hmdp.pay.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pay.mock.enabled", havingValue = "true")
public class MockPayController {

    private static final String TRADE_KEY_PREFIX = "pay:{pay}:trade:";
    private static final long TRADE_TTL_DAYS = 7;

    private final StringRedisTemplate redisTemplate;

    @PostMapping("/pay/mock/paid")
    public String markPaid(@RequestParam("orderId") Long orderId) {
        redisTemplate.opsForValue().set(TRADE_KEY_PREFIX + orderId, "PAID", TRADE_TTL_DAYS, TimeUnit.DAYS);
        return "OK";
    }

    @PostMapping("/pay/mock/clear")
    public String clear(@RequestParam("orderId") Long orderId) {
        redisTemplate.delete(TRADE_KEY_PREFIX + orderId);
        return "OK";
    }
}

