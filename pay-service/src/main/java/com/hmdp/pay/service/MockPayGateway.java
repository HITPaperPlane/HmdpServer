package com.hmdp.pay.service;

import com.hmdp.pay.dto.PayCloseResult;
import com.hmdp.pay.dto.PayStatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pay.mock.enabled", havingValue = "true")
public class MockPayGateway implements PayGateway {

    private static final String TRADE_KEY_PREFIX = "pay:{pay}:trade:";
    private static final long TRADE_TTL_DAYS = 7;

    private final StringRedisTemplate redisTemplate;
    private final PayOrderRepository orderRepository;

    @Override
    public PayStatusResult query(Long orderId) {
        String v = redisTemplate.opsForValue().get(TRADE_KEY_PREFIX + orderId);
        if ("PAID".equalsIgnoreCase(v)) {
            return PayStatusResult.paid();
        }
        return PayStatusResult.unpaid();
    }

    @Override
    public PayCloseResult close(Long orderId) {
        String key = TRADE_KEY_PREFIX + orderId;
        String v = redisTemplate.opsForValue().get(key);
        if ("PAID".equalsIgnoreCase(v)) {
            return PayCloseResult.tradeSuccess();
        }
        redisTemplate.opsForValue().set(key, "CLOSED", TRADE_TTL_DAYS, TimeUnit.DAYS);
        return PayCloseResult.ok();
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
        BigDecimal total = BigDecimal.valueOf(unit).multiply(BigDecimal.valueOf(cnt));
        String title = StringUtils.hasText(info.getVoucherTitle()) ? info.getVoucherTitle() : ("订单 " + orderId);
        String safeReturn = StringUtils.hasText(returnUrl) ? returnUrl : "";

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset='utf-8' />")
                .append("<meta name='viewport' content='width=device-width,initial-scale=1' />")
                .append("<title>Mock Pay</title>")
                .append("<style>")
                .append("body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,Helvetica,Arial,sans-serif;")
                .append("background:#f7f8fa;margin:0;padding:24px;color:#111}")
                .append(".card{max-width:520px;margin:0 auto;background:#fff;border-radius:14px;")
                .append("box-shadow:0 10px 25px rgba(0,0,0,.06);padding:18px}")
                .append(".title{font-size:18px;font-weight:800}")
                .append(".meta{margin-top:10px;color:rgba(0,0,0,.65);font-size:13px;line-height:1.6}")
                .append(".btn{margin-top:16px;display:inline-block;border:none;border-radius:10px;")
                .append("background:#1677ff;color:#fff;padding:12px 16px;font-weight:700;cursor:pointer}")
                .append(".btn2{margin-left:10px;background:#f0f0f0;color:#111}")
                .append("</style></head><body><div class='card'>")
                .append("<div class='title'>模拟支付（Mock）</div>")
                .append("<div class='meta'>")
                .append("<div>订单号：").append(orderId).append("</div>")
                .append("<div>商品：").append(escapeHtml(title)).append("</div>")
                .append("<div>金额：¥").append(total.toPlainString()).append("</div>")
                .append("</div>")
                .append("<button class='btn' onclick='pay()'>模拟支付成功</button>")
                .append("<button class='btn btn2' onclick='back()'>返回</button>")
                .append("<script>")
                .append("async function pay(){")
                .append("await fetch('/pay/mock/paid?orderId=").append(orderId).append("',{method:'POST'}).catch(()=>{});")
                .append("if('").append(escapeJs(safeReturn)).append("'){location.href='").append(escapeJs(safeReturn)).append("';return;}")
                .append("document.querySelector('.title').innerText='已模拟支付成功';")
                .append("}")
                .append("function back(){history.length>1?history.back():location.href='").append(escapeJs(safeReturn)).append("';}")
                .append("</script></div></body></html>");
        return sb.toString();
    }

    private String html(String msg) {
        return "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'/>"
                + "<title>Pay</title></head><body style='font-family:Arial;padding:24px;'>" + escapeHtml(msg) + "</body></html>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
    }
}

