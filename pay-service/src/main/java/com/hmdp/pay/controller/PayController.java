package com.hmdp.pay.controller;

import com.hmdp.pay.dto.PayCloseResult;
import com.hmdp.pay.dto.PayStatusResult;
import com.hmdp.pay.service.PayGateway;
import com.hmdp.pay.service.PayTokenPayload;
import com.hmdp.pay.service.PayTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PayController {

    private final PayGateway payGateway;
    private final PayTokenService payTokenService;

    @PostMapping("/pay/query")
    public PayStatusResult query(@RequestParam("orderId") Long orderId) {
        return payGateway.query(orderId);
    }

    @PostMapping("/pay/close")
    public PayCloseResult close(@RequestParam("orderId") Long orderId) {
        return payGateway.close(orderId);
    }

    @GetMapping(value = "/pay/start", produces = "text/html;charset=UTF-8")
    public String start(@RequestParam("token") String token,
                        @RequestParam(value = "returnUrl", required = false) String returnUrl,
                        @RequestParam(value = "scene", required = false) String scene,
                        @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        PayTokenPayload payload = payTokenService.verify(token);
        if (payload == null || payload.getOrderId() == null || payload.getUserId() == null) {
            return "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'/>"
                    + "<title>Pay</title></head><body style='font-family:Arial;padding:24px;'>无效或过期的支付链接</body></html>";
        }
        String ua = userAgent;
        if (StringUtils.hasText(scene)) {
            String s = scene.trim().toUpperCase();
            if ("PAGE".equals(s)) {
                ua = "desktop";
            } else if ("WAP".equals(s)) {
                ua = "mobile";
            }
        }
        return payGateway.createPayPage(payload.getOrderId(), payload.getUserId(), returnUrl, ua);
    }
}
