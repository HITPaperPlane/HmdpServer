package com.hmdp.order.mq;

import com.alibaba.fastjson.JSON;
import com.hmdp.order.config.RabbitMQTopicConfig;
import com.hmdp.order.dto.OrderTimeoutMessage;
import com.hmdp.order.dto.pay.PayCloseResult;
import com.hmdp.order.dto.pay.PayStatusResult;
import com.hmdp.order.entity.VoucherOrder;
import com.hmdp.order.service.OrderProcessor;
import com.hmdp.order.service.pay.PayClient;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderTimeoutListener {

    private static final String PROCESSED_KEY_PREFIX = "order:{order}:close:processed:";
    private static final String RETRY_KEY_PREFIX = "order:{order}:close:retry:";
    private static final int MAX_RETRY = 10;

    private final PayClient payClient;
    private final OrderProcessor orderProcessor;
    private final StringRedisTemplate redisTemplate;

    @RabbitListener(queues = RabbitMQTopicConfig.ORDER_CLOSE_QUEUE)
    public void handleTimeout(String msg, Channel channel, Message message) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        Long orderId = parseOrderId(msg);
        if (orderId == null) {
            channel.basicAck(tag, false);
            return;
        }

        String processedKey = PROCESSED_KEY_PREFIX + orderId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(processedKey))) {
            channel.basicAck(tag, false);
            return;
        }

        try {
            VoucherOrder order = orderProcessor.getById(orderId);
            if (order == null || order.getStatus() == null || order.getStatus() != 1) {
                markProcessed(processedKey);
                channel.basicAck(tag, false);
                return;
            }

            PayStatusResult payStatus = payClient.queryPayStatus(orderId);
            if (payStatus != null && payStatus.isSuccess()) {
                orderProcessor.paySuccess(orderId);
                markProcessed(processedKey);
                channel.basicAck(tag, false);
                return;
            }

            PayCloseResult closeResult = payClient.closeTrade(orderId);
            if (closeResult != null && closeResult.isSuccess()) {
                orderProcessor.cancelOrder(orderId);
                markProcessed(processedKey);
                channel.basicAck(tag, false);
                return;
            }
            if (closeResult != null && closeResult.isTradeSuccess()) {
                orderProcessor.paySuccess(orderId);
                markProcessed(processedKey);
                channel.basicAck(tag, false);
                return;
            }

            throw new IllegalStateException("close trade failed, need retry");
        } catch (Exception e) {
            int retries = increaseRetry(orderId);
            log.error("order close failed, orderId={}, retry={}", orderId, retries, e);
            if (retries >= MAX_RETRY) {
                log.error("order close exceeds max retry, ack and give up. orderId={}", orderId);
                channel.basicAck(tag, false);
                return;
            }
            channel.basicNack(tag, false, true);
        }
    }

    private void markProcessed(String processedKey) {
        redisTemplate.opsForValue().set(processedKey, "1", 1, TimeUnit.DAYS);
    }

    private int increaseRetry(Long orderId) {
        Long v = redisTemplate.opsForValue().increment(RETRY_KEY_PREFIX + orderId);
        if (v != null && v == 1L) {
            redisTemplate.expire(RETRY_KEY_PREFIX + orderId, 1, TimeUnit.DAYS);
        }
        return v == null ? 0 : v.intValue();
    }

    private Long parseOrderId(String msg) {
        if (msg == null) {
            return null;
        }
        try {
            OrderTimeoutMessage payload = JSON.parseObject(msg, OrderTimeoutMessage.class);
            if (payload != null && payload.getOrderId() != null) {
                return payload.getOrderId();
            }
        } catch (Exception ignore) {
        }
        try {
            return Long.valueOf(msg.trim());
        } catch (Exception ignore) {
        }
        return null;
    }
}

