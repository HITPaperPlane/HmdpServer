package com.hmdp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_voucher_order")
public class VoucherOrder {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String requestId;
    private Long userId;
    private Long voucherId;
    private Integer count;
    private Integer limitType;
    private Integer userLimit;
    private Integer payType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime updateTime;
}
