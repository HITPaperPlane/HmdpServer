package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SeckillMessage {
    private Long orderId;
    private String requestId;
    private Long voucherId;
    private Long userId;
    private Integer count;
    private Integer limitType;
    private Integer userLimit;
    private Long timestamp;
}
