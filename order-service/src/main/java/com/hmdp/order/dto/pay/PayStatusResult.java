package com.hmdp.order.dto.pay;

import lombok.Data;

@Data
public class PayStatusResult {
    /**
     * true: 第三方已支付
     * false: 未支付/不存在/失败
     */
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

    public static PayStatusResult fail(String code, String message) {
        PayStatusResult r = new PayStatusResult();
        r.setSuccess(false);
        r.setTradeStatus("ERROR");
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}

