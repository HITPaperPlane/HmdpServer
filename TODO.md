# TODO（下一阶段：前端体验 + 数据一致性 + 运维可控）

> 说明：本文件记录“尚未做/需要确认/需要补强”的事项，按优先级从高到低。  
> 已完成的核心链路（秒杀三种限购、Feed outbox→MQ→写收件箱、店铺创建者权限、商家券页重构、管理员审核预热秒杀券）不再重复列出。
>
> 2025-12-17：本文件所列项已全部落地；回归/压测记录见 `docx/test/manual_regression_2025-12-17_v2.md`。

---

## P0 - 用户侧体验（必须）

### 1) 我的关注（Followings）页面
- [x] 前端：在用户侧增加“我的关注”入口（建议放在 `Profile.vue` 或侧边栏）
- [x] 前端：新增 `MyFollows.vue`（或 `Followings.vue`）
  - 展示：关注的用户列表（头像、昵称、角色 Tag：商家/普通用户/管理员）
  - 点击某人进入其主页/笔记列表（复用 `GET /blog/of/user?id=<uid>`）
- [x] 后端：补充接口 `GET /follow/of/me`
  - 返回字段建议：`userId/nickName/icon/role/introduce`（一次性返回，避免前端 N+1）
  - 可选：返回 `followTime` 或“最近发帖时间”用于排序

### 2) 作者主页/作者笔记列表
- [x] 前端：新增作者页（例如 `/users/:id`）
  - 头部展示用户信息（`GET /user/{id}`）
  - 下方展示其笔记列表（`GET /blog/of/user?id=<id>&current=1...`）
  - 增加关注/取关按钮（复用 `/follow/{id}/{isFollow}`）

### 3) 发布笔记按钮位置
- [x] 前端：把 `/blogs` 页的“发布笔记”按钮向左移动（布局对齐标题区域，不要贴右边缘）
  - 同时确认移动端下按钮不遮挡内容、不溢出

### 4) 首页城市选择 → 经纬度 → 附近模式（触发 GEO 查询）
- [x] 前端：移除首页写死的“城市：上海”，改为可选城市（下拉/弹窗）
- [x] 前端：建立 `city -> (lon,lat)` 映射（先做静态映射；后续如需更精准再接入地理编码服务）
  - 注意：当前样例店铺坐标更接近杭州（120.x,30.x），若选择上海（121.x,31.x）会导致“附近 5km 内无店铺”
- [x] 前端：在“按类型”加载店铺时，如果存在城市坐标，则请求：
  - `/shop/of/type?typeId=<id>&current=<p>&x=<lon>&y=<lat>`（后端会走 Redis GEO 附近查询分支）
- [x] 前端：增加一个显式开关（建议）
  - `按类型（不按距离）`：不带 `x/y`（保证有数据）
  - `附近（按距离）`：带 `x/y`（按距离排序）
- [x] 后端/运维：补一条“GEO 索引重建”手段（一次性/可重复）
  - 现状：`shop:geo:{typeId}` 只在店铺新建/更新时写入（见 `hmdp-service/.../ShopServiceImpl.saveShop/update`），历史数据导入 DB 后不会自动出现在 GEO
  - 建议：提供管理端接口或脚本，把 `tb_shop` 全量 `GEOADD` 到对应 `shop:geo:{typeId}`，避免“附近模式”空结果/排序异常

### 5) 订单页美化（并提升信息密度）
- [x] 前端：重写 `Orders.vue` UI（卡片化/分组/空态/加载态/错误态）
- [x] 后端/前端（二选一方案）：
  - 方案 A（推荐）：新增 `GET /voucher-order/my/detail` 返回 OrderVO（已 join voucher/shop）
  - 方案 B：前端拿 `/voucher-order/my` 后，批量请求 voucher/shop 信息（需要新增“按 id 批量查券/店铺”的接口，否则会 N+1）
