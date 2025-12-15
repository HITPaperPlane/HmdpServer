# feed流

## 大V更新帖子

### 利用 **Redis + 多线程 Relay + RabbitMQ** 的架构，可以完美解决大 V 发布笔记时接口超时的问题。

- 不过，**Feed 流（推模式）** 与 **秒杀（扣减模式）** 在“DB 兜底”的逻辑上有一个核心区别：
  
    - **秒杀**：DB 存的是 **“结果”**（订单）。如果 Redis 丢数据，DB 有订单记录，或者因为库存不足而拦截，保证资金安全。
      
    - **Feed**：DB 存的是 **“源头”**（`tb_blog` 笔记和 `tb_follow` 关注关系）。粉丝的收件箱（Inbox）本质上是一个**聚合视图**。我们**绝不会**在 DB 里给几千万粉丝每个人建一张“收件箱表”存数据，那样 DB 会瞬间爆炸。
      
### 因此，Feed 流的“兜底”不是靠“DB 乐观锁防超卖”，而是靠 **“读时回源（Pull 兜底）”**。

### 以下是基于您心仪的 **Relay 架构** 定制的 Feed 推送详细设计：

### 🚀 架构全览：异步 Feed 推送 (Push Mode)

- 1. 组件映射关系
  
    - **Redis (Outbox)**
      
        - 存放“秒杀请求”信号
          
        - 存放“新笔记发布”信号 (只存 `blogId`, `authorId`)
          
    - **Relay Service**
      
        - 搬运请求到 MQ
          
        - 搬运发布信号到 MQ，**削峰填谷**（防止大V发帖瞬间打挂 MQ）
          
    - **RabbitMQ**
      
        - 缓冲流量
          
        - **任务分发中心**。对于大 V，这里甚至需要进行“裂变”（把一个推送任务拆分成多个子任务）
          
    - **Consumer**
      
        - 扣减库存、下订单
          
        - **查询粉丝 + 写 Redis Inbox**。核心是“扇出”（Fan-out）操作
          
    - **DB (MySQL)**
      
        - **最终账本** (Orders 表)
          
        - **数据源头** (`tb_blog` + `tb_follow`)。**注意：DB 不存收件箱数据！**
          
    - **Redis (Inbox)**
      
        - (秒杀不涉及)
          
        - **粉丝收件箱** (`feed:{userId}` ZSet)
          
### 2. 全链路极致详细流程

- 🟢 阶段 1：极速发布 (API 层)
  
    - **目标**：让发笔记接口 (POST /blog) 在 20ms 内响应，不管你有多少粉丝。
      
    - **动作**：
      
        1. **落库**：保存笔记到 MySQL `tb_blog` 表（这是元数据，必须先强一致落库）。
           
        2. **写入 Outbox**：将 `{ "blogId": 101, "authorId": 888, "type": "feed_push" }` 写入 Redis List (`feed:outbox`)。
           
        3. **返回**：直接给前端返回“发布成功”。
           
    - **优势**：用户发完即走，完全无感知后续的百万次推送。
      
- 🔵 阶段 2：可靠搬运 (Relay 层)
  
    - **逻辑**：与秒杀完全一致。
      
    - **动作**：
      
        - 多线程从 Redis `feed:outbox` 拉取消息。
          
        - 发送到 RabbitMQ Exchange (例如 `blog.topic`，RoutingKey: `blog.publish`)。
          
        - 等待 Confirm 确认，确保消息进入 MQ。
          
        - 删除 Redis Outbox 消息
          
