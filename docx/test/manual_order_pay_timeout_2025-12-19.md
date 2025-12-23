# 手工测试：订单支付 + 超时关单（2025-12-19）

> 说明：本次验证的是“Transactional Outbox + RabbitMQ 延迟队列 + 超时三板斧（查单/关单/补单）”链路。

## 0. 测试环境
- 本地（按 `DEPLOY.md` 启动）：
  - `hmdp-service`：`http://127.0.0.1:8088`
  - `relay-service`：`http://127.0.0.1:8084`
  - `order-service`：`http://127.0.0.1:8083`（本次为了快速验收，用启动参数 `--hmdp.order.close-delay-ms=30000`）
  - `feed-service`：`http://127.0.0.1:8085`（可选）
- 远程（公网服务器）：
  - `pay-service`：`http://115.190.193.236:8090`（本次开启 `--pay.mock.enabled=true`）
- 基础设施：
  - MySQL：`123.56.100.212:3306/hmdp`
  - Redis Cluster：`123.56.100.212/39.97.193.168/115.190.193.236:6379`
  - RabbitMQ：`115.190.193.236:5672`
- 本次使用数据：
  - 店铺：`tb_shop.id=24`
  - 用户：`tb_user.id=1054`（用于下单，避免 `tb_voucher_order.user_id` 外键失败）

## 1. 准备：写入登录 Token（跳过短信/邮箱）
> 说明：`hmdp-service` 从请求头 `authorization` 读 token，并从 Redis `login:token:<token>` 取用户信息。

1) 生成 token（示例）：
```bash
uuidgen # 作为 adminToken
uuidgen # 作为 userToken
```

2) 写入 Redis（示例：`adminToken` 用于发券/预热，`userToken` 用于下单）：
```bash
# adminToken：role=ADMIN（id 可用 0）
redis-cli -c -h 123.56.100.212 -a 123456 HSET login:token:<adminToken> id 0 nickName admin icon "" role ADMIN
redis-cli -c -h 123.56.100.212 -a 123456 EXPIRE login:token:<adminToken> 1800

# userToken：role=USER，id 必须是 tb_user 存在的 id（本次用 1054）
redis-cli -c -h 123.56.100.212 -a 123456 HSET login:token:<userToken> id 1054 nickName testuser icon "" role USER
redis-cli -c -h 123.56.100.212 -a 123456 EXPIRE login:token:<userToken> 1800
```

## 2. 场景 A：未支付 → 超时关单（订单取消 + 回补库存）
### 2.1 创建并预热秒杀券
> 说明：用 `ADMIN` 身份创建秒杀券会自动预热 Redis 库存键（`seckill:{seckill}:stock:<voucherId>`）。

```bash
curl -X POST "http://127.0.0.1:8088/voucher/seckill" \
  -H "Content-Type: application/json" \
  -H "authorization: <adminToken>" \
  -d '{"shopId":24,"title":"未支付超时关单测试券","subTitle":"unpaid-timeout-close","rules":"test","payValue":1,"actualValue":100,"type":1,"stock":1,"beginTime":"2025-12-19T06:08:00","endTime":"2025-12-19T06:40:00","limitType":1,"userLimit":1,"ownerType":0,"status":1}'
```

本次创建结果：
- `voucherId=9921`

### 2.2 下单并等待超时
1) 用户下单（得到 `reqId`）：
```bash
curl -X POST "http://127.0.0.1:8088/voucher-order/seckill/9921" -H "authorization: <userToken>"
```

2) 轮询拿到 `orderId`：
```bash
curl "http://127.0.0.1:8088/voucher-order/status?reqId=<reqId>" -H "authorization: <userToken>"
```

本次下单结果：
- `orderId=537426882631565314`

3) 不做任何支付操作，等待 `> 30s`（本次 `order-service` 的 close-delay=30000ms）。

### 2.3 验证结果（MySQL）
- 订单被取消（`status=4`），且 `pay_time` 为空：
```sql
SELECT id, voucher_id, user_id, status, pay_time, create_time, update_time
FROM tb_voucher_order WHERE id = 537426882631565314;
```

- 库存回补（`stock=1`）：
```sql
SELECT voucher_id, stock FROM tb_seckill_voucher WHERE voucher_id = 9921;
```

- Outbox 被 relay 成功投递（`status=1`）：
```sql
SELECT id, biz_type, biz_id, status, retry_count, create_time, update_time
FROM message_outbox WHERE biz_type = 'ORDER_CLOSE' AND biz_id = '537426882631565314';
```

## 3. 场景 B：已支付 → 超时触发补单（订单变已支付，不回补库存）
### 3.1 创建并预热秒杀券
```bash
curl -X POST "http://127.0.0.1:8088/voucher/seckill" \
  -H "Content-Type: application/json" \
  -H "authorization: <adminToken>" \
  -d '{"shopId":24,"title":"超时补单测试券","subTitle":"paid-before-timeout","rules":"test","payValue":1,"actualValue":100,"type":1,"stock":1,"beginTime":"2025-12-19T06:18:00","endTime":"2025-12-19T06:50:00","limitType":1,"userLimit":1,"ownerType":0,"status":1}'
```

本次创建结果：
- `voucherId=9922`

### 3.2 下单并在超时前标记已支付
1) 用户下单（得到 `reqId`）：
```bash
curl -X POST "http://127.0.0.1:8088/voucher-order/seckill/9922" -H "authorization: <userToken>"
```

2) 轮询拿到 `orderId`：
```bash
curl "http://127.0.0.1:8088/voucher-order/status?reqId=<reqId>" -H "authorization: <userToken>"
```

本次下单结果：
- `orderId=537429210503839747`

3) 在 `> 30s` 超时前，调用 pay-service mock 标记“已支付”：
```bash
curl -X POST "http://115.190.193.236:8090/pay/mock/paid?orderId=537429210503839747"
```

4) 等待 `> 30s`，让 `order.close.queue` 消息触发（listener 会执行“查单 → 补单/关单”逻辑）。

### 3.3 验证结果（MySQL）
- 订单补单为已支付（`status=2`，`pay_time` 非空）：
```sql
SELECT id, voucher_id, user_id, status, pay_time, create_time, update_time
FROM tb_voucher_order WHERE id = 537429210503839747;
```

- 库存不回补（`stock=0`）：
```sql
SELECT voucher_id, stock FROM tb_seckill_voucher WHERE voucher_id = 9922;
```

- Outbox 被 relay 成功投递（`status=1`）：
```sql
SELECT id, biz_type, biz_id, status, retry_count, create_time, update_time
FROM message_outbox WHERE biz_type = 'ORDER_CLOSE' AND biz_id = '537429210503839747';
```

## 4. 结论
- ✅ 未支付：超时后触发关单，订单变更为取消（`status=4`），库存回补。
- ✅ 已支付：超时后触发查单补单，订单变更为已支付（`status=2`），库存不回补。
- ✅ DB Outbox：`message_outbox.status=1`，说明 relay 成功投递延迟消息到 RabbitMQ。
