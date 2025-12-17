# 部署手册（拆分版）

## 环境前置
- MySQL：`jdbc:mysql://123.56.100.212:3306/hmdp`，账号 `root` / `#gmrGMR110202`，表结构已扩展三种限购类型及角色表。
- Redis 集群（三主三从，密码 `123456`）：
  - `123.56.100.212:6379`，`39.97.193.168:6379`，`115.190.193.236:6379`
- RabbitMQ：`115.190.193.236:5672`，用户 `paperplane` / `123456`，vhost `/`。
- Node.js：用于构建/启动 `hmdp-frontend`（建议 Node 18+）。

## 服务目录
- `hmdp-service/`：主服务（登录、店铺、笔记、秒杀入口等），端口 8088。
- `order-service/`：订单落库与幂等消费，端口 8083。
- `relay-service/`：Redis Outbox → RabbitMQ 搬运，以及后续 Canal 订阅缓存同步，端口 8084。
- `hmdp-frontend/`：单站点 Web 前端（同一套页面覆盖用户/商家/管理员）。
- `nginx/`：Nginx 反代/静态托管示例配置。

## 构建与启动
统一要求：所有日志输出到仓库根目录 `logs/`（不存在先创建）。

### 0) 初始化日志目录
```bash
mkdir -p logs
```

### 1) hmdp-service（8088）
构建：
```bash
cd hmdp-service
mvn clean package -DskipTests
```
启动（推荐 jar 方式，日志落 `logs/`）：
```bash
nohup java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar --server.port=8088 > ../logs/hmdp-service.log 2>&1 & echo $!
```
查看日志：
```bash
tail -f logs/hmdp-service.log
```
停止/重启（按端口）：
```bash
kill $(lsof -ti:8088)
# 然后再次执行 nohup 启动命令
```

### 2) order-service（8083）
构建：
```bash
cd order-service
mvn clean package -DskipTests
```
启动：
```bash
nohup java -jar target/order-service-0.0.1-SNAPSHOT.jar --server.port=8083 > ../logs/order-service.log 2>&1 & echo $!
```
查看日志：
```bash
tail -f logs/order-service.log
```
停止：
```bash
kill $(lsof -ti:8083)
```

### 3) relay-service（8084）
构建：
```bash
cd relay-service
mvn clean package -DskipTests
```
启动：
```bash
nohup java -jar target/relay-service-0.0.1-SNAPSHOT.jar --server.port=8084 > ../logs/relay-service.log 2>&1 & echo $!
```
查看日志：
```bash
tail -f logs/relay-service.log
```
停止：
```bash
kill $(lsof -ti:8084)
```

### 4) feed-service（8085，异步推送关注流收件箱）
构建：
```bash
cd feed-service
mvn clean package -DskipTests
```
启动：
```bash
nohup java -jar target/feed-service-0.0.1-SNAPSHOT.jar --server.port=8085 > ../logs/feed-service.log 2>&1 & echo $!
```
查看日志：
```bash
tail -f logs/feed-service.log
```
停止：
```bash
kill $(lsof -ti:8085)
```

### 4) hmdp-frontend（Web）
开发启动（可用于服务器上临时联调，日志落 `logs/`）：
```bash
npm --prefix hmdp-frontend install
nohup npm --prefix hmdp-frontend run dev -- --host 0.0.0.0 --port 5173 > logs/hmdp-frontend.log 2>&1 & echo $!
tail -f logs/hmdp-frontend.log
```

生产构建（生成 `hmdp-frontend/dist`）：
```bash
npm --prefix hmdp-frontend install
npm --prefix hmdp-frontend run build
```

#### 4.0) 推荐：由 `hmdp-service:8088` 直接托管前端（无需占用 5173）
> 适用：服务器不方便再开一个前端端口，或 `vite/nginx` 监听端口受限时。

1) 构建前端：
```bash
npm --prefix hmdp-frontend install
npm --prefix hmdp-frontend run build
```

2) 启动 `hmdp-service`（默认会从 `../hmdp-frontend/dist` 提供静态资源；可用 JVM 参数覆盖）：
```bash
nohup java -Dhmdp.frontend-dist-dir="$(pwd)/hmdp-frontend/dist" -jar hmdp-service/target/hm-dianping-0.0.1-SNAPSHOT.jar --server.port=8088 > logs/hmdp-service.log 2>&1 & echo $!
```

3) 访问：
- 前端：`http://127.0.0.1:8088/`
- API：前端统一走 `/api/**`（后端已做前缀转发到原有 controller 路径）

