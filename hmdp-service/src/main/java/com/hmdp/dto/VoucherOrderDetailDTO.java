package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VoucherOrderDetailDTO {
    private Long id;
    private String requestId;
    private Long voucherId;
    private String voucherTitle;
    private Integer voucherType;
    private Long shopId;
    private String shopName;
    private Integer count;
    private Integer limitType;
    private Integer userLimit;
    private Integer payType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
}
