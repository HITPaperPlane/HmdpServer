package com.hmdp.order.dto;

import lombok.Data;

@Data
public class OrderPaidMessage {
    private Long orderId;
    private Long payTime;
    private Integer payType;
    private String tradeNo;
}

