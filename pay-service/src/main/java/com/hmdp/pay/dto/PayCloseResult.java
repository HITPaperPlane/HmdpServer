package com.hmdp.pay.dto;

import lombok.Data;

@Data
public class PayCloseResult {
    private boolean success;
    private boolean tradeSuccess;
    private String code;
    private String message;

    public static PayCloseResult ok() {
        PayCloseResult r = new PayCloseResult();
        r.setSuccess(true);
        return r;
    }

    public static PayCloseResult tradeSuccess() {
        PayCloseResult r = new PayCloseResult();
        r.setSuccess(false);
        r.setTradeSuccess(true);
        return r;
    }

    public static PayCloseResult fail(String code, String message) {
        PayCloseResult r = new PayCloseResult();
        r.setSuccess(false);
        r.setTradeSuccess(false);
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}

