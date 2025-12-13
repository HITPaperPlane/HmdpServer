package com.hmdp.order.dto;

import lombok.Data;

@Data
public class OrderMessage {
    private Long orderId;
    private Long voucherId;
    private Long userId;
    private Integer limitType;
    private Integer userLimit;
}
