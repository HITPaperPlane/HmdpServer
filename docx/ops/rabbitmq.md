# RabbitMQ 运维手册（HMDP）

## 1) 基本信息
- 服务器：`115.190.193.236:5672`
- vhost：`/`
- 用户：`paperplane / 123456`
- 数据目录（持久化）：`/var/lib/rabbitmq/mnesia/rabbit@<hostname>/`

> “消息持久化”需要同时满足：Exchange durable + Queue durable + Message persistent。  
> 本项目已在 `relay-service` / `feed-service` 显式配置 `spring.rabbitmq.template.delivery-mode=persistent`。

## 2) 查看资源
```bash
rabbitmqctl list_queues name durable messages
rabbitmqctl list_exchanges name type durable
rabbitmqctl list_bindings source_name destination_name routing_key
```

## 3) 清空消息（不删定义）
```bash
rabbitmqctl purge_queue seckillQueue
rabbitmqctl purge_queue feed.publish.queue
rabbitmqctl purge_queue feed.batch.queue
rabbitmqctl purge_queue canal.error.queue
```

一键清空（推荐先确认队列名）：
```bash
for q in seckillQueue feed.publish.queue feed.batch.queue canal.error.queue; do
  rabbitmqctl purge_queue "$q"
done
```

## 4) 导出/导入“定义”（不含消息体）
```bash
rabbitmqctl export_definitions /tmp/definitions.json
rabbitmqctl import_definitions /tmp/definitions.json
```

## 5) 备份/清空持久化（含消息体，需停机）
备份：
```bash
systemctl stop rabbitmq-server
tar -czf /tmp/rabbitmq-mnesia.tar.gz -C /var/lib/rabbitmq/mnesia rabbit@<hostname>
systemctl start rabbitmq-server
```

清空整套持久化（危险：会删除 vhost/users/queues/exchanges/definitions/messages 等全部数据）：
```bash
rabbitmqctl stop_app && rabbitmqctl reset && rabbitmqctl start_app
```

