package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IShopService shopService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    public Result queryVoucherOfShopForManage(Long shopId) {
        Result check = validateShopManager(shopId);
        if (!Boolean.TRUE.equals(check.getSuccess())) {
            return check;
        }
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShopForManage(shopId);
        return Result.ok(vouchers);
    }

    @Override
    public Result createVoucher(Voucher voucher) {
        Result check = validateShopOwner(voucher.getShopId());
        if (!Boolean.TRUE.equals(check.getSuccess())) {
            return check;
        }
        boolean saved = super.save(voucher);
        return saved ? Result.ok(voucher.getId()) : Result.fail("保存优惠券失败");
    }

    @Override
    @Transactional
    public Result addSeckillVoucher(Voucher voucher) {
        Result check = validateShopOwner(voucher.getShopId());
        if (!Boolean.TRUE.equals(check.getSuccess())) {
            return check;
        }
        UserDTO current = UserHolder.getUser();
        boolean isAdmin = current != null && "ADMIN".equalsIgnoreCase(StrUtil.blankToDefault(current.getRole(), ""));
        // 保存优惠券到普通优惠券voucher数据库
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucher.setLimitType(voucher.getLimitType());
        seckillVoucher.setUserLimit(voucher.getUserLimit());
        if (isAdmin) {
            // 管理员创建：直接预热 Redis 库存，用户端可立即参与秒杀
            stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
            seckillVoucher.setPreheatStatus(2);
        } else {
            // 商家创建：进入待审核/待预热状态，需管理员端审核并预热后用户端才可见/可抢
            seckillVoucher.setPreheatStatus(0);
        }
        seckillVoucherService.save(seckillVoucher);
        return Result.ok(voucher.getId());
    }

    @Override
    @Transactional
    public Result preheatSeckillVoucher(Long voucherId) {
        UserDTO current = UserHolder.getUser();
        boolean isAdmin = current != null && "ADMIN".equalsIgnoreCase(StrUtil.blankToDefault(current.getRole(), ""));
        if (!isAdmin) {
            return Result.fail("无权限");
        }
        if (voucherId == null) {
            return Result.fail("voucherId 不能为空");
        }
        SeckillVoucher sv = seckillVoucherService.getById(voucherId);
        if (sv == null) {
            return Result.fail("秒杀券不存在");
        }
        if (sv.getStock() == null || sv.getStock() < 0) {
            return Result.fail("库存未配置");
        }
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucherId, sv.getStock().toString());
        sv.setPreheatStatus(2);
        seckillVoucherService.updateById(sv);
        return Result.ok();
    }

    private Result validateShopOwner(Long shopId) {
        if (shopId == null) {
            return Result.fail("店铺不能为空");
        }
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.fail("未登录，无法发券");
        }
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        boolean isOwner = Objects.equals(shop.getCreatedBy(), current.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(StrUtil.blankToDefault(current.getRole(), ""));
        if (!isOwner && !isAdmin) {
            return Result.fail("只能为自己创建的店铺发券");
        }
        return Result.ok();
    }

    private Result validateShopManager(Long shopId) {
        if (shopId == null) {
            return Result.fail("店铺不能为空");
        }
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.fail("未登录");
        }
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        boolean isOwner = Objects.equals(shop.getCreatedBy(), current.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(StrUtil.blankToDefault(current.getRole(), ""));
        if (!isOwner && !isAdmin) {
            return Result.fail("无权限查看该店铺券");
        }
        return Result.ok();
    }
}
