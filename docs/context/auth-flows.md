# Authentication Flows

## Register Flow

**Request**: `POST /register`

```json
{
  "email": "user@example.com",
  "motDePasse": "securePassword123",
  "nom": "John Doe"
}
```

**Process**:
1. Check if email exists in MySQL
   - If exists → `409 Conflict`
2. Hash password with BCrypt
3. Create `Utilisateur` with:
   - `role = CLIENT`
   - `active = true`
4. Create `Abonnement` with `statut = NONE`
5. Return `201 Created`:

```json
{
  "message": "Inscription réussie"
}
```

---

## Login Flow

**Request**: `POST /login`

```json
{
  "email": "user@example.com",
  "motDePasse": "securePassword123"
}
```

**Steps**:

1. Check Redis: `GET login_attempts:{email}`
   - If count > 5 → `429 Too Many Requests`

2. Find user in MySQL by email
   - If not found: increment counter, return `401` with generic "Email ou mot de passe incorrect"

3. Verify BCrypt password
   - If invalid: increment counter, return `401` generic

4. Check `active` flag
   - If false → `403 Forbidden`

5. On success: clear `login_attempts:{email}` (Redis)

6. Fetch user's `Abonnement` (for `statutAbonnement` claim)

7. Generate tokens:
   - `jti` = random UUID (for this access token)
   - `tokenFamily` = new UUID (for this login session)
   - Access token (JWT, 15 min expiry)
   - Refresh token = raw UUID (will be stored only in HttpOnly cookie)

8. Hash refresh token with SHA-256 → `tokenHash`

9. Persist to MySQL:
   - `RefreshTokenRecord(tokenHash, tokenFamily, expiresAt=now+30d, lastUsedAt=now, revoked=false)`

10. Redis:
    - `SETEX refresh:{userId} 604800 {tokenHash}` (7 days sliding)
    - `SADD sessions:{userId} {jti}` (no expiry, 30 days max lifetime)

11. Set HttpOnly cookie on response:
    ```
    Set-Cookie: refreshToken={rawUUID}; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=2592000
    ```

12. Return `200` body (access token **in body**):

```json
{
  "accessToken": "eyJhbG...",
  "userId": "uuid...",
  "email": "user@example.com",
  "role": "CLIENT",
  "statutAbonnement": "NONE"
}
```

---

## Refresh Token Flow

**Request**: `POST /refresh` (no body)

**Cookie sent**: `refreshToken={rawToken}`

**Steps**:

1. Read `refreshToken` from HttpOnly cookie
   - Missing → `401`

2. Compute SHA-256 hash of raw token

3. Query MySQL: find `RefreshTokenRecord` where `tokenHash = {hash}`
   - Not found → `401`
   - Found but `revoked = true` → **REPLAY ATTACK** (see below)
   - `expiresAt < now` → `401 "Session expirée"`

4. Check Redis: `GET refresh:{userId}`
   - Not found (inactivity window expired) → `401`
   - Hash mismatch → `401` (should not happen if DB/Redis are in sync)

5. **Valid** → rotate token:

   a. Calculate remaining TTL of current access token (for blacklist)

   b. Blacklist current access token: `SETEX blacklist:{jti} {remainingTTL} "1"`

   c. Generate new `jti` and new access token

   d. Generate new refresh token UUID

   e. SHA-256 hash new token

   f. Invalidate old record:
      - Set `revoked=true` on old `RefreshTokenRecord`

   g. Create new `RefreshTokenRecord`:
      - Same `tokenFamily` (links to original login)
      - New `tokenHash`
      - `expiresAt` = **original** `expiresAt` (30 days from first login, NOT extended)
      - `lastUsedAt = now`
      - `revoked = false`

   h. Redis updates:
      - `DEL refresh:{userId}` (old)
      - `SETEX refresh:{userId} 604800 {newTokenHash}` (reset sliding window)
      - `SADD sessions:{userId} {newJti}`

   i. Set new HttpOnly cookie with new raw refresh token

6. Return `200`:

```json
{
  "accessToken": "eyJhbG..."
}
```

---

## Logout Flow

**Request**: `POST /logout`

**Headers**: `Authorization: Bearer {accessToken}`

**Steps**:

1. Extract `jti` and `userId` from JWT (validated by Spring Security)

2. Calculate remaining access token TTL (from `exp` claim)

3. Blacklist: `SETEX blacklist:{jti} {remainingTTL} "1"`

4. Redis: `SREM sessions:{userId} {jti}`

5. Read refresh token from HttpOnly cookie

6. Compute SHA-256 hash of cookie token

7. Update MySQL: set `revoked=true` where `tokenHash = {hash}`

8. Redis: `DEL refresh:{userId}`

9. Clear cookie: `Set-Cookie: refreshToken=; Max-Age=0; Path=/api/auth`

10. Return `200`:

```json
{
  "message": "Déconnexion réussie"
}
```

---

## Replay Attack Detection

**Scenario**: Attacker obtains a refresh token after victim logged out (token was already rotated and invalidated). Attacker presents revoked token to `/refresh`.

**Detection**:
1. In Step 3 of `/refresh`, `tokenHash` lookup finds record with `revoked = true`

**Actions**:
1. Block the request: return `401 "Session compromise détectée"`

2. Find **all** `RefreshTokenRecord` with same `tokenFamily` (for this user)
   - Set `revoked=true` on all of them (compromised entire session family)

3. `DEL refresh:{userId}` from Redis (kill all session tracking)

4. Log incident with:
   - `userId`
   - Client IP (from request)
   - Timestamp
   - `tokenFamily`

5. Return `401` response

**Effect**: Attacker can't reuse the stolen token; legitimate user is logged out everywhere (defensive reaction).

---

## Security Notes

- **Generic errors** on login failure always: "Email ou mot de passe incorrect" (no distinction between "user not found" and "wrong password")
- **Password handling**: BCrypt only; never in responses; never in logs
- **JWT validation**: Done in `JwtAuthenticationFilter` using `TokenService`
- **Redis operations**: All wrapped in try-catch; failures log warning but don't crash flow (except critical failures in login may fail without Redis? depends on strictness)
