# HMDP 前端使用指南（hmdp-frontend 单站点，覆盖用户/商家/管理员）

## 1. 目录与启动
- 前端：`hmdp-frontend/`（Vite + Vue3 + Pinia + Vue Router，纯 Web）
  - 开发：`cd hmdp-frontend && npm install && npm run dev`（默认端口 5173）
  - 预览/构建：`npm run build`，产物在 `dist/`
- 后端：保持 `hmdp-service`(8088) / `order-service`(8083) / `relay-service`(8084) 已启动；nginx 反代可参考 `nginx/hmdp.conf`，将 `/api` 指向 8088。

## 2. 页面导航与身份
- 顶部导航：切换「用户站点 / 商家中心 / 运营后台」，右上角登录/退出。侧边栏根据身份显示对应菜单。
- 登录入口 `/login`：
  - 用户：邮箱 + `/user/code` + `/user/login`。
  - 商家：邮箱 + `/merchant/code` + `/merchant/login`（首次会补齐商家、角色表）。
  - 管理员：账号 `admin` / 密码 `123456` 走 `/admin/login`。

## 3. 角色功能覆盖
### 用户（C 端）
- 首页 `/`：店铺分类、搜索、城市选择 + 附近模式（`/shop-type/list`、`/shop/of/type`、`/shop/of/name`）。
  - `按类型`：不传 `x/y`，保证能看到该类型店铺列表
  - `附近`：传 `x/y`（来自选中的城市经纬度），后端走 Redis GEO 附近查询分支
- 券/秒杀：展开后用 `/voucher/list/{shopId}` 拉券，点击「秒杀」触发 `/voucher-order/seckill/{id}`，Redis 扣减 `seckill:{seckill}:stock:<id>` 并写入 outbox，order-service 落库 `tb_voucher_order`。
  - 一人多单/累计限购：前端会先调用 `/voucher-order/req/{id}` 获取 `reqId`，并写入 `sessionStorage`（30 分钟 TTL），避免刷新页面后丢失导致无法继续轮询状态。
- 订单 `/orders`：`/voucher-order/my/detail?current&size` 查本人订单（已 join 券/店铺，前端卡片化展示）。
- 笔记 `/blogs`：热榜 `/blog/hot`、关注流 `/blog/of/follow`，发布 `/blog`（可填店铺 ID），点赞 `/blog/like/{id}`，关注作者 `/follow/{id}/true`。
- 我的关注 `/follows`：展示关注列表（`GET /follow/of/me`），点击进入作者主页 `/users/:id` 浏览其笔记。
- 我的 `/profile`：`/user/me` 查看当前登录，签到 `/user/sign`（Redis BitMap），连续签到 `/user/sign/count`，UV `/user/uv` 写入 HyperLogLog，查询 `/user/uv?days=3`。

### 商家（B 端）
- 总览 `/merchant/dashboard`：按类型预览店铺并跳转各子页。
- 店铺管理 `/merchant/shops`：`POST /shop` 创建，`PUT /shop` 更新，`/shop/{id}` 查看，`/shop/of/name` 搜索。更新会淘汰缓存键 `shop:cache:id`。
- 券与秒杀 `/merchant/vouchers`：
  - 选择店铺：`GET /shop/of/me`（只返回当前商家创建的店铺）
  - 管理端券列表：`GET /voucher/list/manage/{shopId}`（包含“待审核预热”的秒杀券）
  - 普通券：`POST /voucher`（普通券无库存概念，创建后用户端立即可见）
  - 秒杀券：`POST /voucher/seckill`（商家创建默认不预热，状态为“待审核预热”）
- 内容/笔记 `/merchant/content`：上传图片 `/upload/blog`，发布 `/blog`（写 DB + 推送关注者 feed），`/blog/of/me` 查看本人。

### 管理员（A 端）
- 指标 `/admin/dashboard`：`POST /user/uv` 写 UV，`/user/uv?days=` 查询 HyperLogLog。
- 店铺巡检 `/admin/shops`：按类型/坐标浏览 `/shop/of/type`，精确编辑 `/shop` POST/PUT；一键重建 GEO：`POST /shop/geo/rebuild`。
- 券池管理 `/admin/vouchers`：
  - 管理端券列表：`GET /voucher/list/manage/{shopId}`
  - 审核并预热秒杀券：`POST /voucher/seckill/preheat/{voucherId}`（写入 Redis 库存键后，用户端才会展示该秒杀券）
  - 管理员直接创建秒杀券：`POST /voucher/seckill`（管理员创建会直接预热）
- 笔记巡查 `/admin/blogs`：`/blog/hot` 热榜，`/blog/{id}` 查询单条，`/blog/like/{id}` 点赞/取消，上传/删除图片 `/upload/blog`、`/upload/blog/delete?name=xxx`。

