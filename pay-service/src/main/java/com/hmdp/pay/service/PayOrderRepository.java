package com.hmdp.pay.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PayOrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public PayOrderInfo findVoucherOrder(Long orderId) {
        String sql = "SELECT " +
                " o.id AS order_id, " +
                " o.user_id AS user_id, " +
                " o.voucher_id AS voucher_id, " +
                " o.count AS count, " +
                " o.status AS status, " +
                " v.title AS voucher_title, " +
                " v.pay_value AS pay_value " +
                "FROM tb_voucher_order o " +
                "JOIN tb_voucher v ON o.voucher_id = v.id " +
                "WHERE o.id = ?";
        List<PayOrderInfo> rows = jdbcTemplate.query(sql, new Object[]{orderId}, (rs, rowNum) -> {
            PayOrderInfo info = new PayOrderInfo();
            info.setOrderId(rs.getLong("order_id"));
            info.setUserId(rs.getLong("user_id"));
            info.setVoucherId(rs.getLong("voucher_id"));
            int cnt = rs.getInt("count");
            info.setCount(rs.wasNull() ? null : cnt);
            int st = rs.getInt("status");
            info.setStatus(rs.wasNull() ? null : st);
            info.setVoucherTitle(rs.getString("voucher_title"));
            long pv = rs.getLong("pay_value");
            info.setPayValue(rs.wasNull() ? null : pv);
            return info;
        });
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }
}

