package com.hmdp.controller;

import cn.hutool.core.lang.UUID;
import com.hmdp.dto.Result;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@RestController
@RequestMapping("/admin")
public class AdminAuthController {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "123456";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public static class AdminLoginForm {
        public String username;
        public String password;
    }

    @PostMapping("/login")
    public Result login(@RequestBody AdminLoginForm form) {
        if (!ADMIN_USER.equals(form.username) || !ADMIN_PASS.equals(form.password)) {
            return Result.fail("账号或密码错误");
        }
        String token = UUID.randomUUID().toString();
        HashMap<String, String> map = new HashMap<>();
        map.put("id", "0");
        map.put("nickName", "admin");
        map.put("icon", "");
        map.put("role", "ADMIN");
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, map);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }
}