- [x] UI 信息建议：订单号、券标题、店铺名、数量、下单时间、限购类型（1/2/3）、状态（PENDING/SUCCESS/FAILED 的排队状态可选展示）

---

## P0 - 秒杀“审核预热”闭环（必须）

### 6) 秒杀券：商家提交 → 管理员审核预热 → 用户可见/可抢
- [x] 前端：管理员端在券列表中更直观地展示“待预热/已预热”状态（已加按钮，但文案/视觉可再优化）
- [x] 前端：商家端明确提示“秒杀券需审核预热后才会出现在用户端”
- [x] 后端：为“秒杀券状态”定义更清晰枚举/文档（`preheat_status`：0草稿/1预热中/2已预热）
- [x] 文档：在 `FRONTEND_USAGE.md` 保持点击路径与真实行为一致（已更新，但后续 UI 变更要同步）

---

## P1 - Feed 流一致性与读扩散（性能/可用性）

### 7) “刷新” vs “强制加载更多”的差异化语义（需要按 docx/feed流.md 落地）
- [x] 前端：关注流新增两个动作入口
  - `刷新`：拉最新（Smart Pull），用于修复“最新丢失”
  - `强制加载更多`：触发更强的回源/回填，用于修复“中间空洞/Redis 不完整”
- [x] 后端：为 `/blog/of/follow` 增加参数（示例）
  - `?refresh=true`：仅做增量一致性检查（低成本）
  - `?force=true`：允许在 Redis 非空时也触发 DB 回源并回填（高成本）
- [x] 后端：补齐“Redis 不为空但缺数据”的兜底逻辑（目前只有 zset 为空才回源）

### 8) 店铺列表“回源回填/读扩散”（按类型列表缓存补强）
- [x] 现状（已落地）：无 `x/y` 时走 `cache:shop:zset:{typeId}`（ZSET，score=update_time）缓存 Top-N 最新店铺
  - 读取：按 `current` 计算 `start/end`，`ZREVRANGE` 取一页 shopId，再批量查 DB
  - 深分页：start>=200 直接回源 DB（不缓存回填）
- [x] 风险点（需要对照文档/需求确认）：
  - 页序稳定性：回源补齐时“写入列表”的方式可能影响全局顺序，导致不同页读到的内容不稳定
  - 深分页：当前列表缓存只保留 Top-N（例如 200），深页必然回源 DB（可接受但需明确）
  - 并发一致性：店铺更新会把该店铺 ID 提到列表前方，分页视图会变化（是否符合预期需要明确）
- [x] 目标：明确“按类型分页”的一致性语义（稳定分页 vs 近实时分页），并据此选择一种实现
  - 方案 A：只缓存 Top-N 最新（分页不保证稳定），深页直接 DB，不做“补齐回填”
  - 方案 B：改用 ZSET（score=update_time）来做全局分页（稳定、可回填、易维护）
  - 方案 C：按页缓存（`cache:shop:page:{typeId}:{page}`），TTL 短、回源回填更精确

### 9) 联合索引（文档要求）
- [x] DB：按 `docx/feed流.md` 增加索引
  - `tb_blog`：`INDEX idx_user_time (user_id, create_time)`
  - `tb_follow`：建议至少增加
    - `INDEX idx_user_follow (user_id, follow_user_id)`
    - `INDEX idx_follow_user (follow_user_id, user_id)`（用于查粉丝分页）

---

## P1 - 秒杀 reqId 的前端可靠性（体验与可追踪性）

### 10) reqId 存储策略（针对“一人多单/累计限购”）
- [x] 评审：目前 reqId 仅存在 JS 内存，页面刷新会丢，用户无法继续轮询状态
- [x] 前端建议：
  - 用 `sessionStorage` 存“待确认的 reqId”（按 `voucherId` 分桶），设置 TTL（如 30 分钟）
  - 订单确认 `SUCCESS/FAILED` 后清理对应 reqId
