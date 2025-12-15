package com.hmdp.feed.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQTopicConfig {
    public static final String FEED_EXCHANGE = "feedExchange";
    public static final String FEED_PUBLISH_QUEUE = "feed.publish.queue";
    public static final String FEED_PUBLISH_ROUTING_KEY = "feed.publish";
    public static final String FEED_BATCH_QUEUE = "feed.batch.queue";
    public static final String FEED_BATCH_ROUTING_KEY = "feed.batch";

    @Bean
    public TopicExchange feedExchange() {
        return new TopicExchange(FEED_EXCHANGE, true, false);
    }

    @Bean
    public Queue feedPublishQueue() {
        return new Queue(FEED_PUBLISH_QUEUE, true);
    }

    @Bean
    public Queue feedBatchQueue() {
        return new Queue(FEED_BATCH_QUEUE, true);
    }

    @Bean
    public Binding feedPublishBinding() {
        return BindingBuilder.bind(feedPublishQueue()).to(feedExchange()).with(FEED_PUBLISH_ROUTING_KEY);
    }

    @Bean
    public Binding feedBatchBinding() {
        return BindingBuilder.bind(feedBatchQueue()).to(feedExchange()).with(FEED_BATCH_ROUTING_KEY);
    }
}
