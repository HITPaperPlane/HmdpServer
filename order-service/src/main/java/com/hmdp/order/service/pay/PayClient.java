package com.hmdp.order.service.pay;

import com.hmdp.order.dto.pay.PayCloseResult;
import com.hmdp.order.dto.pay.PayStatusResult;

public interface PayClient {
    PayStatusResult queryPayStatus(Long orderId);

    PayCloseResult closeTrade(Long orderId);
}

