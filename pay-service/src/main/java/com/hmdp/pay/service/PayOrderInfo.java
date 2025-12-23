package com.hmdp.pay.service;

import lombok.Data;

@Data
public class PayOrderInfo {
    private Long orderId;
    private Long userId;
    private Long voucherId;
    private Integer count;
    private Integer status;
    private String voucherTitle;
    private Long payValue;
}

