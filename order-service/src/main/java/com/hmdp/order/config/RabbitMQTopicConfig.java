package com.hmdp.order.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQTopicConfig {
    public static final String QUEUE = "seckillQueue";
    public static final String EXCHANGE = "seckillExchange";
    public static final String ROUTINGKEY = "seckill.#";

    public static final String ORDER_PAY_QUEUE = "order.pay.queue";
    public static final String ORDER_PAY_EXCHANGE = "order.pay.exchange";
    public static final String ORDER_PAY_ROUTING_KEY = "order.pay";

    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";

    public static final String ORDER_CLOSE_QUEUE = "order.close.queue";
    public static final String ORDER_CLOSE_EXCHANGE = "order.close.exchange";
    public static final String ORDER_CLOSE_ROUTING_KEY = "order.close";

    @Value("${hmdp.order.close-delay-ms:1800000}")
    private long closeDelayMs;

    @Bean
    public Queue queue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(topicExchange()).with(ROUTINGKEY);
    }

    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderCloseExchange() {
        return new DirectExchange(ORDER_CLOSE_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderPayExchange() {
        return new DirectExchange(ORDER_PAY_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .withArgument("x-message-ttl", closeDelayMs)
                .withArgument("x-dead-letter-exchange", ORDER_CLOSE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_CLOSE_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderCloseQueue() {
        return new Queue(ORDER_CLOSE_QUEUE, true);
    }

    @Bean
    public Queue orderPayQueue() {
        return new Queue(ORDER_PAY_QUEUE, true);
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue()).to(orderDelayExchange()).with(ORDER_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding orderCloseBinding() {
        return BindingBuilder.bind(orderCloseQueue()).to(orderCloseExchange()).with(ORDER_CLOSE_ROUTING_KEY);
    }

    @Bean
    public Binding orderPayBinding() {
        return BindingBuilder.bind(orderPayQueue()).to(orderPayExchange()).with(ORDER_PAY_ROUTING_KEY);
    }
}
