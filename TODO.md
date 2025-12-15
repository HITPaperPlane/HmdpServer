# 项目待办事项 (TODO List)

## 1. 核心业务重构：社交功能 (Social Feed)

### 🔴 架构修正：发布功能迁移 (Refactor)
- **当前问题**：发布探店笔记 (`saveBlog`) 的完整 UI 交互主要集成在 **商户端** (`MerchantContent.vue`)。虽然用户端 (`Blogs.vue`) 也有发布按钮，但逻辑割裂，且“我的笔记”列表仅在商户端可见。
- **重构目标**：探店笔记是 **C端用户 (User)** 的核心社交资产，而非商户的管理工具。
- **执行计划**：
  - [ ] **UI 迁移**：将 `MerchantContent.vue` 中的“发布笔记”组件完整迁移/复用到用户端（建议新建 `PublishBlog.vue` 或在 `Blogs.vue` 中优化弹窗）。
  - [ ] **入口调整**：在 `Profile.vue` (个人中心) 增加“我发布的笔记”入口，调用 `GET /blog/of/me` (目前该接口逻辑正确，但前端无入口)。
  - [ ] **商户端清理**：移除或隐藏商户端的普通笔记发布功能，除非将其重新定义为“官方公告/营销动态”。
  - [ ] **商户的个人信息**：商户的个人信息也要支持增删改查啊，另外现在别管是商户还是普通用户，我都看不到头像

### 🟠 性能优化：关注流查询 (Feed Stream)
- **当前问题 (严重)**：`BlogServiceImpl.queryBlogOfFollow` 方法中存在严重的 **N+1 查询问题**。
  - 代码逻辑：先查 Redis 获取 Feed ID 列表，然后 **for 循环** 遍历每个 Blog，在循环内逐次调用 `queryBlogUser` (查 DB) 和 `isBlogLiked` (查 Redis)。
  - 当前问题：严重的 N+1 问题在 queryBlogOfFollow 方法中，您目前的实现逻辑是：先从 Redis 拿到一页 BlogID 列表（比如 10 个），然后 For 循环 遍历这 10 个 ID。
    ``` java
    // BlogServiceImpl.java L225
    for (Blog blog : blogs) {
        // 5.1 查询发布该blog的用户信息 (Inside: Select * from tb_user where id = ?)
        queryBlogUser(blog); 
        // 5.2 查询当前用户是否给该blog点过赞 (Inside: Redis ZSCORE)
        isBlogLiked(blog);
    }
    ```
    后果推演： 假设一页展示 10 条笔记：DB 查询次数：1 次查 Blogs 列表 + 10 次查 User (作者信息) + 10 次查 UserInfo (头像等) = 21 次 DB 交互。Redis 查询次数：1 次查 Feed ZSet + 10 次查 IsLike ZScore = 11 次 Redis 交互。

  - 网络 IO：共计 32 次网络请求。这会造成巨大的网络延迟（RTT），导致 Feed 流加载缓慢，数据库连接池瞬间被占满。
  - 对比：`queryHotBlog` (热榜查询) 中已经做了优秀的批量优化 (Map 缓存)。
