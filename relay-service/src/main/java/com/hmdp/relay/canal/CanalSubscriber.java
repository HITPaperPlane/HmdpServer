package com.hmdp.relay.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.hmdp.relay.config.CanalProperties;
import com.hmdp.relay.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class CanalSubscriber {

    private final CanalProperties canalProperties;
    private final StringRedisTemplate redisTemplate;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private static final int MAX_RETRY_TIMES = 3;

    @PostConstruct
    public void start() {
        pool.submit(this::consumeLoop);
    }

    private void consumeLoop() {
        CanalConnector connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(canalProperties.getHost(), canalProperties.getPort()),
                canalProperties.getDestination(),
                canalProperties.getUsername(),
                canalProperties.getPassword());
        while (true) {
            try {
                connector.connect();
                connector.subscribe(canalProperties.getSubscribe());
                connector.rollback();
                while (true) {
                    Message message = connector.getWithoutAck(canalProperties.getBatchSize());
                    long batchId = message.getId();
                    if (batchId == -1 || message.getEntries().isEmpty()) {
                        TimeUnit.SECONDS.sleep(1);
                        continue;
                    }
                    boolean success = processBatch(message.getEntries());
                    if (success) {
                        connector.ack(batchId);
                    } else {
                        connector.rollback();
                        TimeUnit.SECONDS.sleep(5);
                    }
                }
            } catch (Exception e) {
                log.error("canal consume failed, retrying", e);
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException ignored) {}
            } finally {
                try {
                    connector.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean processBatch(List<CanalEntry.Entry> entries) {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                continue;
            }
            boolean processed = handleEntryWithRetry(entry);
            if (!processed) {
                return false;
            }
        }
        return true;
    }

    private boolean handleEntryWithRetry(CanalEntry.Entry entry) {
        int retry = 0;
        while (retry < MAX_RETRY_TIMES) {
            try {
                parseAndInvalidateCache(entry);
                return true;
            } catch (Exception e) {
                retry++;
                log.warn("Process canal entry failed, retry {}/{}", retry, MAX_RETRY_TIMES, e);
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return sendToErrorQueue(entry);
    }

    private void parseAndInvalidateCache(CanalEntry.Entry entry) throws Exception {
        CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
        String tableName = entry.getHeader().getTableName();
        CanalEntry.EventType eventType = rowChange.getEventType();
        if (eventType != CanalEntry.EventType.INSERT && eventType != CanalEntry.EventType.UPDATE && eventType != CanalEntry.EventType.DELETE) {
            return;
        }
        for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
            String id = extractId(rowData);
            if (id == null) {
                continue;
            }
            if ("tb_shop".equalsIgnoreCase(tableName)) {
                redisTemplate.delete(RedisKeys.CACHE_SHOP_KEY + id);
            } else if ("tb_blog".equalsIgnoreCase(tableName)) {
                redisTemplate.delete(RedisKeys.CACHE_BLOG_KEY + id);
            }
        }
    }

    private boolean sendToErrorQueue(CanalEntry.Entry entry) {
        try {
            java.util.Map<String, Object> errorMsg = new java.util.HashMap<>();
            errorMsg.put("tableName", entry.getHeader().getTableName());
            errorMsg.put("entryType", entry.getEntryType().name());
            errorMsg.put("logfileName", entry.getHeader().getLogfileName());
            errorMsg.put("logfileOffset", entry.getHeader().getLogfileOffset());
            errorMsg.put("time", System.currentTimeMillis());
            rabbitTemplate.convertAndSend(
                    com.hmdp.relay.config.RabbitMQTopicConfig.EXCHANGE,
                    com.hmdp.relay.config.RabbitMQTopicConfig.CANAL_ERROR_ROUTING_KEY,
                    errorMsg
            );
            log.error("Message processing failed after retries. Sent to DLQ: {}", errorMsg);
            return true;
        } catch (Exception e) {
            log.error("FATAL: Failed to send message to DLQ!", e);
            return false;
        }
    }

    private String extractId(CanalEntry.RowData rowData) {
        List<CanalEntry.Column> columns = rowData.getAfterColumnsList();
        if (columns == null || columns.isEmpty()) {
            columns = rowData.getBeforeColumnsList();
        }
        for (CanalEntry.Column c : columns) {
            if (c.getIsKey()) {
                return c.getValue();
            }
        }
        return null;
    }
}
