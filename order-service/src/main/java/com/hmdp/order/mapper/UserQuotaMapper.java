package com.hmdp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.order.entity.UserQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserQuotaMapper extends BaseMapper<UserQuota> {
    @Update("INSERT INTO tb_user_quota (user_id, voucher_id, owned_count) VALUES (#{userId}, #{voucherId}, #{count}) " +
            "ON DUPLICATE KEY UPDATE owned_count = IF(owned_count + #{count} <= #{limit}, owned_count + #{count}, owned_count)")
    int upsertQuota(@Param("userId") Long userId, @Param("voucherId") Long voucherId, @Param("count") int count, @Param("limit") int limit);
}
