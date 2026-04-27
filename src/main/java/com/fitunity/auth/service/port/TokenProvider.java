package com.fitunity.auth.service.port;

import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Date;

public interface TokenProvider {

    String generateAccessToken(UserDetails userDetails, String statutAbonnement);

    String generateRefreshToken();

    boolean validateToken(String token);

    String extractUserId(String token);

    String extractRole(String token);

    String extractSubscriptionStatus(String token);

    String extractJti(String token);

    Date extractExpiration(String token);

    long calculateRemainingTtlSeconds(String token);

    boolean isTokenBlacklisted(String token);

    LocalDateTime getRefreshTokenExpiryDate();

    long getRefreshTokenInactivityTtlSeconds();
}
