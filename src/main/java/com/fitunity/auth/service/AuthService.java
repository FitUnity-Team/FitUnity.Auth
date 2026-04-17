package com.fitunity.auth.service;

import com.fitunity.auth.domain.RefreshTokenRecord;
import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.dto.AuthResponse;
import com.fitunity.auth.dto.LoginRequest;
import com.fitunity.auth.dto.RefreshResponse;
import com.fitunity.auth.dto.RegisterRequest;
import com.fitunity.auth.exception.AccountDisabledException;
import com.fitunity.auth.exception.EmailExistsException;
import com.fitunity.auth.exception.InvalidCredentialsException;
import com.fitunity.auth.exception.ReplayAttackException;
import com.fitunity.auth.exception.SessionExpiredException;
import com.fitunity.auth.exception.TooManyAttemptsException;
import com.fitunity.auth.repository.RefreshTokenRecordRepository;
import com.fitunity.auth.repository.UtilisateurRepository;
import com.fitunity.auth.service.auth.AuthResponseMapper;
import com.fitunity.auth.service.auth.RefreshCookieService;
import com.fitunity.auth.service.auth.RefreshTokenManager;
import com.fitunity.auth.service.auth.SubscriptionStatusResolver;
import com.fitunity.auth.service.auth.UserFactory;
import com.fitunity.auth.service.port.AuthFacade;
import com.fitunity.auth.service.port.IdentityUserDetails;
import com.fitunity.auth.service.port.RedisStore;
import com.fitunity.auth.service.port.TokenProvider;
import com.fitunity.auth.service.port.UserIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService implements AuthFacade {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long ROLE_OVERRIDE_TTL_SECONDS = 300L;

    private final UtilisateurRepository utilisateurRepository;
    private final RefreshTokenRecordRepository refreshTokenRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RedisStore redisStore;
    private final UserIdentityService userIdentityService;
    private final UserFactory userFactory;
    private final RefreshTokenManager refreshTokenManager;
    private final AuthResponseMapper authResponseMapper;
    private final SubscriptionStatusResolver subscriptionStatusResolver;
    private final RefreshCookieService refreshCookieService;

    public AuthService(
            UtilisateurRepository utilisateurRepository,
            RefreshTokenRecordRepository refreshTokenRecordRepository,
            PasswordEncoder passwordEncoder,
            TokenProvider tokenProvider,
            RedisStore redisStore,
            UserIdentityService userIdentityService,
            UserFactory userFactory,
            RefreshTokenManager refreshTokenManager,
            AuthResponseMapper authResponseMapper,
            SubscriptionStatusResolver subscriptionStatusResolver,
            RefreshCookieService refreshCookieService
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.refreshTokenRecordRepository = refreshTokenRecordRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.redisStore = redisStore;
        this.userIdentityService = userIdentityService;
        this.userFactory = userFactory;
        this.refreshTokenManager = refreshTokenManager;
        this.authResponseMapper = authResponseMapper;
        this.subscriptionStatusResolver = subscriptionStatusResolver;
        this.refreshCookieService = refreshCookieService;
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        String normalizedEmail = userFactory.normalizeEmail(request.getEmail());
        if (utilisateurRepository.existsByEmail(normalizedEmail)) {
            throw new EmailExistsException("Un utilisateur avec cet email existe déjà");
        }

        Utilisateur utilisateur = userFactory.newClientUser(
                normalizedEmail,
                passwordEncoder.encode(request.getPassword()),
                request.getNom()
        );
        utilisateurRepository.save(utilisateur);

        log.info("New user registered: {}", normalizedEmail);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        String normalizedEmail = userFactory.normalizeEmail(request.getEmail());

        long attempts = redisStore.getLoginAttempts(normalizedEmail);
        if (attempts >= 5) {
            throw new TooManyAttemptsException("Trop de tentatives de connexion. Veuillez réessayer dans 15 minutes.");
        }

        Utilisateur utilisateur = utilisateurRepository.findByEmail(normalizedEmail).orElse(null);
        if (utilisateur == null) {
            redisStore.incrementLoginAttempts(normalizedEmail);
            throw new InvalidCredentialsException("Email ou mot de passe incorrect");
        }

        if (!passwordEncoder.matches(request.getPassword(), utilisateur.getPasswordHash())) {
            redisStore.incrementLoginAttempts(normalizedEmail);
            throw new InvalidCredentialsException("Email ou mot de passe incorrect");
        }

        if (!utilisateur.isActive()) {
            throw new AccountDisabledException("Compte désactivé");
        }

        redisStore.clearLoginAttempts(normalizedEmail);

        String userId = utilisateur.getId().toString();
        String statutAbonnement = getSubscriptionStatus(userId);
        String accessToken = issueAccessToken(utilisateur, statutAbonnement);
        String refreshToken = tokenProvider.generateRefreshToken();
        String refreshTokenHash = refreshTokenManager.hash(refreshToken);
        String tokenFamily = UUID.randomUUID().toString();

        refreshTokenManager.persist(userId, tokenFamily, refreshTokenHash, tokenProvider.getRefreshTokenExpiryDate());
        redisStore.setRefreshTokenHash(userId, refreshTokenHash, tokenProvider.getRefreshTokenInactivityTtlSeconds());
        redisStore.addSession(userId, tokenProvider.extractJti(accessToken));
        refreshCookieService.setRefreshTokenCookie(response, refreshToken);

        return authResponseMapper.toLoginResponse(accessToken, utilisateur, statutAbonnement);
    }

    @Override
    @Transactional
    public RefreshResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = refreshCookieService.getRefreshTokenFromCookie(request);
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidCredentialsException("Refresh token manquant");
        }

        String incomingHash = refreshTokenManager.hash(rawRefreshToken);
        RefreshTokenRecord record = refreshTokenManager.findByHash(incomingHash)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token invalide"));

        if (record.isRevoked()) {
            handleReplayAttack(record, request);
            throw new ReplayAttackException("Session compromise détectée");
        }

        if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new SessionExpiredException("Session expirée");
        }

        String redisHash = redisStore.getRefreshTokenHash(record.getUserId());
        if (redisHash == null) {
            throw new SessionExpiredException("Session expirée");
        }
        if (!refreshTokenManager.secureEquals(incomingHash, redisHash)) {
            throw new InvalidCredentialsException("Refresh token invalide");
        }

        Utilisateur utilisateur = utilisateurRepository.findById(UUID.fromString(record.getUserId()))
                .orElseThrow(() -> new InvalidCredentialsException("Utilisateur non trouvé"));
        if (!utilisateur.isActive()) {
            throw new AccountDisabledException("Compte désactivé");
        }

        String userId = utilisateur.getId().toString();
        blacklistIfAccessTokenPresent(request, userId);

        String newRawRefreshToken = tokenProvider.generateRefreshToken();
        String newRefreshHash = refreshTokenManager.hash(newRawRefreshToken);
        String statutAbonnement = getSubscriptionStatus(userId);
        String newAccessToken = issueAccessToken(utilisateur, statutAbonnement);

        refreshTokenManager.rotate(record, newRefreshHash);

        redisStore.deleteRefreshToken(userId);
        redisStore.setRefreshTokenHash(userId, newRefreshHash, tokenProvider.getRefreshTokenInactivityTtlSeconds());
        redisStore.addSession(userId, tokenProvider.extractJti(newAccessToken));
        refreshCookieService.setRefreshTokenCookie(response, newRawRefreshToken);

        return new RefreshResponse(newAccessToken);
    }

    @Override
    @Transactional
    public void logout(String accessToken, HttpServletRequest request, HttpServletResponse response) {
        String userId = tokenProvider.extractUserId(accessToken);
        String jti = tokenProvider.extractJti(accessToken);

        if (jti != null) {
            redisStore.blacklistToken(jti, tokenProvider.calculateRemainingTtlSeconds(accessToken));
        }
        if (userId != null && jti != null) {
            redisStore.removeSession(userId, jti);
        }

        String rawRefreshToken = refreshCookieService.getRefreshTokenFromCookie(request);
        if (rawRefreshToken != null) {
            String refreshHash = refreshTokenManager.hash(rawRefreshToken);
            refreshTokenManager.revokeFamilyByRefreshHash(refreshHash);
        }

        if (userId != null) {
            redisStore.deleteRefreshToken(userId);
        }
        refreshCookieService.clearRefreshTokenCookie(response);
    }

    @Override
    @Transactional
    public Utilisateur updateRole(String userId, Role role) {
        Utilisateur user = utilisateurRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new InvalidCredentialsException("Utilisateur non trouvé"));
        user.setRole(role);
        user.setUpdatedAt(LocalDateTime.now());
        Utilisateur saved = utilisateurRepository.save(user);
        redisStore.setRoleOverride(userId, role.getValue(), ROLE_OVERRIDE_TTL_SECONDS);
        return saved;
    }

    @Override
    @Transactional
    public Utilisateur activateUser(String userId) {
        Utilisateur user = utilisateurRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new InvalidCredentialsException("Utilisateur non trouvé"));
        user.setActive(true);
        user.setUpdatedAt(LocalDateTime.now());
        return utilisateurRepository.save(user);
    }

    @Override
    @Transactional
    public Utilisateur deactivateUser(String userId) {
        Utilisateur user = utilisateurRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new InvalidCredentialsException("Utilisateur non trouvé"));
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        utilisateurRepository.save(user);

        List<RefreshTokenRecord> tokens = refreshTokenRecordRepository.findActiveByUserId(userId);
        for (RefreshTokenRecord token : tokens) {
            token.setRevoked(true);
        }
        refreshTokenRecordRepository.saveAll(tokens);

        redisStore.deleteRefreshToken(userId);
        redisStore.deleteSessions(userId);
        return user;
    }

    private void blacklistIfAccessTokenPresent(HttpServletRequest request, String userId) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return;
        }

        String oldAccessToken = authorization.substring(7);
        if (!tokenProvider.validateToken(oldAccessToken)) {
            return;
        }

        String oldJti = tokenProvider.extractJti(oldAccessToken);
        if (oldJti == null) {
            return;
        }

        redisStore.blacklistToken(oldJti, tokenProvider.calculateRemainingTtlSeconds(oldAccessToken));
        redisStore.removeSession(userId, oldJti);
    }

    private String issueAccessToken(Utilisateur utilisateur, String statutAbonnement) {
        IdentityUserDetails userDetails = userIdentityService.requireIdentity(utilisateur.getId().toString());
        if (userDetails == null) {
            throw new InvalidCredentialsException("Utilisateur non trouvé");
        }
        return tokenProvider.generateAccessToken(userDetails, statutAbonnement);
    }

    private String getSubscriptionStatus(String userId) {
        try {
            return subscriptionStatusResolver.normalize(redisStore.getSubscriptionStatus(userId));
        } catch (Exception e) {
            log.warn("Failed to fetch subscription status from Redis for userId={}", userId, e);
            return "NONE";
        }
    }

    private void handleReplayAttack(RefreshTokenRecord compromisedRecord, HttpServletRequest request) {
        refreshTokenManager.revokeFamily(compromisedRecord.getTokenFamilyId());
        redisStore.deleteRefreshToken(compromisedRecord.getUserId());
        log.warn("Replay attack detected for userId={}, tokenFamily={}, ip={}",
                compromisedRecord.getUserId(), compromisedRecord.getTokenFamilyId(), request.getRemoteAddr());
    }
}
