package com.hmdp.mapper;

import com.hmdp.dto.VoucherOrderDetailDTO;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
    @Select("SELECT " +
            "  o.id AS id, " +
            "  o.request_id AS requestId, " +
            "  o.voucher_id AS voucherId, " +
            "  o.count AS count, " +
            "  o.limit_type AS limitType, " +
            "  o.user_limit AS userLimit, " +
            "  o.pay_type AS payType, " +
            "  o.status AS status, " +
            "  o.create_time AS createTime, " +
            "  v.title AS voucherTitle, " +
            "  v.type AS voucherType, " +
            "  v.shop_id AS shopId, " +
            "  s.name AS shopName " +
            "FROM tb_voucher_order o " +
            "JOIN tb_voucher v ON o.voucher_id = v.id " +
            "LEFT JOIN tb_shop s ON v.shop_id = s.id " +
            "WHERE o.user_id = #{userId} " +
            "ORDER BY o.create_time DESC " +
            "LIMIT #{offset}, #{size}")
    List<VoucherOrderDetailDTO> queryMyOrderDetails(@Param("userId") Long userId,
                                                    @Param("offset") Integer offset,
                                                    @Param("size") Integer size);
}