- 🟠 阶段 3：扇出与分片 (Consumer 层 - **核心难点**)
  
    - **问题**：如果作者是普通人（粉丝 100 个），直接查库、推 Redis 没问题。如果作者是千万级大 V，一个 Consumer 处理不过来，甚至查 `tb_follow` 表都会超时。
      
    - **策略：任务裂变 (Splitter)**
      
        1. **Consumer A (任务分发者)** 收到 `blog.publish` 消息。
           
        2. 它先去查 `tb_follow` 表的**数量**（或者查 Redis 里的粉丝数缓存）。
           
        3. **分支判断**：
           
            - **粉丝 < 5000**：直接查出所有粉丝 ID，Pipeline 写入 Redis，结束。
              
            - **粉丝 > 5000 (大 V)**：
              
                - 不要自己处理！
                  
                - 按页切分，比如每 1000 个粉丝生成一个**子任务消息**。
                  
                - 发送子任务消息到 RabbitMQ 的另一个队列 `feed.push.batch`。
                  
                - 消息体：`{ blogId: 101, batchStart: 0, batchSize: 1000 }`。
                  
        4. **Consumer B (打工仔)** 监听 `feed.push.batch`。
           
            - 收到子任务，去 DB 分页查 `tb_follow` (Limit 0, 1000)。
              
            - Redis Pipeline 批量写入这 1000 个粉丝的 `feed:{userId}`。
              
