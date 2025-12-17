# 全流程手工压测记录（2025-12-17）

> 目标：验证「店铺展示/权限」、「三种秒杀限购」、「Feed 关注流」全链路可用，并给出可复现的手工压测步骤与观测点。

## 0) 前置说明
- 端口：
  - `hmdp-service`：`8088`
  - `order-service`：`8083`
  - `relay-service`：`8084`
  - `feed-service`：`8085`
- 日志（按 DEPLOY.md 统一落仓库根目录 `logs/`）：
  - `logs/hmdp-service.log`
  - `logs/order-service.log`
  - `logs/relay-service.log`
  - `logs/feed-service.log`
- Redis 集群（密码 `123456`）：`123.56.100.212:6379` / `39.97.193.168:6379` / `115.190.193.236:6379`
- MySQL：`123.56.100.212:3306/hmdp`（root）
- RabbitMQ：`115.190.193.236:5672`（paperplane/123456）

## 1) 启动/重启（建议顺序）
1. 启动 `hmdp-service` → `relay-service` → `order-service` → `feed-service`
2. 启动前端（二选一）：
   - 推荐：直接访问 `http://127.0.0.1:8088/`（`hmdp-service` 托管 `hmdp-frontend/dist`，无需另起前端进程）
   - 开发：`cd hmdp-frontend && npm run dev`（Vite：`http://127.0.0.1:5173/`，自动代理 `/api` 和 `/imgs` 到 `8088`）
3. 打开页面后先做一次硬刷新（`Ctrl+Shift+R`），避免旧 JS 缓存导致“请求成功但不渲染”。

## 2) 店铺展示与权限（核心：能看到店铺、能点进店铺详情）

### 2.1 店铺类型与列表展示（C 端首页）
1. 访问首页 `/`，观察是否出现店铺列表（至少 1 条）。
2. 打开浏览器 DevTools：
   - Network 里确认 `GET /api/shop-type/list` 返回成功。
   - Network 里确认 `GET /api/shop/of/type?typeId=<id>&current=1` 返回 `success=true` 且 `data` 有数组。
3. 若请求成功但页面仍空：
   - 若使用 Vite 开发模式（`5173`）：Console 可执行 `window.__homeState / window.__homeLoadMore?.()` 辅助定位是否卡在 `loading/finished`。
   - 若使用后端托管（`8088`）：生产构建会移除调试变量，建议直接看 Network 与接口返回。

### 2.2 商户创建店铺 → C 端可见
1. 商户账号登录（商户端页面或统一登录页切到 MERCHANT）。
2. 新建店铺（必填：名称/类型/地址/经纬度），提交成功后记下 `shopId`。
3. 退出商户，切到普通用户访问首页：
   - 期望：列表能出现该店铺（按类型分页/按名称搜索均可找到）。
4. 观测点：
   - `hmdp-service`：新建店铺会写入 DB，同时维护 Redis GEO 与类型列表缓存，并预热详情缓存。

### 2.3 权限校验：非创建者禁止改店/发券
1. 商户 A 新建店铺（或选一个 `created_by=商户A` 的店铺）。
2. 切换到商户 B 登录：
   - 尝试 `PUT /shop` 更新该店铺信息 → 期望返回“无权限修改该店铺”。
   - 尝试 `POST /voucher` 或 `POST /voucher/seckill` 给该店铺发券 → 期望返回“无权限”。
3. 管理员登录（`/admin/login`）：
   - 对同一店铺更新/发券应成功（ADMIN 可越权）。

## 3) 秒杀三种限购模式（强一致兜底：Redis + MQ + DB）

### 3.0 统一观测点（每种模式都要看）
- Redis（Lua 入口写）：
  - `seckill:{seckill}:stock:<voucherId>` 库存
  - `seckill:{seckill}:req:<reqId>`（请求幂等键）
  - `seckill:{seckill}:status:<reqId>`（前端轮询状态键）
  - `seckill:{seckill}:order:<voucherId>`（一人一单 set）
  - `seckill:{seckill}:usercount:<voucherId>`（累计限购 hash）
  - `seckill:{seckill}:outbox`（Redis outbox 列表）
