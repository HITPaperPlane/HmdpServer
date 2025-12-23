package com.hmdp.pay.controller;

import com.hmdp.pay.dto.OrderPaidMessage;
import com.hmdp.pay.service.OrderPayEventPublisher;
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
    private final OrderPayEventPublisher eventPublisher;

    @PostMapping("/pay/mock/paid")
    public String markPaid(@RequestParam("orderId") Long orderId) {
        redisTemplate.opsForValue().set(TRADE_KEY_PREFIX + orderId, "PAID", TRADE_TTL_DAYS, TimeUnit.DAYS);
        OrderPaidMessage msg = new OrderPaidMessage();
        msg.setOrderId(orderId);
        msg.setPayTime(System.currentTimeMillis());
        msg.setPayType(2);
        eventPublisher.publishPaid(msg);
        return "OK";
    }

    @PostMapping("/pay/mock/clear")
    public String clear(@RequestParam("orderId") Long orderId) {
        redisTemplate.delete(TRADE_KEY_PREFIX + orderId);
        return "OK";
    }
}