- 🟣 阶段 4：DB 兜底与防脑裂 (Design for Failure)
  
    - 您担心的“Redis 脑裂”在 Feed 场景下表现为：**推送任务显示成功了，但粉丝的 Redis Inbox 里实际上没数据（丢了）。**
      
    - 由于我们不能在 DB 里存 Inbox，我们采用 **“推拉结合 (Hybrid)”** 或 **“读扩散兜底”** 策略。
      
    - **Redis (Inbox)**：是“加速层”。
      
    - **DB (****`tb_follow` + ****`tb_blog`)**：是“真理层”。
      
    - **兜底流程（读的时候触发）：**
      
    1. 粉丝刷新 Feed 流 (`GET /blog/of/follow`)。
       
    2. 后端先查 Redis `feed:{userId}`。
       
    3. **判断异常**：
       
        - 如果 Redis 为空（可能是新用户，也可能是 Redis 挂了/脑裂丢数据了）。
          
            - 进一步的，即使不为空，也会出现**“缓存不一致（Cache Inconsistency）”中的“假阳性”问题**。
              
                - 仅仅判断 `Redis不为空` 确实无法识别“部分丢失”的情况。
                  
                - 如果用户关注了 A, B, C 三个人：
                  
                    1. A 和 B 的帖子推送到 Redis 了（Redis 有数据，不为空）。
                       
                        1. 细分主题 1
                           
                    2. C 的帖子因为网络原因、MQ 丢消息或 Redis 写入失败而 **丢了**。
                       
                        1. 细分主题 1
                           
                    3. 后端查 Redis -> **非空** -> 直接返回 -> **用户永远看不到 C 的那条帖子**（除非 Redis key 过期或者被清空）。
                       
                        1. 细分主题 1
                           
                - 为了解决这个“部分丢失”的问题，我们需要升级兜底策略。最有效的方案是采用 **“增量同步（Smart Pull）”** 和 **“生命周期管理”** 相结合。
                  
                - 1. 核心解法：刷新时的“增量比对” (Smart Refresh)
                  
                    - 我们不能每次**滚动（Load More）**都查数据库（那是读扩散，性能差），但我们可以在用户**主动刷新（Pull to Refresh）**时，做一个“快速的一致性检查”。
                      
                    - 逻辑流程：
                      
                        - 当用户在 APP 顶部下拉刷新时（请求第一页）：
                          
                        1. **取 Redis 哨兵**：先获取 Redis 收件箱中**最新一条**微博的时间戳 (`max_score`)。
                           
                            - 如果 Redis 为空，`max_score = 0`。
                              
                        2. **DB 增量查询**：拿着这个 `max_score` 去数据库查询，看有没有**比这更新**的帖子。
                           
                            - SQL: `SELECT * FROM tb_blog WHERE user_id IN (关注列表) AND create_time > {max_score} ORDER BY create_time DESC`
                              
                            - *注意：这里利用了之前提到的 **`(user_id, create_time)` 联合索引，查询速度很快。*
                              
                        3. **分支判断**：
                           
                            - **DB 返回空**：说明 Redis 里已经是最新数据了。直接返回 Redis 数据。
                              
                            - **DB 有数据**：说明发生了“部分丢失”（即 Redis 漏了最新的帖子）。
                              
                                - **动作**：将 DB 查到的这几条新数据 **ZADD 回填** 到 Redis。
                                  
                                - **返回**：返回合并后的最新数据。
                                  
                        - **✅ 效果**：
                          
                        - 平时滑动浏览（翻页）：走纯 Redis（Push 模式），极速。
                          
                        - 顶部下拉刷新：走 DB 增量检查（Pull 模式），确保不丢最新的数据。
                          
                        - 用户感觉：如果刚才丢了 C 的帖子，一刷新就出来了。
                          
                - 2. 辅助解法：TTL 与 会话重建 (Self-Healing)
                  
                    - 为了防止“中间的帖子丢了”（即不是最新的，而是夹在中间的），我们需要让缓存定期“自杀”重来。
                      
                    - 策略 A：设置较短的 TTL (Time To Live)
                      
                        - **做法**：给 `feed:{userId}` 设置过期时间，比如 **24 小时** 或 **1 小时**（取决于活跃度）。
                          
                        - **逻辑**：
                          
                            - 一旦 Key 过期，Redis 变空。
                              
                            - 下次用户请求时，触发原本设计的“Redis 为空 -> 全量回源 DB”的逻辑。
                              
                            - 数据自动修复。
                              
                    - 策略 B：登录/冷启动时重建
                      
                        - **做法**：当用户杀掉 APP 重新打开，或者重新登录时。
                          
                        - **逻辑**：
                          
                            - 后端异步触发一个 `RebuildTask`。
                              
                            - 直接删除 Redis 旧 Key，从 DB 拉取最新的 50 条重构 Inbox。
                              
                            - 保证用户每次“重新开始”时，看到的都是绝对正确的。
                              
                    - A和B同时使用
                      
                - 3. 业务场景的“关注动作”补全
                  
                    - 还有一种“丢数据”的情况是用户**新关注**了一个人。
                      
                    - **场景**：我刚关注了“刘德华”。Redis 里只有我之前关注的“张学友”的帖子。Redis 不为空。
                      
                    - **问题**：我看不到刘德华刚刚发的帖子。
                      
                    - **解决**：在 **关注 (Follow)** 的业务逻辑中，必须加入一步 **“数据热加载”**。
                      
                        - 当执行 `save(follow)` 成功后。
                          
                        - 立即去 DB 查询该博主最近发布的 10 条笔记。
                          
                        - `ZADD` 到当前用户的 Redis Inbox 中。
                          
                        - 这样用户一刷新，就能马上看到新关注的大 V 的内容。
                          
                - 📊 总结：完整的抗风险架构
                  
                    - 为了堵死“部分丢失”的漏洞，最终的架构是这样的：
                      
                    - (empty)
                      
                        - **滑动翻页** (Load More)
                          
                            - **纯 Redis (Push)**
                              
                            - 极致性能，丝滑体验
                              
                            - 零
                              
                        - **下拉刷新** (Refresh)
                          
                            - **Redis + DB增量检查 (Smart Pull)**
                              
                            - 修复“最新”丢失的数据，确保一致性
                              
                            - 低 (仅查最新)
                              
                        - **新关注博主**
                          
                            - **DB 补全 (Copy)**
                              
                            - 将新博主的旧贴拷过来
                              
                            - 低 (单次)
                              
                        - **冷启动/登录**
                          
                            - **异步重建 (Rebuild)**
                              
                            - 彻底修复所有潜在的“中间空洞”
                              
                            - 中 (异步不阻塞)
                              
                        - **数据过期**
                          
                            - **TTL 失效**
                              
                            - 兜底机制，防止脏数据永久驻留
                              
                            - -
                              
                    - **结论：**  
                      你现在的架构中，**“推模式 (Push)”负责性能**，**“拉模式 (Pull)”负责一致性**。  
                      将两者结合（翻页用推，刷新用拉），就能在“Redis 不为空但缺数据”的情况下，通过用户的刷新动作自动修复数据。
                      
        - 或者 Redis 里的数据最后更新时间很久远。
          
    4. **触发兜底 (Fallback to Pull)**：
       
        - 后端直接去 DB 查该用户的关注列表 (`tb_follow`)。
          
        - 拿着关注列表去 DB 查 `tb_blog` (WHERE user_id IN (...) ORDER BY time DESC LIMIT 10)。
          
            - 这告诉我们最好在数据库建一个联合索引(user_id, create_time) 
              
                - SQL: INDEX idx_user_time (user_id, create_time)
                  
                    - 执行逻辑（精准定位 + 局部排序）：
                      
                    - 定位：数据库利用索引左边的 user_id，直接跳到你关注的 User_A、User_B... 的数据块。
                      
                    - 范围扫描：在每个用户的索引块内，数据本来就是按 create_time 排序的（因为是联合索引）。
                      
                        - 它只需要把每个关注者 09:00 之前的最新几条数据取出来。
                          
                    - 合并排序 (Filesort)：
                      
                        - 数据库拿到了（假设关注了 50 人）这 50 个人的最新数据候选集（数据量很小）。
                          
                        - 在内存中对这几百条数据做一个快速排序，取前 10。
                          
        - **重构 Redis**：将查到的结果“回填”到 Redis Inbox 中（重建缓存）。
          
        - 返回数据给前端。
          
