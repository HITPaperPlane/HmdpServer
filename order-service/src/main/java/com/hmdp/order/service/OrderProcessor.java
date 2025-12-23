package com.hmdp.order.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.order.config.RabbitMQTopicConfig;
import com.hmdp.order.dto.OrderMessage;
import com.hmdp.order.dto.OrderTimeoutMessage;
import com.hmdp.order.entity.MessageOutbox;
import com.hmdp.order.entity.SeckillVoucher;
import com.hmdp.order.entity.VoucherOrder;
import com.hmdp.order.mapper.MessageOutboxMapper;
import com.hmdp.order.mapper.UserQuotaMapper;
import com.hmdp.order.mapper.VoucherOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderProcessor extends ServiceImpl<VoucherOrderMapper, VoucherOrder> {

    private final com.hmdp.order.mapper.SeckillVoucherMapper seckillVoucherMapper;
    private final UserQuotaMapper userQuotaMapper;
    private final MessageOutboxMapper outboxMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final long STATUS_TTL_MINUTES = 30;
    private static final String STATUS_KEY_PREFIX = "seckill:{seckill}:status:";
    private static final String STOCK_KEY_PREFIX = "seckill:{seckill}:stock:";
    private static final String OUTBOX_BIZ_TYPE_ORDER_CLOSE = "ORDER_CLOSE";

    @Transactional
    public void process(OrderMessage message) {
        String requestId = cn.hutool.core.util.StrUtil.isNotBlank(message.getRequestId())
                ? message.getRequestId()
                : String.valueOf(message.getOrderId());
        message.setRequestId(requestId);
        int buyCount = message.getCount() == null || message.getCount() < 1 ? 1 : message.getCount();
        int limitType = message.getLimitType() == null ? 1 : message.getLimitType();
        int userLimit = message.getUserLimit() == null ? Integer.MAX_VALUE : message.getUserLimit();
        // 幂等：若 request_id 已存在则直接返回
        VoucherOrder exists = this.getBaseMapper().selectOne(
                new QueryWrapper<VoucherOrder>().eq("request_id", requestId));
        if (exists != null) {
            log.info("request {} already processed", message.getOrderId());
            writeStatus(requestId, "SUCCESS", exists.getId(), exists.getVoucherId(), exists.getUserId(), null, exists.getCount());
            return;
        }

        // 业务限购校验
        boolean quotaTouched = false;
        if (limitType == 1) {
            if (buyCount > 1) {
                writeStatus(requestId, "FAILED", null, message.getVoucherId(), message.getUserId(), "LIMIT", buyCount);
                return;
            }
            Long cnt = this.count(new QueryWrapper<VoucherOrder>()
                    .eq("user_id", message.getUserId())
                    .eq("voucher_id", message.getVoucherId()));
            if (cnt != null && cnt > 0) {
                log.warn("user {} already bought voucher {}", message.getUserId(), message.getVoucherId());
                writeStatus(requestId, "FAILED", null, message.getVoucherId(), message.getUserId(), "LIMIT", buyCount);
                return;
            }
        } else if (limitType == 3) {
            if (userLimit <= 0) {
                writeStatus(requestId, "FAILED", null, message.getVoucherId(), message.getUserId(), "LIMIT", buyCount);
                return;
            }
            int affected = userQuotaMapper.upsertQuota(message.getUserId(), message.getVoucherId(), buyCount, userLimit);
            if (affected == 0) {
                log.warn("user {} exceeds quota for voucher {}", message.getUserId(), message.getVoucherId());
                writeStatus(requestId, "FAILED", null, message.getVoucherId(), message.getUserId(), "LIMIT", buyCount);
                return;
            }
            quotaTouched = true;
        }

        // 扣减库存（乐观锁）
        int updated = seckillVoucherMapper.update(null, new UpdateWrapper<SeckillVoucher>()
                .setSql("stock = stock - " + buyCount)
                .eq("voucher_id", message.getVoucherId())
                .ge("stock", buyCount));
        if (updated <= 0) {
            log.warn("stock not enough for voucher {}", message.getVoucherId());
            if (quotaTouched) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            writeStatus(requestId, "FAILED", null, message.getVoucherId(), message.getUserId(), "STOCK", buyCount);
            return;
        }

        // 创建订单
        VoucherOrder order = new VoucherOrder()
                .setId(message.getOrderId())
                .setRequestId(requestId)
                .setUserId(message.getUserId())
                .setVoucherId(message.getVoucherId())
                .setCount(buyCount)
                .setLimitType(limitType)
                .setUserLimit(message.getUserLimit())
                .setPayType(2)
                .setStatus(1)
                .setCreateTime(LocalDateTime.now());
        try {
            this.save(order);
        } catch (DuplicateKeyException e) {
            log.info("request {} duplicate, ignore", message.getOrderId());
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            writeStatus(requestId, "SUCCESS", message.getOrderId(), message.getVoucherId(), message.getUserId(), null, buyCount);
            return;
        }
        enqueueTimeoutClose(order.getId());
        writeStatus(requestId, "SUCCESS", order.getId(), order.getVoucherId(), order.getUserId(), null, buyCount);
    }

    @Transactional
    public boolean paySuccess(Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = this.getBaseMapper().update(null, new UpdateWrapper<VoucherOrder>()
                .set("status", 2)
                .set("pay_type", 2)
                .set("pay_time", now)
                .set("update_time", now)
                .eq("id", orderId)
                .eq("status", 1));
        return updated > 0;
    }

    @Transactional
    public boolean cancelOrder(Long orderId) {
        VoucherOrder order = this.getById(orderId);
        if (order == null) {
            return false;
        }
        int count = order.getCount() == null || order.getCount() < 1 ? 1 : order.getCount();
        LocalDateTime now = LocalDateTime.now();
        int updated = this.getBaseMapper().update(null, new UpdateWrapper<VoucherOrder>()
                .set("status", 4)
                .set("update_time", now)
                .eq("id", orderId)
                .eq("status", 1));
        if (updated <= 0) {
            return false;
        }

        seckillVoucherMapper.update(null, new UpdateWrapper<SeckillVoucher>()
                .setSql("stock = stock + " + count)
                .eq("voucher_id", order.getVoucherId()));

        try {
            stringRedisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + order.getVoucherId(), count);
        } catch (Exception e) {
            log.error("restore redis stock failed, voucherId={}, count={}", order.getVoucherId(), count, e);
        }
        return true;
    }

    private void enqueueTimeoutClose(Long orderId) {
        OrderTimeoutMessage payload = new OrderTimeoutMessage();
        payload.setOrderId(orderId);
        payload.setTimestamp(System.currentTimeMillis());

        MessageOutbox outbox = new MessageOutbox()
                .setBizType(OUTBOX_BIZ_TYPE_ORDER_CLOSE)
                .setBizId(String.valueOf(orderId))
                .setExchangeName(RabbitMQTopicConfig.ORDER_DELAY_EXCHANGE)
                .setRoutingKey(RabbitMQTopicConfig.ORDER_DELAY_ROUTING_KEY)
                .setPayload(com.alibaba.fastjson.JSON.toJSONString(payload))
                .setStatus(0)
                .setRetryCount(0)
                .setNextRetryTime(LocalDateTime.now());
        try {
            outboxMapper.insert(outbox);
        } catch (DuplicateKeyException ignore) {
        }
    }

    private void writeStatus(String requestId, String status, Long orderId, Long voucherId, Long userId, String reason, Integer count) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status);
        if (orderId != null) {
            payload.put("orderId", orderId);
        }
        if (voucherId != null) {
            payload.put("voucherId", voucherId);
        }
        if (userId != null) {
            payload.put("userId", userId);
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(reason)) {
            payload.put("reason", reason);
        }
        payload.put("timestamp", System.currentTimeMillis());
        if (count != null) {
            payload.put("count", count);
        }
        stringRedisTemplate.opsForValue().set(
                STATUS_KEY_PREFIX + requestId,
                cn.hutool.json.JSONUtil.toJsonStr(payload),
                STATUS_TTL_MINUTES,
                TimeUnit.MINUTES
        );
    }
}
