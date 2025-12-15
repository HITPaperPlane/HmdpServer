package com.hmdp.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@TableName("tb_user_quota")
public class UserQuota {
    @TableId(value = "user_id")
    private Long userId;
    private Long voucherId;
    private Integer ownedCount;
}
