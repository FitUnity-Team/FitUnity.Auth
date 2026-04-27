package com.fitunity.auth.service;

import com.fitunity.auth.dto.SubscriptionEvent;
import com.fitunity.auth.service.port.RedisStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Component
public class SubscriptionEventListener {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventListener.class);

    private final RedisStore redisStore;
    private final ObjectMapper objectMapper;

    public SubscriptionEventListener(RedisStore redisStore, ObjectMapper objectMapper) {
        this.redisStore = redisStore;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "auth.subscription.queue")
    public void handleSubscriptionEvent(String message) {
        try {
            SubscriptionEvent event = objectMapper.readValue(message, SubscriptionEvent.class);
            if (event.userId == null || event.userId.isBlank()) {
                log.warn("Ignoring subscription event without userId");
                return;
            }
            long ttlSeconds;

            if (event.expiresAt != null) {
                ttlSeconds = Duration.between(LocalDateTime.now(), event.expiresAt).getSeconds();
                if (ttlSeconds < 0) ttlSeconds = 0;
            } else {
                ttlSeconds = Duration.ofDays(30).getSeconds(); // 30 days default
            }

            String status = event.status == null ? "NONE" : event.status.toUpperCase(Locale.ROOT);
            if (!("ACTIVE".equals(status) || "EXPIREE".equals(status) || "NONE".equals(status))) {
                status = "NONE";
            }

            redisStore.setSubscriptionStatus(event.userId, status, ttlSeconds);
            log.info("Subscription status updated for user {}: {} (ttl={}s)", event.userId, status, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to process subscription event: {}", message, e);
        }
    }
}
