# 手工回归测试日志（2025-12-17 v2）

> 目标：覆盖秒杀三种限购类型 + Feed 推送/兜底 + 店铺 GEO 附近查询 + 关注列表/作者页/订单页等核心链路。  
> 日志文件：`logs/hmdp-service.log`、`logs/order-service.log`、`logs/relay-service.log`、`logs/feed-service.log`、`logs/hmdp-frontend.log`。

## 0. 测试环境
- 前端：`http://localhost:5173/`（或 `http://127.0.0.1:8088/` 静态托管）
- 后端：
  - `hmdp-service`：8088
  - `order-service`：8083
  - `relay-service`：8084
  - `feed-service`：8085
- DB：`123.56.100.212:3306/hmdp`
- Redis Cluster：`123.56.100.212/39.97.193.168/115.190.193.236:6379`
- RabbitMQ：`115.190.193.236:5672`
- 测试账号（本次新增）：
  - 管理员：`admin/123456`
  - 商家：`merchant_test_1@hmdp.local`（userId=1031）
  - 用户：`user_test_1@hmdp.local`（userId=1032）
- 本次创建数据：
  - 店铺：`tb_shop.id=23`（测试店铺A，type=11，美食，经纬度=120.15/30.28，created_by=1031）
  - 普通券：`tb_voucher.id=9913`
  - 秒杀券：一人一单=9914，一人多单=9915，累计限购=9916
  - 压测券：一人多单=9917（stock=50）

## 1. 秒杀：三种限购类型（商家→管理员预热→用户抢购→落库）
### 1.1 一人一单
- 步骤：
  1) 商家创建秒杀券：`POST /voucher/seckill`（limitType=1, stock=5）
  2) 管理员预热：`POST /voucher/seckill/preheat/9914`
  3) 用户抢购：`POST /voucher-order/seckill/9914`（不传 reqId）
  4) 轮询状态：`GET /voucher-order/status?reqId=<md5>`
- 观察点：
  - `/voucher-order/seckill/{id}` 返回 reqId（后端基于 `voucherId+userId` 的 md5）
  - Redis：库存扣减、状态写入
  - order-service：最终落库 `tb_voucher_order`，同一用户重复请求不会重复写入
- 结果记录：
  - ✅：
    - 首次返回 `reqId=e4d2e1cf3fc43958de222ec7424ef451`，轮询返回 `SUCCESS`，`orderId=536943823364816917`
    - 重复调用 `POST /voucher-order/seckill/9914` 仍返回相同 `reqId`，DB `tb_voucher_order` 仍为 1 条（未重复写入）

### 1.2 一人多单
- 步骤：
  1) 商家创建秒杀券：`POST /voucher/seckill`（limitType=2, stock=8）
  2) 管理员预热：`POST /voucher/seckill/preheat/9915`
  3) 用户每次购买先取 reqId：`GET /voucher-order/req/9915`
  4) 购买：`POST /voucher-order/seckill/9915?reqId=<reqId>&count=1`
  5) 重复提交同一 reqId：应幂等
- 观察点：
  - `GET /voucher-order/req/{id}` 获取 reqId
  - 前端 `sessionStorage` 持久化 reqId（刷新页面不丢）
- 结果记录：
  - ✅：
    - 第 1 单：`reqId=536944012343377931` → `SUCCESS(orderId=536944012343377943)`
    - 第 2 单：`reqId=536944016638345228` → `SUCCESS(orderId=536944020933312536)`
    - 重复提交第 2 单 reqId：仍返回成功且 DB 订单总数保持 2

### 1.3 累计限购
- 步骤：
  1) 商家创建秒杀券：`POST /voucher/seckill`（limitType=3, userLimit=5, stock=20）
  2) 管理员预热：`POST /voucher/seckill/preheat/9916`
  3) 用户分 3 次购买：每次先 `GET /voucher-order/req/9916?count=2`，再 `POST /voucher-order/seckill/9916?reqId=<reqId>&count=2`
- 观察点：
  - `tb_user_quota` 的 upsert/累计校验
  - Lua 与消费端二次校验一致
- 结果记录：
  - ✅：
    - 第 1 次（count=2）：`reqId=536944154077298701` → `SUCCESS(orderId=536944158372266010)`
    - 第 2 次（count=2）：`reqId=536944162667233294` → `SUCCESS(orderId=536944162667233307)`
    - 第 3 次（count=2）：`reqId=536944166962200591` → `FAILED(reason=LIMIT)`
    - DB：`tb_user_quota.owned_count=4`，`tb_voucher_order` 对应 voucher=9916 共 2 单、总 count=4

