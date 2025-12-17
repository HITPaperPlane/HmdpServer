package com.hmdp.dto;

import lombok.Data;

@Data
public class FollowUserDTO {
    private Long userId;
    private String nickName;
    private String icon;
    private String role;
    private String introduce;
}