## 4. 经典秒杀验证（前端操作 + 观察点）
1) 商家登录 → `/merchant/vouchers` 选择自己店铺 → 「秒杀券（待审核）」填写开始/结束时间、库存、限购类型（3 种之一）并提交。  
   - DB：`tb_voucher` 新增；`tb_seckill_voucher` 新增；`preheat_status=0`（待审核预热）。  
2) 管理员登录 → `/admin/vouchers` 选择同一店铺 → 在券列表中找到该秒杀券，点击「审核并预热」。  
   - Redis：写入 `seckill:{seckill}:stock:<voucherId>`；DB：`tb_seckill_voucher.preheat_status=2`。  
3) 用户登录 → 打开该店铺详情页 `/shops/<id>` → 在优惠券列表中能看到刚才的秒杀券 → 点击「秒杀」。  
   - Redis：Lua 原子扣减库存/限购；写入 outbox；前端轮询 `/voucher-order/status`。  
4) relay-service → RabbitMQ → order-service 落库后，用户可在 `/orders` 刷新看到订单。  
5) 普通券验证：商家在「普通券」创建后，用户端立刻可见（无需预热）。

## 5. 手工点击测试流程（你可以照着逐项点）

### 5.1 商家端：选择店铺 + 创建普通券
1) 登录页 `/login` 切到「商家」登录（邮箱验证码）。  
2) 进入 `/merchant/vouchers`：左侧「我的店铺」列表应只显示你创建的店铺；点击其中一家。  
3) 进入「普通券」Tab，填写标题/金额/规则，点击「创建普通券」。  
4) 下方「券列表」应出现刚创建的普通券（Tag：普通券）。

### 5.2 商家端：提交秒杀券（三种限购之一）
1) 仍在 `/merchant/vouchers`，切到「秒杀券（待审核）」Tab。  
2) 必填：标题、库存、开始时间、结束时间。  
3) 选择限购类型：
   - 一人一单：选择「一人一单」，单用户限购会自动锁为 1
   - 一人多单：选择「一人多单」，可多次购买（每次购买需新的 reqId）
   - 累计限购：选择「累计限购」，填写单用户限购阈值（如 5）
4) 点击「提交秒杀券审核」。  
5) 下方「券列表」出现该券，且标识为「秒杀券 + 待审核预热」。

### 5.3 管理员端：审核并预热（让用户端能看到并可抢）
1) 用管理员账号登录（`admin / 123456`）。  
2) 进入 `/admin/vouchers`，搜索并选择同一店铺。  
3) 在券列表里找到刚才商家提交的秒杀券（Tag：待预热），点击「审核并预热」。  
4) 预期：Tag 变为「已预热」。

### 5.4 用户端：确认展示 + 秒杀抢购
1) 切换到用户身份登录。  
2) 打开店铺详情页 `/shops/<shopId>`。  
3) 在「优惠券」列表里应能看到刚才的秒杀券；选择数量（限购类型 2/3 才有数量选择），点击「秒杀」。  
4) 预期：按钮显示排队/成功；订单页 `/orders` 刷新可看到订单。

### 5.5 Feed：刷新 vs 强制加载更多
1) 用户 A 登录后关注用户 B（在 `/blogs` 里点「关注作者」或在作者页 `/users/:id` 点关注）。  
2) 用户 B 发布一条新笔记（`/blogs` → 发布笔记）。  
3) 用户 A 打开 `/blogs` →「关注流」：
  - 点击 `刷新`：触发 `refresh=true` 的 Smart Pull（低成本增量补齐最新丢失）
  - 点击 `强制加载更多`：触发 `force=true` 的回源回填（高成本重建/修复中间空洞）

### 5.6 GEO：附近模式与一键重建
1) 管理员进入 `/admin/shops`，点击 `重建 GEO(全量)` 或 `重建 GEO(当前类型)`。  
2) 用户端首页 `/` 选择城市 → 切换为 `附近` 模式 → 加载店铺（后端走 GEO）。  

## 6. 部署要点
1) 构建：`cd hmdp-frontend && npm install && npm run build`，产物 `dist/`。  
2) nginx：根路径指向 `dist`，`/api` 反代到 `http://127.0.0.1:8088` 并 `rewrite ^/api/?(.*)$ /$1 break;`。  
3) 本地调试直接 `npm run dev` 打开 `http://localhost:5173/`，三个身份都在同一站点切换。  
4) 如需手验数据，可在 Redis 检查 `shop:cache:id:*`、`seckill:{seckill}:stock:*`、`login:token:*`，在 MySQL 查看 `tb_shop`、`tb_voucher`、`tb_voucher_order`、`tb_blog` 等表。
