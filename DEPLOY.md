# 部署手册（拆分版）

## 部署目标（重要）
本次新增的“订单支付 + 超时关单”依赖第三方回调，因此 **pay-service 必须部署在 `115.190.193.236`**（有固定公网 IP）。如果你本机没有公网 IP，不要把 pay-service 跑在本机。

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
- `pay-service/`：支付服务（当前为 stub，提供 `/pay/query`、`/pay/close`），端口 8090（必须部署在 `115.190.193.236`）。

## 在 115.190.193.236 部署 pay-service（只部署 pay-service）
> 说明：本机无公网 IP 的情况下，只有 pay-service 需要在公网服务器上运行；其余服务都按本文档在本地启动即可。

1) 登录服务器：
```bash
ssh root@115.190.193.236
```

2) 准备运行目录（示例）：
```bash
mkdir -p /opt/hmdp && cd /opt/hmdp
```

3) 拉代码：
```bash
git clone <你的仓库地址> HmdpServer
cd HmdpServer
```
如果你不方便在服务器上拉代码，也可以在本机构建后把 pay-service 的 jar 传上去（示例）：
```bash
# 本机（有源码的机器）
cd HmdpServer
cd pay-service && mvn -DskipTests clean package && cd ..
scp pay-service/target/pay-service-0.0.1-SNAPSHOT.jar root@115.190.193.236:/opt/hmdp/HmdpServer/pay-service/target/
```

4) 开放端口（必须）：`8090/tcp`（pay-service）。

> 不同云厂商/系统的安全组/防火墙命令不同，此处不硬写；你只要确保外网能访问 `http://115.190.193.236:8090/` 即可。

## 数据库初始化（Outbox）
`order-service` 启动时会自动创建 `message_outbox` 表（见 `order-service/src/main/java/com/hmdp/order/config/SchemaInitializer.java`）。
如果你想手动建表，可执行（MySQL 8.0+）：
```sql
CREATE TABLE IF NOT EXISTS message_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT,
  biz_type VARCHAR(64) NOT NULL,
  biz_id VARCHAR(128) NOT NULL,
  exchange_name VARCHAR(128) NOT NULL,
  routing_key VARCHAR(128) NOT NULL,
  payload TEXT NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz (biz_type, biz_id),
  INDEX idx_status_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 构建与启动
统一要求：所有日志输出到仓库根目录 `logs/`（不存在先创建）。
运行环境：Spring Boot 2.3.x 建议使用 Java 8（`java -version` 显示 `1.8`）。

### 0) 初始化日志目录
```bash
mkdir -p logs
```

### 1) hmdp-service（本地 8088）
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
tail -f ../logs/hmdp-service.log
```
停止/重启（按端口）：
```bash
kill $(lsof -ti:8088)
# 然后再次执行 nohup 启动命令
```

### 2) order-service（本地 8083）
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
tail -f ../logs/order-service.log
```
停止：
```bash
kill $(lsof -ti:8083)
```

### 3) relay-service（本地 8084）
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
tail -f ../logs/relay-service.log
```
停止：
```bash
kill $(lsof -ti:8084)
```