- MQ：
  - Relay 将 outbox 搬运到 RabbitMQ（交换机/队列见 `relay-service` 配置）。
- DB：
  - `tb_voucher` / `tb_seckill_voucher`（券与秒杀元数据）
  - `tb_voucher_order`（订单）
  - `tb_user_quota`（累计限购的用户已购计数）

### 3.1 一人一单（limit_type=1，后端自动 MD5 reqId）
1. 创建秒杀券：`limit_type=1`，库存建议 `stock=5`，时间窗口覆盖当前。
2. 普通用户进入店铺详情页，点击秒杀：
   - 期望：无需先取 `reqId`，接口直接返回一个 `reqId`（后端按 `voucherId:userId` 做 MD5）。
3. 连续重复点击：
   - 期望：立即返回“已达到限购次数”（或状态 `FAILED/LIMIT`），且库存不再减少。
4. 观测点：
   - Redis：`order:<voucherId>` set 中包含该用户；`req:<md5>` 已存在；`status:<md5>` 最终 `SUCCESS`。
   - DB：只出现 1 条该用户的订单记录（`count=1`）。

### 3.2 一人多单（limit_type=2，必须先领 reqId）
1. 创建秒杀券：`limit_type=2`，库存建议 `stock>=20`，允许自定义 `count`。
2. 用户在店铺详情选择数量（例如 3）点击秒杀：
   - 期望：前端先调用 `GET /voucher-order/req/<voucherId>?count=3` 获取 `reqId`（纯 reqId，便于重试与轮询）；
   - 再调用 `POST /voucher-order/seckill/<voucherId>?reqId=<reqId>&count=3`。
3. 用同一个 reqId 重试：
   - 期望：入口 Lua 识别幂等，不重复扣库存、不重复入队。
4. 再次购买必须重新申请新的 reqId（否则仍视为同一笔购买意图）。
5. DB 校验：同用户同券可出现多条订单（不同 `request_id`），每条 `count` 与本次一致。

### 3.3 累计限购（limit_type=3，累计上限 user_limit）
1. 创建秒杀券：`limit_type=3`，例如 `user_limit=5`，库存建议 `stock>=20`。
2. 用户分多次购买：`2 + 2 + 1`（总计 5）：
   - 期望：三次均成功，状态 `SUCCESS`。
3. 第四次再买 `1`：
   - 期望：入口 Lua 直接返回 `FAILED/LIMIT`（不扣库存、不入队）。
4. DB 校验：
   - `tb_user_quota`：该用户该券 `owned_count=5`。
   - `tb_voucher_order`：三条订单 `count` 分别为 2/2/1，且 `request_id` 均唯一。

## 4) Feed 关注流（发布 → outbox → MQ → feed-service → 收件箱）

### 4.1 关注作者与发布笔记
1. 用户 A（粉丝）登录，用户 B（作者）登录。
2. A 关注 B：点击“关注作者”，或调用 `PUT /follow/<B>/true`。
3. B 发布笔记：在 `/blogs` 页面点击“发布笔记”弹窗发布（可选关联店铺）。
4. 观测点：
   - `hmdp-service`：发布会写 DB，同时把 FeedMessage 写入 `feed:{feed}:outbox`。
   - `relay-service`：从 outbox 搬运到 RabbitMQ（feed exchange）。
   - `feed-service`：消费消息并把 blogId 扇出写入 A 的 `feed:<A>` zset。
5. A 打开“关注流”：
   - 期望：能看到 B 的最新笔记，加载速度明显优于 N+1（后端已做批量补全用户信息与 Redis Pipeline 点赞状态）。

### 4.2 兜底：收件箱缺失回源 DB
1. 手动删除 `feed:<A>`（仅测试环境操作）。
2. A 再次访问“关注流”：
   - 期望：接口回源 DB 拉最近 N 条并回填 `feed:<A>`，避免“关注流全空”。

