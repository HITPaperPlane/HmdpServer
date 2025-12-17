# 缓存策略说明（Shop / Feed / 秒杀）

## 1) Shop 详情（防穿透）
- Key：`cache:shop:<id>`
- 方案：缓存空值（空字符串）+ 短 TTL，避免穿透反复打 DB
- TTL：
  - 空值：`CACHE_NULL_TTL=2min`
  - 正常：`CACHE_SHOP_TTL=30min`

## 2) ShopType 列表（原子写入 + TTL）
- Key：`cache:shoptype:`
- 结构：Redis List
- 写入：Lua 原子设置 list + expire，避免“写入成功但未设置过期”导致永不过期

## 3) 店铺按类型列表（Top-N ID 缓存）
- Key：`cache:shop:zset:<typeId>`（ZSET，member=shopId，score=update_time 毫秒）
- TTL：`CACHE_SHOP_ZSET_TTL=30min`
- 语义：
  - 只缓存 Top-N（默认 200）最新店铺
  - 深分页（start>=200）直接回源 DB，不做回填
- 维护：
  - 新建/更新店铺会 `ZADD` 触达对应 typeId 的 zset

## 4) 附近店铺 GEO
- Key：`shop:geo:<typeId>`（GEO，member=shopId）
- 写入时机：店铺新建/更新时写入；类型变化会从旧 typeId GEO 移除
- 兜底：管理员可调用 `POST /shop/geo/rebuild` 一键重建（全量或指定 typeId）

## 5) Feed 关注流（推拉结合）
- Inbox（收件箱）Key：`feed:<userId>`（ZSET，member=blogId，score=create_time 毫秒）
- Outbox Key：`feed:{feed}:outbox`（List，只存发布事件）
- 可靠推送链路：API 写 Outbox → relay 搬运至 MQ → feed-service 写入粉丝 Inbox
- 兜底：
  - `refresh=true`：Smart Pull（查 DB 增量，补齐“最新丢失”）
  - `force=true`：强制回源回填（重建/修复“中间空洞”）

## 6) 秒杀（Lua 原子 + DB 幂等）
- 库存：`seckill:{seckill}:stock:<voucherId>`
- 幂等：`seckill:{seckill}:req:<reqId>`（String）
- 状态：`seckill:{seckill}:status:<reqId>`（String，PENDING/SUCCESS/FAILED）
- Outbox：`seckill:{seckill}:outbox`（List）

