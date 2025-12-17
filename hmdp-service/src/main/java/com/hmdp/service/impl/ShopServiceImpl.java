package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //这里需要声明一个线程池，因为下面缓存击穿问题，我们需要新建一个线程来完成重构缓存
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private static final int SHOP_TYPE_CACHE_MAX = 200;
    @Override
    public Result queryById(Long id) {
        //解决缓存穿透的代码逻辑
        Shop shop = querywithchuantou(id);
        //利用互斥锁解决缓存击穿的代码逻辑
//        Shop shop = querywithjichuan_mutex(id);
//       Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在！！");
        }
        return Result.ok(shop);
    }

    //解决缓存穿透的代码
    public Shop querywithchuantou(Long id) {
        //先从Redis中查，这里的常量值是固定的前缀 + 店铺id
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //如果不为空（查询到了），则转为Shop类型直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //如果这个数据不存在，将这个数据写入到Redis中，并且将value设置为空字符串，然后设置一个较短的TTL，返回错误信息。
        // 当再次发起查询时，先去Redis中判断value是否为空字符串，如果是空字符串，则说明是刚刚我们存的不存在的数据，直接返回错误信息

        //如果查询到的是空字符串，则说明是我们缓存的空数据
        if (shopJson!=null) {
            return  null;
        }

        //否则去数据库中查
        Shop shop = getById(id);

        //查不到，则将空字符串写入Redis
        if (shop == null) {
            //这里的常量值是2分钟
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        //查到了则转为json字符串
        String jsonStr = JSONUtil.toJsonStr(shop);
        //并存入redis,并设置TTL，防止存了错的缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //最终把查询到的商户信息返回给前端
        return shop;
    }

    //互斥锁解决缓存击穿
    public Shop querywithjichuan_mutex(Long id) {
        //先从Redis中查，这里的常量值是固定的前缀 + 店铺id
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //如果不为空（查询到了），则转为Shop类型直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //如果查询到的是空字符串“”，则说明是我们缓存的空数据
        if (shopJson!=null) {
            return  null;
        }

        //实现在高并发的情况下缓存重建
        Shop shop = null;
        try {
            //1.获取互斥锁
            boolean flag = tryLock(LOCK_SHOP_KEY + id);
//        2.失败，则休眠并重试
            while (!flag) {
                Thread.sleep(50);
                return querywithjichuan_mutex(id);
            }
            //3.获取成功->读取数据库，重建缓存
            //查不到，则将空值写入Redis
            shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //查到了则转为json字符串
            String jsonStr = JSONUtil.toJsonStr(shop);
            //并存入redis，设置TTL
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            //最终把查询到的商户信息返回给前端
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(LOCK_SHOP_KEY + id);
        }
        return shop;
    }

    //逻辑过期解决缓存击穿
    public Shop queryWithLogicalExpire(Long id) {
        //1. 从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2. 如果未命中，则返回空
        if (StrUtil.isBlank(json)) {
            return null;
        }
        //3. 命中，将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //3.1 将data转为Shop对象
        JSONObject shopJson = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        //3.2 获取过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //4. 判断是否过期
        if (LocalDateTime.now().isBefore(expireTime)) {
            //5. 未过期，直接返回商铺信息
            return shop;
        }
        //6. 过期，尝试获取互斥锁
        boolean flag = tryLock(LOCK_SHOP_KEY + id);
        //7. 获取到了锁
        if (flag) {
            //8. 开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);//此处的expirSeconds应该为物品的活动时间,设置为20只为测试
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(LOCK_SHOP_KEY + id);
                }
            });
            //9. 直接返回商铺信息
            return shop;
        }
        //10. 未获取到锁，直接返回商铺信息
        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null){
            return Result.fail("店铺id不能为空！！");
        }
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.fail("未登录，无法修改店铺");
        }
        Shop dbShop = getById(shop.getId());
        if (dbShop == null) {
            return Result.fail("店铺不存在！！");
        }
        boolean isOwner = Objects.equals(dbShop.getCreatedBy(), current.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(StrUtil.blankToDefault(current.getRole(), ""));
        if (!isOwner && !isAdmin) {
            return Result.fail("无权限修改该店铺");
        }

        Long oldTypeId = dbShop.getTypeId();
        // 避免部分字段未传导致覆盖，为关键字段兜底
        if (shop.getTypeId() == null) {
            shop.setTypeId(oldTypeId);
        }
        if (shop.getX() == null) {
            shop.setX(dbShop.getX());
        }
        if (shop.getY() == null) {
            shop.setY(dbShop.getY());
        }
        // 保持创建人不变
        shop.setCreatedBy(dbShop.getCreatedBy());

        // 先修改数据库
        updateById(shop);

        // 更新地理索引
        updateGeoIndex(dbShop, shop);

        // 维护类型列表缓存
        if (!Objects.equals(oldTypeId, shop.getTypeId())) {
            removeShopFromTypeZset(oldTypeId, shop.getId());
        }
        touchShopToTypeZset(shop.getTypeId(), shop.getId(), System.currentTimeMillis());

        // 删详情缓存，等待下次读取重建
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    private void updateGeoIndex(Shop oldShop, Shop newShop) {
        Long oldTypeId = oldShop.getTypeId();
        Long newTypeId = newShop.getTypeId();
        Double newX = newShop.getX();
        Double newY = newShop.getY();
        if (!Objects.equals(oldTypeId, newTypeId) && oldTypeId != null) {
            stringRedisTemplate.opsForGeo().remove(SHOP_GEO_KEY + oldTypeId, oldShop.getId().toString());
        }
        if (newTypeId != null && newX != null && newY != null) {
            stringRedisTemplate.opsForGeo().add(
                    SHOP_GEO_KEY + newTypeId,
                    new org.springframework.data.geo.Point(newX, newY),
                    newShop.getId().toString()
            );
        }
    }

    private List<Shop> getShopsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        String idsStr = StrUtil.join(",", ids);
        return query().in("id", ids)
                .last("ORDER BY FIELD( id," + idsStr + ")")
                .list();
    }

    private String typeZsetKey(Long typeId) {
        return CACHE_SHOP_ZSET_KEY + typeId;
    }

    private long shopUpdateScore(Shop shop) {
        java.time.LocalDateTime t = shop == null ? null : shop.getUpdateTime();
        if (t == null && shop != null) {
            t = shop.getCreateTime();
        }
        if (t == null) {
            return System.currentTimeMillis();
        }
        return t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void touchShopToTypeZset(Long typeId, Long shopId, Long scoreMillis) {
        if (typeId == null || shopId == null) {
            return;
        }
        String key = typeZsetKey(typeId);
        double score = scoreMillis == null ? System.currentTimeMillis() : scoreMillis.doubleValue();
        stringRedisTemplate.opsForZSet().add(key, shopId.toString(), score);
        stringRedisTemplate.expire(key, CACHE_SHOP_ZSET_TTL, TimeUnit.MINUTES);
    }

    private void removeShopFromTypeZset(Long typeId, Long shopId) {
        if (typeId == null || shopId == null) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(typeZsetKey(typeId), shopId.toString());
    }

    private void rebuildTypeZset(Long typeId) {
        if (typeId == null) {
            return;
        }
        List<Shop> top = query()
                .eq("type_id", typeId)
                .orderByDesc("update_time")
                .last("LIMIT " + SHOP_TYPE_CACHE_MAX)
                .list();
        String key = typeZsetKey(typeId);
        stringRedisTemplate.delete(key);
        if (top == null || top.isEmpty()) {
            return;
        }
        for (Shop s : top) {
            if (s == null || s.getId() == null) {
                continue;
            }
            stringRedisTemplate.opsForZSet().add(key, s.getId().toString(), shopUpdateScore(s));
        }
        stringRedisTemplate.expire(key, CACHE_SHOP_ZSET_TTL, TimeUnit.MINUTES);
    }

    private void cacheShopDetail(Shop shop) {
        if (shop == null || shop.getId() == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + shop.getId(),
                JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

    //下面用来解决热点高并发访问中的缓存击穿问题

    //获取锁的代码逻辑
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //避免返回值为null，我们这里使用了BooleanUtil工具类
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    //-------------------------------------------------------------
    //逻辑过期实现缓存击穿问题->热点问题的数据预热
    public void saveShop2Redis(Long id, Long expirSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200); //模拟上面取数据的时间

        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expirSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result saveShop(Shop shop) {
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.fail("未登录，无法新建店铺");
        }
        shop.setCreatedBy(current.getId());
        // 1. Save to database
        save(shop);
        // 2. Add to Redis Geohash index
        if (shop.getTypeId() != null && shop.getX() != null && shop.getY() != null) {
            stringRedisTemplate.opsForGeo().add(
                SHOP_GEO_KEY + shop.getTypeId(),
                new org.springframework.data.geo.Point(shop.getX(), shop.getY()),
                shop.getId().toString()
            );
        }
        // 3. 写入类型列表 ZSET（按更新时间排序）
        touchShopToTypeZset(shop.getTypeId(), shop.getId(), System.currentTimeMillis());
        // 4. 预热详情缓存，减少首次查询延迟
        cacheShopDetail(shop);
        // 3. Return shop id
        return Result.ok(shop.getId());
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1. 判断是否需要根据距离查询
        if (x == null || y == null) {
            int pageSize = SystemConstants.DEFAULT_PAGE_SIZE;
            int page = (current == null || current < 1) ? 1 : current;
            int start = (page - 1) * pageSize;
            int end = start + pageSize - 1;
            // 深分页直接回源 DB（只缓存 Top-N 最新店铺）
            if (start >= SHOP_TYPE_CACHE_MAX) {
                List<Shop> dbPage = query().eq("type_id", typeId)
                        .orderByDesc("update_time")
                        .last("LIMIT " + start + "," + pageSize)
                        .list();
                dbPage.forEach(this::cacheShopDetail);
                return Result.ok(dbPage);
            }

            Long t = typeId == null ? null : typeId.longValue();
            String zsetKey = CACHE_SHOP_ZSET_KEY + typeId;
            Long cachedSize = stringRedisTemplate.opsForZSet().zCard(zsetKey);
            if (cachedSize == null || cachedSize < (end + 1)) {
                rebuildTypeZset(t);
            }
            Set<String> cachedIds = stringRedisTemplate.opsForZSet().reverseRange(zsetKey, start, end);
            List<Long> ids = cachedIds == null ? Collections.emptyList()
                    : cachedIds.stream().filter(StrUtil::isNotBlank).map(Long::valueOf).collect(Collectors.toList());
            List<Shop> shops = getShopsByIds(ids);
            if (shops.isEmpty()) {
                // 缓存缺失时兜底回源
                List<Shop> dbPage = query().eq("type_id", typeId)
                        .orderByDesc("update_time")
                        .last("LIMIT " + start + "," + pageSize)
                        .list();
                dbPage.forEach(this::cacheShopDetail);
                return Result.ok(dbPage);
            }
            shops.forEach(this::cacheShopDetail);
            return Result.ok(shops);
        }
//        以下是需要根据距离查询

        //2. 计算分页查询参数
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
        int end = current * SystemConstants.MAX_PAGE_SIZE;


        String key = SHOP_GEO_KEY + typeId;
        //3. 查询redis、按照距离排序、分页; 结果：shopId、distance
        //GEOSEARCH key FROMLONLAT x y BYRADIUS 5000 m WITHDIST
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        if (results == null) {
            // 距离内无结果，回退到普通分页
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        //4. 解析出id
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();

        if (list == null || list.isEmpty()) {
            // GEO 索引缺失或附近无店铺，回退到普通分页（保证页面有数据）
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        if (list.size() < from) {
            //起始查询位置大于数据总量，则说明没数据了，返回空集合
            // 距离内无结果，回退到普通分页
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        ArrayList<Long> ids = new ArrayList<>(list.size());
        HashMap<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }


        //5. 根据id查询shop
        String idsStr = StrUtil.join(",", ids);

        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD( id," + idsStr + ")").list();
        for (Shop shop : shops) {
            //设置shop的举例属性，从distanceMap中根据shopId查询
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //6. 返回
        return Result.ok(shops);
    }

    @Override
    public Result queryMyShops(String name) {
        UserDTO current = UserHolder.getUser();
        if (current == null || current.getId() == null) {
            return Result.fail("未登录");
        }
        List<Shop> shops = query()
                .eq("created_by", current.getId())
                .like(StrUtil.isNotBlank(name), "name", name)
                .orderByDesc("update_time")
                .last("LIMIT 50")
                .list();
        return Result.ok(shops);
    }

    @Override
    @Transactional
    public Result rebuildGeoIndex(Integer typeId) {
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.fail("未登录");
        }
        boolean isAdmin = "ADMIN".equalsIgnoreCase(StrUtil.blankToDefault(current.getRole(), ""));
        if (!isAdmin) {
            return Result.fail("无权限执行该操作");
        }

        List<Shop> shops;
        if (typeId != null) {
            shops = query()
                    .eq("type_id", typeId)
                    .isNotNull("x")
                    .isNotNull("y")
                    .list();
            String geoKey = SHOP_GEO_KEY + typeId;
            stringRedisTemplate.delete(geoKey);
            int added = 0;
            if (shops != null) {
                for (Shop s : shops) {
                    if (s == null || s.getId() == null || s.getX() == null || s.getY() == null) {
                        continue;
                    }
                    stringRedisTemplate.opsForGeo().add(
                            geoKey,
                            new org.springframework.data.geo.Point(s.getX(), s.getY()),
                            s.getId().toString()
                    );
                    added++;
                }
            }
            java.util.Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("typeId", typeId);
            resp.put("shops", added);
            return Result.ok(resp);
        }

        shops = query()
                .isNotNull("type_id")
                .isNotNull("x")
                .isNotNull("y")
                .list();
        if (shops == null || shops.isEmpty()) {
            return Result.ok(java.util.Collections.singletonMap("shops", 0));
        }
        java.util.Map<Long, List<Shop>> byType = shops.stream()
                .filter(s -> s != null && s.getTypeId() != null)
                .collect(Collectors.groupingBy(Shop::getTypeId));
        int total = 0;
        for (java.util.Map.Entry<Long, List<Shop>> entry : byType.entrySet()) {
            Long t = entry.getKey();
            String geoKey = SHOP_GEO_KEY + t;
            stringRedisTemplate.delete(geoKey);
            for (Shop s : entry.getValue()) {
                if (s.getId() == null || s.getX() == null || s.getY() == null) {
                    continue;
                }
                stringRedisTemplate.opsForGeo().add(
                        geoKey,
                        new org.springframework.data.geo.Point(s.getX(), s.getY()),
                        s.getId().toString()
                );
                total++;
            }
        }
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("types", byType.size());
        resp.put("shops", total);
        return Result.ok(resp);
    }
}
