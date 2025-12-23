package com.hmdp.pay.controller;

import com.alipay.api.internal.util.AlipaySignature;
import com.hmdp.pay.config.AlipayProperties;
import com.hmdp.pay.dto.OrderPaidMessage;
import com.hmdp.pay.service.OrderPayEventPublisher;
import com.hmdp.pay.service.PayOrderInfo;
import com.hmdp.pay.service.PayOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pay.mock.enabled", havingValue = "false", matchIfMissing = true)
public class AlipayNotifyController {

    private static final String TRADE_KEY_PREFIX = "pay:{pay}:trade:";
    private static final long TRADE_TTL_DAYS = 7;

    private final AlipayProperties alipayProperties;
    private final PayOrderRepository orderRepository;
    private final OrderPayEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    @PostMapping("/alipay/notify")
    public String notify(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        if (params.isEmpty()) {
            return "failure";
        }

        boolean verified;
        try {
            verified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayProperties.getAlipayPublicKey(),
                    alipayProperties.getCharset(),
                    alipayProperties.getSignType()
            );
        } catch (Exception e) {
            log.warn("alipay notify verify error", e);
            return "failure";
        }
        if (!verified) {
            log.warn("alipay notify sign verify failed");
            return "failure";
        }

        String appId = params.get("app_id");
        if (StringUtils.hasText(appId) && StringUtils.hasText(alipayProperties.getAppId())
                && !alipayProperties.getAppId().equals(appId)) {
            log.warn("alipay notify appId mismatch, appId={}", appId);
            return "failure";
        }

        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");
        String totalAmount = params.get("total_amount");

        Long orderId;
        try {
            orderId = Long.valueOf(outTradeNo);
        } catch (Exception e) {
            log.warn("alipay notify invalid out_trade_no={}", outTradeNo);
            return "failure";
        }

        if (!"TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) && !"TRADE_FINISHED".equalsIgnoreCase(tradeStatus)) {
            log.info("alipay notify ignored, orderId={}, tradeStatus={}", orderId, tradeStatus);
            return "success";
        }

        PayOrderInfo info = orderRepository.findVoucherOrder(orderId);
        if (info == null) {
            log.warn("alipay notify order not found, orderId={}", orderId);
            return "failure";
        }
        int cnt = info.getCount() == null || info.getCount() < 1 ? 1 : info.getCount();
        long unit = info.getPayValue() == null ? 0L : info.getPayValue();
        BigDecimal expected = BigDecimal.valueOf(unit).multiply(BigDecimal.valueOf(cnt)).setScale(2, RoundingMode.HALF_UP);
        try {
            if (StringUtils.hasText(totalAmount)) {
                BigDecimal actual = new BigDecimal(totalAmount).setScale(2, RoundingMode.HALF_UP);
                if (expected.compareTo(actual) != 0) {
                    log.warn("alipay notify amount mismatch, orderId={}, expected={}, actual={}", orderId, expected, actual);
                    return "failure";
                }
            }
        } catch (Exception e) {
            log.warn("alipay notify parse amount failed, orderId={}, total_amount={}", orderId, totalAmount, e);
            return "failure";
        }

        redisTemplate.opsForValue().set(TRADE_KEY_PREFIX + orderId, "PAID", TRADE_TTL_DAYS, TimeUnit.DAYS);

        OrderPaidMessage msg = new OrderPaidMessage();
        msg.setOrderId(orderId);
        msg.setPayTime(System.currentTimeMillis());
        msg.setPayType(2);
        msg.setTradeNo(tradeNo);
        try {
            eventPublisher.publishPaid(msg);
        } catch (Exception e) {
            log.error("publish paid event failed, orderId={}", orderId, e);
            return "failure";
        }

        log.info("alipay notify processed, orderId={}, tradeNo={}", orderId, tradeNo);
        return "success";
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> raw = request.getParameterMap();
        if (raw == null || raw.isEmpty()) {
            return params;
        }
        raw.forEach((k, v) -> {
            if (v == null || v.length == 0) {
                params.put(k, "");
            } else {
                params.put(k, v[0]);
            }
        });
        return params;
    }
}