#### 4.1) 前端无法用 Node 监听端口时（推荐）：用 Nginx 在 5173 提供静态站点 + /api 反代
> 场景：某些机器上 `node/vite` 可能出现 `listen EPERM` 无法绑定端口。此时用仓库内 Nginx 配置即可跑通前端联调。

准备（先构建一次前端）：
```bash
npm --prefix hmdp-frontend install
npm --prefix hmdp-frontend run build
```

启动（使用仓库内 `nginx/hmdp-5173.conf`，PID/日志落 `logs/`）：
```bash
mkdir -p logs
nohup nginx -p "$(pwd)" -c nginx/hmdp-5173.conf -g 'pid logs/nginx-5173.pid;' > logs/nginx-5173.log 2>&1 & echo $!
tail -f logs/nginx-5173.log
```

停止：
```bash
nginx -p "$(pwd)" -c nginx/hmdp-5173.conf -g 'pid logs/nginx-5173.pid;' -s stop
```

## Nginx 挂载（静态前端 + /api 反代）
说明：`hmdp-frontend` 是 SPA，`/admin/**`、`/merchant/**` 等都需要回落到 `index.html`。

示例配置（可保存为 `/etc/nginx/conf.d/hmdp.conf`，也可参考并更新仓库内 `nginx/hmdp.conf`）：
```nginx
server {
    listen 80;
    server_name hmdp.local;

    # 前端静态资源（hmdp-frontend/dist）
    location / {
        root /home/gmr/Postgraduate/HMDP/HmdpServer/hmdp-frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    # API 代理 → hmdp-service 8088
    location /api/ {
        proxy_pass http://127.0.0.1:8088/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        rewrite ^/api/(.*)$ /$1 break;
    }

    # 图片访问（头像/笔记图片等，后端已用 Spring 静态映射暴露 /imgs/**）
    location /imgs/ {
        proxy_pass http://127.0.0.1:8088/imgs/;
        proxy_set_header Host $host;
    }

    gzip on;
    gzip_types text/css application/javascript application/json application/xml text/plain image/svg+xml;
}
```

加载/校验/重载（仅记录命令，不在此处执行）：
```bash
sudo nginx -t
sudo nginx -s reload
```

## 关键配置
### 后端
- `hmdp-service/src/main/resources/application.yaml`
  - Redis 指向集群；RabbitMQ 指向 `115.190.193.236`；RabbitListener 关闭自动启动（消费交由 order-service）。
- `order-service/src/main/resources/application.yaml`
  - 数据源同上；RabbitMQ 同上；消费队列 `seckillQueue`，QoS=50，幂等基于 `request_id` 唯一键与业务校验。
- `relay-service/src/main/resources/application.yaml`
  - Redis 集群+密码；RabbitMQ 同上；Publisher confirm 已启用。
- `feed-service/src/main/resources/application.yaml`
  - 监听 `feed.publish.queue` 进行大 V 批量扇出拆分，`feed.batch.queue` 批量写粉丝收件箱，Redis 集群与其他服务一致。

## 运行时流程（秒杀）
1) 管理员/商家预热：`VoucherService.addSeckillVoucher` 将库存、限购策略写入 DB 与 Redis。
2) 用户抢券：`/voucher-order/seckill/{id}` 调用 Lua，校验库存与限购，写入 Redis Outbox（`seckill:outbox`），返回排队中的订单号。
3) Relay：`relay-service` 多线程 `BRPOPLPUSH` 从 Outbox 搬运至 RabbitMQ（publisher confirm 成功后清理线程私有队列）。
4) Order：`order-service` 消费 `seckillQueue`，按限购类型校验、扣减 `tb_seckill_voucher.stock`，写入 `tb_voucher_order`（`request_id` 幂等）。

## 缓存同步（店铺/笔记）
- `relay-service` 预留 `CanalSubscriber` 组件：配置 canal-server 地址后订阅 `tb_shop`、`tb_blog` 等表变更，按项目缓存策略刷新 Redis（店铺、笔记内容以 MySQL 为准）。

## 账号说明
- 管理员：可在网关/业务层硬编码账号密码后放行预热与审批接口（需在现有服务补充），商家沿用用户短信登录自动注册。

## 日志与排障
- Redis 集群日志：`/var/log/redis/redis-server*.log`
- RabbitMQ：`/var/log/rabbitmq/`
- 服务日志：统一落仓库根目录 `logs/`：
  - `logs/hmdp-service.log`
  - `logs/order-service.log`
  - `logs/relay-service.log`
  - `logs/hmdp-frontend.log`（若使用 `npm run dev` 后台启动）