## 5) 压测建议（手工 + 轻量）
- 秒杀：
  - 同一用户在 limit_type=2/3 下，连续申请 reqId 并秒杀 20 次，观察：
    - Redis outbox 消费是否堆积
    - `order-service` 是否出现重复消费（应被 `request_id` 幂等拦截）
    - DB 库存是否出现负数（不应出现）
- Feed：
  - 作者连续发布 20 篇笔记，粉丝端下拉关注流，观察：
    - 接口耗时是否稳定
    - `feed-service` 是否有批量写入日志

## 6) 常用排查命令（按需）
- Redis（示例）：
  - `redis-cli -c -h 123.56.100.212 -a 123456 GET "seckill:{seckill}:stock:<vid>"`
  - `redis-cli -c -h 123.56.100.212 -a 123456 GET "seckill:{seckill}:status:<reqId>"`
  - `redis-cli -c -h 123.56.100.212 -a 123456 LRANGE "seckill:{seckill}:outbox" 0 5`
- MySQL（示例）：
  - `SELECT * FROM tb_voucher_order WHERE request_id = '<reqId>';`
  - `SELECT * FROM tb_user_quota WHERE user_id=<uid> AND voucher_id=<vid>;`

## 7) 本次实测结果（2025-12-17 15:26 重启后）

### 7.1 基础可用性
- 前端：
  - `GET http://127.0.0.1:8088/` 返回 `index.html`（后端托管 SPA 可用）
  - `GET http://127.0.0.1:5173/` 返回 `200`（Vite dev 可用）
- 店铺：
  - `GET /api/shop-type/list` 返回类型列表（11–15）
  - `GET /api/shop/of/type?typeId=11&current=1` 返回 2 条店铺数据

### 7.2 秒杀三种限购（新建券 ID）
- 管理员创建三张秒杀券（`shopId=17`）：
  - 一人一单（`limit_type=1`）：`voucherId=9907`
  - 一人多单（`limit_type=2`）：`voucherId=9908`
  - 累计限购（`limit_type=3,user_limit=5`）：`voucherId=9909`

#### 一人一单（9907）
- `POST /voucher-order/seckill/9907` 返回 `reqId=f5f4e6217eb87624b2cd0280a39f4e74`
- `GET /voucher-order/status?reqId=...` → `SUCCESS`，`orderId=536829508515266569`
- 重复下单返回同一 `reqId`（幂等成功）

#### 一人多单（9908）
- `GET /voucher-order/req/9908?count=3` → `reqId=536829749033435142`
- `POST /voucher-order/seckill/9908?reqId=...&count=3` → 轮询状态 `SUCCESS`，`orderId=536829749033435147`，`count=3`
- 同一 `reqId` 重试幂等（不重复扣库存、不重复入队/入库）

#### 累计限购（9909）
- 连续购买：`2 + 2 + 1`（三次均 `SUCCESS`）
  - `reqId=536829830637813767`（2）
  - `reqId=536829834932781064`（2）
  - `reqId=536829839227748361`（1）
- 第四次再买 `1`：`reqId=536829843522715658` → `FAILED` 且 `reason=LIMIT`
- DB：`tb_user_quota(user_id=2001,voucher_id=9909).owned_count=5`

### 7.3 Feed 关注流（实测）
- 用户 `2001` 关注 `2002`：`PUT /follow/2002/true` → `success=true`
- 用户 `2002` 发布笔记：`POST /blog` → `blogId=32`
- Redis：`ZREVRANGE feed:2001 0 5 WITHSCORES` 包含 `32 <timestamp>`
- API：`GET /blog/of/follow?lastId=9999999999999&offset=0` 返回首条 `id=32`
- 兜底验证：`DEL feed:2001` 后再次请求 `blog/of/follow` 仍返回数据并回填 `feed:2001`
