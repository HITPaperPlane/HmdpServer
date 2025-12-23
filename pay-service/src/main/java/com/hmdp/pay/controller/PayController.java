package com.hmdp.pay.controller;

import com.hmdp.pay.dto.PayCloseResult;
import com.hmdp.pay.dto.PayStatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class PayController {

    private static final String TRADE_KEY_PREFIX = "pay:{pay}:trade:";
    private static final long TRADE_TTL_DAYS = 7;

    private final StringRedisTemplate redisTemplate;

    @PostMapping("/pay/query")
    public PayStatusResult query(@RequestParam("orderId") Long orderId) {
        String v = redisTemplate.opsForValue().get(TRADE_KEY_PREFIX + orderId);
        if ("PAID".equalsIgnoreCase(v)) {
            return PayStatusResult.paid();
        }
        return PayStatusResult.unpaid();
    }

    @PostMapping("/pay/close")
    public PayCloseResult close(@RequestParam("orderId") Long orderId) {
        String key = TRADE_KEY_PREFIX + orderId;
        String v = redisTemplate.opsForValue().get(key);
        if ("PAID".equalsIgnoreCase(v)) {
            return PayCloseResult.tradeSuccess();
        }
        redisTemplate.opsForValue().set(key, "CLOSED", TRADE_TTL_DAYS, TimeUnit.DAYS);
        return PayCloseResult.ok();
    }
}

