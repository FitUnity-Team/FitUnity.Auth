package com.fitunity.auth.service;

import com.fitunity.auth.service.port.RedisStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class RedisService implements RedisStore {

    private static final Logger log = LoggerFactory.getLogger(RedisService.class);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // token operations
    @Override
    public String getRefreshTokenHash(String userId) {
        try {
            return redisTemplate.opsForValue().get("refresh:" + userId);
        } catch (Exception e) {
            log.warn("Redis get failed for key refresh:{}", userId, e);
            return null;
        }
    }

    @Override
    public void setRefreshTokenHash(String userId, String hash, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set("refresh:" + userId, hash, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set failed for key refresh:{}", userId, e);
        }
    }

    @Override
    public void deleteRefreshToken(String userId) {
        try {
            redisTemplate.delete("refresh:" + userId);
        } catch (Exception e) {
            log.warn("Redis delete failed for key refresh:{}", userId, e);
        }
    }

    // login attempts
    @Override
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

    @Override
    public long getLoginAttempts(String email) {
        try {
            String val = redisTemplate.opsForValue().get("login_attempts:" + email);
            return val != null ? Long.parseLong(val) : 0;
        } catch (Exception e) {
            log.warn("Redis get failed for key login_attempts:{}", email, e);
            return 0;
        }
    }

    @Override
    public void clearLoginAttempts(String email) {
        try {
            redisTemplate.delete("login_attempts:" + email);
        } catch (Exception e) {
            log.warn("Redis delete failed for key login_attempts:{}", email, e);
        }
    }

    // sessions
    @Override
    public void addSession(String userId, String jti) {
        try {
            redisTemplate.opsForSet().add("sessions:" + userId, jti);
        } catch (Exception e) {
            log.warn("Redis SADD failed for sessions:{}", userId, e);
        }
    }

    @Override
    public void removeSession(String userId, String jti) {
        try {
            redisTemplate.opsForSet().remove("sessions:" + userId, jti);
        } catch (Exception e) {
            log.warn("Redis SREM failed for sessions:{}", userId, e);
        }
    }

    @Override
    public void deleteSessions(String userId) {
        try {
            redisTemplate.delete("sessions:" + userId);
        } catch (Exception e) {
            log.warn("Redis DEL failed for sessions:{}", userId, e);
        }
    }

    // blacklist
    @Override
    public void blacklistToken(String jti, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set("blacklist:" + jti, "1", ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set failed for blacklist:{}", jti, e);
        }
    }

    // role override
    @Override
    public void setRoleOverride(String userId, String role, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set("user_role:" + userId, role, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set failed for user_role:{}", userId, e);
        }
    }

    @Override
    public String getRoleOverrideFromRedis(String userId) {
        try {
            return redisTemplate.opsForValue().get("user_role:" + userId);
        } catch (Exception e) {
            log.warn("Redis get failed for user_role:{}", userId, e);
            return null;
        }
    }

    // subscription status
    @Override
    public void setSubscriptionStatus(String userId, String status, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set("user_sub:" + userId, status, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set failed for user_sub:{}, status={}", userId, status, e);
        }
    }

    @Override
    public String getSubscriptionStatus(String userId) {
        try {
            return redisTemplate.opsForValue().get("user_sub:" + userId);
        } catch (Exception e) {
            log.warn("Redis get failed for user_sub:{}", userId, e);
            return null;
        }
    }
}
