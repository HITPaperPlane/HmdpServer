# HMDP 前端使用指南（hmdp-frontend 单站点，覆盖用户/商家/管理员）

## 1. 目录与启动
- 前端：`hmdp-frontend/`（Vite + Vue3 + Pinia + Vue Router，纯 Web）
  - 开发：`cd hmdp-frontend && npm install && npm run dev`（默认端口 5173）
  - 预览/构建：`npm run build`，产物在 `dist/`
- 后端：保持 `hmdp-service`(8088) / `order-service`(8083) / `relay-service`(8084) 已启动；nginx 反代可参考 `nginx/hmdp.conf`，将 `/api` 指向 8088。

## 2. 页面导航与身份
- 顶部导航：切换「用户站点 / 商家中心 / 运营后台」，右上角登录/退出。侧边栏根据身份显示对应菜单。
- 登录入口 `/login`：
  - 用户：手机号/邮箱 + `/user/code` + `/user/login`。
  - 商家：邮箱 + `/merchant/code` + `/merchant/login`（首次会补齐商家、角色表）。
  - 管理员：账号 `admin` / 密码 `Admin#123456` 走 `/admin/login`。

## 3. 角色功能覆盖
### 用户（C 端）
- 首页 `/`：店铺分类、搜索、定位附近（`/shop-type/list`、`/shop/of/type`、`/shop/of/name`）。点击店铺即可展开券列表。
- 券/秒杀：展开后用 `/voucher/list/{shopId}` 拉券，点击「秒杀」触发 `/voucher-order/seckill/{id}`，Redis 扣减 `seckill:{seckill}:stock:<id>` 并写入 outbox，order-service 落库 `tb_voucher_order`。
- 订单 `/orders`：`/voucher-order/my?current&size` 查本人订单（DB）。
- 笔记 `/blogs`：热榜 `/blog/hot`、关注流 `/blog/of/follow`，发布 `/blog`（可填店铺 ID），点赞 `/blog/like/{id}`，关注作者 `/follow/{id}/true`。
- 我的 `/profile`：`/user/me` 查看当前登录，签到 `/user/sign`（Redis BitMap），连续签到 `/user/sign/count`，UV `/user/uv` 写入 HyperLogLog，查询 `/user/uv?days=3`。

### 商家（B 端）
- 总览 `/merchant/dashboard`：按类型预览店铺并跳转各子页。
- 店铺管理 `/merchant/shops`：`POST /shop` 创建，`PUT /shop` 更新，`/shop/{id}` 查看，`/shop/of/name` 搜索。更新会淘汰缓存键 `shop:cache:id`。
- 券与秒杀 `/merchant/vouchers`：`POST /voucher` 普通券，`POST /voucher/seckill` 秒杀券（预热 Redis 库存与限购字段），`/voucher/list/{shopId}` 查看。
- 内容/笔记 `/merchant/content`：上传图片 `/upload/blog`，发布 `/blog`（写 DB + 推送关注者 feed），`/blog/of/me` 查看本人。

### 管理员（A 端）
- 指标 `/admin/dashboard`：`POST /user/uv` 写 UV，`/user/uv?days=` 查询 HyperLogLog。
- 店铺巡检 `/admin/shops`：按类型/坐标浏览 `/shop/of/type`，精确编辑 `/shop` POST/PUT。
- 券池管理 `/admin/vouchers`：同商家，可全局为店铺发券/秒杀。
- 笔记巡查 `/admin/blogs`：`/blog/hot` 热榜，`/blog/{id}` 查询单条，`/blog/like/{id}` 点赞/取消，上传/删除图片 `/upload/blog`、`/upload/blog/delete?name=xxx`。

## 4. 经典秒杀验证（前端操作 + 观察点）
1) 管理员或商家登录 → 「券与秒杀」页创建秒杀券（填店铺 ID、库存、时间窗口、限购）。  
   - DB：`tb_voucher` 新增；`tb_seckill_voucher` 新增；Redis 预热 `seckill:{seckill}:stock:<id>`。  
2) 用户登录 → 首页选择对应店铺 → 展开券 → 点击「秒杀」。  
   - Redis：库存原子扣减；一人一单用 `seckill:{seckill}:order:<voucherId>`；Lua 推入 Stream/列表供 relay-service 拉取。  
3) relay-service → RabbitMQ → order-service 处理后，可在 `/orders` 刷新看到订单；DB `tb_voucher_order` 增加，库存扣减。  
4) 关注日志：`logs/hmdp-service.log`（入口校验/写缓存），`logs/relay-service.log`（出站队列），`logs/order-service.log`（落库、幂等）。

## 5. 部署要点
1) 构建：`cd hmdp-frontend && npm install && npm run build`，产物 `dist/`。  
2) nginx：根路径指向 `dist`，`/api` 反代到 `http://127.0.0.1:8088` 并 `rewrite ^/api/?(.*)$ /$1 break;`。  
3) 本地调试直接 `npm run dev` 打开 `http://localhost:5173/`，三个身份都在同一站点切换。  
4) 如需手验数据，可在 Redis 检查 `shop:cache:id:*`、`seckill:{seckill}:stock:*`、`login:token:*`，在 MySQL 查看 `tb_shop`、`tb_voucher`、`tb_voucher_order`、`tb_blog` 等表。
