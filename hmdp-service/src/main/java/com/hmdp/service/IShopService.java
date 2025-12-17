package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);

    Result saveShop(Shop shop);

    Result queryMyShops(String name);

    /**
     * 管理端一键重建 GEO 索引（避免 Redis 丢失或历史数据导入导致“附近无店铺”）
     *
     * @param typeId 指定类型（可选）；为空则全量按 typeId 重建
     */
    Result rebuildGeoIndex(Integer typeId);
}
