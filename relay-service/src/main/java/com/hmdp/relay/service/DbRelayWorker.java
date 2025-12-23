package com.hmdp.relay.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class DbRelayWorker {

    private static final int WORKER_THREADS = 4;
    private static final int BATCH_SIZE = 10;
    private static final long IDLE_SLEEP_MS = 200;
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final RabbitTemplate rabbitTemplate;

    private final ExecutorService pool = Executors.newFixedThreadPool(WORKER_THREADS);

    @PostConstruct
    public void start() {
        for (int i = 0; i < WORKER_THREADS; i++) {
            final int idx = i;
            pool.submit(() -> loop(idx));
        }
    }

    private void loop(int idx) {
        while (true) {
            try {
                boolean worked = pollAndSendOnce();
                if (!worked) {
                    TimeUnit.MILLISECONDS.sleep(IDLE_SLEEP_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("db relay worker {} failed", idx, e);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private boolean pollAndSendOnce() {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            List<OutboxRow> messages = jdbcTemplate.query(
                    "SELECT id, exchange_name, routing_key, payload, retry_count " +
                            "FROM message_outbox " +
                            "WHERE status = 0 AND next_retry_time <= NOW() " +
                            "ORDER BY id " +
                            "LIMIT ? FOR UPDATE SKIP LOCKED",
                    (rs, rowNum) -> mapRow(rs),
                    BATCH_SIZE
            );
            if (messages == null || messages.isEmpty()) {
                return false;
            }

            for (OutboxRow msg : messages) {
                try {
                    send(msg);
                    jdbcTemplate.update(
                            "UPDATE message_outbox SET status = 1, update_time = NOW() WHERE id = ?",
                            msg.id
                    );
                } catch (Exception e) {
                    int backoffSeconds = computeBackoffSeconds(msg.retryCount);
                    jdbcTemplate.update(
                            "UPDATE message_outbox " +
                                    "SET retry_count = retry_count + 1, " +
                                    "    next_retry_time = DATE_ADD(NOW(), INTERVAL ? SECOND), " +
                                    "    update_time = NOW() " +
                                    "WHERE id = ?",
                            backoffSeconds,
                            msg.id
                    );
                    log.warn("db relay send failed, msgId={}, nextRetryIn={}s", msg.id, backoffSeconds, e);
                }
            }
            return true;
        }));
    }

    private void send(OutboxRow msg) throws Exception {
        CorrelationData correlationData = new CorrelationData(msg.id + ":" + UUID.randomUUID());
        rabbitTemplate.convertAndSend(msg.exchangeName, msg.routingKey, msg.payload, correlationData);
        boolean ack = correlationData.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS).isAck();
        if (!ack) {
            throw new IllegalStateException("broker negative ack for msgId=" + msg.id);
        }
    }

    private int computeBackoffSeconds(int retryCount) {
        int retry = Math.max(retryCount, 0);
        if (retry <= 0) {
            return 1;
        }
        if (retry == 1) {
            return 2;
        }
        if (retry == 2) {
            return 4;
        }
        if (retry == 3) {
            return 8;
        }
        if (retry == 4) {
            return 16;
        }
        return 30;
    }

    private static OutboxRow mapRow(ResultSet rs) throws SQLException {
        OutboxRow row = new OutboxRow();
        row.id = rs.getLong("id");
        row.exchangeName = rs.getString("exchange_name");
        row.routingKey = rs.getString("routing_key");
        row.payload = rs.getString("payload");
        row.retryCount = rs.getInt("retry_count");
        return row;
    }

    private static class OutboxRow {
        private long id;
        private String exchangeName;
        private String routingKey;
        private String payload;
        private int retryCount;
    }
}