### 3. 代码实现关键点 (伪代码)

- A. 发布接口 (Controller)
  
    - ```java  
      @PostMapping  
      public Result saveBlog(@RequestBody Blog blog) {  
          // 1. 先存 DB (元数据)  
          boolean success = blogService.save(blog);  
          if (!success) return Result.fail("保存失败");  
        
          // 2. 写入 Redis Outbox (只存 ID，极小)  
          // 模仿秒杀的 seckill:outbox  
          RedisFeedMessage msg = new RedisFeedMessage(blog.getId(), UserHolder.getUser().getId());  
          stringRedisTemplate.opsForList().leftPush("feed:outbox", JSONUtil.toJsonStr(msg));  
        
          return Result.ok(blog.getId());  
      }  
      ```
      
- B. 消费者逻辑 (MQ Consumer)
  
    - 这里展示**分批推送**的逻辑，防止大 V 阻塞。
      
    - ```java  
      @RabbitListener(queues = "feed.queue")  
      public void handleFeedPush(String msg) {  
          RedisFeedMessage payload = JSONUtil.toBean(msg, RedisFeedMessage.class);  
          Long blogId = payload.getBlogId();  
          Long authorId = payload.getAuthorId();  
          long timestamp = System.currentTimeMillis();  
        
          // 1. 预估粉丝数 (从 Redis 或 DB Count)  
          int fanCount = followService.countFans(authorId);  
        
          // 2. 策略选择  
          if (fanCount < 5000) {  
              // 小 V：直接处理  
              List<Long> fanIds = followService.queryFanIds(authorId);  
              pushToRedis(fanIds, blogId, timestamp);  
          } else {  
              // 大 V：异步分片 (发送回 MQ 或 线程池处理)  
              int pageSize = 1000;  
              for (int i = 0; i < fanCount; i += pageSize) {  
                  // 发送分片任务到 RabbitMQ 的 batch 队列  
                  // 消息体包含: { authorId, blogId, offset: i, limit: pageSize }  
                  rabbitTemplate.convertAndSend("feed.batch.exchange", "batch.push",   
                      new BatchPushTask(authorId, blogId, i, pageSize));  
              }  
          }  
      }  
        
      // 批量写入 Redis (Pipeline 优化)  
      private void pushToRedis(List<Long> fanIds, Long blogId, long timestamp) {  
          stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {  
              for (Long fanId : fanIds) {  
                  String key = "feed:" + fanId;  
                  // ZADD feed:1001 170888888 202  
                  connection.zAdd(key.getBytes(), timestamp, blogId.toString().getBytes());  
              }  
              return null;  
          });  
      }  
      ```
      
### 任务裂变过程的幂等性分析（redis的收信箱结构也和db一样有天然的幂等性，这是核心）

- 🏛️ 核心架构组件
  
    1. **Consumer A (Master/Dispatcher)**: **包工头**。只负责计算和发令，不负责搬砖。部署少量节点（如 2-3 个）。
       
    2. **RabbitMQ (Task Queue)**: **传送带**。专门的队列 `feed.push.batch.queue`，用于传输拆分后的子任务。
       
    3. **Consumer B (Worker/Executor)**: **搬砖工**。负责查库和写 Redis。部署大量节点（如 50-100 个），且支持动态扩容。
       
