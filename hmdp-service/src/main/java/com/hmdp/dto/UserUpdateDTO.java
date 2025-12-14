package com.hmdp.dto;

import lombok.Data;

/**
 * 用户资料更新入参
 */
@Data
public class UserUpdateDTO {
    private String icon;
    private String nickName;
    private Integer gender; // 0/1
    private String birthday; // yyyy-MM-dd
    private String city;
    private String introduce;
}

