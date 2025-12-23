package com.hmdp.order.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        ensureColumn("tb_voucher_order", "request_id", "ALTER TABLE tb_voucher_order ADD COLUMN request_id VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumn("tb_voucher_order", "count", "ALTER TABLE tb_voucher_order ADD COLUMN `count` INT NOT NULL DEFAULT 1");
        ensureColumn("tb_voucher_order", "limit_type", "ALTER TABLE tb_voucher_order ADD COLUMN limit_type TINYINT DEFAULT 1");
        ensureColumn("tb_voucher_order", "user_limit", "ALTER TABLE tb_voucher_order ADD COLUMN user_limit INT DEFAULT NULL");
        ensureIndex("tb_voucher_order", "uk_request", "ALTER TABLE tb_voucher_order ADD UNIQUE KEY uk_request (request_id)");
        ensureIndex("tb_voucher_order", "idx_user_voucher", "ALTER TABLE tb_voucher_order ADD INDEX idx_user_voucher (user_id, voucher_id)");
        ensureTable();
        ensureOutboxTable();
    }

    private void ensureColumn(String table, String column, String sql) {
        try {
            jdbcTemplate.execute("SELECT `" + column + "` FROM " + table + " LIMIT 0");
        } catch (DataAccessException e) {
            try {
                jdbcTemplate.execute(sql);
                log.info("added column {}.{}", table, column);
            } catch (DataAccessException ex) {
                log.warn("column {}.{} init skipped: {}", table, column, ex.getMessage());
            }
        }
    }

    private void ensureIndex(String table, String indexName, String sql) {
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?", Integer.class, table, indexName);
        } catch (DataAccessException e) {
            try {
                jdbcTemplate.execute(sql);
                log.info("added index {} on {}", indexName, table);
            } catch (DataAccessException ex) {
                log.warn("index {} init skipped: {}", indexName, ex.getMessage());
            }
        }
    }

    private void ensureTable() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS tb_user_quota (" +
                    "user_id BIGINT NOT NULL," +
                    "voucher_id BIGINT NOT NULL," +
                    "owned_count INT DEFAULT 0," +
                    "PRIMARY KEY (user_id, voucher_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (DataAccessException e) {
            log.warn("tb_user_quota init skipped: {}", e.getMessage());
        }
    }

    private void ensureOutboxTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS message_outbox (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT," +
                            "biz_type VARCHAR(64) NOT NULL," +
                            "biz_id VARCHAR(128) NOT NULL," +
                            "exchange_name VARCHAR(128) NOT NULL," +
                            "routing_key VARCHAR(128) NOT NULL," +
                            "payload TEXT NOT NULL," +
                            "status TINYINT NOT NULL DEFAULT 0," +
                            "retry_count INT NOT NULL DEFAULT 0," +
                            "next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (id)," +
                            "UNIQUE KEY uk_biz (biz_type, biz_id)," +
                            "INDEX idx_status_retry (status, next_retry_time)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        } catch (DataAccessException e) {
            log.warn("message_outbox init skipped: {}", e.getMessage());
        }
    }
}
