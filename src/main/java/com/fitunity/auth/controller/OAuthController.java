package com.fitunity.auth.controller;

import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.service.auth.RefreshCookieService;
import com.fitunity.auth.service.auth.RefreshTokenManager;
import com.fitunity.auth.service.auth.SubscriptionStatusResolver;
import com.fitunity.auth.service.oauth.GoogleOAuthService;
import com.fitunity.auth.service.oauth.OAuthAccountResolver;
import com.fitunity.auth.service.oauth.OAuthStateService;
import com.fitunity.auth.service.oauth.model.GoogleUserProfile;
import com.fitunity.auth.service.port.IdentityUserDetails;
import com.fitunity.auth.service.port.RedisStore;
import com.fitunity.auth.service.port.TokenProvider;
import com.fitunity.auth.service.port.UserIdentityService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

@Controller
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    private final GoogleOAuthService googleOAuthService;
    private final OAuthStateService oauthStateService;
    private final OAuthAccountResolver oauthAccountResolver;
    private final TokenProvider tokenProvider;
    private final UserIdentityService userIdentityService;
    private final RedisStore redisStore;
    private final RefreshTokenManager refreshTokenManager;
    private final RefreshCookieService refreshCookieService;
    private final SubscriptionStatusResolver subscriptionStatusResolver;
    private final String frontendRedirectUri;

    public OAuthController(
            GoogleOAuthService googleOAuthService,
            OAuthStateService oauthStateService,
            OAuthAccountResolver oauthAccountResolver,
            TokenProvider tokenProvider,
            UserIdentityService userIdentityService,
            RedisStore redisStore,
            RefreshTokenManager refreshTokenManager,
            RefreshCookieService refreshCookieService,
            SubscriptionStatusResolver subscriptionStatusResolver,
            @Value("${oauth.frontend.redirect-uri:http://localhost:5173/auth/callback}") String frontendRedirectUri
    ) {
        this.googleOAuthService = googleOAuthService;
        this.oauthStateService = oauthStateService;
        this.oauthAccountResolver = oauthAccountResolver;
        this.tokenProvider = tokenProvider;
        this.userIdentityService = userIdentityService;
        this.redisStore = redisStore;
        this.refreshTokenManager = refreshTokenManager;
        this.refreshCookieService = refreshCookieService;
        this.subscriptionStatusResolver = subscriptionStatusResolver;
        this.frontendRedirectUri = frontendRedirectUri;
    }

    @GetMapping("/oauth2/authorize/google")
    public void authorizeGoogle(HttpServletResponse response) throws IOException {
        String state = oauthStateService.issueState();
        response.sendRedirect(googleOAuthService.buildAuthorizationUrl(state));
    }

    @GetMapping("/oauth2/callback/google")
    public void callbackGoogle(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpServletResponse response
    ) throws IOException {
        if (!oauthStateService.consumeIfValid(state)) {
            redirectError(response, "invalid_state");
            return;
        }

        try {
            GoogleUserProfile profile = googleOAuthService.exchangeCodeForUserProfile(code);
            if (!profile.isEmailVerified()) {
                redirectError(response, "email_not_verified");
                return;
            }

            Utilisateur user = oauthAccountResolver.resolveOrCreate(profile.getEmail(), profile.getDisplayName());
            if (!user.isActive()) {
                redirectError(response, "token_validation_failed");
                return;
            }

            String userId = user.getId().toString();
            String statutAbonnement = resolveSubscriptionStatus(userId);
            IdentityUserDetails identity = userIdentityService.requireIdentity(userId);
            if (identity == null) {
                redirectError(response, "internal_error");
                return;
            }

            String accessToken = tokenProvider.generateAccessToken(identity, statutAbonnement);
            String refreshToken = tokenProvider.generateRefreshToken();
            String refreshHash = refreshTokenManager.hash(refreshToken);
            String tokenFamily = UUID.randomUUID().toString();

            refreshTokenManager.persist(userId, tokenFamily, refreshHash, tokenProvider.getRefreshTokenExpiryDate());
            redisStore.setRefreshTokenHash(userId, refreshHash, tokenProvider.getRefreshTokenInactivityTtlSeconds());
            redisStore.addSession(userId, tokenProvider.extractJti(accessToken));
            refreshCookieService.setRefreshTokenCookie(response, refreshToken);

            redirectSuccess(response);
        } catch (IllegalArgumentException ex) {
            log.warn("OAuth callback validation failed: {}", ex.getMessage());
            redirectError(response, "token_validation_failed");
        } catch (Exception ex) {
            log.error("OAuth callback failed", ex);
            redirectError(response, "internal_error");
        }
    }

    private String resolveSubscriptionStatus(String userId) {
        try {
            return subscriptionStatusResolver.normalize(redisStore.getSubscriptionStatus(userId));
        } catch (Exception ex) {
            log.warn("Failed to resolve subscription status for OAuth userId={}", userId, ex);
            return "NONE";
        }
    }

    private void redirectSuccess(HttpServletResponse response) throws IOException {
        String redirect = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("oauth", "success")
                .build()
                .toUriString();
        response.sendRedirect(redirect);
    }

    private void redirectError(HttpServletResponse response, String code) throws IOException {
        String redirect = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("oauth", "error")
                .queryParam("code", code)
                .build()
                .toUriString();
        response.sendRedirect(redirect);
    }
}
