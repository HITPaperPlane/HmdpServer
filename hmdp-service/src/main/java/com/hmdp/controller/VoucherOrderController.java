package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Autowired
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId,
                                 @RequestParam(value = "reqId", required = false) String requestId,
                                 @RequestParam(value = "count", required = false, defaultValue = "1") Integer count) {
        return voucherOrderService.seckillVoucher(voucherId, requestId, count);
    }

    @GetMapping("req/{id}")
    public Result generateRequestId(@PathVariable("id") Long voucherId,
                                    @RequestParam(value = "count", required = false, defaultValue = "1") Integer count) {
        return voucherOrderService.generateRequestId(voucherId, count);
    }

    @GetMapping("/my")
    public Result myOrders(@RequestParam(value = "current", defaultValue = "1") Integer current,
                           @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return voucherOrderService.queryMyOrders(current, size);
    }

    @GetMapping("/my/detail")
    public Result myOrdersDetail(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                 @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return voucherOrderService.queryMyOrdersDetail(current, size);
    }

    @GetMapping("/status")
    public Result queryStatus(@RequestParam("reqId") String requestId) {
        return voucherOrderService.queryStatus(requestId);
    }
}