- **执行计划**：
  - [ ] **批量查询改造**：参考 `queryHotBlog` 的实现，收集所有 `userId` 和 `blogId`，使用 `userService.listByIds()` 和 Pipeline 批量查询用户信息与点赞状态，然后在内存中组装。

    - 收集 ID：遍历 blogs 列表，把所有作者的 userId 放入一个 Set<Long>。

    - 批量查 DB：调用 userService.listByIds(userIds) -> SELECT * FROM tb_user WHERE id IN (...)。调用 userInfoService.listByIds(userIds)。一次 DB 交互 解决所有用户信息。

    - Map 转换：将查询结果转为 Map<Long, User>，方便后续 O(1) 获取。

    - Redis Pipeline (管道)：对于 10 次 isBlogLiked 查询，使用 Redis Pipeline 一次性打包发送，一次 Redis 交互 拿到所有结果。

  - 优化后代码示例 (伪代码)：
    ```java
    // 1. 收集作者ID
    Set<Long> userIds = blogs.stream().map(Blog::getUserId).collect(Collectors.toSet());

    // 2. 批量查询用户信息 (DB) 并转为 Map
    Map<Long, User> userMap = userService.listByIds(userIds).stream()
        .collect(Collectors.toMap(User::getId, u -> u));
    Map<Long, UserInfo> userInfoMap = userInfoService.listByIds(userIds).stream()
        .collect(Collectors.toMap(UserInfo::getUserId, u -> u));

    // 3. 批量查询点赞状态 (Redis Pipeline)
    List<Object> isLikedResults = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
        for (Blog blog : blogs) {
            String key = BLOG_LIKED_KEY + blog.getId();
            // 这里的 connection.zScore 是异步的，不会立即返回
            connection.zScore(key.getBytes(), currentUserId.toString().getBytes());
        }
        return null;
    });

    // 4. 内存组装 (纯内存操作，速度极快)
    for (int i = 0; i < blogs.size(); i++) {
        Blog blog = blogs.get(i);
        // 填用户信息
        if (userMap.containsKey(blog.getUserId())) {
            blog.setName(userMap.get(blog.getUserId()).getNickName());
            blog.setIcon(userInfoMap.get(blog.getUserId()).getIcon());
        }
        // 填点赞状态
        Double score = (Double) isLikedResults.get(i);
        blog.setIsLike(score != null);
    }
    ```
效果：DB 查询降为 2-3 次，Redis 交互降为 2 次。性能提升一个数量级。

### 🟡 代码修正：分页硬编码 (Hardcoding)
- **当前问题**：在 `BlogServiceImpl.queryBlogOfFollow` 中，Redis 滚动分页查询的 count 被硬编码为 **2**。
  - 代码：`reverseRangeByScoreWithScores(key, 0, max, offset, 2)`
  - 影响：导致前端每次滚动只能加载 2 条数据，用户体验极差，且增加了网络交互次数。
- **执行计划**：
  - [ ] **参数标准化**：将硬编码的 `2` 修改为 `SystemConstants.MAX_PAGE_SIZE` (通常为 10)，保持与热榜分页一致。

## 2. 架构升级：高并发支持 (High Concurrency)

### 🔵 异步化：Feed 推送 (Push Mode)
- **当前问题**：`saveBlog` 方法中，发布笔记后 **同步 (Synchronous)** 遍历所有粉丝进行 `ZADD` 推送。
  - 风险：若某大 V 拥有 10 万粉丝，发布笔记将导致接口超时甚至阻塞线程。
- **执行计划**：
  - [ ] **引入消息队列**：利用 RabbitMQ (或 Redis List) 将“推送到收件箱”的动作异步化。发布接口仅保存 DB 即返回成功。
  - [ ] 还需要再引入一个服务，专门消费消息队列的消息，然后推给用户的收件箱
  - 具体实现比较复杂，见 feed流.md

### 🔵 缓存一致性
#### **当前问题：无效的兜底逻辑**

您使用了 `CompletableFuture` 来并行查询，这很好。但是看您的异常处理代码：

**现有代码分析 (`BlogServiceImpl.java`):**

```java
// BlogServiceImpl.java L78
try {
    // 获取异步结果
    blog.setIsLike(likeFuture.join()); 
} catch (CompletionException e) {
    // ...日志...
    // 兜底：同步查询
    try {
        isBlogLiked(blog); // <--- 问题在这里
    } catch (Exception ignore) {}
}
```

**逻辑漏洞：**

  * `likeFuture` 内部执行的就是查询 Redis (`stringRedisTemplate...score`)。
  * 如果 `likeFuture` 抛出异常，通常是因为 **Redis 挂了** 或者 **网络超时**。
  * 在 `catch` 块中，您又调用了 `isBlogLiked(blog)`。
  * **而 `isBlogLiked` 方法内部依然是查 Redis！**
  * **结论**：如果 Redis 挂了，`likeFuture` 会死，`catch` 里的兜底也会死。这不仅没起到兜底作用，反而让用户请求多等待了一次超时的过程，甚至可能导致页面报错。

