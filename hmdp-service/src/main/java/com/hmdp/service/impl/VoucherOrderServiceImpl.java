package com.hmdp.service.impl;

import com.google.common.util.concurrent.RateLimiter;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    private RateLimiter rateLimiter=RateLimiter.create(10);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final String REQ_TOKEN_SECRET = "hmdp-seckill-req-secret";
    private static final long STATUS_TTL_SECONDS = 1800L;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId, String requestId, Integer count) {
        //令牌桶算法 限流
        if (!rateLimiter.tryAcquire(1000, TimeUnit.MILLISECONDS)){
            return Result.fail("目前网络正忙，请重试");
        }
        // 查询秒杀券元数据
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        int limitType = voucher.getLimitType() == null ? 1 : voucher.getLimitType();
        if (limitType == 3 && (voucher.getUserLimit() == null || voucher.getUserLimit() <= 0)) {
            return Result.fail("累计限购必须配置限购数量");
        }
        int buyCount = (count == null || count < 1) ? 1 : count;
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            return Result.fail("秒杀未开始");
        }
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀已结束");
        }
        Long userId = UserHolder.getUser().getId();
        // 一人一单：后端生成确定性 reqId；其他类型必须由前端传入并验签
        String resolvedReqId;
        if (limitType == 1) {
            buyCount = 1;
            resolvedReqId = DigestUtils.md5DigestAsHex((voucherId + ":" + userId).getBytes(StandardCharsets.UTF_8));
        } else {
            resolvedReqId = validateRequestToken(voucherId, userId, requestId);
            if (!StringUtils.hasText(resolvedReqId)) {
                return Result.fail("请求ID非法或已过期，请重新获取");
            }
        }
        if (limitType == 3 && voucher.getUserLimit() != null && voucher.getUserLimit() > 0
                && buyCount > voucher.getUserLimit()) {
            return Result.fail("超过单次限购数量");
        }
        long orderId = redisIdWorker.nextId("order");

        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String orderKey = RedisConstants.SECKILL_LIMIT_SET_KEY + voucherId;
        String userCountKey = RedisConstants.SECKILL_USER_COUNT_KEY + voucherId;
        String outboxKey = RedisConstants.SECKILL_OUTBOX_KEY;
        String requestKey = RedisConstants.SECKILL_REQUEST_KEY + resolvedReqId;
        String statusKey = RedisConstants.SECKILL_STATUS_KEY + resolvedReqId;

        com.hmdp.dto.SeckillMessage payload = new com.hmdp.dto.SeckillMessage()
                .setOrderId(orderId)
                .setRequestId(resolvedReqId)
                .setVoucherId(voucherId)
                .setUserId(userId)
                .setCount(buyCount)
                .setLimitType(limitType)
                .setUserLimit(voucher.getUserLimit())
                .setTimestamp(System.currentTimeMillis());
        String payloadJson = cn.hutool.json.JSONUtil.toJsonStr(payload);

        Long r = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                java.util.Arrays.asList(stockKey, orderKey, userCountKey, outboxKey, requestKey, statusKey),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId),
                String.valueOf(limitType),
                String.valueOf(voucher.getUserLimit()),
                String.valueOf(buyCount),
                payloadJson
        );
        int result = r == null ? -1 : r.intValue();
        if (result != 0) {
            String reason = result == 2 ? "LIMIT" : "STOCK";
            writeImmediateStatus(statusKey, resolvedReqId, voucherId, userId, reason, orderId, buyCount);
            return Result.fail(result == 2 ? "已达到限购次数" : "库存不足");
        }
        // 缓冲队列已入队，返回前端排队中
        return Result.ok(resolvedReqId);
//        单机模式下，使用synchronized实现锁
//        synchronized (userId.toString().intern())
//        {
//            //    createVoucherOrder的事物不会生效,因为你调用的方法，其实是this.的方式调用的，事务想要生效，
//            //    还得利用代理来生效，所以这个地方，我们需要获得原始的事务对象， 来操作事务
//            return voucherOrderService.createVoucherOrder(voucherId);
//        }
    }


