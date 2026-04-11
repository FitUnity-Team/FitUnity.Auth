package com.fitunity.auth.service;

import com.fitunity.auth.dto.SubscriptionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class SubscriptionEventListener {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventListener.class);

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    public SubscriptionEventListener(RedisService redisService, ObjectMapper objectMapper) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "auth.subscription.queue")
    public void handleSubscriptionEvent(String message) {
        try {
            SubscriptionEvent event = objectMapper.readValue(message, SubscriptionEvent.class);
            long ttlSeconds;

            if (event.expiresAt != null) {
                ttlSeconds = Duration.between(LocalDateTime.now(), event.expiresAt).getSeconds();
                if (ttlSeconds < 0) ttlSeconds = 0;
            } else {
                ttlSeconds = Duration.ofDays(30).getSeconds(); // 30 days default
            }

            redisService.setSubscriptionStatus(event.userId, event.status, ttlSeconds);
            log.info("Subscription status updated for user {}: {} (ttl={}s)", event.userId, event.status, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to process subscription event: {}", message, e);
        }
    }
}