### 1.4 库存一致性校验（DB/Redis）
- ✅：
  - `tb_seckill_voucher.stock`：9914=4，9915=6，9916=16（与成功下单数量一致）
  - `Redis GET seckill:{seckill}:stock:<voucherId>` 与 DB 同步下降（以 Redis 为准，DB 为最终落库校验）

## 2. Feed：推送 + 兜底（refresh/force）
### 2.1 基础推送
- 步骤：
  1) 用户 1032 关注作者 1031：`PUT /follow/1031/true`
  2) 作者 1031 发布笔记：`POST /blog`（返回 blogId）
  3) 用户 1032 拉关注流：`GET /blog/of/follow?lastId=<nowMs>&offset=0`
- 结果记录：
  - ✅：新笔记 `blogId=35` 在关注流返回列表中（index=0）

### 2.2 刷新（Smart Pull）
- 步骤：
  - `GET /blog/of/follow?lastId=<nowMs>&offset=0&refresh=true`
- 观察点：
  - 后端做 DB 增量检查并回填 Redis inbox
- 结果记录：
  - ✅：手工删除 Redis 收件箱最新 blogId 后，`refresh=true` 能补回最新（示例：删 36 后恢复为 `[36,35]`）

### 2.3 强制加载更多（force 回源回填）
- 步骤：
  - `GET /blog/of/follow?lastId=<nowMs>&offset=0&force=true`
- 观察点：
  - 后端执行重建/回填并返回正确列表
- 结果记录：
  - ✅：手工删除 Redis 收件箱“中间/较旧” blogId 且 ZSET 非空时，`force=true` 能重建并补回（示例：仅留 36 时恢复为 `[36,35]`）

### 2.4 Feed 压测（20 粉丝扇出）
- 步骤：
  1) 20 个用户并发关注作者 1031（`PUT /follow/1031/true`）
  2) 作者发布笔记：`POST /blog`（返回 blogId=37）
  3) 抽样检查 5 个粉丝 `ZSCORE feed:<uid> 37` 不为空
- 结果记录：
  - ✅：DB `tb_follow WHERE follow_user_id=1031` 粉丝数=21；抽样 5 个粉丝收件箱均包含 `blogId=37`

## 3. 店铺列表：城市坐标 + 附近模式（GEO）
### 3.1 首页城市选择
- 步骤：
  1) 首页点击「城市」选择杭州/上海
  2) 切换 `按类型` / `附近` 模式
- 观察点：
  - `附近` 模式请求 `/shop/of/type` 携带 `x/y`
- 结果记录：
  - ✅：`GET /shop/of/type?typeId=11&current=1&x=120.15&y=30.28` 返回店铺 `id=23`（含 distance）

### 3.2 管理员一键重建 GEO
- 步骤：
  1) 管理员进入 `/admin/shops`
  2) 点击 `重建 GEO(全量)` 或 `重建 GEO(当前类型)`
- 观察点：
  - 结果返回 `{types, shops}` 或 `{typeId, shops}`
- 结果记录：
  - ✅：`POST /shop/geo/rebuild` 返回 `{types:1, shops:2}`，附近查询结果正常

## 4. 关注列表/作者页/订单页
### 4.1 我的关注
- 步骤：用户进入 `/follows` 查看列表，点击进入作者页
- 结果记录：
  - ✅：`GET /follow/of/me` 返回作者列表（含 `role` 字段）

### 4.2 作者页
- 步骤：在 `/users/:id` 查看其笔记，关注/取关
- 结果记录：
  - ✅：`GET /user/{id}`（需登录）返回用户信息；`GET /blog/of/user?id=<id>` 返回其笔记

### 4.3 订单页
- 步骤：进入 `/orders`，应显示券标题/店铺名/数量/限购类型/状态；可点进店铺
- 结果记录：
  - ✅：`GET /voucher-order/my/detail` 返回券标题/店铺名/数量/限购类型/下单时间等字段（可直接渲染订单卡片）

## 5. 秒杀压测（并发 20 单）
- 场景：一人多单券 `voucherId=9917(stock=50)`，20 个用户并发各抢 1 单。
- 结果：
  - ✅：入口请求 success=20/20；DB `tb_voucher_order WHERE voucher_id=9917` 订单数=20；`tb_seckill_voucher.stock=30`；`Redis stock=30`
