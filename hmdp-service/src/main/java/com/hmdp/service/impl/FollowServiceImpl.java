package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.FollowUserDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Autowired
    private IUserInfoService userInfoService;

    @Autowired
    private IBlogService blogService;

    @Override
    @Transactional
    public Result follow(Long followUserId, Boolean isFellow) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //判断是否关注
        if (isFellow) {
            Long exists = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
            if (exists != null && exists > 0) {
                // 幂等：已关注则只保证 Redis 中存在
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
                return Result.ok();
            }
            //关注，则将信息保存到数据库
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean successed = save(follow);
            if (successed) {
                ensureUserInfoExists(userId);
                ensureUserInfoExists(followUserId);
                userInfoService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserInfo>()
                        .setSql("followee = followee + 1")
                        .eq("user_id", userId));
                userInfoService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserInfo>()
                        .setSql("fans = fans + 1")
                        .eq("user_id", followUserId));
            }
            //则将数据也写入Redis
            if (successed) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
                preloadRecentBlogs(userId, followUserId);
            }
        } else {
            //取关：只删除一条，保持幂等 & 计数准确（避免历史重复数据导致一次删除多条）
            QueryWrapper<Follow> delWrapper = new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId)
                    .last("limit 1");
            int deleted = getBaseMapper().delete(delWrapper);
            boolean successed = deleted > 0;
            if (successed) {
                ensureUserInfoExists(userId);
                ensureUserInfoExists(followUserId);
                userInfoService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserInfo>()
                        .setSql("followee = IF(followee > 0, followee - 1, 0)")
                        .eq("user_id", userId));
                userInfoService.update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UserInfo>()
                        .setSql("fans = IF(fans > 0, fans - 1, 0)")
                        .eq("user_id", followUserId));
            }
            //则将数据也从Redis中移除
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //获取当前登录的userId
        Long userId = UserHolder.getUser().getId();
//        select count(*) from tb_follow where user_id = ?  and follow_user_id = ?
        Long count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId).count();
        //只想知道有没有，所以用count(*)即可
        return Result.ok(count != null && count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + id;
        String key2 = "follows:" + userId;
        //对当前用户和博主用户的关注列表取交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            //无交集就返回个空集合
            return Result.ok(Collections.emptyList());
        }
        //将结果转为list
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //之后根据ids去查询共同关注的用户，封装成UserDto再返回
        List<UserDTO> userDTOS = userService.listByIds(ids).stream().map(user ->
                BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        java.util.Map<Long, String> iconMap = userInfoService.listByIds(ids).stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(UserInfo::getUserId, ui -> java.util.Objects.toString(ui.getIcon(), ""), (a, b) -> a));
        for (UserDTO dto : userDTOS) {
            dto.setIcon(iconMap.get(dto.getId()));
        }
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryMyFollows() {
        UserDTO current = UserHolder.getUser();
        if (current == null || current.getId() == null) {
            return Result.fail("未登录");
        }
        Long userId = current.getId();
        List<Follow> follows = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .list();
        if (follows == null || follows.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> followIds = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        for (Follow f : follows) {
            if (f == null || f.getFollowUserId() == null) {
                continue;
            }
            if (seen.add(f.getFollowUserId())) {
                followIds.add(f.getFollowUserId());
            }
        }
        if (followIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        Map<Long, User> userMap = userService.listByIds(followIds).stream()
                .filter(u -> u != null && u.getId() != null)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a, HashMap::new));
        Map<Long, UserInfo> infoMap = userInfoService.listByIds(followIds).stream()
                .filter(ui -> ui != null && ui.getUserId() != null)
                .collect(Collectors.toMap(UserInfo::getUserId, ui -> ui, (a, b) -> a, HashMap::new));

        List<FollowUserDTO> result = new ArrayList<>(followIds.size());
        for (Long fid : followIds) {
            User u = userMap.get(fid);
            if (u == null) {
                continue;
            }
            UserInfo info = infoMap.get(fid);
            FollowUserDTO dto = new FollowUserDTO();
            dto.setUserId(u.getId());
            dto.setNickName(StrUtil.blankToDefault(u.getNickName(), ""));
            dto.setRole(StrUtil.blankToDefault(u.getRole(), "USER"));
            dto.setIcon(info != null ? StrUtil.blankToDefault(info.getIcon(), "") : "");
            dto.setIntroduce(info != null ? StrUtil.blankToDefault(info.getIntroduce(), "") : "");
            result.add(dto);
        }
        return Result.ok(result);
    }

    private void preloadRecentBlogs(Long followerId, Long followUserId) {
        List<Blog> latest = blogService.query()
                .eq("user_id", followUserId)
                .orderByDesc("create_time")
                .last("limit 10")
                .list();
        if (latest == null || latest.isEmpty()) {
            return;
        }
        String feedKey = RedisConstants.FEED_KEY + followerId;
        stringRedisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            for (Blog blog : latest) {
                long ts = blog.getCreateTime() == null ? System.currentTimeMillis()
                        : blog.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                connection.zAdd(feedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        ts,
                        blog.getId().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return null;
        });
        stringRedisTemplate.expire(feedKey, 1, TimeUnit.DAYS);
    }

    private void ensureUserInfoExists(Long userId) {
        if (userId == null) {
            return;
        }
        UserInfo exists = userInfoService.getById(userId);
        if (exists != null) {
            return;
        }
        UserInfo info = new UserInfo()
                .setUserId(userId)
                .setGender(0)
                .setLevel(0)
                .setCredits(0)
                .setFans(0)
                .setFollowee(0);
        try {
            userInfoService.save(info);
        } catch (Exception ignore) {
            // concurrent insert
        }
    }
}
