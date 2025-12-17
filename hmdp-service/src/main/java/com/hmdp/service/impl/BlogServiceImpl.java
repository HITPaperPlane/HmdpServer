package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Autowired
    private IBlogService blogService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IFollowService followService;

    @Resource(name = "applicationTaskExecutor")
    private Executor taskExecutor;

    @Override
    public Result queryById(Integer id) {
        final Long blogId = id == null ? null : id.longValue();
        if (blogId == null) {
            return Result.fail("参数错误");
        }

        final UserDTO currentUser = UserHolder.getUser();
        final Long currentUserId = currentUser == null ? null : currentUser.getId();

        CompletableFuture<Blog> blogFuture = CompletableFuture.supplyAsync(() -> getById(blogId), taskExecutor);
        CompletableFuture<User> userFuture = blogFuture.thenApplyAsync(blog -> {
            if (blog == null || blog.getUserId() == null) {
                return null;
            }
            return userService.getById(blog.getUserId());
        }, taskExecutor);
        CompletableFuture<UserInfo> userInfoFuture = blogFuture.thenApplyAsync(blog -> {
            if (blog == null || blog.getUserId() == null) {
                return null;
            }
            return userInfoService.getById(blog.getUserId());
        }, taskExecutor);
        CompletableFuture<Boolean> likeFuture = CompletableFuture.supplyAsync(() -> {
            if (currentUserId == null) {
                return false;
            }
            String key = BLOG_LIKED_KEY + blogId;
            Double score = stringRedisTemplate.opsForZSet().score(key, currentUserId.toString());
            return score != null;
        }, taskExecutor).exceptionally(e -> {
            log.warn("Redis点赞状态查询失败，降级为未点赞，blogId=" + blogId + ", err=" + e.getMessage());
            return false;
        });

        Blog blog;
        try {
            blog = blogFuture.join();
        } catch (CompletionException e) {
            log.error("查询博客失败，blogId=" + blogId, e);
            return Result.fail("查询失败，请稍后重试");
        }

        if (blog == null) {
            return Result.fail("博客不存在或已被删除");
        }

        try {
            User author = userFuture.join();
            if (author != null) {
                blog.setName(author.getNickName());
            }
            UserInfo authorInfo = userInfoFuture.join();
            if (authorInfo != null) {
                blog.setIcon(authorInfo.getIcon());
            }
            blog.setIsLike(Boolean.TRUE.equals(likeFuture.join()));
        } catch (CompletionException e) {
            log.error("异步加载博客详情失败，blogId=" + blogId, e);
            // 兜底：同步查询（尽量给出可用结果）
            try {
                queryBlogUser(blog);
            } catch (Exception ignore) {
            }
            blog.setIsLike(false);
        }

        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 批量查询发布者信息（避免 N+1）
        Set<Long> userIds = records.stream()
                .map(Blog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, String> iconMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userInfoService.listByIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserInfo::getUserId, ui -> Objects.toString(ui.getIcon(), ""), (a, b) -> a));

        // 填充发布者信息 + 点赞状态
        for (Blog blog : records) {
            User u = userMap.get(blog.getUserId());
            if (u != null) {
                blog.setName(u.getNickName());
            }
            blog.setIcon(iconMap.get(blog.getUserId()));
            //追加判断blog是否被当前用户点赞，逻辑封装到isBlogLiked方法中
            isBlogLiked(blog);
        }
        return Result.ok(records);
    }

    private void isBlogLiked(Blog blog) {
        //1. 获取当前用户信息
        UserDTO userDTO = UserHolder.getUser();
        //当用户未登录时，就不判断了，直接return结束逻辑
        if (userDTO == null) {
            return;
        }

        //2. 判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userDTO.getId().toString());
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        if (user == null) {
            return;
        }
        blog.setName(user.getNickName());
        UserInfo info = userInfoService.getById(userId);
        if (info != null) {
            blog.setIcon(info.getIcon());
        }
    }

    @Override
    public Result likeBlog(Long id) {
        //1. 获取当前用户信息
        Long userId = UserHolder.getUser().getId();
        //2. 如果当前用户未点赞，则点赞数 +1，同时将用户加入set集合
        String key = BLOG_LIKED_KEY + id;
        //尝试获取score
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //为null，则表示集合中没有该用户
        if (score == null) {
            //点赞数 +1
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            //将用户加入set集合
            if (success) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
            //3. 如果当前用户已点赞，则取消点赞，将用户从set集合中移除
        }else {
            //点赞数 -1
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if (success){
                //从set集合移除
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Integer id) {
        String key = BLOG_LIKED_KEY + id;
        //zrange key 0 4  查询zset中前5个元素
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //如果是空的(可能没人点赞)，直接返回一个空集合
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //将ids使用`,`拼接，SQL语句查询出来的结果并不是按照我们期望的方式进行排
        //所以我们需要用order by field来指定排序方式，期望的排序方式就是按照查询出来的id进行排序
        String idsStr = StrUtil.join(",", ids);
        //select * from tb_user where id in (ids[0], ids[1] ...) order by field(id, ids[0], ids[1] ...)
        List<User> users = userService.query().in("id", ids)
                .last("order by field(id," + idsStr + ")")
                .list();
        List<UserDTO> userDTOS = users.stream()
                .map(u -> BeanUtil.copyProperties(u, UserDTO.class))
                .collect(Collectors.toList());
        Map<Long, String> iconMap = userInfoService.listByIds(ids).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserInfo::getUserId, ui -> Objects.toString(ui.getIcon(), ""), (a, b) -> a));
        for (UserDTO dto : userDTOS) {
            dto.setIcon(iconMap.get(dto.getId()));
        }
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = blogService.save(blog);
        if (!isSuccess)
        {
            return Result.fail("新增笔记失败！");
        }
        // 将发布事件写入 Outbox，异步推送粉丝收件箱
        com.hmdp.dto.FeedMessage message = new com.hmdp.dto.FeedMessage()
                .setAuthorId(user.getId())
                .setBlogId(blog.getId())
                .setTimestamp(System.currentTimeMillis());
        stringRedisTemplate.opsForList().leftPush(com.hmdp.utils.RedisConstants.FEED_OUTBOX_KEY,
                cn.hutool.json.JSONUtil.toJsonStr(message));

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        return queryBlogOfFollow(max, offset, false, false);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset, Boolean refresh, Boolean force) {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 查询该用户收件箱（之前我们存的key是固定前缀 + 粉丝id），所以根据当前用户id就可以查询是否有关注的人发了笔记
        String key = FEED_KEY + userId;
        int safeOffset = offset == null || offset < 0 ? 0 : offset;
        long safeMax = max == null || max <= 0 ? System.currentTimeMillis() : max;

        if (Boolean.TRUE.equals(force) && safeOffset == 0) {
            rebuildInboxFromDb(userId, key, SystemConstants.MAX_PAGE_SIZE * 20);
        } else if (Boolean.TRUE.equals(refresh) && safeOffset == 0) {
            smartRefreshLatest(userId, key);
        }

        Set<ZSetOperations.TypedTuple<String>> typeTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, safeMax, safeOffset, SystemConstants.MAX_PAGE_SIZE);
        //3. 非空判断
        if (typeTuples == null || typeTuples.isEmpty()){
            if (safeOffset > 0) {
                return Result.ok(Collections.emptyList());
            }
            // Redis 缺数据时回源 DB 重建一页
            List<Long> followIds = followService.query()
                    .eq("user_id", userId)
                    .list()
                    .stream()
                    .map(Follow::getFollowUserId)
                    .collect(Collectors.toList());
            if (followIds.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            List<Blog> fallbackBlogs = query().in("user_id", followIds)
                    .orderByDesc("create_time")
                    .last("limit " + SystemConstants.MAX_PAGE_SIZE)
                    .list();
            if (fallbackBlogs.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            enrichBlogs(fallbackBlogs, userId);
            // 回填 inbox，防止重复回源
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Blog blog : fallbackBlogs) {
                    long ts = blog.getCreateTime() == null ? System.currentTimeMillis()
                            : blog.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    connection.zAdd(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            ts,
                            blog.getId().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                return null;
            });
            stringRedisTemplate.expire(key, 1, TimeUnit.DAYS);
            ScrollResult scrollResult = new ScrollResult();
            scrollResult.setList(fallbackBlogs);
            long minTs = fallbackBlogs.get(fallbackBlogs.size() - 1).getCreateTime()
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            scrollResult.setMinTime(minTs);
            scrollResult.setOffset(1);
            return Result.ok(scrollResult);
        }
        //4. 解析数据，blogId、minTime（时间戳）、offset，这里指定创建的list大小，可以略微提高效率，因为我们知道这个list就得是这么大
        ArrayList<Long> ids = new ArrayList<>(typeTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typeTuple : typeTuples) {
            //4.1 获取id
            String id = typeTuple.getValue();
            ids.add(Long.valueOf(id));
            //4.2 获取score（时间戳）
            long time = typeTuple.getScore().longValue();
            if (time == minTime){
                os++;
            }else {
                minTime = time;
                os = 1;
            }
        }
        //解决SQL的in不能排序问题，手动指定排序为传入的ids
        String idsStr = StrUtil.join(",", ids);

        //5. 根据id查询blog
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idsStr + ")").list();
        if (blogs.isEmpty()) {
            ScrollResult empty = new ScrollResult();
            empty.setList(Collections.emptyList());
            empty.setOffset(os);
            empty.setMinTime(minTime);
            return Result.ok(empty);
        }

        enrichBlogs(blogs, userId);
        //6. 封装结果并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    private void smartRefreshLatest(Long userId, String feedKey) {
        Double maxScore = 0D;
        try {
            Set<ZSetOperations.TypedTuple<String>> top = stringRedisTemplate.opsForZSet().reverseRangeWithScores(feedKey, 0, 0);
            if (top != null && !top.isEmpty()) {
                ZSetOperations.TypedTuple<String> tuple = top.iterator().next();
                if (tuple != null && tuple.getScore() != null) {
                    maxScore = tuple.getScore();
                }
            }
        } catch (Exception ignore) {
            maxScore = 0D;
        }
        List<Long> followIds = followService.query()
                .eq("user_id", userId)
                .list()
                .stream()
                .map(Follow::getFollowUserId)
                .collect(Collectors.toList());
        if (followIds.isEmpty()) {
            return;
        }
        java.time.LocalDateTime since = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(maxScore.longValue()),
                java.time.ZoneId.systemDefault()
        );
        List<Blog> newer = query().in("user_id", followIds)
                .gt("create_time", since)
                .orderByDesc("create_time")
                .last("limit " + SystemConstants.MAX_PAGE_SIZE)
                .list();
        if (newer == null || newer.isEmpty()) {
            return;
        }
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] keyBytes = feedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (Blog blog : newer) {
                long ts = blog.getCreateTime() == null ? System.currentTimeMillis()
                        : blog.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                connection.zAdd(keyBytes,
                        ts,
                        blog.getId().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return null;
        });
        stringRedisTemplate.expire(feedKey, 1, TimeUnit.DAYS);
    }

    private void rebuildInboxFromDb(Long userId, String feedKey, int limit) {
        List<Long> followIds = followService.query()
                .eq("user_id", userId)
                .list()
                .stream()
                .map(Follow::getFollowUserId)
                .collect(Collectors.toList());
        if (followIds.isEmpty()) {
            stringRedisTemplate.delete(feedKey);
            return;
        }
        int safeLimit = Math.max(SystemConstants.MAX_PAGE_SIZE, Math.min(limit, 500));
        List<Blog> latest = query().in("user_id", followIds)
                .orderByDesc("create_time")
                .last("limit " + safeLimit)
                .list();
        stringRedisTemplate.delete(feedKey);
        if (latest == null || latest.isEmpty()) {
            return;
        }
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] keyBytes = feedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (Blog blog : latest) {
                long ts = blog.getCreateTime() == null ? System.currentTimeMillis()
                        : blog.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                connection.zAdd(keyBytes,
                        ts,
                        blog.getId().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return null;
        });
        stringRedisTemplate.expire(feedKey, 1, TimeUnit.DAYS);
    }

    private void enrichBlogs(List<Blog> blogs, Long currentUserId) {
        Set<Long> userIds = blogs.stream().map(Blog::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, UserInfo> userInfoMap = userInfoService.listByIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserInfo::getUserId, u -> u, (a, b) -> a));

        List<Object> likedFlags = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Blog blog : blogs) {
                String likedKey = BLOG_LIKED_KEY + blog.getId();
                connection.zScore(likedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        currentUserId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return null;
        });
        if (likedFlags == null) {
            likedFlags = Collections.emptyList();
        }

        for (int i = 0; i < blogs.size(); i++) {
            Blog blog = blogs.get(i);
            User u = userMap.get(blog.getUserId());
            if (u != null) {
                blog.setName(u.getNickName());
            }
            UserInfo info = userInfoMap.get(blog.getUserId());
            if (info != null) {
                blog.setIcon(info.getIcon());
            }
            Object flag = i < likedFlags.size() ? likedFlags.get(i) : null;
            blog.setIsLike(flag != null);
        }
    }

    @Override
    public Result queryBlogOfShop(Long shopId, Integer current) {
        if (shopId == null) {
            return Result.fail("店铺ID不能为空");
        }
        int pageNum = (current == null || current < 1) ? 1 : current;

        Page<Blog> page = query()
                .eq("shop_id", shopId)
                .orderByDesc("create_time")
                .page(new Page<>(pageNum, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        Set<Long> userIds = records.stream()
                .map(Blog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, String> iconMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userInfoService.listByIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserInfo::getUserId, ui -> Objects.toString(ui.getIcon(), ""), (a, b) -> a));

        for (Blog blog : records) {
            User u = userMap.get(blog.getUserId());
            if (u != null) {
                blog.setName(u.getNickName());
            }
            blog.setIcon(iconMap.get(blog.getUserId()));
            isBlogLiked(blog);
        }
        return Result.ok(records);
    }
}
