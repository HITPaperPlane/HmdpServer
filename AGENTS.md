# Repository Guidelines

## Project Structure & Module Organization
- `hmdp-service/` (port 8088): main API for login, shops, blogs, feed, likes, follows, sign-in, UV, geo queries, and seckill entry. Code sits under `src/main/java/com/hmdp`, with configs + SQL/Lua/mapper XML under `src/main/resources`.
- `order-service/` (port 8083): RabbitMQ consumer that re-validates seckill quotas, deducts stock, and writes `tb_voucher_order` using idempotent `request_id`.
- `relay-service/` (port 8084): polls Redis outbox → publishes to RabbitMQ, and subscribes to Canal (shop/blog) to evict caches. Canal endpoints are set in `application.yaml`.
- Shared docs are under `docx/`; each service builds independently with its own `pom.xml`.

## Build, Test, and Development Commands
- `cd <service> && mvn clean package -DskipTests`: build fat JAR to `target/`.
- `cd <service> && mvn test`: run available tests (coverage is light; add more when changing core flows).
- `cd <service> && mvn spring-boot:run` or `java -jar target/<artifact>.jar --server.port=xxxx`: start locally; ensure MySQL/Redis cluster/RabbitMQ endpoints in `application.yaml` are reachable.
- Need a quick smoke? Seed shop/blog data in MySQL, preheat vouchers via admin/merchant API, then call `/voucher-order/seckill/{id}`.

## Coding Style & Naming Conventions
- Java 8, Spring Boot 2.3.12, 4-space indent. Controllers/services/mappers grouped by feature in `com.hmdp.<domain>`.
- Mapper interfaces mirror `resources/mapper/*.xml`; service pairs use `*Service`/`*ServiceImpl`. DTO/VO stay in `dto/` and `vo/`; helpers in `utils/`.
- Redis keys use hash tags for cluster routing (e.g., `seckill:{seckill}:stock:<id>`, `seckill:{seckill}:order:<voucherId>`, outbox list `seckill:{seckill}:outbox`). No distributed locks—favor Lua atomicity + DB/Redis idempotency.
- Keep enums/constants centralized; name queues/exchanges/keys explicitly in code and docs.

## Testing Guidelines
- Place new JUnit 5 tests under `src/test/java` mirroring the package path; name classes `*Tests`.
- For Redis/RabbitMQ cases, isolate keys/queues per test and clean up afterward. Validate end-to-end seckill: Lua enqueues → relay publishes → order-service persists and enforces quotas.
- Before merging, run `mvn test` for touched services and note manual verification steps (e.g., merchant/admin login, voucher preheat, blog like/follow/feed, UV/sign-in, geo shop search).

## Commit & Pull Request Guidelines
- Commit messages: imperative and scoped (`feat: add merchant login`, `fix: adjust seckill outbox key`); mention service if unclear.
- PRs should state which service(s) changed, config/env impacts (DB schema, Redis keys, MQ queues, Canal tables), and repro steps with sample commands. Add payload examples or screenshots for API behavior.
- Keep mapper XML and Java mappers synchronized, document new cache keys or idempotency rules, and highlight any operational actions needed (e.g., preheating vouchers, clearing caches).


- 主服务：tail -f /tmp/hmdp-service.log
- 订单消费：tail -f /tmp/order-service.log（如果此前用 nohup 这样启动）
- relay：tail -f /tmp/relay-service.log

我有三个能用的服务器 39.97.193.168，root用户的密码是#gmrGMR110202， 115.190.193.236，root用户的密码是#gmrGMR110202， 123.56.100.212，root用户的密码是#gmrGMR110202

## Runtime Infra (confirmed in application.yaml)
- MySQL: `jdbc:mysql://123.56.100.212:3306/hmdp?useSSL=false&serverTimezone=UTC`, user `root`, password `#gmrGMR110202`.
- Redis cluster: nodes `123.56.100.212:6379`, `39.97.193.168:6379`, `115.190.193.236:6379`, password `123456`.
- RabbitMQ: host `115.190.193.236`, port `5672`, vhost `/`, user `paperplane`, password `123456` (hmdp-service publisher, order-service consumer, relay publisher).
- Canal for cache eviction (relay-service): host `123.56.100.212`, port `11111`, destination `example`, subscribes `hmdp.tb_shop` and `hmdp.tb_blog`.

## 支付宝配置：
- 商家信息
  - 商户账号iwfpdc1019@sandbox.com
  - 登录密码111111
- 买家信息
  - 买家账号fcnkds7206@sandbox.com
  - 登录密码111111
  - 支付密码111111
- 应用公钥：MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh+R74lb9OSEa2UtyXHMzcwLzpQfPECkbggQZ/o6MsowV7m8P+kYLVnv54VPFZ53CE4YX8Wuy3NiLGTBRf3Q5WAAsVWfqdWJEetm0Hr4iD6ZunrqhoapOnrUpLLkwrkiHLcBV2tWPYHCxQqUdNhd4b+0D+eiT7iLuJQX9zQMqWFVbFATP6j3pAxaVNt0DPngr/ONyeKuSCU8qj0F6Ut/Bcdald0EmYJDaK50SafgsYMtND6PSUQuhoBorgSyjP3o3aJEMt/+uc4sfB5kszJ9pu9X6WO/qWbqTlZ6KJFenBu3Gk0TIsjQQo9yBsMNu86Sn1IZ1r99uvjqNtkmaY9l+WwIDAQAB
- 支付宝公钥：MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAisYgKJjkFOl4+PjCdJfKV1n0Ks9zEu8WueX/GkT72GYD3el7PtsxrKhNQWK/IcspZWKcdqtrRt4OBv0nu29Rg0CuHdS/ZWz/75RoC4+mQc6R/Lr7zQwXX1HaQckgTw1FjttjFrhwWB5v5jpQSn+nPRpd7wsMSQN7FRqxFjekoplxbYZooBFHsHlOE9kjjHZRgAPBwRfwblfcr6f7QOwcKWOIARc5JZsjGx9Uj8vvbmSCjvX+EXCS2lh5Lq2WyrRB6gHvZ2oxyy7AzbAUkmgrATmkxovGVMvzAOkzMjieECG8fVXvdInf5mmLFcgh6T7W9/YGhsOIXW0dLNdia1f9TQIDAQAB
- APPID: 9021000143680387