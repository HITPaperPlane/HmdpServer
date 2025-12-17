# relay-service 线程模型与职责

## 1) 组件职责
- Outbox 搬运：Redis Outbox → RabbitMQ（削峰填谷 + confirm 确认）
  - 秒杀：`seckill:{seckill}:outbox` → `seckillExchange/seckillQueue`
  - Feed：`feed:{feed}:outbox` → `feedExchange/feed.publish.queue`
- Canal 订阅：监听 binlog（`tb_shop`/`tb_blog`）变更后删除缓存（`cache:shop:*`、`cache:blog:*`）

## 2) 是否同步/异步？多线程还是轮询？
- Outbox 搬运：多线程 + 轮询（阻塞式）
  - `SeckillRelayWorker`：固定线程池（默认 6）
  - `FeedRelayWorker`：固定线程池（默认 4）
  - 线程循环使用 `rightPopAndLeftPush(..., timeout=2s)`：
    - 从公共 outbox 拉到“线程私有队列”
    - 发送到 MQ 并等待 publisher confirm
    - confirm=ACK 后从线程私有队列删除（保证 at-least-once）
- Canal 订阅：单线程 + 轮询
  - `CanalSubscriber`：单线程 loop 连接 canal-server，批量拉取并 ack/rollback

## 3) 关键 Key / Exchange / Queue
- Redis:
  - `seckill:{seckill}:outbox`、`seckill:{seckill}:relay:<n>`
  - `feed:{feed}:outbox`、`feed:{feed}:relay:<n>`
- MQ（示例命名）：
  - 秒杀：`seckillExchange` + `seckillQueue`
  - Feed：`feedExchange` + `feed.publish.queue` / `feed.batch.queue`

