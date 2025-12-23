package com.hmdp.order.dto;

import lombok.Data;

@Data
public class OrderTimeoutMessage {
    private Long orderId;
    private Long timestamp;
}

