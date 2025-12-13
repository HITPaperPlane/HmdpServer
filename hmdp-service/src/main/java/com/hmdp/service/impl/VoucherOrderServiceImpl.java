package com.hmdp.service.impl;

import com.google.common.util.concurrent.RateLimiter;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
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

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //令牌桶算法 限流
        if (!rateLimiter.tryAcquire(1000, TimeUnit.MILLISECONDS)){
            return Result.fail("目前网络正忙，请重试");
        }
        // 查询秒杀券元数据
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            return Result.fail("秒杀未开始");
        }
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀已结束");
        }
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        String stockKey = com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String orderKey = com.hmdp.utils.RedisConstants.SECKILL_LIMIT_SET_KEY + voucherId;
        String userCountKey = com.hmdp.utils.RedisConstants.SECKILL_USER_COUNT_KEY + voucherId;
        String outboxKey = com.hmdp.utils.RedisConstants.SECKILL_OUTBOX_KEY;

        Long r = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                java.util.Arrays.asList(stockKey, orderKey, userCountKey, outboxKey),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId),
                String.valueOf(voucher.getLimitType()),
                String.valueOf(voucher.getUserLimit())
        );
        //2.判断结果为0
        int result = r.intValue();
        if (result != 0) {
            //2.1不为0代表没有购买资格
            String msg = "库存不足";
            if (result == 2) {
                msg = "已达到限购次数";
            }
            return Result.fail(msg);
        }
        // 缓冲队列已入队，返回前端排队中
        return Result.ok(orderId);
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
    public Result queryMyOrders(Integer current, Integer size) {
        Long userId = UserHolder.getUser().getId();
        int page = (current == null || current < 1) ? 1 : current;
        int limit = (size == null || size < 1) ? SystemConstants.MAX_PAGE_SIZE : Math.min(size, SystemConstants.MAX_PAGE_SIZE);
        Page<VoucherOrder> pager = query().eq("user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(page, limit));
        return Result.ok(pager.getRecords());
    }
}
