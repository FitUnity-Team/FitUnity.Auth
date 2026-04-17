package com.fitunity.auth.controller;

import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.service.auth.RefreshCookieService;
import com.fitunity.auth.service.auth.RefreshTokenManager;
import com.fitunity.auth.service.oauth.GoogleOAuthService;
import com.fitunity.auth.service.oauth.OAuthAccountResolver;
import com.fitunity.auth.service.oauth.OAuthStateService;
import com.fitunity.auth.service.oauth.model.GoogleUserProfile;
import com.fitunity.auth.service.port.IdentityUserDetails;
import com.fitunity.auth.service.port.RedisStore;
import com.fitunity.auth.service.port.TokenProvider;
import com.fitunity.auth.service.port.UserIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = com.fitunity.auth.Application.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb_oauth;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "jwt.secret=test-secret-key-for-unit-tests-minimum-32-chars",
                "oauth.frontend.redirect-uri=http://localhost:5173/auth/callback"
        }
)
@AutoConfigureMockMvc
class OAuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GoogleOAuthService googleOAuthService;

    @MockBean
    private OAuthStateService oauthStateService;

    @MockBean
    private OAuthAccountResolver oauthAccountResolver;

    @MockBean
    private TokenProvider tokenProvider;

    @MockBean
    private UserIdentityService userIdentityService;

    @MockBean
    private RedisStore redisStore;

    @MockBean
    private RefreshTokenManager refreshTokenManager;

    @MockBean
    private RefreshCookieService refreshCookieService;

    @Test
    void authorizeGoogleShouldRedirectToGoogleAuthorizationUrl() throws Exception {
        when(oauthStateService.issueState()).thenReturn("state-123");
        when(googleOAuthService.buildAuthorizationUrl("state-123"))
                .thenReturn("https://accounts.google.com/o/oauth2/v2/auth?state=state-123");

        mockMvc.perform(get("/oauth2/authorize/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://accounts.google.com/o/oauth2/v2/auth?state=state-123"));
    }

    @Test
    void callbackGoogleShouldRedirectErrorOnInvalidState() throws Exception {
        when(oauthStateService.consumeIfValid("bad-state")).thenReturn(false);

        mockMvc.perform(get("/oauth2/callback/google")
                        .param("code", "code-123")
                        .param("state", "bad-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173/auth/callback?oauth=error&code=invalid_state"));
    }

    @Test
    void callbackGoogleShouldCreateSessionAndRedirectSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        Utilisateur user = new Utilisateur();
        user.setId(userId);
        user.setEmail("oauth@test.com");
        user.setRole(Role.CLIENT);
        user.setActive(true);

        GoogleUserProfile profile = new GoogleUserProfile("google-sub-1", "oauth@test.com", true, "OAuth User");
        IdentityUserDetails identity = mock(IdentityUserDetails.class);

        when(oauthStateService.consumeIfValid("state-ok")).thenReturn(true);
        when(googleOAuthService.exchangeCodeForUserProfile("good-code")).thenReturn(profile);
        when(oauthAccountResolver.resolveOrCreate("oauth@test.com", "OAuth User")).thenReturn(user);
        when(userIdentityService.requireIdentity(userId.toString())).thenReturn(identity);
        when(tokenProvider.generateAccessToken(identity, "NONE")).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(refreshTokenManager.hash("refresh-token")).thenReturn("refresh-hash");
        when(tokenProvider.getRefreshTokenExpiryDate()).thenReturn(LocalDateTime.now().plusDays(30));
        when(tokenProvider.getRefreshTokenInactivityTtlSeconds()).thenReturn(3600L);
        when(tokenProvider.extractJti("access-token")).thenReturn("jti-1");

        mockMvc.perform(get("/oauth2/callback/google")
                        .param("code", "good-code")
                        .param("state", "state-ok"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173/auth/callback?oauth=success"));

        verify(refreshTokenManager).persist(org.mockito.ArgumentMatchers.eq(userId.toString()), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("refresh-hash"), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(refreshCookieService).setRefreshTokenCookie(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("refresh-token"));
    }
}
