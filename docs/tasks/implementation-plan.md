# Implementation Plan

## Phase 1: Project Setup
1. Create complete package structure under `src/main/java/com/fitunity/auth/`
2. Update `pom.xml` with all required dependencies
3. Create `application.properties` and `application-dev.properties`

## Phase 2: Domain Layer
4. Create all enums: `Role`, `StatutAbonnement`
5. Create entity classes:
   - `Utilisateur`
   - `Abonnement`
   - `RefreshTokenRecord`
6. Create repository interfaces with JPA query methods

## Phase 3: Security Foundation
7. Create `SecurityConfig` with:
   - Stateless session management
   - CORS configuration
   - Public vs protected endpoint mappings
   - `JwtAuthenticationFilter` bean
8. Implement `JwtAuthenticationFilter` extending `OncePerRequestFilter`
9. Create `JwtUserDetails` implements `UserDetails`

## Phase 4: Token Services
10. Implement `TokenService`:
    - `generateAccessToken(UserDetails, StatutAbonnement)`
    - `validateToken(String)`
    - `extractUserId(String)`, `extractRole(String)`, etc.
    - `calculateRemainingTtl(String)`
    - Use `JJWT` library (version 0.12.6)
11. Implement `RedisService`:
    - All Redis operations with try-catch
    - `getRefreshTokenHash(userId)`
    - `setRefreshTokenHash(userId, hash, ttl)`
    - `deleteRefreshToken(userId)`
    - `incrementLoginAttempts(email)`
    - `getLoginAttempts(email)`
    - `clearLoginAttempts(email)`
    - `addSession(userId, jti)`
    - `removeSession(userId, jti)`
    - `blacklistToken(jti, ttl)`
    - `setRoleOverride(userId, role, ttl)`

## Phase 5: Business Logic
12. Implement `AuthService`:
    - `register(RegisterRequest)`
    - `login(LoginRequest, HttpServletResponse)`
    - `refresh(HttpServletRequest, HttpServletResponse)`
    - `logout(String accessToken, HttpServletRequest, HttpServletResponse)`
    - Each method must follow spec exactly (token rotation, replay detection, etc.)

## Phase 6: Controllers
13. `AuthController`: register, login, refresh endpoints
14. `ProfileController`: GET/PUT /profile
15. `AdminController`: all /admin/** endpoints

## Phase 7: Cross-Cutting
16. `GlobalExceptionHandler`: Return error format (error, message, timestamp)
17. Custom exceptions: `EmailExistsException`, `InvalidCredentialsException`, `AccountDisabledException`, `ReplayAttackException`, `SessionExpiredException`, `TooManyAttemptsException`, etc.

## Phase 8: Infrastructure
18. `Dockerfile` (multi-stage build)
19. `docker-compose.yml` with health checks and network
20. `.env` with placeholder secrets
21. `RedisConfig` Java config class

## Phase 9: Verification
22. `mvn clean package` (0 errors)
23. Update TASKS.md with completion status
24. Update CLAUDE.md with actual implementation notes
25. Write README.md with run instructions
