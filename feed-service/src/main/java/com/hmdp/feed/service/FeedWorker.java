package com.hmdp.feed.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.feed.config.RabbitMQTopicConfig;
import com.hmdp.feed.constants.RedisKeys;
import com.hmdp.feed.dto.FeedBatchTask;
import com.hmdp.feed.entity.Follow;
import com.hmdp.feed.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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
public class FeedWorker {

    private final FollowMapper followMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQTopicConfig.FEED_BATCH_QUEUE)
    public void consume(String msg, Channel channel, Message message) throws IOException {
        try {
            FeedBatchTask task = JSON.parseObject(msg, FeedBatchTask.class);
            if (task == null || task.getAuthorId() == null || task.getBlogId() == null) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            handleBatch(task);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("failed to consume feed batch {}", msg, e);
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
        }
    }

    private void handleBatch(FeedBatchTask task) {
        List<Follow> followers = followMapper.selectList(new QueryWrapper<Follow>()
                .eq("follow_user_id", task.getAuthorId())
                .last("order by id asc limit " + task.getOffset() + "," + task.getLimit()));
        if (followers == null || followers.isEmpty()) {
            return;
        }
        List<Long> fanIds = followers.stream().map(Follow::getUserId).collect(Collectors.toList());
        long ts = task.getTimestamp() != null ? task.getTimestamp() : Instant.now().toEpochMilli();
        stringRedisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            for (Long fanId : fanIds) {
                String key = RedisKeys.FEED_KEY + fanId;
                connection.zAdd(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        ts,
                        task.getBlogId().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return null;
        });
        for (Long fanId : fanIds) {
            stringRedisTemplate.expire(RedisKeys.FEED_KEY + fanId, 1, TimeUnit.DAYS);
        }
    }
}