//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        // 一人一单逻辑
//        Long userId = UserHolder.getUser().getId();
//
//
//        int count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
//        if (count > 0){
//            return Result.fail("你已经抢过优惠券了哦");
//        }
//
//        //5. 扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .gt("stock",0)   //加了CAS 乐观锁，Compare and swap
//                .update();
//
//        if (!success) {
//            return Result.fail("库存不足");
//        }
//
////        库存足且在时间范围内的，则创建新的订单
//        //6. 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //6.1 设置订单id，生成订单的全局id
//        long orderId = redisIdWorker.nextId("order");
//        //6.2 设置用户id
//        Long id = UserHolder.getUser().getId();
//        //6.3 设置代金券id
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(id);
//        //7. 将订单数据保存到表中
//        save(voucherOrder);
//        //8. 返回订单id
//        return Result.ok(orderId);
//    }

    @Override
    public Result queryStatus(String requestId) {
        if (!org.springframework.util.StringUtils.hasText(requestId)) {
            return Result.fail("请求ID不能为空");
        }
        String resolved = unwrapReqId(requestId);
        if (!StringUtils.hasText(resolved)) {
            return Result.fail("请求ID非法");
        }
        String statusKey = RedisConstants.SECKILL_STATUS_KEY + resolved;
        String cached = stringRedisTemplate.opsForValue().get(statusKey);
        if (cn.hutool.core.util.StrUtil.isNotBlank(cached)) {
            try {
                java.util.Map<String, Object> resp = cn.hutool.json.JSONUtil.toBean(cached, java.util.HashMap.class);
                Object statusVal = resp.get("status");
                if (statusVal instanceof String) {
                    resp.put("status", ((String) statusVal).toUpperCase());
                }
                return Result.ok(resp);
            } catch (Exception ignore) {
                // malformed cache, continue fallback
            }
        }
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("status", "NOT_FOUND");
        return Result.ok(resp);
    }

    @Override
    public Result generateRequestId(Long voucherId, Integer count) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        if (voucher.getLimitType() != null && voucher.getLimitType() == 1) {
            return Result.fail("一人一单不需要生成请求ID");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀已结束");
        }
        if (voucher.getLimitType() != null && voucher.getLimitType() == 3
                && (voucher.getUserLimit() == null || voucher.getUserLimit() <= 0)) {
            return Result.fail("累计限购必须配置限购数量");
        }
        int buyCount = (count == null || count < 1) ? 1 : count;
        if (voucher.getLimitType() != null && voucher.getLimitType() == 3
                && voucher.getUserLimit() != null && voucher.getUserLimit() > 0
                && buyCount > voucher.getUserLimit()) {
            return Result.fail("超过单次限购数量");
        }
        Long userId = UserHolder.getUser().getId();
        String reqId = String.valueOf(redisIdWorker.nextId("req"));
        String sign = sign(reqId, voucherId, userId);
        stringRedisTemplate.opsForValue().set(
                RedisConstants.SECKILL_REQ_TOKEN_KEY + reqId,
                voucherId + ":" + userId,
                RedisConstants.SECKILL_REQ_TOKEN_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        return Result.ok(reqId + "." + sign);
    }

    @Override
    public Result queryMyOrders(Integer current, Integer size) {
        Long userId = UserHolder.getUser().getId();
        int page = (current == null || current < 1) ? 1 : current;
        int limit = (size == null || size < 1) ? SystemConstants.MAX_PAGE_SIZE : Math.min(size, SystemConstants.MAX_PAGE_SIZE);
        Page<VoucherOrder> pager = query().eq("user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(page, limit));
        return Result.ok(pager.getRecords());
    }

    private void writeImmediateStatus(String statusKey, String reqId, Long voucherId, Long userId, String reason, Long orderId, Integer count) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("status", "FAILED");
        payload.put("reason", reason);
        payload.put("voucherId", voucherId);
        payload.put("userId", userId);
        if (count != null) {
            payload.put("count", count);
        }
        if (orderId != null) {
            payload.put("orderId", orderId);
        }
        payload.put("timestamp", System.currentTimeMillis());
        stringRedisTemplate.opsForValue().set(statusKey, cn.hutool.json.JSONUtil.toJsonStr(payload), STATUS_TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.expire(RedisConstants.SECKILL_REQUEST_KEY + reqId, STATUS_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private String validateRequestToken(Long voucherId, Long userId, String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return null;
        }
        String reqId = parts[0];
        String sign = parts[1];
        String expected = sign(reqId, voucherId, userId);
        if (!expected.equals(sign)) {
            return null;
        }
        String cached = stringRedisTemplate.opsForValue().get(RedisConstants.SECKILL_REQ_TOKEN_KEY + reqId);
        String expectedCache = voucherId + ":" + userId;
        if (!expectedCache.equals(cached)) {
            return null;
        }
        return reqId;
    }

    private String sign(String reqId, Long voucherId, Long userId) {
        String raw = reqId + ":" + voucherId + ":" + userId;
        return DigestUtils.md5DigestAsHex((raw + REQ_TOKEN_SECRET).getBytes(StandardCharsets.UTF_8));
    }

    private String unwrapReqId(String incoming) {
        if (!StringUtils.hasText(incoming)) {
            return null;
        }
        int idx = incoming.indexOf('.');
        return idx > 0 ? incoming.substring(0, idx) : incoming;
    }
}
