package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        return voucherService.createVoucher(voucher);
    }

    /**
     * 新增秒杀券
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        return voucherService.addSeckillVoucher(voucher);
    }

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }

    /**
     * 管理端查询店铺券列表（商家/管理员可见，包含待预热秒杀券）
     */
    @GetMapping("/list/manage/{shopId}")
    public Result queryVoucherOfShopForManage(@PathVariable("shopId") Long shopId) {
        return voucherService.queryVoucherOfShopForManage(shopId);
    }

    /**
     * 管理员审核并预热秒杀券（写入 Redis 库存键）
     */
    @PostMapping("/seckill/preheat/{id}")
    public Result preheatSeckill(@PathVariable("id") Long voucherId) {
        return voucherService.preheatSeckillVoucher(voucherId);
    }
}
