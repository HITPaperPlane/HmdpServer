package com.hmdp.pay.service;

import com.hmdp.pay.dto.PayCloseResult;
import com.hmdp.pay.dto.PayStatusResult;

public interface PayGateway {
    PayStatusResult query(Long orderId);

    PayCloseResult close(Long orderId);

    String createPayPage(Long orderId, Long userId, String returnUrl, String userAgent);
}

