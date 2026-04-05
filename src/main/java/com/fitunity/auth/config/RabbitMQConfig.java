package com.fitunity.auth.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange:fitunity.events}")
    private String exchangeName;

    @Value("${rabbitmq.queue.auth:auth.subscription.queue}")
    private String authQueueName;

    @Bean
    public TopicExchange subscriptionExchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Queue authSubscriptionQueue() {
        return QueueBuilder.durable(authQueueName)
                .withArgument("x-dead-letter-exchange", "") // discard dead letters
                .build();
    }

    @Bean
    public Binding authQueueBinding() {
        return BindingBuilder.bind(authSubscriptionQueue())
                .to(subscriptionExchange())
                .with("subscription.activated")
                .and(subscriptionExchange())
                .with("subscription.expired")
                .and(subscriptionExchange())
                .with("subscription.cancelled");
    }
}
