# Security Checklist

## Password Security
- [x] BCrypt hashing (never plaintext)
- [x] Password never in any response
- [ ] Password strength validator on register (optional, out of scope?)

## JWT Security
- [x] Claims exactly: `userId`, `role`, `statutAbonnement`, `jti`
- [x] HS256 algorithm
- [x] Short access token expiry (15 min)
- [x] JWT secret from environment ONLY (no defaults in code)
- [ ] Token blacklist protects against logout token reuse
- [ ] Blacklist TTL matches remaining token lifetime
- [ ] Gateway validates tokens using same secret

## Refresh Token Security
- [x] HttpOnly cookie only (never in response body)
- [x] Secure flag (HTTPS only in prod)
- [x] SameSite=Strict
- [x] Path=/api/auth (scoped)
- [x] Max-Age=30 days
- [x] SHA-256 hashing before storage in MySQL
- [x] Raw token never stored in MySQL
- [x] Token rotation on every refresh
- [x] Replay attack detection (detect revoked token reuse)
- [x] Token family revocation (entire session compromised)
- [x] Hard expiry 30 days (MySQL expiresAt)
- [x] Inactivity expiry 7 days sliding (Redis TTL)
- [x] Revocation on logout
- [x] Revocation on account deactivation
- [ ] Revocation on password change (if UserController added later)

## Redis Operations
- [ ] All Redis calls wrapped in try-catch
- [ ] Log warnings on Redis failures, don't crash
- [ ] Use connection pooling (Spring Data Redis default)
- [ ] All keys have appropriate TTLs
- [ ] No sensitive data in Redis (only hashes or blacklist markers)

## Performance
- [ ] Refresh token lookup uses Redis for fast sliding window
- [ ] Fallback to MySQL if Redis miss? (depends on implementation)
- [ ] Token family queries indexed by `tokenFamily`

## Attack Prevention
- [x] Login throttling (5 attempts / 15 min)
- [x] Generic error on login failure (no user enumeration)
- [ ] CSRF protection: SameSite=Strict + stateless JWT
- [x] Replay attack detection with family revocation
- [x] Session fixation prevented via token rotation
- [ ] Rate limiting on refresh endpoint? (optional, may not be needed)
- [x] IP logging on replay detection

## Authorization
- [x] Sub-admin cannot access /admin/**
- [x] ADMIN can access /admin/**
- [ ] Role changes propagate via Redis cache override (5 min TTL)
- [ ] Subscription status changes use Redis override

## Secrets Management
- [ ] .env file in .gitignore
- [ ] No secrets in application.properties
- [ ] JWT_SECRET min 32 characters in production
- [ ] Redis password configured
- [ ] MySQL credentials configured

## Docker Security
- [ ] Only external port exposed: 5020 (api-gateway)
- [ ] MySQL and Redis only exposed for dev debugging
- [ ] No root user in containers
- [ ] Health checks on critical dependencies
- [ ] Bridge network for isolated service communication

## Logging & Monitoring
- [ ] No sensitive data in logs (passwords, tokens, cookies)
- [ ] Replay attacks logged with userId and IP
- [ ] Login failures logged (but not which reason to prevent user enumeration)
- [ ] Successful authentications logged (userId, IP, timestamp)

## Data Validation
- [ ] Request bodies validated with `@Valid`
- [ ] Email format validation
- [ ] Password minimum length (business logic, not enforced?)

## Database
- [ ] Foreign keys: Abonnement.utilisateurId -> Utilisateur.id
- [ ] Index on RefreshTokenRecord.tokenHash
- [ ] Index on RefreshTokenRecord.tokenFamily
- [ ] Index on RefreshTokenRecord.utilisateurId
- [ ] Index on RefreshTokenRecord.revoked for cleanup queries
- [ ] Automatic cleanup of expired/revoked refresh tokens (cron? deferred)