- 🔄 全链路详细流程设计
  
    - 🟢 阶段 1：Master 收到信号与“防重裂变”
      
        - **场景**：API 层发送了 `feed.publish` 消息，Consumer A 收到了。  
          **挑战**：如果 MQ 抽风，把这条消息投递了两次，Consumer A 会不会把 1000 万个任务拆分两遍？导致 MQ 里堵塞 2000 万条消息？
          
        - **Step 1.1: 幂等性检查 (The Lock)**
          
            - Consumer A 收到消息 `{blogId: 101, authorId: 888}`。
              
            - **动作**：立即尝试在 Redis 设置一个互斥锁（或者状态标记）。
              
            - `SETNX feed:split:lock:101 1 EX 300` (5分钟过期)
              
            - **结果**：
              
                - **成功 (True)**：我是第一个处理的，继续。
                  
                - **失败 (False)**：说明已经有别的 Master 节点在拆分这个 Blog 了，**直接 ACK 并丢弃消息**。
                  
        - **Step 1.2: 评估工作量**
          
            - 查询 `tb_follow` 表或 Redis 缓存，得知 `authorId=888` 有 1000 万粉丝。
              
            - 设定 `BATCH_SIZE = 1000` (每个子任务处理 1000 人)。
              
            - 计算子任务数：`10,000,000 / 1000 = 10,000` 个批次。
              
        - **Step 1.3: 极速裂变 (Dispatch)**
          
            - Consumer A 启动一个循环，生成 10,000 条消息。
              
            - **关键设计**：消息体中必须包含 **“锚点”**，确保任务是确定的。
              
            - **消息体格式**：
              
                - ```json  
                  {  
                    "taskId": "task_101_5",    // 唯一业务ID：blogId_第几批  
                    "blogId": 101,             // 帖子ID  
                    "authorId": 888,           // 大V ID  
                    "offset": 5000,            // 数据库查询偏移量  
                    "limit": 1000,             // 查询数量  
                    "timestamp": 1709999999    // 发布时间戳 (用于ZSet Score)  
                  }  
                  ```
                  
            - **发送**：批量发送到 `feed.push.batch` 队列。
              
        - **Step 1.4: 结束**
          
            - Consumer A 完工，ACK 原消息。耗时仅几百毫秒。
              
    - 🟡 阶段 2：MQ 缓冲与负载均衡
      
        - **RabbitMQ** 里现在堆积了 10,000 条小任务。
          
        - **特性**：这 10,000 条任务互不干扰，顺序无关。
          
    - 🟠 阶段 3：Worker 并行处理与“天然幂等”
      
        - **场景**：100 个 Consumer B 节点同时监听队列，大家拼命抢任务做。  
          **挑战**：如果 Worker 刚做完写入 Redis，还没来得及给 MQ 发 ACK 就断电了。MQ 会重发这条任务。**会导致粉丝收件箱里有两条一样的帖子吗？**
          
        - **Step 3.1: 领取任务**
          
            - Worker X 抢到了第 5 批任务：`{offset: 5000, limit: 1000}`。
              
        - **Step 3.2: 查库 (IO Bound)**
          
            - Worker 去 MySQL 查询粉丝列表。
              
            - `SELECT user_id FROM tb_follow WHERE follow_user_id = 888 LIMIT 5000, 1000`
              
            - *(注：深分页会有性能问题，进阶做法是用 **`last_id` 游标法，这里为了好理解先用 limit)*
              
        - **Step 3.3: 写 Redis (天然幂等性核心)**
          
            - Worker 拿到 1000 个粉丝 ID。
              
            - **动作**：使用 Pipeline 批量写入粉丝的收件箱。
              
            - **指令**：`ZADD feed:{fan_id} {timestamp} {blogId}`
              
            - **幂等性分析**：
              
                - Redis 的 **Set (集合)** 和 **ZSet (有序集合)** 具有**天然幂等性**。
                  
                - **第一次执行**：`ZADD feed:UserA 170999 101` -> 成功，返回 1。
                  
                - **第二次执行 (重试)**：`ZADD feed:UserA 170999 101` -> Redis 发现 `101` 已经存在且 Score 也是 `170999`，**直接忽略**，返回 0。
                  
                - **结论**：无论这个任务重复执行多少次，Redis 里的数据永远只有一条，且顺序正确。**不需要任何额外的防重逻辑！**
                  
        - **Step 3.4: 提交**
          
            - 执行成功，ACK 消息。
              