#### **优化方案：Fail-Fast (快速失败) 与 核心数据优先**

在微服务架构中，有一个原则：**非核心数据缺失，不应影响核心业务展示。**

  * **核心数据**：Blog 的标题、内容、作者。如果没有这些，详情页没法看。
  * **非核心数据**：当前用户是否点赞。如果获取失败，大不了显示“未点赞”，用户依然可以阅读文章。

**重构思路：**

1.  **区分轻重**：MySQL 的 Blog 数据必须查到。Redis 的点赞状态查不到就算了。
2.  **静默失败 (Silent Fail)**：当 `likeFuture` 异常时，记录日志，然后默认 `isLike = false`，**不要**再去重试查 Redis。

**优化后代码示例：**

```java
// 定义 Future
CompletableFuture<Boolean> likeFuture = CompletableFuture.supplyAsync(() -> {
    // ... 查 Redis ...
}, taskExecutor).exceptionally(e -> {
    // 【关键优化】如果 Redis 报错，直接返回 false，不要抛异常给主线程
    log.warn("Redis点赞状态查询失败，降级为未点赞: {}", e.getMessage());
    return false; 
});

// 获取结果
try {
    // 因为上面用了 exceptionally 吃掉了异常，这里 join 不会再爆错
    blog.setIsLike(likeFuture.join());
} catch (Exception e) {
    // 即使还有万一，也默认 false
    blog.setIsLike(false);
}

// 至于 User 和 UserInfo，如果 DB 挂了，那整个服务也不可用了，抛错是合理的。
// 但如果是 Redis 挂了，用户应该依然能看到博客内容，只是看不到点赞状态。
```

## 3. 前端体验优化
- [ ] **强制关联店铺逻辑**：`Blogs.vue` 中的发布逻辑强制要求 `shopId` (`submitDisabled` 检查了 `!compose.shopId`)。需确认产品逻辑：是否允许用户发布不关联店铺的纯生活动态？如果不允许，需在 UI 上给出更明确的引导。

## 4. 实现全新升级的秒杀架构
- 新方案太长了，见文件秒杀.md

## 5. bug: 现在的代码把canal异常“吃掉”了
现在的代码把异常“吃掉”了（Swallowed），没有正确地抛出给上层去触发重试机制。
核心问题：handleEntries 方法中的 try-catch 范围过大

### 新方案总结：Canal + 重试 + 死信兜底

为了保证数据最终一致性，同时防止程序因单条异常数据陷入死循环，采用以下策略：

1.  **正常流程**：拉取 Canal 消息 -\> 解析 -\> 操作 Redis -\> **ACK**。
2.  **本地重试**：如果操作 Redis 抛出异常（如网络抖动），不要立即回滚，先在本地重试 N 次（防止频繁网络交互）。
3.  **死信兜底**：如果重试 N 次后依然失败（如 Redis 持续宕机或代码逻辑 bug），将该消息（表名、ID、操作类型）发送到 **RabbitMQ 的异常队列**。
4.  **强制 ACK**：只要消息成功发送到了 RabbitMQ，就对 Canal Server 执行 **ACK**。这样 Canal 游标可以继续后移，不会阻塞后续正常的缓存更新。
5.  **后续处理**：由人工或另一个服务专门消费 RabbitMQ 的异常队列，进行补偿处理。

-----

### 第一步：修改 RabbitMQ 配置 (定义异常队列)

在 `RabbitMQTopicConfig.java` 中增加一个专门用于存放 Canal 处理失败消息的队列。

**文件：** `relay-service/src/main/java/com/hmdp/relay/config/RabbitMQTopicConfig.java`

