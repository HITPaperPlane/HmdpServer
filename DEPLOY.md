# 部署手册（拆分版）

## 环境前置
- MySQL：`jdbc:mysql://123.56.100.212:3306/hmdp`，账号 `root` / `#gmrGMR110202`，表结构已扩展三种限购类型及角色表。
- Redis 集群（三主三从，密码 `123456`）：
  - `123.56.100.212:6379`，`39.97.193.168:6379`，`115.190.193.236:6379`
- RabbitMQ：`115.190.193.236:5672`，用户 `paperplane` / `123456`，vhost `/`。

## 服务目录
- `dianping/`：现有业务（登录、店铺、笔记、秒杀入口等），端口 8081。
- `order-service/`：订单落库与幂等消费，端口 8083。
- `relay-service/`：Redis Outbox → RabbitMQ 搬运，以及后续 Canal 订阅缓存同步，端口 8084。

## 构建与启动
在各目录下执行：
```bash
mvn clean package -DskipTests
java -jar target/<artifact>.jar
```
或使用 `mvn spring-boot:run`。

## 关键配置
- `dianping/src/main/resources/application.yaml`
  - Redis 指向集群；RabbitMQ 指向 `115.190.193.236`；RabbitListener 关闭自动启动（消费交由 order-service）。
- `order-service/src/main/resources/application.yaml`
  - 数据源同上；RabbitMQ 同上；消费队列 `seckillQueue`，QoS=50，幂等基于 `request_id` 唯一键与业务校验。
- `relay-service/src/main/resources/application.yaml`
  - Redis 集群+密码；RabbitMQ 同上；Publisher confirm 已启用。

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
- 服务日志：控制台或各自 `logging.level` 配置，必要时增加 `--logging.file.name`。
