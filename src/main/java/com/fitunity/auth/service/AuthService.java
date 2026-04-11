package com.fitunity.auth.service;

import com.fitunity.auth.domain.RefreshTokenRecord;
import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.StatutAbonnement;
import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.dto.AuthResponse;
import com.fitunity.auth.dto.LoginRequest;
import com.fitunity.auth.dto.RegisterRequest;
import com.fitunity.auth.exception.AccountDisabledException;
import com.fitunity.auth.exception.EmailExistsException;
import com.fitunity.auth.exception.InvalidCredentialsException;
import com.fitunity.auth.exception.ReplayAttackException;
import com.fitunity.auth.exception.SessionExpiredException;
import com.fitunity.auth.exception.TooManyAttemptsException;
import com.fitunity.auth.repository.RefreshTokenRecordRepository;
import com.fitunity.auth.repository.UtilisateurRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UtilisateurRepository utilisateurRepository;
    private final RefreshTokenRecordRepository refreshTokenRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RedisService redisService;

    public AuthService(
            UtilisateurRepository utilisateurRepository,
            RefreshTokenRecordRepository refreshTokenRecordRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            RedisService redisService
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.refreshTokenRecordRepository = refreshTokenRecordRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.redisService = redisService;
    }

    /**
     * Register a new user.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (utilisateurRepository.existsByEmail(request.getEmail())) {
            throw new EmailExistsException("Un utilisateur avec cet email existe déjà");
        }

        // Create new user
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail(request.getEmail());
        utilisateur.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        utilisateur.setRole(Role.CLIENT);

        // Save to database
        utilisateur = utilisateurRepository.save(utilisateur);

        log.info("New user registered: {}", utilisateur.getEmail());

        // Generate tokens
        return generateTokensAndResponse(utilisateur, null);
    }

    /**
     * Login user with email and password.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        // Check Redis for login attempts (throttling)
        long attempts = redisService.getLoginAttempts(request.getEmail());
        if (attempts >= 5) {
            throw new TooManyAttemptsException("Trop de tentatives de connexion. Veuillez réessayer dans 15 minutes.");
        }

        // Find user by email
        Utilisateur utilisateur = utilisateurRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (utilisateur == null) {
            redisService.incrementLoginAttempts(request.getEmail());
            throw new InvalidCredentialsException("Email ou mot de passe incorrect");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), utilisateur.getPasswordHash())) {
            redisService.incrementLoginAttempts(request.getEmail());
            throw new InvalidCredentialsException("Email ou mot de passe incorrect");
        }

        // Check if account is active (note: Utilisateur entity doesn't have 'active' field yet)
        // For now, all users are considered active

        // Clear login attempts on success
        redisService.clearLoginAttempts(request.getEmail());

        // Get subscription status from Redis (maintained by Payment Service via RabbitMQ)
        StatutAbonnement statutAbonnement = getSubscriptionStatus(utilisateur.getId().toString());

        // Generate token family for this login session
        String tokenFamily = UUID.randomUUID().toString();

        // Generate access token for response
        String accessToken = tokenService.generateAccessToken(
                utilisateur.getId().toString(),
                utilisateur.getEmail(),
                utilisateur.getRole().name(),
                statutAbonnement
        );

        // Generate refresh token
        String refreshToken = tokenService.generateRefreshToken();

        // Hash refresh token for storage
        String refreshTokenHash = hashRefreshToken(refreshToken);

        // Store refresh token in MySQL
        RefreshTokenRecord record = new RefreshTokenRecord();
        record.setUserId(utilisateur.getId().toString());
        record.setTokenFamilyId(tokenFamily);
        record.setRefreshTokenHash(refreshTokenHash);
        record.setExpiresAt(tokenService.getRefreshTokenExpiryDate());
        record.setRevoked(false);
        refreshTokenRecordRepository.save(record);

        // Store in Redis for sliding window (7 days inactivity expiry)
        redisService.setRefreshTokenHash(
                utilisateur.getId().toString(),
                refreshTokenHash,
                tokenService.getRefreshTokenInactivityTtlSeconds()
        );

        // Add session to Redis
        String jti = tokenService.extractJti(accessToken);
        redisService.addSession(utilisateur.getId().toString(), jti);

        // Set HttpOnly cookie with raw refresh token
        setRefreshTokenCookie(response, refreshToken);

        log.info("User logged in: {}", utilisateur.getEmail());

        // Build response
        AuthResponse authResponse = new AuthResponse();
        authResponse.setAccessToken(accessToken);
        authResponse.setUserId(utilisateur.getId().toString());
        authResponse.setEmail(utilisateur.getEmail());
        authResponse.setRole(utilisateur.getRole().name());
        authResponse.setStatutAbonnement(statutAbonnement.name());

        return authResponse;
    }

    /**
     * Refresh access token using refresh token from cookie.
     */
    @Transactional
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        // Get refresh token from cookie
        String rawRefreshToken = getRefreshTokenFromCookie(request);
        if (rawRefreshToken == null) {
            throw new InvalidCredentialsException("Refresh token manquant");
        }

        // Hash the token
        String refreshTokenHash = hashRefreshToken(rawRefreshToken);

        // Find token record in MySQL
        List<RefreshTokenRecord> records = refreshTokenRecordRepository.findValidRefreshTokens(
                null, // We'll filter by hash manually
                LocalDateTime.now()
        );

        RefreshTokenRecord record = null;
        for (RefreshTokenRecord r : records) {
            if (r.getRefreshTokenHash().equals(refreshTokenHash)) {
                record = r;
                break;
            }
        }

        if (record == null) {
            throw new InvalidCredentialsException("Refresh token invalide");
        }

        // Check if token is revoked (REPLAY ATTACK DETECTION)
        if (record.isRevoked()) {
            handleReplayAttack(record, request);
            throw new ReplayAttackException("Session compromise détectée");
        }

        // Check Redis for sliding window
        String storedHash = redisService.getRefreshTokenHash(record.getUserId());
        if (storedHash == null || !storedHash.equals(refreshTokenHash)) {
            throw new InvalidCredentialsException("Session expirée par inactivité");
        }

        // Get user
        Utilisateur utilisateur = utilisateurRepository.findById(UUID.fromString(record.getUserId()))
                .orElseThrow(() -> new InvalidCredentialsException("Utilisateur non trouvé"));

        // Get subscription status
        StatutAbonnement statutAbonnement = getSubscriptionStatus(utilisateur.getId().toString());

        // Calculate remaining TTL of current access token (for blacklist)
        String oldJti = null;
        // Note: We don't have the old access token here, so we can't blacklist it
        // In a full implementation, you'd pass the old access token in the request header

        // Generate new refresh token
        String newRefreshToken = tokenService.generateRefreshToken();
        String newRefreshTokenHash = hashRefreshToken(newRefreshToken);

        // Invalidate old record
        record.setRevoked(true);
        refreshTokenRecordRepository.save(record);

        // Create new record with same token family
        RefreshTokenRecord newRecord = new RefreshTokenRecord();
        newRecord.setUserId(record.getUserId());
        newRecord.setTokenFamilyId(record.getTokenFamilyId());
        newRecord.setRefreshTokenHash(newRefreshTokenHash);
        newRecord.setExpiresAt(record.getExpiresAt()); // Original expiry, NOT extended
        newRecord.setRevoked(false);
        refreshTokenRecordRepository.save(newRecord);

        // Update Redis - reset sliding window
        redisService.setRefreshTokenHash(
                record.getUserId(),
                newRefreshTokenHash,
                tokenService.getRefreshTokenInactivityTtlSeconds()
        );

        // Generate new access token
        String newJti = UUID.randomUUID().toString();
        String newAccessToken = tokenService.generateAccessToken(
                utilisateur.getId().toString(),
                utilisateur.getEmail(),
                utilisateur.getRole().name(),
                statutAbonnement
        );

        // Add new session to Redis
        redisService.addSession(utilisateur.getId().toString(), newJti);

        // Set new cookie
        setRefreshTokenCookie(response, newRefreshToken);

        log.info("Token refreshed for user: {}", utilisateur.getEmail());

        AuthResponse authResponse = new AuthResponse();
        authResponse.setAccessToken(newAccessToken);
        return authResponse;
    }

    /**
     * Logout user - blacklist token and revoke refresh tokens.
     */
    @Transactional
    public void logout(String accessToken, HttpServletRequest request, HttpServletResponse response) {
        // Extract JTI and userId from token
        String jti = tokenService.extractJti(accessToken);
        String userId = tokenService.extractUserId(accessToken);

        if (jti != null && userId != null) {
            // Calculate remaining TTL and blacklist
            long remainingTtl = tokenService.calculateRemainingTtlSeconds(accessToken);
            redisService.blacklistToken(jti, remainingTtl);

            // Remove session from Redis
            redisService.removeSession(userId, jti);
        }

        // Get refresh token from cookie and revoke
        String rawRefreshToken = getRefreshTokenFromCookie(request);
        if (rawRefreshToken != null) {
            String refreshTokenHash = hashRefreshToken(rawRefreshToken);

            // Find and revoke all tokens in the same family
            List<RefreshTokenRecord> records = refreshTokenRecordRepository.findValidRefreshTokens(
                    userId,
                    LocalDateTime.now()
            );

            for (RefreshTokenRecord record : records) {
                if (record.getRefreshTokenHash().equals(refreshTokenHash)) {
                    // Found the token - revoke entire family
                    List<RefreshTokenRecord> familyTokens = refreshTokenRecordRepository.findActiveTokensByFamily(
                            record.getTokenFamilyId()
                    );
                    for (RefreshTokenRecord familyToken : familyTokens) {
                        familyToken.setRevoked(true);
                    }
                    refreshTokenRecordRepository.saveAll(familyTokens);

                    // Remove from Redis
                    redisService.deleteRefreshToken(userId);

                    log.info("User logged out: {}", userId);
                    break;
                }
            }
        }

        // Clear cookie
        clearRefreshTokenCookie(response);
    }

    /**
     * Get subscription status from Redis, default to NONE if not found.
     */
    private StatutAbonnement getSubscriptionStatus(String userId) {
        try {
            String status = redisService.getSubscriptionStatus(userId);
            if (status != null) {
                return StatutAbonnement.valueOf(status);
            }
        } catch (Exception e) {
            log.warn("Failed to get subscription status from Redis for user: {}", userId, e);
        }
        return StatutAbonnement.NONE;
    }

    /**
     * Generate tokens and build auth response.
     */
    private AuthResponse generateTokensAndResponse(Utilisateur utilisateur, StatutAbonnement statutAbonnement) {
        String accessToken = tokenService.generateAccessToken(
                utilisateur.getId().toString(),
                utilisateur.getEmail(),
                utilisateur.getRole().name(),
                statutAbonnement != null ? statutAbonnement : StatutAbonnement.NONE
        );

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setUserId(utilisateur.getId().toString());
        response.setEmail(utilisateur.getEmail());
        response.setRole(utilisateur.getRole().name());
        response.setStatutAbonnement(statutAbonnement != null ? statutAbonnement.name() : StatutAbonnement.NONE.name());

        return response;
    }

    /**
     * Hash refresh token with SHA-256.
     */
    private String hashRefreshToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Set HttpOnly cookie with refresh token.
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // HTTPS only
        cookie.setPath("/api/auth");
        cookie.setMaxAge(2592000); // 30 days
        // Note: SameSite=Strict requires Servlet 6.0 or manual header setting
        response.addCookie(cookie);
    }

    /**
     * Clear refresh token cookie.
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * Get refresh token from request cookies.
     */
    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Handle replay attack detection - revoke entire token family.
     */
    private void handleReplayAttack(RefreshTokenRecord compromisedRecord, HttpServletRequest request) {
        // Revoke all tokens in the same family
        List<RefreshTokenRecord> familyTokens = refreshTokenRecordRepository.findActiveTokensByFamily(
                compromisedRecord.getTokenFamilyId()
        );
        for (RefreshTokenRecord token : familyTokens) {
            token.setRevoked(true);
        }
        refreshTokenRecordRepository.saveAll(familyTokens);

        // Remove from Redis
        redisService.deleteRefreshToken(compromisedRecord.getUserId());

        // Log the incident
        log.warn("REPLAY ATTACK DETECTED - userId={}, tokenFamily={}, ip={}, timestamp={}",
                compromisedRecord.getUserId(),
                compromisedRecord.getTokenFamilyId(),
                request.getRemoteAddr(),
                LocalDateTime.now()
        );
    }
}
