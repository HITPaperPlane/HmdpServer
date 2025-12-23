package com.hmdp.pay.service;

import cn.hutool.json.JSONUtil;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayApiException;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.hmdp.pay.config.AlipayProperties;
import com.hmdp.pay.dto.PayCloseResult;
import com.hmdp.pay.dto.PayStatusResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pay.mock.enabled", havingValue = "false", matchIfMissing = true)
public class AlipayPayGateway implements PayGateway {

    private static final String TRADE_KEY_PREFIX = "pay:{pay}:trade:";
    private static final long TRADE_TTL_DAYS = 7;

    private final AlipayClient alipayClient;
    private final AlipayProperties alipayProperties;
    private final StringRedisTemplate redisTemplate;
    private final PayOrderRepository orderRepository;

    @Override
    public PayStatusResult query(Long orderId) {
        String key = TRADE_KEY_PREFIX + orderId;
        String cached = redisTemplate.opsForValue().get(key);
        if ("PAID".equalsIgnoreCase(cached)) {
            return PayStatusResult.paid();
        }
        if ("CLOSED".equalsIgnoreCase(cached)) {
            return PayStatusResult.unpaid();
        }

        AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
        Map<String, Object> biz = new HashMap<>();
        biz.put("out_trade_no", String.valueOf(orderId));
        req.setBizContent(JSONUtil.toJsonStr(biz));
        try {
            AlipayTradeQueryResponse resp = alipayClient.execute(req);
            if (resp == null || !resp.isSuccess()) {
                PayStatusResult r = PayStatusResult.unpaid();
                if (resp != null) {
                    r.setTradeStatus(resp.getTradeStatus());
                    r.setCode(resp.getCode());
                    r.setMessage(resp.getSubMsg());
                }
                return r;
            }
            String tradeStatus = resp.getTradeStatus();
            if ("TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) || "TRADE_FINISHED".equalsIgnoreCase(tradeStatus)) {
                redisTemplate.opsForValue().set(key, "PAID", TRADE_TTL_DAYS, TimeUnit.DAYS);
                PayStatusResult r = PayStatusResult.paid();
                r.setTradeStatus(tradeStatus);
                return r;
            }
            if ("TRADE_CLOSED".equalsIgnoreCase(tradeStatus)) {
                redisTemplate.opsForValue().set(key, "CLOSED", TRADE_TTL_DAYS, TimeUnit.DAYS);
            }
            PayStatusResult r = PayStatusResult.unpaid();
            r.setTradeStatus(tradeStatus);
            return r;
        } catch (AlipayApiException e) {
            log.error("alipay query failed, orderId={}", orderId, e);
            PayStatusResult r = PayStatusResult.unpaid();
            r.setTradeStatus("ERROR");
            r.setMessage("alipay query failed");
            return r;
        }
    }

    @Override
    public PayCloseResult close(Long orderId) {
        String key = TRADE_KEY_PREFIX + orderId;
        String cached = redisTemplate.opsForValue().get(key);
        if ("PAID".equalsIgnoreCase(cached)) {
            return PayCloseResult.tradeSuccess();
        }
        if ("CLOSED".equalsIgnoreCase(cached)) {
            return PayCloseResult.ok();
        }

        PayStatusResult status = query(orderId);
        if (status != null && status.isSuccess()) {
            return PayCloseResult.tradeSuccess();
        }

        AlipayTradeCloseRequest req = new AlipayTradeCloseRequest();
        Map<String, Object> biz = new HashMap<>();
        biz.put("out_trade_no", String.valueOf(orderId));
        req.setBizContent(JSONUtil.toJsonStr(biz));
        try {
            AlipayTradeCloseResponse resp = alipayClient.execute(req);
            if (resp != null && resp.isSuccess()) {
                redisTemplate.opsForValue().set(key, "CLOSED", TRADE_TTL_DAYS, TimeUnit.DAYS);
                return PayCloseResult.ok();
            }
            // trade not exist / already closed => treat as closed ok
            if (resp != null && ("ACQ.TRADE_NOT_EXIST".equalsIgnoreCase(resp.getSubCode())
                    || "ACQ.TRADE_HAS_CLOSE".equalsIgnoreCase(resp.getSubCode())
                    || "ACQ.TRADE_STATUS_ERROR".equalsIgnoreCase(resp.getSubCode()))) {
                PayStatusResult again = query(orderId);
                if (again != null && again.isSuccess()) {
                    return PayCloseResult.tradeSuccess();
                }
                redisTemplate.opsForValue().set(key, "CLOSED", TRADE_TTL_DAYS, TimeUnit.DAYS);
                return PayCloseResult.ok();
            }
            return PayCloseResult.fail(resp == null ? "NO_RESPONSE" : resp.getSubCode(), resp == null ? "close failed" : resp.getSubMsg());
        } catch (AlipayApiException e) {
            log.error("alipay close failed, orderId={}", orderId, e);
            return PayCloseResult.fail("ALIPAY_ERROR", "alipay close failed");
        }
    }

