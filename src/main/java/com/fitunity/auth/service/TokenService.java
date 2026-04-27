package com.fitunity.auth.service;

import com.fitunity.auth.service.port.IdentityUserDetails;
import com.fitunity.auth.service.port.TokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Service
public class TokenService implements TokenProvider {

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

    @Override
    public String generateAccessToken(UserDetails userDetails, String statutAbonnement) {
        if (!(userDetails instanceof IdentityUserDetails identityUserDetails)) {
            throw new IllegalArgumentException("Unsupported UserDetails implementation");
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiryMinutes * 60 * 1000);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .claim("userId", identityUserDetails.getUserId())
                .claim("role", identityUserDetails.getRole().getValue())
                .claim("statutAbonnement", statutAbonnement)
                .claim("jti", jti)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    @Override
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            if (!issuer.equals(claims.getIssuer())) {
                return false;
            }
            Object aud = claims.get("aud");
            if (aud == null || !aud.toString().contains(audience)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String extractUserId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return claims.get("userId", String.class);
        } catch (Exception e) {
            log.error("Failed to extract userId from token", e);
            return null;
        }
    }

    @Override
    public String extractRole(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return claims.get("role", String.class);
        } catch (Exception e) {
            log.error("Failed to extract role from token", e);
            return null;
        }
    }

    @Override
    public String extractSubscriptionStatus(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return claims.get("statutAbonnement", String.class);
        } catch (Exception e) {
            log.error("Failed to extract subscription status from token", e);
            return null;
        }
    }

    @Override
    public String extractJti(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return claims.get("jti", String.class);
        } catch (Exception e) {
            log.error("Failed to extract jti from token", e);
            return null;
        }
    }

    @Override
    public Date extractExpiration(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return claims.getExpiration();
        } catch (Exception e) {
            log.error("Failed to extract expiration from token", e);
            return null;
        }
    }

    @Override
    public long calculateRemainingTtlSeconds(String token) {
        Date expiration = extractExpiration(token);
        if (expiration == null) {
            return 0;
        }
        long remainingMs = expiration.getTime() - System.currentTimeMillis();
        return Math.max(0, remainingMs / 1000);
    }

    @Override
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

    public long getAccessTokenExpiryMinutes() {
        return accessTokenExpiryMinutes;
    }

    @Override
    public LocalDateTime getRefreshTokenExpiryDate() {
        return LocalDateTime.now().plusDays(refreshTokenExpiryDays);
    }

    @Override
    public long getRefreshTokenInactivityTtlSeconds() {
        return Duration.ofDays(7).toSeconds();
    }
}
