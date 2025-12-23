package com.hmdp.pay.dto;

import lombok.Data;

@Data
public class PayStatusResult {
    private boolean success;
    private String tradeStatus;
    private String code;
    private String message;

    public static PayStatusResult paid() {
        PayStatusResult r = new PayStatusResult();
        r.setSuccess(true);
        r.setTradeStatus("PAID");
        return r;
    }

    public static PayStatusResult unpaid() {
        PayStatusResult r = new PayStatusResult();
        r.setSuccess(false);
        r.setTradeStatus("UNPAID");
        return r;
    }
}

