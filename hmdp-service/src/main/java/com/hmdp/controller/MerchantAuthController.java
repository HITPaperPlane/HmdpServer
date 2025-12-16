package com.hmdp.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Merchant;
import com.hmdp.entity.Role;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.entity.UserRole;
import com.hmdp.mapper.MerchantMapper;
import com.hmdp.mapper.RoleMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.mapper.UserRoleMapper;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@RestController
@RequestMapping("/merchant")
public class MerchantAuthController {

    private static final String MERCHANT_ROLE_CODE = "MERCHANT";
    private static final String MERCHANT_ROLE_NAME = "商家";

    @Resource
    private IUserService userService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private UserRoleMapper userRoleMapper;
    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private IUserInfoService userInfoService;
    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    public static class MerchantProfileDTO {
        public String nickName;
        public String icon;
        public Integer gender;
        public String city;
        public String introduce;
    }

    @PostMapping("/code")
    public Result sendCode(@RequestBody LoginFormDTO form) throws MessagingException {
        return userService.sendCode(form.getEmail(), null);
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm) {
        String email = loginForm.getEmail();
        String code = loginForm.getCode();
        if (RegexUtils.isEmailInvalid(email)) {
            return Result.fail("邮箱格式不正确！！");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + email);
        if (cacheCode == null || !Objects.equals(cacheCode, code)) {
            return Result.fail("无效的验证码");
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            user.setRole(MERCHANT_ROLE_CODE);
            userMapper.insert(user);
        } else {
            user.setRole(MERCHANT_ROLE_CODE);
            userMapper.updateById(user);
        }
        ensureRole(user.getId());
        ensureMerchant(user.getId());

        String token = UUID.randomUUID().toString();
        UserDTO dto = BeanUtil.copyProperties(user, UserDTO.class);
        UserInfo info = userInfoService.getById(user.getId());
        HashMap<String, String> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(dto.getId()));
        userMap.put("nickName", dto.getNickName());
        userMap.put("icon", info != null ? Objects.toString(info.getIcon(), "") : "");
        userMap.put("role", MERCHANT_ROLE_CODE);
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        stringRedisTemplate.delete(LOGIN_CODE_KEY + email);
        return Result.ok(token);
    }

    private void ensureRole(Long userId) {
        Role role = roleMapper.selectOne(new QueryWrapper<Role>().eq("code", MERCHANT_ROLE_CODE));
        if (role == null) {
            role = new Role().setCode(MERCHANT_ROLE_CODE).setName(MERCHANT_ROLE_NAME);
            roleMapper.insert(role);
        }
        Long exists = userRoleMapper.selectCount(new QueryWrapper<UserRole>()
                .eq("user_id", userId).eq("role_id", role.getId()));
        if (exists == null || exists == 0) {
            userRoleMapper.insert(new UserRole().setUserId(userId).setRoleId(role.getId()));
        }
    }

    private void ensureMerchant(Long userId) {
        Long exists = merchantMapper.selectCount(new QueryWrapper<Merchant>().eq("user_id", userId));
        if (exists == null || exists == 0) {
            merchantMapper.insert(new Merchant().setUserId(userId).setStatus(1));
        }
    }

    @PostMapping("/profile/update")
    public Result updateProfile(@RequestBody MerchantProfileDTO dto) {
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("未登录");
        }
        if (dto.nickName != null) {
            userService.lambdaUpdate()
                    .eq(User::getId, userId)
                    .set(User::getNickName, dto.nickName)
                    .update();
        }
        userInfoService.lambdaUpdate()
                .eq(UserInfo::getUserId, userId)
                .set(dto.icon != null, UserInfo::getIcon, dto.icon)
                .set(dto.gender != null, UserInfo::getGender, dto.gender)
                .set(dto.city != null, UserInfo::getCity, dto.city)
                .set(dto.introduce != null, UserInfo::getIntroduce, dto.introduce)
                .update();
        return Result.ok();
    }
}
