package com.hmdp.pay.service;

import lombok.Data;

@Data
public class PayTokenPayload {
    private Long orderId;
    private Long userId;
    private Long expireAt;
}

