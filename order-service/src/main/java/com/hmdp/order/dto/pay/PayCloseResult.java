package com.hmdp.order.dto.pay;

import lombok.Data;

@Data
public class PayCloseResult {
    /**
     * true: 已成功关单/关闭交易
     * false: 关单失败或无需关单
     */
    private boolean success;

    /**
     * true: 关单失败的原因是“交易已支付/已成功”，需要触发补单
     */
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

