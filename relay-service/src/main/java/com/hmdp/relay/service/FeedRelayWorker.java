package com.hmdp.relay.service;

import com.hmdp.relay.config.RabbitMQTopicConfig;
import com.hmdp.relay.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class FeedRelayWorker {

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @PostConstruct
    public void start() {
        for (int i = 0; i < 4; i++) {
            final String queue = RedisKeys.FEED_RELAY_QUEUE_PREFIX + i;
            requeueDangling(queue);
            final int idx = i;
            pool.submit(() -> pollAndForward(idx));
        }
    }

    private void requeueDangling(String queue) {
        List<String> hanging = redisTemplate.opsForList().range(queue, 0, -1);
        if (hanging == null || hanging.isEmpty()) {
            return;
        }
        for (String payload : hanging) {
            forward(queue, payload);
        }
    }

    private void pollAndForward(int idx) {
        String threadQueue = RedisKeys.FEED_RELAY_QUEUE_PREFIX + idx;
        while (true) {
            try {
                String payload = redisTemplate.opsForList()
                        .rightPopAndLeftPush(RedisKeys.FEED_OUTBOX_KEY, threadQueue, 2, TimeUnit.SECONDS);
                if (payload == null) {
                    continue;
                }
                forward(threadQueue, payload);
            } catch (Exception e) {
                log.error("feed relay worker {} failed", idx, e);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void forward(String threadQueue, String payload) {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbitTemplate.convertAndSend(RabbitMQTopicConfig.FEED_EXCHANGE,
                    RabbitMQTopicConfig.FEED_PUBLISH_ROUTING_KEY,
                    payload,
                    correlation);
            if (correlation.getFuture().get(5, TimeUnit.SECONDS).isAck()) {
                redisTemplate.opsForList().remove(threadQueue, 1, payload);
            } else {
                log.warn("feed relay negative ack for {}", payload);
            }
        } catch (Exception e) {
            log.error("feed relay forward failed for {}", payload, e);
        }
    }
}