    @Override
    public String createPayPage(Long orderId, Long userId, String returnUrl, String userAgent) {
        PayOrderInfo info = orderRepository.findVoucherOrder(orderId);
        if (info == null) {
            return html("订单不存在或尚未落库，请稍后重试");
        }
        if (info.getUserId() == null || !info.getUserId().equals(userId)) {
            return html("无权支付该订单");
        }
        if (info.getStatus() == null || info.getStatus() != 1) {
            return html("订单当前不可支付（可能已支付或已取消）");
        }

        int cnt = info.getCount() == null || info.getCount() < 1 ? 1 : info.getCount();
        long unit = info.getPayValue() == null ? 0L : info.getPayValue();
        BigDecimal total = BigDecimal.valueOf(unit).multiply(BigDecimal.valueOf(cnt)).setScale(2, RoundingMode.HALF_UP);
        String subject = StringUtils.hasText(info.getVoucherTitle())
                ? (info.getVoucherTitle() + (cnt > 1 ? (" x" + cnt) : ""))
                : ("HMDP Voucher Order " + orderId);

        boolean isMobile = isMobile(userAgent);
        String targetReturnUrl = StringUtils.hasText(returnUrl) ? returnUrl : alipayProperties.getReturnUrlDefault();
        String notifyUrl = alipayProperties.getNotifyUrl();

        Map<String, Object> biz = new HashMap<>();
        biz.put("out_trade_no", String.valueOf(orderId));
        biz.put("total_amount", total.toPlainString());
        biz.put("subject", subject);
        biz.put("timeout_express", "30m");

        try {
            if (isMobile) {
                biz.put("product_code", "QUICK_WAP_WAY");
                AlipayTradeWapPayRequest req = new AlipayTradeWapPayRequest();
                req.setBizContent(JSONUtil.toJsonStr(biz));
                if (StringUtils.hasText(targetReturnUrl)) {
                    req.setReturnUrl(targetReturnUrl);
                }
                if (StringUtils.hasText(notifyUrl)) {
                    req.setNotifyUrl(notifyUrl);
                }
                return alipayClient.pageExecute(req).getBody();
            }
            biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
            AlipayTradePagePayRequest req = new AlipayTradePagePayRequest();
            req.setBizContent(JSONUtil.toJsonStr(biz));
            if (StringUtils.hasText(targetReturnUrl)) {
                req.setReturnUrl(targetReturnUrl);
            }
            if (StringUtils.hasText(notifyUrl)) {
                req.setNotifyUrl(notifyUrl);
            }
            return alipayClient.pageExecute(req).getBody();
        } catch (Exception e) {
            log.error("create pay page failed, orderId={}", orderId, e);
            return html("生成支付页面失败，请稍后重试");
        }
    }

    private boolean isMobile(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return true;
        }
        String ua = userAgent.toLowerCase();
        return ua.contains("android") || ua.contains("iphone") || ua.contains("ipad") || ua.contains("mobile");
    }

    private String html(String msg) {
        return "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'/>"
                + "<title>Pay</title></head><body style='font-family:Arial;padding:24px;'>" + escapeHtml(msg) + "</body></html>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