```java
package com.hmdp.relay.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQTopicConfig {
    // 原有的秒杀队列配置
    public static final String QUEUE = "seckillQueue";
    public static final String EXCHANGE = "seckillExchange";
    public static final String ROUTINGKEY = "seckill.message";

    // === 新增：Canal 异常处理队列配置 ===
    public static final String CANAL_ERROR_QUEUE = "canal.error.queue";
    public static final String CANAL_ERROR_ROUTING_KEY = "canal.error";

    @Bean
    public Queue queue(){
        return new Queue(QUEUE, true);
    }
    @Bean
    public TopicExchange topicExchange(){
        return new TopicExchange(EXCHANGE, true, false);
    }
    @Bean
    public Binding binding(){
        return BindingBuilder.bind(queue()).to(topicExchange()).with(ROUTINGKEY);
    }

    // === 新增 Bean ===
    @Bean
    public Queue canalErrorQueue() {
        return new Queue(CANAL_ERROR_QUEUE, true);
    }

    @Bean
    public Binding canalErrorBinding() {
        return BindingBuilder.bind(canalErrorQueue()).to(topicExchange()).with(CANAL_ERROR_ROUTING_KEY);
    }
}
```

-----

### 第二步：重写 CanalSubscriber (引入重试与死信逻辑)

这是核心修改。我们将逻辑分为“拉取循环”和“处理逻辑”，并引入 `RabbitTemplate`。

**文件：** `relay-service/src/main/java/com/hmdp/relay/canal/CanalSubscriber.java`

