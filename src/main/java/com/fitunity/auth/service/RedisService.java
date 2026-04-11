package com.fitunity.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class RedisService {

    private static final Logger log = LoggerFactory.getLogger(RedisService.class);

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${cookie.ttl.seconds:2592000}")
    private long defaultCookieTtl;

    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // token operations
    public String getRefreshTokenHash(String userId) {
        try {
            return redisTemplate.opsForValue().get("refresh:" + userId);
        } catch (Exception e) {
            log.warn("Redis get failed for key refresh:{}", userId, e);
            return null;
        }
    }

    public void setRefreshTokenHash(String userId, String hash, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set("refresh:" + userId, hash, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set failed for key refresh:{}", userId, e);
        }
    }

    public void deleteRefreshToken(String userId) {
        try {
            redisTemplate.delete("refresh:" + userId);
        } catch (Exception e) {
            log.warn("Redis delete failed for key refresh:{}", userId, e);
        }
    }

    // login attempts
    public long incrementLoginAttempts(String email) {
        try {
            Long count = redisTemplate.opsForValue().increment("login_attempts:" + email);
            redisTemplate.expire("login_attempts:" + email, Duration.ofMinutes(15));
            return count != null ? count : 1;
        } catch (Exception e) {
            log.warn("Redis increment failed for key login_attempts:{}", email, e);
            return 0;
        }
    }

    public long getLoginAttempts(String email) {
        try {
            String val = redisTemplate.opsForValue().get("login_attempts:" + email);
            return val != null ? Long.parseLong(val) : 0;
        } catch (Exception e) {
            log.warn("Redis get failed for key login_attempts:{}", email, e);
            return 0;
        }
    }

    public void clearLoginAttempts(String email) {
        try {
            redisTemplate.delete("login_attempts:" + email);
        } catch (Exception e) {
            log.warn("Redis delete failed for key login_attempts:{}", email, e);
        }
    }

    // sessions
    public void addSession(String userId, String jti) {
        try {
            redisTemplate.opsForSet().add("sessions:" + userId, jti);
        } catch (Exception e) {
            log.warn("Redis SADD failed for sessions:{}", userId, e);
        }
    }

    public void removeSession(String userId, String jti) {
        try {
            redisTemplate.opsForSet().remove("sessions:" + userId, jti);
        } catch (Exception e) {
            log.warn("Redis SREM failed for sessions:{}", userId, e);
        }
    }

    // blacklist
    public void blacklistToken(String jti, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set("blacklist:" + jti, "1", ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set failed for blacklist:{}", jti, e);
        }
    }

    // role override
    public void setRoleOverride(String userId, String role, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set("user_role:" + userId, role, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set failed for user_role:{}", userId, e);
        }
    }

    public String getRoleOverrideFromRedis(String userId) {
        try {
            return redisTemplate.opsForValue().get("user_role:" + userId);
        } catch (Exception e) {
            log.warn("Redis get failed for user_role:{}", userId, e);
            return null;
        }
    }

    // subscription status
    public void setSubscriptionStatus(String userId, String status, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set("user_sub:" + userId, status, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set failed for user_sub:{}, status={}", userId, status, e);
        }
    }

    public String getSubscriptionStatus(String userId) {
        try {
            return redisTemplate.opsForValue().get("user_sub:" + userId);
        } catch (Exception e) {
            log.warn("Redis get failed for user_sub:{}", userId, e);
            return null;
        }
    }
}
