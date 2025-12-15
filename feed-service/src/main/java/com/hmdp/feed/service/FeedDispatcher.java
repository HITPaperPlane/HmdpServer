package com.hmdp.feed.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.feed.config.RabbitMQTopicConfig;
import com.hmdp.feed.constants.RedisKeys;
import com.hmdp.feed.dto.FeedBatchTask;
import com.hmdp.feed.dto.FeedPublishMessage;
import com.hmdp.feed.entity.Follow;
import com.hmdp.feed.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class FeedDispatcher {

    private static final int BATCH_SIZE = 1000;
    private static final int DIRECT_THRESHOLD = 5000;

    private final FollowMapper followMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQTopicConfig.FEED_PUBLISH_QUEUE)
    public void dispatch(String msg, Channel channel, Message message) throws IOException {
        try {
            FeedPublishMessage payload = JSON.parseObject(msg, FeedPublishMessage.class);
            if (payload == null || payload.getBlogId() == null || payload.getAuthorId() == null) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            handlePublish(payload);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("failed to dispatch feed publish message {}", msg, e);
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
        }
    }

    private void handlePublish(FeedPublishMessage payload) {
        String lockKey = RedisKeys.FEED_SPLIT_LOCK_KEY + payload.getBlogId();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(locked)) {
            log.info("publish {} already dispatched, skip", payload.getBlogId());
            return;
        }
        long fanCount = followMapper.selectCount(new QueryWrapper<Follow>().eq("follow_user_id", payload.getAuthorId()));
        if (fanCount == 0) {
            return;
        }
        long ts = payload.getTimestamp() != null ? payload.getTimestamp() : Instant.now().toEpochMilli();
        payload.setTimestamp(ts);

        if (fanCount <= DIRECT_THRESHOLD) {
            List<Long> fanIds = fetchFollowers(payload.getAuthorId(), 0, (int) fanCount);
            pushToRedis(fanIds, payload);
            return;
        }

        for (long offset = 0; offset < fanCount; offset += BATCH_SIZE) {
            FeedBatchTask task = new FeedBatchTask()
                    .setAuthorId(payload.getAuthorId())
                    .setBlogId(payload.getBlogId())
                    .setOffset(offset)
                    .setLimit(BATCH_SIZE)
                    .setTimestamp(ts);
            rabbitTemplate.convertAndSend(RabbitMQTopicConfig.FEED_EXCHANGE,
                    RabbitMQTopicConfig.FEED_BATCH_ROUTING_KEY,
                    JSON.toJSONString(task));
        }
    }

    private List<Long> fetchFollowers(Long authorId, long offset, int limit) {
        List<Follow> list = followMapper.selectList(new QueryWrapper<Follow>()
                .eq("follow_user_id", authorId)
                .last("order by id asc limit " + offset + "," + limit));
        return list.stream().map(Follow::getUserId).collect(Collectors.toList());
    }

    private void pushToRedis(List<Long> fanIds, FeedPublishMessage payload) {
        if (fanIds == null || fanIds.isEmpty()) {
            return;
        }
        long ts = payload.getTimestamp() != null ? payload.getTimestamp() : Instant.now().toEpochMilli();
        stringRedisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            for (Long fanId : fanIds) {
                String key = RedisKeys.FEED_KEY + fanId;
                connection.zAdd(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        ts,
                        payload.getBlogId().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return null;
        });
        for (Long fanId : fanIds) {
            String key = RedisKeys.FEED_KEY + fanId;
            stringRedisTemplate.expire(key, 1, TimeUnit.DAYS);
        }
    }
}
