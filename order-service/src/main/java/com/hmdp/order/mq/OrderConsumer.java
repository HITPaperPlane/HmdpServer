package com.hmdp.order.mq;

import com.alibaba.fastjson.JSON;
import com.hmdp.order.config.RabbitMQTopicConfig;
import com.hmdp.order.dto.OrderMessage;
import com.hmdp.order.service.OrderProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderConsumer {

    private final OrderProcessor orderProcessor;

    @RabbitListener(queues = RabbitMQTopicConfig.QUEUE)
    public void receive(String msg) {
        try {
            OrderMessage message = JSON.parseObject(msg, OrderMessage.class);
            orderProcessor.process(message);
        } catch (Exception e) {
            log.error("failed to consume seckill message {}", msg, e);
            throw e;
        }
    }
}
