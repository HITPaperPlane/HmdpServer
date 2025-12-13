package com.hmdp.service.impl;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<String> setListWithExpireScript;

    @PostConstruct
    public void init() {
        setListWithExpireScript = new DefaultRedisScript<>();
        setListWithExpireScript.setScriptText(ResourceUtil.readUtf8Str("lua/setListWithExpire.lua"));
        setListWithExpireScript.setResultType(String.class);
    }
    @Override
    public Result queryList() {
        //先从Redis中查，这里的常量值是固定前缀 + 店铺id
        List<String> shopTypes =
                stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        //如果不为空（查询到了），则转为ShopType类型直接返回
        if (!shopTypes.isEmpty()) {
            List<ShopType> tmp = new ArrayList<>();
            for (String types : shopTypes) {
                ShopType shopType = JSONUtil.toBean(types, ShopType.class);
                tmp.add(shopType);
            }
            return Result.ok(tmp);
        }
        //否则去数据库中查
        List<ShopType> tmp = query().orderByAsc("sort").list();
        if (tmp == null){
            return Result.fail("店铺类型不存在！！");
        }
        //查到了转为json字符串，存入redis
        for (ShopType shopType : tmp) {
            String jsonStr = JSONUtil.toJsonStr(shopType);
            shopTypes.add(jsonStr);
        }

        //使用Lua脚本原子地设置List和过期时间，避免两次命令之间失败导致永不过期
        List<String> scriptArgs = new ArrayList<>();
        scriptArgs.add(String.valueOf(CACHE_SHOP_TYPE_TTL * 60)); // 转换为秒
        scriptArgs.addAll(shopTypes);
        stringRedisTemplate.execute(
            setListWithExpireScript,
            Collections.singletonList(CACHE_SHOP_TYPE_KEY),
            scriptArgs.toArray()
        );

        //最终把查询到的商户分类信息返回给前端
        return Result.ok(tmp);
    }
}