### 4) feed-service（本地 8085，异步推送关注流收件箱）
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
tail -f ../logs/feed-service.log
```
停止：
```bash
kill $(lsof -ti:8085)
```

### 5) pay-service（115.190.193.236:8090，支付 stub）
构建：
```bash
cd pay-service
mvn clean package -DskipTests
```
启动：
```bash
nohup java -jar target/pay-service-0.0.1-SNAPSHOT.jar --server.port=8090 > ../logs/pay-service.log 2>&1 & echo $!
```
查看日志：
```bash
tail -f ../logs/pay-service.log
```
停止：
```bash
kill $(lsof -ti:8090)
```

### 6) hmdp-frontend（Web）
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
  - 超时关单：声明 `order.delay.queue`（TTL=`hmdp.order.close-delay-ms`）→ 死信到 `order.close.queue`；监听 `order.close.queue` 调用 pay-service。
- `relay-service/src/main/resources/application.yaml`
  - Redis 集群+密码；RabbitMQ 同上；Publisher confirm 已启用。
  - 额外引入 MySQL 数据源，用于轮询 `message_outbox` 表并投递“延迟关单消息”到 RabbitMQ。
- `feed-service/src/main/resources/application.yaml`
  - 监听 `feed.publish.queue` 进行大 V 批量扇出拆分，`feed.batch.queue` 批量写粉丝收件箱，Redis 集群与其他服务一致。
- `pay-service/src/main/resources/application.yaml`
  - 端口默认 8090；Redis 同集群；`pay.mock.enabled=false`（需要测试时可临时用启动参数打开）。

## 运行时流程（秒杀）
1) 管理员/商家预热：`VoucherService.addSeckillVoucher` 将库存、限购策略写入 DB 与 Redis。
2) 用户抢券：`/voucher-order/seckill/{id}` 调用 Lua，校验库存与限购，写入 Redis Outbox（`seckill:outbox`），返回排队中的订单号。
3) Relay：`relay-service` 多线程 `BRPOPLPUSH` 从 Outbox 搬运至 RabbitMQ（publisher confirm 成功后清理线程私有队列）。
4) Order：`order-service` 消费 `seckillQueue`，按限购类型校验、扣减 `tb_seckill_voucher.stock`，写入 `tb_voucher_order`（`request_id` 幂等）。
5) 【新增】下单落库同事务写入 `message_outbox`（bizType=`ORDER_CLOSE`），由 `relay-service` 的 DB 轮询线程投递到 `order.delay.exchange`。
6) 【新增】RabbitMQ 延迟队列 TTL 到期后死信转发到 `order.close.queue`，`order-service` 监听后调用 `pay-service`：未支付则关单并恢复库存，已支付则补单更新本地状态。

## 全量重启顺序（建议）
本次部署拓扑：`pay-service` 在 `115.190.193.236`，其余服务（`hmdp-service/order-service/relay-service/feed-service`）在本地。

### 1) 115.190.193.236（pay-service）
```bash
# 在 115.190.193.236 上执行
mkdir -p logs
kill $(lsof -ti:8090) 2>/dev/null || true
nohup java -jar pay-service/target/pay-service-0.0.1-SNAPSHOT.jar --server.port=8090 > logs/pay-service.log 2>&1 & echo $!
tail -n 50 logs/pay-service.log
```

### 2) 本地（relay/order/feed/hmdp-service）
```bash
# 在本机执行
mkdir -p logs
kill $(lsof -ti:8084) 2>/dev/null || true
kill $(lsof -ti:8083) 2>/dev/null || true
kill $(lsof -ti:8085) 2>/dev/null || true
kill $(lsof -ti:8088) 2>/dev/null || true

# 1) relay-service（包含 Redis outbox + MySQL outbox 搬运）
nohup java -jar relay-service/target/relay-service-0.0.1-SNAPSHOT.jar --server.port=8084 > logs/relay-service.log 2>&1 & echo $!

# 2) order-service（下单落库 + 延迟关单消费）
nohup java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar --server.port=8083 > logs/order-service.log 2>&1 & echo $!

# 3) feed-service（可选）
nohup java -jar feed-service/target/feed-service-0.0.1-SNAPSHOT.jar --server.port=8085 > logs/feed-service.log 2>&1 & echo $!

# 4) hmdp-service（主服务）
nohup java -jar hmdp-service/target/hm-dianping-0.0.1-SNAPSHOT.jar --server.port=8088 > logs/hmdp-service.log 2>&1 & echo $!

tail -n 50 logs/relay-service.log
tail -n 50 logs/order-service.log
tail -n 50 logs/hmdp-service.log
```

### 3) 快速健康检查
```bash
# pay-service（公网）
curl -s -X POST "http://115.190.193.236:8090/pay/query?orderId=1"