```java
package com.hmdp.relay.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.hmdp.relay.config.CanalProperties;
import com.hmdp.relay.config.RabbitMQTopicConfig;
import com.hmdp.relay.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class CanalSubscriber {

    private final CanalProperties canalProperties;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate; // 注入 RabbitTemplate
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    private static final int MAX_RETRY_TIMES = 3; // 最大本地重试次数

    @PostConstruct
    public void start() {
        pool.submit(this::consumeLoop);
    }

    private void consumeLoop() {
        CanalConnector connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(canalProperties.getHost(), canalProperties.getPort()),
                canalProperties.getDestination(),
                canalProperties.getUsername(),
                canalProperties.getPassword());
        
        while (true) {
            try {
                connector.connect();
                connector.subscribe(canalProperties.getSubscribe());
                connector.rollback(); // 连接后先回滚，防止上次未处理完的数据丢失

                while (true) {
                    // 1. 获取消息
                    Message message = connector.getWithoutAck(canalProperties.getBatchSize());
                    long batchId = message.getId();
                    
                    if (batchId == -1 || message.getEntries().isEmpty()) {
                        TimeUnit.SECONDS.sleep(1);
                        continue;
                    }

                    // 2. 处理消息 (包含重试和死信逻辑)
                    boolean success = processBatch(message.getEntries());

                    if (success) {
                        // 3. 只有成功处理（或成功转入死信队列）才 ACK
                        connector.ack(batchId);
                    } else {
                        // 4. 如果连死信队列都进不去，说明基础设施严重故障，回滚并休眠，等待下一次循环
                        log.error("Serious failure: Unable to process or offload to MQ. Rolling back.");
                        connector.rollback(); 
                        TimeUnit.SECONDS.sleep(5); 
                    }
                }
            } catch (Exception e) {
                log.error("Canal connection error, retrying...", e);
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException ignored) {}
            } finally {
                try {
                    connector.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 处理一批 Entry，返回 true 表示这批数据可以 ACK（无论是成功处理还是已转入死信）
     */
    private boolean processBatch(List<CanalEntry.Entry> entries) {
        for (CanalEntry.Entry entry : entries) {
            // 忽略事务开启/结束类型的 Entry
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                continue;
            }

            // 尝试处理单条 Entry
            boolean entryProcessed = handleEntryWithRetry(entry);
            
            // 如果某条关键消息处理彻底失败（也没进MQ），则整批回滚
            if (!entryProcessed) {
                return false; 
            }
        }
        return true;
    }

    /**
     * 带重试机制的单条处理逻辑
     */
    private boolean handleEntryWithRetry(CanalEntry.Entry entry) {
        int retryCount = 0;
        while (retryCount < MAX_RETRY_TIMES) {
            try {
                // 尝试解析并操作 Redis
                parseAndInvalidateCache(entry);
                return true; // 成功
            } catch (Exception e) {
                retryCount++;
                log.warn("Process entry failed, retry {}/{}. Error: {}", retryCount, MAX_RETRY_TIMES, e.getMessage());
                try {
                    TimeUnit.MILLISECONDS.sleep(500); // 稍微停顿
                } catch (InterruptedException ignored) {}
            }
        }

        // 重试耗尽，发送到死信队列
        return sendToErrorQueue(entry);
    }

    private void parseAndInvalidateCache(CanalEntry.Entry entry) throws Exception {
        CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
        String tableName = entry.getHeader().getTableName();
        CanalEntry.EventType eventType = rowChange.getEventType();

        // 仅关注增删改
        if (eventType == CanalEntry.EventType.INSERT || eventType == CanalEntry.EventType.UPDATE || eventType == CanalEntry.EventType.DELETE) {
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                // 获取 ID (删除操作取 before，其他取 after)
                String id = extractId(rowData);
                if (id != null) {
                    if ("tb_shop".equalsIgnoreCase(tableName)) {
                        redisTemplate.delete(RedisKeys.CACHE_SHOP_KEY + id);
                    } else if ("tb_blog".equalsIgnoreCase(tableName)) {
                        redisTemplate.delete(RedisKeys.CACHE_BLOG_KEY + id);
                    }
                }
            }
        }
    }

    /**
     * 发送异常消息到 RabbitMQ
     */
    private boolean sendToErrorQueue(CanalEntry.Entry entry) {
        try {
            String tableName = entry.getHeader().getTableName();
            // 这里简单封装一个 Map 或者自定义对象发送，包含足够人工排查的信息
            Map<String, Object> errorMsg = new HashMap<>();
            errorMsg.put("tableName", tableName);
            errorMsg.put("entryType", entry.getEntryType().name());
            errorMsg.put("logfileName", entry.getHeader().getLogfileName());
            errorMsg.put("logfileOffset", entry.getHeader().getLogfileOffset());
            errorMsg.put("time", System.currentTimeMillis());
            
            // 发送到我们在配置里定义的交换机和 RoutingKey
            rabbitTemplate.convertAndSend(RabbitMQTopicConfig.EXCHANGE, RabbitMQTopicConfig.CANAL_ERROR_ROUTING_KEY, errorMsg);
            
            log.error("Message processing failed after retries. Sent to DLQ: {}", errorMsg);
            return true; // 发送 MQ 成功，视为本条消息已处理（虽然是降级处理）
        } catch (Exception e) {
            log.error("FATAL: Failed to send message to DLQ!", e);
            return false; // 发送 MQ 也失败，必须回滚 Canal
        }
    }

    private String extractId(CanalEntry.RowData rowData) {
        // 优先从 afterColumns 找主键
        List<CanalEntry.Column> columns = rowData.getAfterColumnsList();
        if (columns == null || columns.isEmpty()) {
            // 如果是 DELETE 操作，after 为空，取 before
            columns = rowData.getBeforeColumnsList();
        }
        
        for (CanalEntry.Column c : columns) {
            if (c.getIsKey()) {
                return c.getValue();
            }
        }
        return null;
    }
}
```

### 方案分析

1.  **安全性**：

      * 旧代码：异常直接吞掉 -\> Redis 脏数据。
      * 新代码：异常 -\> 本地重试 3 次 -\> 发送 MQ 留痕 -\> 人工介入。最大程度保证了系统不会因为一条坏数据卡死，也不会悄无声息地丢数据。

2.  **兜底逻辑**：

      * `sendToErrorQueue` 如果返回 `true`，说明虽然 Redis 删除失败了，但我们要么记录下来了，后续可以写个脚本消费这个队列去补删 Redis，所以 Canal 可以放心地 ACK。
      * 只有当 Redis 挂了 **且** RabbitMQ 也挂了的时候，才会返回 `false`，触发 Canal 的 `rollback`，这是最后的防线。

3.  **ID 提取优化**：

      * 原代码提取 ID 的逻辑有点冗余，我稍微优化了一下 `extractId`，针对 `DELETE` 事件（`AfterColumns` 为空）做了兼容处理。
