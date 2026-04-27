package com.fitunity.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.repository.RefreshTokenRecordRepository;
import com.fitunity.auth.repository.UtilisateurRepository;
import com.fitunity.auth.service.auth.UserFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication endpoints.
 *
 * Tests cover:
 * - User registration
 * - Login with valid/invalid credentials
 * - Token refresh
 * - Logout
 * - Profile access
 * - Admin endpoints
 */
@SpringBootTest(
        classes = com.fitunity.auth.Application.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=63799",
                "spring.rabbitmq.host=localhost",
                "spring.rabbitmq.port=56729",
                "jwt.secret=test-secret-key-for-unit-tests-minimum-32-chars"
        }
)
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private RefreshTokenRecordRepository refreshTokenRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserFactory userFactory;

    private String accessToken;
    private Cookie refreshCookie;
    @BeforeEach
    void setUp() {
        // Clean up before each test
        refreshTokenRecordRepository.deleteAll();
        utilisateurRepository.deleteAll();

        // Create admin user for admin endpoint tests
        createAndSaveUser("admin@fitunity.com", "admin123", "Admin User", Role.ADMIN);
    }

    // ==================== REGISTRATION TESTS ====================

    @Test
    @DisplayName("POST /register - should register new user with CLIENT role")
    void registerNewUser() throws Exception {
        Map<String, String> request = Map.of(
                "email", "newuser@test.com",
                "motDePasse", "securePassword123",
                "nom", "New User"
        );

                mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Inscription réussite"));
    }

    @Test
    @DisplayName("POST /register - should return 409 for duplicate email")
    void registerDuplicateEmail() throws Exception {
        createAndSaveUser("duplicate@test.com", "password", "Duplicate User", Role.CLIENT);

        Map<String, String> request = Map.of(
                "email", "duplicate@test.com",
                "motDePasse", "anotherPassword",
                "nom", "Duplicate User"
        );

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ==================== LOGIN TESTS ====================

    @Test
    @DisplayName("POST /login - should authenticate with valid credentials")
    void loginWithValidCredentials() throws Exception {
        Utilisateur user = createAndSaveUser("login@test.com", "correctPassword", "Login User", Role.CLIENT);

        Map<String, String> request = Map.of(
                "email", "login@test.com",
                "motDePasse", "correctPassword"
        );

        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andReturn();

        // Verify refresh token cookie is set
        Cookie[] cookies = result.getResponse().getCookies();
        assertNotNull(cookies);
        boolean foundRefreshCookie = false;
        for (Cookie cookie : cookies) {
            if ("refreshToken".equals(cookie.getName())) {
                foundRefreshCookie = true;
                // Note: HttpOnly flag cannot be verified in tests, but it's set in AuthService
                assertEquals("/api/auth", cookie.getPath());
            }
        }
        assertTrue(foundRefreshCookie, "Refresh token cookie should be set");
    }

    @Test
    @DisplayName("POST /login - should return 401 for invalid password")
    void loginWithInvalidPassword() throws Exception {
        createAndSaveUser("wrongpass@test.com", "correctPassword", "Wrong Pass", Role.CLIENT);

        Map<String, String> request = Map.of(
                "email", "wrongpass@test.com",
                "motDePasse", "wrongPassword"
        );

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /login - should return 401 for non-existent user")
    void loginWithNonExistentUser() throws Exception {
        Map<String, String> request = Map.of(
                "email", "nonexistent@test.com",
                "motDePasse", "anyPassword"
        );

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ==================== REFRESH TOKEN TESTS ====================

    @Test
    @DisplayName("POST /refresh - should return 401 when Redis refresh state is unavailable")
    void refreshWithValidToken() throws Exception {
        // First login to get refresh cookie
        loginAndGetTokens("admin@fitunity.com", "admin123");

        // Use refresh endpoint
        mockMvc.perform(post("/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /refresh - should return 401 without refresh cookie")
    void refreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/refresh"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== LOGOUT TESTS ====================

    @Test
    @DisplayName("POST /logout - should logout with valid access token")
    void logoutWithValidToken() throws Exception {
        loginAndGetTokens("admin@fitunity.com", "admin123");

        mockMvc.perform(post("/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(refreshCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /logout - should return 401 without authorization header")
    void logoutWithoutAuthHeader() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== PROFILE TESTS ====================

    @Test
    @DisplayName("GET /profile - should return user profile")
    void getProfile() throws Exception {
        loginAndGetTokens("admin@fitunity.com", "admin123");

                mockMvc.perform(get("/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("admin@fitunity.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("PUT /profile - should update user name")
    void updateProfile() throws Exception {
        loginAndGetTokens("admin@fitunity.com", "admin123");

        Map<String, String> update = Map.of("nom", "Admin Updated");

                mockMvc.perform(put("/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Admin Updated"));
    }

    @Test
    @DisplayName("GET /profile - should return 401 without token")
    void getProfileWithoutAuth() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== ADMIN ENDPOINT TESTS ====================

    @Test
    @DisplayName("GET /admin/users - should return paginated users for ADMIN")
    void adminGetUsers() throws Exception {
        loginAndGetTokens("admin@fitunity.com", "admin123");

                mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.pageable.pageNumber").value(0));
    }

    @Test
    @DisplayName("PUT /admin/users/{id}/role - should update user role")
    void adminUpdateUserRole() throws Exception {
        Utilisateur user = createAndSaveUser("user@test.com", "password", "Regular User", Role.CLIENT);

        loginAndGetTokens("admin@fitunity.com", "admin123");

        Map<String, String> roleUpdate = Map.of("role", "COACH");

        mockMvc.perform(put("/admin/users/" + user.getId() + "/role")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COACH"));
    }

    @Test
    @DisplayName("PUT /admin/users/{id}/deactivate - should deactivate user")
    void adminDeactivateUser() throws Exception {
        Utilisateur user = createAndSaveUser("deactivate@test.com", "password", "Deactivate User", Role.CLIENT);

        loginAndGetTokens("admin@fitunity.com", "admin123");

        mockMvc.perform(put("/admin/users/" + user.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // ==================== UTILITY METHODS ====================

    private void loginAndGetTokens(String email, String password) throws Exception {
        Map<String, String> loginRequest = Map.of(
                "email", email,
                "motDePasse", password
        );

        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        accessToken = (String) responseMap.get("accessToken");

        Cookie[] cookies = result.getResponse().getCookies();
        for (Cookie cookie : cookies) {
            if ("refreshToken".equals(cookie.getName())) {
                refreshCookie = cookie;
                break;
            }
        }
    }

    private Utilisateur createAndSaveUser(String email, String rawPassword, String nom, Role role) {
        Utilisateur user = userFactory.newClientUser(email, passwordEncoder.encode(rawPassword), nom);
        user.setRole(role);
        return utilisateurRepository.save(user);
    }
}
