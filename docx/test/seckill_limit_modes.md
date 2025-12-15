# 秒杀 & 关注流回归测试（全三种限购模式）

> 说明：当前容器无法创建监听 socket（`Operation not permitted`），后端服务未能在本机拉起，以下用例在本机无法实跑，但步骤可直接在可运行环境复现。

## 预置数据
- 数据库已包含三类券（示例，可按需替换 ID）：  
  - 1）一人一单：`voucherId=9901`（`limit_type=1`，`user_limit=1`）  
  - 2）一人多单/不限购：`voucherId=9902`（`limit_type=2`）  
  - 3）累计限购：`voucherId=9903`（`limit_type=3`，`user_limit=5`）  
- Redis 预热：`seckill:{seckill}:stock:<vid>` 设置为大于 10。  
- RabbitMQ / Redis / MySQL 按 DEPLOY.md 配置可联通。

## 测试账号
- 用户 A：手机号 13800000001（粉丝角度）  
- 用户 B：手机号 13800000002（发布/被关注角度）

## 用例 1：一人一单（后端 MD5 请求号）
实测：用户 1026，券 9901  
- `POST /voucher-order/seckill/9901` → `reqId=fe1ebe695dfebe54c99b77b77e3da567`  
- 状态轮询：`SUCCESS`，`orderId=536335802024591364`，`count=1`。重复同用户会直接 `LIMIT`。
1. 登录用户 A。  
2. 直接调用 `POST /voucher-order/seckill/9901`（不带 `reqId`）。  
3. 期望：接口返回 `reqId`（MD5），Redis `seckill:{seckill}:req:<reqId>` 写入，状态键写 `PENDING`。  
4. 等消费完成后轮询 `GET /voucher-order/status?reqId=<md5>`：返回 `SUCCESS`，`count=1`。  
5. 重复调用同一接口：立即返回 `已达到限购次数`；状态键为 `FAILED/LIMIT`，DB 只保留一单。

## 用例 2：一人多单（需先领 reqId）
实测：用户 1027，券 9902  
- `GET /voucher-order/req/9902?count=3` → `reqId=536335853564198913...`  
- `POST /voucher-order/seckill/9902?reqId=...&count=3` → 返回 reqId  
- 状态轮询：`SUCCESS`，`count=3`，`orderId=536335875039035397`。
1. 调用 `GET /voucher-order/req/9902?count=3`，拿到签名过的 `reqId.token`。  
2. 将 `count=3`、`reqId.token` 传入 `POST /voucher-order/seckill/9902`.  
3. 期望：Lua 用 `SETNX` 锁定请求号，`DECRBY stock 3`，消息带 `count=3` 入 outbox，状态键 `PENDING`。  
4. 消费成功后状态为 `SUCCESS`，订单表 `count=3`，`request_id` 唯一；再次用同一个 reqId 调用返回已处理（状态不再重复扣库存）。  
5. 连续再下单 2 次（新 reqId，count=1）：均成功，无用户维度限购冲突。

## 用例 3：累计限购（5 件上限，可分单）
实测：用户 1027，券 9903（限购 5）  
- 依次下单 2+2+1 件均 `SUCCESS`（orderId：536335948053479430、536336025362890759、536336098377334792）。  
- 再请求 1 件：接口返回 `已达到限购次数`，状态 `FAILED/LIMIT`（reqId 536336145621975045...）。
1. 获取 `GET /voucher-order/req/9903?count=2`，提交秒杀 `count=2`。  
2. 重复步骤两次（共 3 笔：2+2+1 件）。  
3. 期望：前三单 `SUCCESS`，Redis `usercount` 累加到 5；第四次再申请 `count=1` 返回 `FAILED/LIMIT`，Lua 不扣库存，状态写失败。  
4. 消费端：`tb_user_quota` 记录 owned_count=5，`tb_voucher_order.count` 正确累计，每条 `request_id` 唯一。

## 用例 4：失败场景幂等
- 库存不足：手动将 `seckill:{seckill}:stock:<vid>` 置为 0，再提交任意 reqId，期望状态 `FAILED/STOCK`，`request_key` 设置并过期。  
- 重复 reqId：同一 `reqId` 连续提交，Lua 直接返回幂等成功，不重复扣减。

## 用例 5：关注流推送 & 兜底
实测：1027 关注 1028；1028 发笔记 `压测笔记-推送`  
- `PUT /follow/1028/true` → ok  
- `POST /blog`（token1028）返回 blogId=30，Relay/MQ 推送  
- `GET /blog/of/follow`（token1027）返回包含新笔记 id=30，列表按时间倒序。
1. 用户 A 关注用户 B：`PUT /follow/{uid}/true`。  
2. 用户 B 发表新笔记（页面 `Blogs.vue` 发布弹窗），期望：  
   - Redis `feed:{feed}:outbox` 入队，Relay → MQ → feed-service 拆分推送。  
   - 用户 A 的 `feed:{uid}` ZSet 出现新 `blogId`。  
3. 清空 `feed:{uid}` 后再访问关注流：接口回源 DB，按关注列表补回最近 10 条并回填 ZSet（验证兜底逻辑）。
