package com.fitunity.auth.service.port;

public interface RedisStore {

    String getRefreshTokenHash(String userId);

    void setRefreshTokenHash(String userId, String hash, long ttlSeconds);

    void deleteRefreshToken(String userId);

    long incrementLoginAttempts(String email);

    long getLoginAttempts(String email);

    void clearLoginAttempts(String email);

    void addSession(String userId, String jti);

    void removeSession(String userId, String jti);

    void deleteSessions(String userId);

    void blacklistToken(String jti, long ttlSeconds);

    void setRoleOverride(String userId, String role, long ttlSeconds);

    String getRoleOverrideFromRedis(String userId);

    void setSubscriptionStatus(String userId, String status, long ttlSeconds);

    String getSubscriptionStatus(String userId);
}
