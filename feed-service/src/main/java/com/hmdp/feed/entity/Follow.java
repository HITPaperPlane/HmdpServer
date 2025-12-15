package com.hmdp.feed.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@TableName("tb_follow")
public class Follow {
    @TableId
    private Long id;
    private Long userId;
    private Long followUserId;
    private java.time.LocalDateTime createTime;
}
