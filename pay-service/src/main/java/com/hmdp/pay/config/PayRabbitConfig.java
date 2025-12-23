package com.hmdp.pay.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PayRabbitConfig {

    public static final String ORDER_PAY_EXCHANGE = "order.pay.exchange";
    public static final String ORDER_PAY_ROUTING_KEY = "order.pay";

    @Bean
    public DirectExchange orderPayExchange() {
        return new DirectExchange(ORDER_PAY_EXCHANGE, true, false);
    }
}

