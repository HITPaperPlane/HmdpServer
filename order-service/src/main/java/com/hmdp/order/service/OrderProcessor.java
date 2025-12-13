package com.hmdp.order.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.order.dto.OrderMessage;
import com.hmdp.order.entity.SeckillVoucher;
import com.hmdp.order.entity.VoucherOrder;
import com.hmdp.order.mapper.VoucherOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderProcessor extends ServiceImpl<VoucherOrderMapper, VoucherOrder> {

    private final com.hmdp.order.mapper.SeckillVoucherMapper seckillVoucherMapper;

    @Transactional
    public void process(OrderMessage message) {
        // 幂等：若 request_id 已存在则直接返回
        VoucherOrder exists = this.getBaseMapper().selectOne(
                new QueryWrapper<VoucherOrder>().eq("request_id", message.getOrderId().toString()));
        if (exists != null) {
            log.info("request {} already processed", message.getOrderId());
            return;
        }

        // 业务限购校验
        if (message.getLimitType() != null) {
            if (message.getLimitType() == 1) {
                Integer count = this.count(new QueryWrapper<VoucherOrder>()
                        .eq("user_id", message.getUserId())
                        .eq("voucher_id", message.getVoucherId()));
                if (count != null && count > 0) {
                    log.warn("user {} already bought voucher {}", message.getUserId(), message.getVoucherId());
                    return;
                }
            } else if (message.getLimitType() == 3) {
                Integer count = this.count(new QueryWrapper<VoucherOrder>()
                        .eq("user_id", message.getUserId())
                        .eq("voucher_id", message.getVoucherId()));
                if (count != null && count >= message.getUserLimit()) {
                    log.warn("user {} reach limit {} for voucher {}", message.getUserId(), message.getUserLimit(), message.getVoucherId());
                    return;
                }
            }
        }

        // 扣减库存（乐观锁）
        int updated = seckillVoucherMapper.update(null, new UpdateWrapper<SeckillVoucher>()
                .setSql("stock = stock - 1")
                .eq("voucher_id", message.getVoucherId())
                .gt("stock", 0));
        if (updated <= 0) {
            log.warn("stock not enough for voucher {}", message.getVoucherId());
            return;
        }

        // 创建订单
        VoucherOrder order = new VoucherOrder()
                .setId(message.getOrderId())
                .setRequestId(message.getOrderId().toString())
                .setUserId(message.getUserId())
                .setVoucherId(message.getVoucherId())
                .setLimitType(message.getLimitType())
                .setUserLimit(message.getUserLimit())
                .setPayType(1)
                .setStatus(1)
                .setCreateTime(LocalDateTime.now());
        try {
            this.save(order);
        } catch (DuplicateKeyException e) {
            log.info("request {} duplicate, ignore", message.getOrderId());
        }
    }
}
