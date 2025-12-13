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