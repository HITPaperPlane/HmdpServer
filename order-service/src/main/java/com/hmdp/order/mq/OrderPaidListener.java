package com.hmdp.order.mq;

import com.alibaba.fastjson.JSON;
import com.hmdp.order.config.RabbitMQTopicConfig;
import com.hmdp.order.dto.OrderPaidMessage;
import com.hmdp.order.service.OrderProcessor;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderPaidListener {

    private final OrderProcessor orderProcessor;

    @RabbitListener(queues = RabbitMQTopicConfig.ORDER_PAY_QUEUE)
    public void handlePaid(String msg, Channel channel, Message message) throws java.io.IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        OrderPaidMessage payload;
        try {
            payload = JSON.parseObject(msg, OrderPaidMessage.class);
        } catch (Exception e) {
            log.warn("invalid order paid message, ack. msg={}", msg, e);
            channel.basicAck(tag, false);
            return;
        }
        if (payload == null || payload.getOrderId() == null) {
            channel.basicAck(tag, false);
            return;
        }
        try {
            boolean updated = orderProcessor.paySuccess(payload.getOrderId());
            log.info("order paid consumed, orderId={}, updated={}", payload.getOrderId(), updated);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("consume order paid failed, requeue. orderId={}, msg={}", payload.getOrderId(), msg, e);
            channel.basicNack(tag, false, true);
        }
    }
}

