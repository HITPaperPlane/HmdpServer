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
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

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
                    handleEntries(message.getEntries());
                    connector.ack(batchId);
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

    private void handleEntries(List<CanalEntry.Entry> entries) {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                continue;
            }
            try {
                CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                String tableName = entry.getHeader().getTableName();
                for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                    String id = extractId(rowData.getAfterColumnsList(), rowData.getBeforeColumnsList());
                    if (id == null) {
                        continue;
                    }
                    if ("tb_shop".equalsIgnoreCase(tableName)) {
                        redisTemplate.delete(RedisKeys.CACHE_SHOP_KEY + id);
                    } else if ("tb_blog".equalsIgnoreCase(tableName)) {
                        redisTemplate.delete(RedisKeys.CACHE_BLOG_KEY + id);
                    }
                }
            } catch (Exception e) {
                log.error("parse canal entry failed", e);
            }
        }
    }

    private String extractId(List<CanalEntry.Column> after, List<CanalEntry.Column> before) {
        for (CanalEntry.Column c : after) {
            if (c.getIsKey()) {
                return c.getValue();
            }
        }
        for (CanalEntry.Column c : before) {
            if (c.getIsKey()) {
                return c.getValue();
            }
        }
        return null;
    }
}