- 🛡️ 幂等性设计总结 (Idempotency Matrix)
  
    - 在这个裂变系统中，我们在三层做了防御，构成了**“金刚不坏”**之身：
      
    - (empty)
      
        - **Layer 1: Master**
          
            - 上游重复发送 `blog.publish` 指令
              
            - **Redis 分布式锁 (SETNX)**
              
            - 即使收到 10 次指令，只执行 1 次裂变，不会生成 1 亿个子任务。
              
        - **Layer 2: MQ**
          
            - 消息积压或网络抖动
              
            - **唯一 TaskID**
              
            - 虽然 MQ 不保证只投递一次，但下游有去重机制。
              
        - **Layer 3: Worker**
          
            - 子任务重复投递 (Redelivery)
              
            - **Redis ZADD 天然幂等**
              
            - 只要 `blogId` 和 `timestamp` 没变，覆盖写入等于无操作。数据绝对一致。
              
- ⚡ 极端异常处理 (Corner Cases)
  
    1. **Master 刚发了一半任务挂了？**
       
        - **现象**：Redis 锁还在，但只发了 5000 个子任务。
          
        - **恢复**：
          
            - 方案 A (简单)：锁设置过期时间 (TTL)。过期后，如果上游重试，新的 Master 会再次裂变。此时会有重复任务进入 MQ。**没关系！** 因为 Layer 3 的 Worker 是幂等的，重复执行一遍只会浪费一点 CPU，不会导致数据错乱。
              
            - 方案 B (严谨)：Master 发送完所有消息后，才去删除 Redis 锁或标记状态。
              
    2. **Worker 查 MySQL 超时了？**
       
        - **现象**：大 V 粉丝表太大，LIMIT 越往后越慢。
          
        - **优化**：Master 在裂变时，不要用 `offset/limit`。
          
        - **进阶裂变**：Master 先查出 ID 的范围（Range）。
          
            - Task 1: `id > 0 AND id <= 10000`
              
            - Task 2: `id > 10000 AND id <= 20000`
              
        - 这样 Worker 查库走**聚簇索引**，速度极快且稳定。
          
- 📝 最终代码形态 (Java伪代码)
  
    - **Master (Consumer A):**
      
    - ```java  
      public void onMessage(BlogPublishMsg msg) {  
          String lockKey = "lock:split:" + msg.getBlogId();  
          // 1. 防重裂变  
          if (!redis.setIfAbsent(lockKey, "1", 5, TimeUnit.MINUTES)) {  
              return; // 已经有人在处理了  
          }  
        
          long totalFans = followService.count(msg.getAuthorId());  
          long batchSize = 1000;  
            
          // 2. 裂变循环  
          for (long i = 0; i < totalFans; i += batchSize) {  
              BatchTask task = new BatchTask();  
              task.setBlogId(msg.getBlogId());  
              task.setOffset(i); // 或者使用 minId/maxId  
              task.setLimit(batchSize);  
              task.setTimestamp(msg.getTimestamp());  
                
              // 3. 发送给 Worker 队列  
              rabbitTemplate.send("feed.batch.queue", task);  
          }  
      }  
      ```
      
    - **Worker (Consumer B):**
      
    - ```java  
      public void onBatchMessage(BatchTask task) {  
          // 1. 查库 (只查一小批)  
          List<Long> fanIds = followService.getFans(task.getAuthorId(), task.getOffset(), task.getLimit());  
            
          // 2. 写 Redis (Pipeline + ZADD 天然幂等)  
          redisTemplate.executePipelined(connection -> {  
              for (Long fanId : fanIds) {  
                  // Key: feed:用户ID, Score: 时间戳, Member: 博客ID  
                  // ZADD 如果存在则更新Score(其实没变)，不存在则插入  
                  connection.zAdd(  
                      ("feed:" + fanId).getBytes(),   
                      task.getTimestamp(),   
                      task.getBlogId().toString().getBytes()  
                  );  
              }  
              return null;  
          });  
            
          // 3. 自动 ACK  
      }  
      ```
      
