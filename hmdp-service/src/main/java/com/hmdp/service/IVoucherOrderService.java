package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId, String requestId, Integer count);

    Result queryMyOrders(Integer current, Integer size);

    Result queryMyOrdersDetail(Integer current, Integer size);

    Result queryStatus(String requestId);

    Result generateRequestId(Long voucherId, Integer count);
}
