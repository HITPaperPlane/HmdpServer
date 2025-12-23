package com.hmdp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("message_outbox")
public class MessageOutbox {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String bizType;
    private String bizId;
    private String exchangeName;
    private String routingKey;
    private String payload;

    /**
     * 0=待发送, 1=已发送
     */
    private Integer status;

    private Integer retryCount;
    private LocalDateTime nextRetryTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
