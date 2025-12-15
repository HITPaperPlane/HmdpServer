# 手工测试记录（2025-12-16）

## 1) 秒杀链路（Redis → Relay → MQ → Order）
- 预置数据：
  - MySQL：`tb_voucher`/`tb_seckill_voucher` 新增 `id=9901`（库存 5，限购 1，时间窗口覆盖当前）。
  - Redis 预热：`redis-cli -c -h 123.56.100.212 -a 123456 SET "seckill:{seckill}:stock:9901" 5 EX 86400`，清空 `order/usercount/outbox` 对应键。
  - 登录态：Redis 手工写登录 token `login:token:token_fan`（user_id=1027）。
- 执行：
  - `curl -H "authorization: token_fan" -X POST "http://127.0.0.1:8088/voucher-order/seckill/9901?reqId=REQ_TEST_9901"`
  - 轮询状态：`curl -H "authorization: token_fan" "http://127.0.0.1:8088/voucher-order/status?reqId=REQ_TEST_9901"`
- 结果：
  - 请求返回 `REQ_TEST_9901`，状态查询返回 `{"status":"SUCCESS","orderId":536314009360531457,"voucherId":9901,"userId":1027}`。
  - 再次下单同券返回限购提示，符合一人一单。

## 2) Feed 推送链路（发布 → Redis Outbox → Relay → MQ → Feed-Service → 收件箱）
- 预置数据：
  - MySQL 用户：新增 user 1027（粉丝）、1028（作者）；`tb_user_info` 对应初始化。
  - 关注关系：`tb_follow` 插入 user_id=1027, follow_user_id=1028，并在 Redis 设置 `SADD follows:1027 1028`。
  - 登录态：Redis 写入 `login:token:token_author`（1028）、`login:token:token_fan`（1027）。
- 执行：
  - 发布多篇博客（作者 token）：  
    `curl -H "authorization: token_author" -H "Content-Type: application/json" -X POST -d '{"title":"测试推送笔记N","content":"<p>feed push</p>","images":"","shopId":1}' http://127.0.0.1:8088/blog`
  - 等待 3s 后检查收件箱：`redis-cli -c -h 123.56.100.212 -a 123456 ZRANGE feed:1027 0 -1 WITHSCORES`
  - 通过粉丝接口验证：`curl -H "authorization: token_fan" "http://127.0.0.1:8088/blog/of/follow?lastId=9999999999999&offset=0"`
- 结果：
  - 收件箱出现推送：示例 `28 1765837017065`, `29 1765837152326` 等，表示 blogId+时间戳已入收件箱。
  - 接口返回列表含最新博客（ID 29/28/...），`minTime` 与收件箱一致。
  - 删除收件箱 `DEL feed:1027` 后再次拉取触发 DB 回源回填，接口返回 5 篇博客并回建收件箱，验证“Redis 缺失兜底”逻辑生效。

## 服务进程
- hmdp-service :8088
- order-service:8083
- relay-service:8084
- feed-service :8085

以上命令均在仓库根目录执行，Redis/MQ/MySQL 连接使用仓库默认配置。***
