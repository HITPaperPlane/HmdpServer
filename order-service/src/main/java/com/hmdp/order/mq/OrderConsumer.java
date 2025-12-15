package com.hmdp.order.mq;

import com.alibaba.fastjson.JSON;
import com.hmdp.order.config.RabbitMQTopicConfig;
import com.hmdp.order.dto.OrderMessage;
import com.hmdp.order.service.OrderProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderConsumer {

    private final OrderProcessor orderProcessor;

    @RabbitListener(queues = RabbitMQTopicConfig.QUEUE)
    public void receive(String msg, Channel channel, Message message) throws java.io.IOException {
        try {
            OrderMessage payload = JSON.parseObject(msg, OrderMessage.class);
            orderProcessor.process(payload);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("failed to consume seckill message {}", msg, e);
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
        }
    }
}
