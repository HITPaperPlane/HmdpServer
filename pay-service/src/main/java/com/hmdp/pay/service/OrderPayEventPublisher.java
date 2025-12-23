package com.hmdp.pay.service;

import cn.hutool.json.JSONUtil;
import com.hmdp.pay.config.PayRabbitConfig;
import com.hmdp.pay.dto.OrderPaidMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPayEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishPaid(OrderPaidMessage message) {
        String body = JSONUtil.toJsonStr(message);
        rabbitTemplate.convertAndSend(PayRabbitConfig.ORDER_PAY_EXCHANGE, PayRabbitConfig.ORDER_PAY_ROUTING_KEY, body);
    }
}

