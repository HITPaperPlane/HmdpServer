package com.hmdp.pay.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Service
public class PayTokenService {

    @Value("${pay.token.secret:hmdp-pay-secret}")
    private String payTokenSecret;

    public PayTokenPayload verify(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        Long orderId;
        Long userId;
        Long expireAt;
        try {
            orderId = Long.valueOf(parts[0]);
            userId = Long.valueOf(parts[1]);
            expireAt = Long.valueOf(parts[2]);
        } catch (Exception e) {
            return null;
        }
        if (expireAt <= System.currentTimeMillis()) {
            return null;
        }
        String expected = sign(orderId, userId, expireAt);
        if (!expected.equals(parts[3])) {
            return null;
        }
        PayTokenPayload payload = new PayTokenPayload();
        payload.setOrderId(orderId);
        payload.setUserId(userId);
        payload.setExpireAt(expireAt);
        return payload;
    }

    private String sign(Long orderId, Long userId, Long expireAt) {
        String raw = orderId + ":" + userId + ":" + expireAt;
        return DigestUtils.md5DigestAsHex((raw + ":" + payTokenSecret).getBytes(StandardCharsets.UTF_8));
    }
}