# 本地 hmdp-service
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8088/"
```

## 测试清单（订单支付 + 超时关单）
> 提示：默认关单延迟是 30 分钟（`hmdp.order.close-delay-ms=1800000`）。要快速验收，推荐用启动参数临时改成 60 秒（或 30 秒）并重建 RabbitMQ 队列。

### 0)（可选）把关单延迟临时调到 60 秒
1) 在 RabbitMQ 服务器（115.190.193.236）上删除旧队列（否则会报队列参数不一致）：
```bash
rabbitmqctl delete_queue order.delay.queue
rabbitmqctl delete_queue order.close.queue
```

2) 在本机重启 `order-service`，用启动参数覆盖：
```bash
kill $(lsof -ti:8083) 2>/dev/null || true
nohup java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar --server.port=8083 --hmdp.order.close-delay-ms=60000 > logs/order-service.log 2>&1 & echo $!
tail -n 50 logs/order-service.log
```

### 1) 未支付 → 超时关单（恢复库存）
1) 用前端或接口下单秒杀，拿到 `reqId`：
```bash
curl -X POST "http://127.0.0.1:8088/voucher-order/seckill/<voucherId>" -H "authorization: <token>"
```
2) 轮询查单结果（拿到 `orderId`）：
```bash
curl "http://127.0.0.1:8088/voucher-order/status?reqId=<reqId>" -H "authorization: <token>"
```
3) 确认 MySQL 订单初始状态是未支付：
```sql
SELECT id, voucher_id, status, create_time FROM tb_voucher_order WHERE id = <orderId>;
```
4) 确认 outbox 里有一条关单消息，且 relay 会把它置为已发送：
```sql
SELECT id, biz_type, biz_id, status, retry_count, next_retry_time, create_time, update_time
FROM message_outbox WHERE biz_type = 'ORDER_CLOSE' AND biz_id = '<orderId>';
```
5) 等待超过 TTL（60 秒或 30 分钟），观察：
- `logs/order-service.log` 出现 `OrderTimeoutListener` 处理日志
- MySQL：订单 `status` 变为 `4`（已取消）
- MySQL：`tb_seckill_voucher.stock` 对应券库存回补

### 2) 已支付 → 超时触发补单（不关单）
> 说明：你现在的 pay-service 是 stub，真实支付宝/回调你后续再接；这里只验证“超时三板斧”的逻辑正确性。

1) 启动 pay-service 时临时打开 mock（只建议用于测试环境）：
```bash
# 在 115.190.193.236 上执行
kill $(lsof -ti:8090) 2>/dev/null || true
nohup java -jar pay-service/target/pay-service-0.0.1-SNAPSHOT.jar --server.port=8090 --pay.mock.enabled=true > logs/pay-service.log 2>&1 & echo $!
```

2) 下一个新单，拿到 `orderId` 后，立刻把它标记为“已支付”：
```bash
curl -X POST "http://115.190.193.236:8090/pay/mock/paid?orderId=<orderId>"
```

3) 等待 TTL 到期后，确认：
- MySQL：订单 `status` 变为 `2`（已支付），`pay_time` 不为空
- MySQL：库存不会回补

### 3) 排障点
- outbox 一直 `status=0`：看 `logs/relay-service.log` 是否能连上 MySQL / RabbitMQ，是否有 `SKIP LOCKED` 查询报错。
- 超时消息不触发：确认 `order.delay.queue` / `order.close.queue` 是否存在，是否有消息堆积（可用 `rabbitmqctl list_queues name messages`）。
- 关单失败一直重试：看 `logs/order-service.log`；超过 10 次会放弃（避免死循环），此时需要你修复 pay-service/网络后手动处理这笔订单。

## 缓存同步（店铺/笔记）
- `relay-service` 预留 `CanalSubscriber` 组件：配置 canal-server 地址后订阅 `tb_shop`、`tb_blog` 等表变更，按项目缓存策略刷新 Redis（店铺、笔记内容以 MySQL 为准）。

## RabbitMQ 运维（持久化/清理/导出）
> RabbitMQ 的“消息持久化”需要同时满足：Exchange durable + Queue durable + Message persistent（代码里已显式设置 `delivery-mode=persistent`）。

### 1) 查看队列/交换机（在 MQ 服务器 115.190.193.236 执行）
```bash
rabbitmqctl list_queues name durable messages
rabbitmqctl list_exchanges name type durable
```

### 2) 清空队列消息（不删定义，只清消息）
```bash
rabbitmqctl purge_queue seckillQueue
rabbitmqctl purge_queue feed.publish.queue
rabbitmqctl purge_queue feed.batch.queue
rabbitmqctl purge_queue canal.error.queue
rabbitmqctl purge_queue order.delay.queue
rabbitmqctl purge_queue order.close.queue
```

一次性清空全部（推荐先确认队列名）：
```bash
for q in seckillQueue feed.publish.queue feed.batch.queue canal.error.queue order.delay.queue order.close.queue; do
  rabbitmqctl purge_queue "$q"
done
```

### 3) 导出/导入“定义”（交换机/队列/绑定，不含消息体）
```bash
rabbitmqctl export_definitions /tmp/definitions.json
rabbitmqctl import_definitions /tmp/definitions.json
```

### 4) 备份/清空“持久化数据目录”（包含消息体，需要停机）
数据目录：`/var/lib/rabbitmq/mnesia/rabbit@<hostname>/`

备份（停机）：
```bash
systemctl stop rabbitmq-server
tar -czf /tmp/rabbitmq-mnesia.tar.gz -C /var/lib/rabbitmq/mnesia rabbit@<hostname>
systemctl start rabbitmq-server
```

清空整套持久化（危险：会删除 vhost/users/queues/exchanges/definitions/messages 等全部数据）：
```bash
rabbitmqctl stop_app && rabbitmqctl reset && rabbitmqctl start_app
```

## 账号说明
- 管理员：可在网关/业务层硬编码账号密码后放行预热与审批接口（需在现有服务补充），商家沿用用户短信登录自动注册。

## 日志与排障
- Redis 集群日志：`/var/log/redis/redis-server*.log`
- RabbitMQ：`/var/log/rabbitmq/`
- 服务日志：统一落仓库根目录 `logs/`：
  - `logs/hmdp-service.log`
  - `logs/order-service.log`
  - `logs/relay-service.log`
  - `logs/pay-service.log`
  - `logs/hmdp-frontend.log`（若使用 `npm run dev` 后台启动）
