package com.hmdp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_seckill_voucher")
public class SeckillVoucher {
    @TableId(value = "voucher_id", type = IdType.INPUT)
    private Long voucherId;
    private Integer stock;
    private Integer limitType;
    private Integer userLimit;
    private LocalDateTime createTime;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private Integer preheatStatus;
    private LocalDateTime updateTime;
}
