package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_merchant")
public class Merchant {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long shopId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