- [x] 说明：不建议用 Cookie
  - Cookie 会随请求发送，增加无意义流量且更难做安全隔离（非 HttpOnly Cookie 也不安全）

---

## P1 - RabbitMQ 持久化与运维操作（必须清晰）

### 11) 当前 RabbitMQ 持久化现状（115.190.193.236）
- [x] 已确认（现状记录，不需要再改代码）：
  - RabbitMQ：3.9.27
  - 队列 durable=true：`seckillQueue`、`feed.publish.queue`、`feed.batch.queue`、`canal.error.queue`
  - 交换机 durable=true：`seckillExchange`、`feedExchange`
  - 数据目录：`/var/lib/rabbitmq/mnesia/rabbit@<hostname>/`
- [x] 待补强（建议）：
  - 明确设置 Publisher 的 `delivery_mode=2(PERSISTENT)`（Spring 通常默认是 persistent，但建议显式配置）
    - 方案：配置 `spring.rabbitmq.template.delivery-mode=persistent` 或发消息时显式设置 message properties

### 12) RabbitMQ 导出/清理操作（写进运维文档/脚本）
- [x] 导出“定义”（交换机/队列/绑定）：
  - `rabbitmqctl export_definitions /tmp/definitions.json`
- [x] 导入“定义”：
  - `rabbitmqctl import_definitions /tmp/definitions.json`
- [x] 清空某个队列（不删定义，只清消息）：
  - `rabbitmqctl purge_queue seckillQueue`
  - `rabbitmqctl purge_queue feed.publish.queue`
- [x] 删除队列（会丢定义）：
  - `rabbitmqctl delete_queue seckillQueue`
- [x] 备份“持久化数据目录”（包含消息存储，需停机）：
  - `systemctl stop rabbitmq-server`
  - `tar -czf /tmp/rabbitmq-mnesia.tar.gz -C /var/lib/rabbitmq/mnesia rabbit@<hostname>`
  - `systemctl start rabbitmq-server`
- [x] 清空整套持久化（危险：会删 vhost/users/queues 等）：
  - `rabbitmqctl stop_app && rabbitmqctl reset && rabbitmqctl start_app`

---

## P2 - 数据库外键约束（需要先做数据清洗与字段语义统一）

### 13) 外键（FK）与“0 哨兵值”的冲突
- [x] 现状：部分字段用 `0` 表示“无归属/可选”（例如 `shop.created_by=0`、`blog.shop_id=0`）
- [x] 结论：要加 FK，建议把“可选字段”改成 `NULL`，并做一次数据迁移：
  - 迁移：把历史 `0` 更新为 `NULL`
  - 字段：改为 `NULL` 可空
  - FK：使用 `ON DELETE SET NULL` 或限制删除

### 14) 建议加的 FK（按业务语义）
- [x] `tb_shop.created_by` → `tb_user.id`
- [x] `tb_voucher.shop_id` → `tb_shop.id`
- [x] `tb_seckill_voucher.voucher_id` → `tb_voucher.id`
- [x] `tb_voucher_order.voucher_id` → `tb_voucher.id`
- [x] `tb_blog.user_id` → `tb_user.id`
- [x] `tb_follow.user_id`/`tb_follow.follow_user_id` → `tb_user.id`

---

## P2 - 文档与说明（持续维护）
- [x] 把“刷新 vs 强制加载更多”的设计与接口参数写入 `FRONTEND_USAGE.md`
- [x] 补充一份 `docx/ops/rabbitmq.md`（可选）：记录 MQ 导出/清理/备份操作
- [x] 补充一份 `docx/ops/cache.md`（可选）：梳理“穿透/击穿/雪崩”的现用策略与各业务的选型（Shop/Feed/秒杀）
- [x] 补充一份 `docx/ops/relay.md`（可选）：记录 relay-service 的线程模型（多线程 outbox 搬运 + 单线程 canal 订阅）与关键 Redis Key/Exchange/Queue
