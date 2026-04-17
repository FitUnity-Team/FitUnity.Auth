package com.fitunity.auth.service;

import com.fitunity.auth.domain.RefreshTokenRecord;
import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.dto.LoginRequest;
import com.fitunity.auth.dto.RefreshResponse;
import com.fitunity.auth.repository.RefreshTokenRecordRepository;
import com.fitunity.auth.repository.UtilisateurRepository;
import com.fitunity.auth.service.auth.AuthResponseMapper;
import com.fitunity.auth.service.auth.RefreshCookieService;
import com.fitunity.auth.service.auth.RefreshTokenManager;
import com.fitunity.auth.service.auth.SubscriptionStatusResolver;
import com.fitunity.auth.service.auth.UserFactory;
import com.fitunity.auth.service.port.IdentityUserDetails;
import com.fitunity.auth.service.port.RedisStore;
import com.fitunity.auth.service.port.TokenProvider;
import com.fitunity.auth.service.port.UserIdentityService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private RefreshTokenRecordRepository refreshTokenRecordRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenProvider tokenProvider;
    @Mock
    private RedisStore redisStore;
    @Mock
    private UserIdentityService userIdentityService;
    @Mock
    private RefreshTokenManager refreshTokenManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                utilisateurRepository,
                refreshTokenRecordRepository,
                passwordEncoder,
                tokenProvider,
                redisStore,
                userIdentityService,
                new UserFactory(),
                refreshTokenManager,
                new AuthResponseMapper(),
                new SubscriptionStatusResolver(),
                new RefreshCookieService(true, "Strict")
        );
    }

    @Test
    void refreshShouldDelegateHashFindAndRotateToManager() {
        String rawToken = "refresh-raw";
        String incomingHash = "incoming-hash";
        String newRawToken = "refresh-new";
        String newHash = "new-hash";
        String userId = UUID.randomUUID().toString();
        UUID userUuid = UUID.fromString(userId);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);
        IdentityUserDetails identity = mock(IdentityUserDetails.class);

        RefreshTokenRecord record = new RefreshTokenRecord();
        record.setId(UUID.randomUUID());
        record.setUserId(userId);
        record.setTokenFamilyId(UUID.randomUUID().toString());
        record.setRefreshTokenHash(incomingHash);
        record.setExpiresAt(expiresAt);
        record.setRevoked(false);
        record.setCreatedAt(LocalDateTime.now());

        Utilisateur user = baseUser(userUuid);

        when(refreshTokenManager.hash(rawToken)).thenReturn(incomingHash);
        when(refreshTokenManager.findByHash(incomingHash)).thenReturn(Optional.of(record));
        when(redisStore.getRefreshTokenHash(userId)).thenReturn(incomingHash);
        when(refreshTokenManager.secureEquals(incomingHash, incomingHash)).thenReturn(true);
        when(utilisateurRepository.findById(userUuid)).thenReturn(Optional.of(user));
        when(tokenProvider.generateRefreshToken()).thenReturn(newRawToken);
        when(refreshTokenManager.hash(newRawToken)).thenReturn(newHash);
        when(redisStore.getSubscriptionStatus(userId)).thenReturn("NONE");
        when(userIdentityService.requireIdentity(userId)).thenReturn(identity);
        when(tokenProvider.generateAccessToken(any(), eq("NONE"))).thenReturn("new-access-token");
        when(tokenProvider.extractJti("new-access-token")).thenReturn("new-jti");
        when(tokenProvider.getRefreshTokenInactivityTtlSeconds()).thenReturn(3600L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", rawToken));
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshResponse refreshResponse = authService.refresh(request, response);

        assertEquals("new-access-token", refreshResponse.getAccessToken());
        verify(refreshTokenManager).hash(rawToken);
        verify(refreshTokenManager).findByHash(incomingHash);
        verify(refreshTokenManager).secureEquals(incomingHash, incomingHash);
        verify(refreshTokenManager).hash(newRawToken);
        verify(refreshTokenManager).rotate(record, newHash);
    }

    @Test
    void loginShouldPersistRefreshRecordThroughManager() {
        UUID userId = UUID.randomUUID();
        Utilisateur user = baseUser(userId);
        user.setPasswordHash("encoded-password");
        IdentityUserDetails identity = mock(IdentityUserDetails.class);
        LocalDateTime refreshExpiry = LocalDateTime.now().plusDays(30);

        when(redisStore.getLoginAttempts("user@test.com")).thenReturn(0L);
        when(utilisateurRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(redisStore.getSubscriptionStatus(userId.toString())).thenReturn("NONE");
        when(userIdentityService.requireIdentity(userId.toString())).thenReturn(identity);
        when(tokenProvider.generateAccessToken(any(), anyString())).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(refreshTokenManager.hash("refresh-token")).thenReturn("refresh-hash");
        when(tokenProvider.getRefreshTokenExpiryDate()).thenReturn(refreshExpiry);
        when(tokenProvider.getRefreshTokenInactivityTtlSeconds()).thenReturn(3600L);
        when(tokenProvider.extractJti("access-token")).thenReturn("jti-1");

        LoginRequest request = new LoginRequest();
        request.setEmail("user@test.com");
        request.setPassword("password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authService.login(request, response);

        verify(refreshTokenManager).hash("refresh-token");
        verify(refreshTokenManager).persist(eq(userId.toString()), anyString(), eq("refresh-hash"), eq(refreshExpiry));
    }

    @Test
    void logoutShouldRevokeFamilyThroughManager() {
        UUID userId = UUID.randomUUID();
        when(tokenProvider.extractUserId("access-token")).thenReturn(userId.toString());
        when(tokenProvider.extractJti("access-token")).thenReturn("old-jti");
        when(tokenProvider.calculateRemainingTtlSeconds("access-token")).thenReturn(120L);
        when(refreshTokenManager.hash("refresh-token")).thenReturn("refresh-hash");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", "refresh-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        authService.logout("access-token", request, response);

        verify(refreshTokenManager).hash("refresh-token");
        verify(refreshTokenManager).revokeFamilyByRefreshHash("refresh-hash");
    }

    @Test
    void updateRoleShouldRefreshUpdatedAt() {
        UUID userId = UUID.randomUUID();
        Utilisateur user = baseUser(userId);
        LocalDateTime previous = LocalDateTime.now().minusDays(1);
        user.setUpdatedAt(previous);

        when(utilisateurRepository.findById(userId)).thenReturn(Optional.of(user));
        when(utilisateurRepository.save(ArgumentMatchers.any(Utilisateur.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Utilisateur saved = authService.updateRole(userId.toString(), Role.COACH);

        assertEquals(Role.COACH, saved.getRole());
        assertNotNull(saved.getUpdatedAt());
        assertTrue(saved.getUpdatedAt().isAfter(previous));
    }

    @Test
    void activateAndDeactivateShouldRefreshUpdatedAt() {
        UUID userId = UUID.randomUUID();
        Utilisateur user = baseUser(userId);
        user.setActive(false);
        LocalDateTime beforeActivate = LocalDateTime.now().minusDays(1);
        user.setUpdatedAt(beforeActivate);

        when(utilisateurRepository.findById(userId)).thenReturn(Optional.of(user));
        when(utilisateurRepository.save(ArgumentMatchers.any(Utilisateur.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRecordRepository.findActiveByUserId(userId.toString())).thenReturn(List.of());

        Utilisateur activated = authService.activateUser(userId.toString());
        assertTrue(activated.isActive());
        assertTrue(activated.getUpdatedAt().isAfter(beforeActivate));

        LocalDateTime beforeDeactivate = activated.getUpdatedAt();
        Utilisateur deactivated = authService.deactivateUser(userId.toString());
        assertFalse(deactivated.isActive());
        assertTrue(deactivated.getUpdatedAt().isAfter(beforeDeactivate));
    }

    private Utilisateur baseUser(UUID id) {
        Utilisateur user = new Utilisateur();
        user.setId(id);
        user.setEmail("user@test.com");
        user.setPasswordHash("encoded-password");
        user.setRole(Role.CLIENT);
        user.setNom("User");
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now().minusDays(10));
        user.setUpdatedAt(LocalDateTime.now().minusDays(5));
        return user;
    }

}
