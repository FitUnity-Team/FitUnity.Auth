package com.fitunity.auth.service;

import com.fitunity.auth.domain.StatutAbonnement;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final SecretKey secretKey;
    private final long accessTokenExpiryMinutes;
    private final long refreshTokenExpiryDays;
    private final String issuer;
    private final String audience;

    private final RedisTemplate<String, String> redisTemplate;

    public TokenService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.access-token-expiry-minutes}") long accessTokenExpiryMinutes,
            @Value("${jwt.refresh-token-expiry-days}") long refreshTokenExpiryDays,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience,
            RedisTemplate<String, String> redisTemplate
    ) {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;
        this.refreshTokenExpiryDays = refreshTokenExpiryDays;
        this.issuer = issuer;
        this.audience = audience;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generate a new JWT access token for the authenticated user.
     */
    public String generateAccessToken(String userId, String email, String role, StatutAbonnement statutAbonnement) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiryMinutes * 60 * 1000);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(userId)
                .id(jti)
                .claim("email", email)
                .claim("role", role)
                .claim("statutAbonnement", statutAbonnement.name())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generate a new refresh token (UUID string).
     * The raw token is sent via cookie; only its hash is stored server-side.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Validate JWT token structure and signature.
     * Does NOT check blacklist - use isTokenBlacklisted() separately.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract user ID (subject) from JWT token.
     */
    public String extractUserId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            log.error("Failed to extract userId from token", e);
            return null;
        }
    }

    /**
     * Extract role claim from JWT token.
     */
    public String extractRole(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return claims.get("role", String.class);
        } catch (Exception e) {
            log.error("Failed to extract role from token", e);
            return null;
        }
    }

    /**
     * Extract email claim from JWT token.
     */
    public String extractEmail(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return claims.get("email", String.class);
        } catch (Exception e) {
            log.error("Failed to extract email from token", e);
            return null;
        }
    }

    /**
     * Extract statutAbonnement claim from JWT token.
     */
    public String extractSubscriptionStatus(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return claims.get("statutAbonnement", String.class);
        } catch (Exception e) {
            log.error("Failed to extract subscription status from token", e);
            return null;
        }
    }

    /**
     * Extract JTI (unique token identifier) from JWT token.
     */
    public String extractJti(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return claims.getId();
        } catch (Exception e) {
            log.error("Failed to extract jti from token", e);
            return null;
        }
    }

    /**
     * Extract expiration date from JWT token.
     */
    public Date extractExpiration(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return claims.getExpiration();
        } catch (Exception e) {
            log.error("Failed to extract expiration from token", e);
            return null;
        }
    }

    /**
     * Calculate remaining TTL in seconds for a token (for blacklist TTL).
     */
    public long calculateRemainingTtlSeconds(String token) {
        Date expiration = extractExpiration(token);
        if (expiration == null) {
            return 0;
        }
        long remainingMs = expiration.getTime() - System.currentTimeMillis();
        return Math.max(0, remainingMs / 1000);
    }

    /**
     * Check if a token's JTI is in the Redis blacklist.
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String jti = extractJti(token);
            if (jti == null) {
                return false;
            }
            String blacklisted = redisTemplate.opsForValue().get("blacklist:" + jti);
            return "1".equals(blacklisted);
        } catch (Exception e) {
            log.warn("Redis blacklist check failed", e);
            return false;
        }
    }

    /**
     * Get the access token expiry in minutes.
     */
    public long getAccessTokenExpiryMinutes() {
        return accessTokenExpiryMinutes;
    }

    /**
     * Get the refresh token expiry as a LocalDateTime from now.
     */
    public LocalDateTime getRefreshTokenExpiryDate() {
        return LocalDateTime.now().plusDays(refreshTokenExpiryDays);
    }

    /**
     * Get the refresh token inactivity sliding window TTL in seconds (7 days).
     */
    public long getRefreshTokenInactivityTtlSeconds() {
        return Duration.ofDays(7).toSeconds();
    }
}
