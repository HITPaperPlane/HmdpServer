package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.dto.UserUpdateDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.MailUtils;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserInfoService userInfoService;
    //发短信
    @Override
    public Result sendCode(String email, HttpSession session) throws MessagingException {
        // 1. 判断是否在一级限制条件内
        Boolean oneLevelLimit = stringRedisTemplate.opsForSet().isMember(ONE_LEVERLIMIT_KEY + email, "1");
        if (oneLevelLimit != null && oneLevelLimit) {
            // 在一级限制条件内，不能发送验证码
            return Result.fail("您需要等5分钟后再请求");
        }

// 2. 判断是否在二级限制条件内
        Boolean twoLevelLimit = stringRedisTemplate.opsForSet().isMember(TWO_LEVERLIMIT_KEY + email, "1");
        if (twoLevelLimit != null && twoLevelLimit) {
            // 在二级限制条件内，不能发送验证码
            return Result.fail("您需要等20分钟后再请求");
        }

// 3. 检查过去1分钟内发送验证码的次数
        long oneMinuteAgo = System.currentTimeMillis() - 60 * 1000;
        long count_oneminute = stringRedisTemplate.opsForZSet().count(SENDCODE_SENDTIME_KEY + email, oneMinuteAgo, System.currentTimeMillis());
        if (count_oneminute >= 1) {
            // 过去1分钟内已经发送了1次，不能再发送验证码
            return Result.fail("距离上次发送时间不足1分钟，请1分钟后重试");
        }

        // 4. 检查发送验证码的次数
        long fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000;
        long count_fiveminute = stringRedisTemplate.opsForZSet().count(SENDCODE_SENDTIME_KEY + email, fiveMinutesAgo, System.currentTimeMillis());
        if (count_fiveminute % 3 == 2 && count_fiveminute > 5) {
            // 发送了8, 11, 14, ...次，进入二级限制
            stringRedisTemplate.opsForSet().add(TWO_LEVERLIMIT_KEY + email, "1");
            stringRedisTemplate.expire(TWO_LEVERLIMIT_KEY + email, 20, TimeUnit.MINUTES);
            return Result.fail("接下来如需再发送，请等20分钟后再请求");
        } else if (count_fiveminute == 5) {
            // 过去5分钟内已经发送了5次，进入一级限制
            stringRedisTemplate.opsForSet().add(ONE_LEVERLIMIT_KEY + email, "1");
            stringRedisTemplate.expire(ONE_LEVERLIMIT_KEY + email, 5, TimeUnit.MINUTES);
            return Result.fail("5分钟内已经发送了5次，接下来如需再发送请等待5分钟后重试");
        }

          //生成验证码
        String code = MailUtils.achieveCode();

        log.info("发送登录验证码：{}", code);
        try {
            MailUtils.sendtoMail(email, code);
        } catch (MessagingException e) {
            log.error("发送登录验证码邮件失败，email={}", email, e);
            stringRedisTemplate.delete(LOGIN_CODE_KEY + email);
            return Result.fail("验证码发送失败，请稍后重试");
        }

        //将生成的验证码保持到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + email, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 更新发送时间和次数
        String sendTimeKey = SENDCODE_SENDTIME_KEY + email;
        stringRedisTemplate.opsForZSet().add(sendTimeKey, System.currentTimeMillis() + "", System.currentTimeMillis());
        // 设置6分钟过期（由常量定义），覆盖5分钟的统计窗口，并自动清理过期数据
        stringRedisTemplate.expire(sendTimeKey, SENDCODE_SENDTIME_TTL, TimeUnit.MINUTES);

        return Result.ok();
}

    //登录注册
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String email = loginForm.getEmail();
        String code = loginForm.getCode();
        //检验手机号是否正确，不同的请求就应该再次去进行确认
        if(RegexUtils.isEmailInvalid(email))
        {
            //如果无效，则直接返回
            return Result.fail("邮箱格式不正确！！");
        }
        //从redis中读取验证码，并进行校验
        String Cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + email);
        //1. 校验邮箱
        if (RegexUtils.isEmailInvalid(email)) {
            return Result.fail("邮箱格式不正确！！");
        }
        //2. 不符合格式则报错
        if (Cachecode==null || !code.equals(Cachecode))
        {
            return Result.fail("无效的验证码");
        }
        //如果上述都没有问题的话，就从数据库中查询该用户的信息

        //select * from tb_user where email = ?
        User user = query().eq("email", email).one();

        //判断用户是否存在
        if (user==null)
        {
            user = createuser(email);
        }
        //保存用户信息到Redis中
        String token = UUID.randomUUID().toString();

        //7.2 将UserDto对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        if (StrUtil.isBlank(userDTO.getRole())) {
            userDTO.setRole("USER");
            user.setRole("USER");
        }
        HashMap<String, String > userMap = new HashMap<>();
        userMap.put("id", String.valueOf(userDTO.getId()));
        userMap.put("nickName", StrUtil.blankToDefault(userDTO.getNickName(), ""));
        String icon = "";
        UserInfo info = userInfoService.getById(userDTO.getId());
        if (info != null && StrUtil.isNotBlank(info.getIcon())) {
            icon = info.getIcon();
        }
        userMap.put("icon", icon);
        userMap.put("role", StrUtil.blankToDefault(userDTO.getRole(), "USER"));


        //7.3 存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        //7.4 设置token有效期为30分钟
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //7.5 登陆成功则删除验证码信息
        stringRedisTemplate.delete(LOGIN_CODE_KEY + email);

        //8. 返回token
        return Result.ok(token);
    }

    private User createuser(String email) {
        //创建用户
        User user = new User();
        user.setEmail(email);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +RandomUtil.randomString(10));
        user.setRole("USER");
        //保存用户 insert into tb_user(email,nick_name) values(?,?)
        save(user);
        return user;
    }

    @Override
    public Result sign() {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 获取日期
        LocalDateTime now = LocalDateTime.now();
        //3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4. 获取今天是当月第几天(1~31)
        int dayOfMonth = now.getDayOfMonth();
        //5. 写入Redis  SETBIT key offset 1（返回旧值，用于幂等）
        Boolean old = stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        if (Boolean.TRUE.equals(old)) {
            return Result.fail("今日已签到");
        }

        // 6. 累计积分，并推导等级（规则：每次签到 +1 credits；level = min(9, credits/10)）
        try {
            UserInfo info = userInfoService.getById(userId);
            if (info == null) {
                info = new UserInfo().setUserId(userId).setGender(0).setLevel(0).setCredits(0).setFans(0).setFollowee(0);
            }
            int credits = Optional.ofNullable(info.getCredits()).orElse(0) + 1;
            int level = Math.min(9, credits / 10);
            info.setCredits(credits);
            info.setLevel(level);
            userInfoService.saveOrUpdate(info);
            return Result.ok();
        } catch (Exception e) {
            // best-effort rollback redis sign flag to allow retry
            try {
                stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, false);
            } catch (Exception ignore) {
            }
            log.error("签到积分更新失败，userId={}", userId, e);
            return Result.fail("签到失败，请稍后重试");
        }
    }

    @Override
    public Result signCount() {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 获取日期
        LocalDateTime now = LocalDateTime.now();
        //3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4. 获取今天是当月第几天(1~31)
        int dayOfMonth = now.getDayOfMonth();


        //5. 获取截止至今日的签到记录  BITFIELD key GET uDay 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        //6. 循环遍历
        int count = 0;
        Long num = result.get(0);
        while (true) {
            if ((num & 1) == 0) {
                break;
            } else
                count++;
            //数字右移，抛弃最后一位
            num = num>>>1;
        }
        return Result.ok(count);
    }

    @Override
    public Result recordUv(javax.servlet.http.HttpServletRequest request) {
        // use userId when logged in, otherwise fallback to remote address to approximate UV
        String identifier = Optional.ofNullable(UserHolder.getUser()).map(UserDTO::getId).map(String::valueOf)
                .orElseGet(() -> request.getRemoteAddr());
        String key = UV_KEY + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        stringRedisTemplate.opsForHyperLogLog().add(key, identifier);
        Long size = stringRedisTemplate.opsForHyperLogLog().size(key);
        return Result.ok(size);
    }

    @Override
    public Result queryUv(Integer days) {
        int window = (days == null || days < 1) ? 1 : Math.min(days, 30);
        String[] keys = new String[window];
        LocalDate today = LocalDate.now();
        for (int i = 0; i < window; i++) {
            keys[i] = UV_KEY + today.minusDays(i).format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        Long size = stringRedisTemplate.opsForHyperLogLog().size(keys);
        return Result.ok(size);
    }

    @Override
    public Result updateInfo(UserUpdateDTO dto, String token) {
        Long userId = UserHolder.getUser().getId();

        boolean nickNameUpdated = false;
        if (dto != null && StrUtil.isNotBlank(dto.getNickName())) {
            nickNameUpdated = update().set("nick_name", dto.getNickName()).eq("id", userId).update();
        }

        boolean infoUpdated = false;
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            info = new UserInfo().setUserId(userId).setGender(0).setLevel(0).setCredits(0).setFans(0).setFollowee(0);
        }
        if (dto != null) {
            if (dto.getIcon() != null) {
                info.setIcon(dto.getIcon());
                infoUpdated = true;
            }
            if (dto.getGender() != null) {
                info.setGender(dto.getGender());
                infoUpdated = true;
            }
            if (StrUtil.isNotBlank(dto.getBirthday())) {
                info.setBirthday(LocalDate.parse(dto.getBirthday()));
                infoUpdated = true;
            }
            if (dto.getCity() != null) {
                info.setCity(dto.getCity());
                infoUpdated = true;
            }
            if (dto.getIntroduce() != null) {
                info.setIntroduce(dto.getIntroduce());
                infoUpdated = true;
            }
        }
        if (infoUpdated) {
            userInfoService.saveOrUpdate(info);
        }

        if (StrUtil.isNotBlank(token)) {
            String tokenKey = LOGIN_USER_KEY + token;
            if (dto != null && StrUtil.isNotBlank(dto.getNickName())) {
                stringRedisTemplate.opsForHash().put(tokenKey, "nickName", dto.getNickName());
            }
            if (dto != null && dto.getIcon() != null) {
                stringRedisTemplate.opsForHash().put(tokenKey, "icon", dto.getIcon());
            }
            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        }

        UserDTO current = UserHolder.getUser();
        if (current != null && dto != null) {
            if (StrUtil.isNotBlank(dto.getNickName())) current.setNickName(dto.getNickName());
            if (dto.getIcon() != null) current.setIcon(dto.getIcon());
        }

        return Result.ok(nickNameUpdated || infoUpdated);
    }

    @Override
    public Result signDetail() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();

        java.util.ArrayList<Integer> days = new java.util.ArrayList<>();
        for (int day = 1; day <= dayOfMonth; day++) {
            Boolean signed = stringRedisTemplate.opsForValue().getBit(key, day - 1);
            if (Boolean.TRUE.equals(signed)) {
                days.add(day);
            }
        }
        return Result.ok(days);
    }
}
