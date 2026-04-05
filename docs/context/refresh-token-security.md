# Refresh Token Security — Implementation Guide

## 1. Secure Storage: HttpOnly Cookie

**Why**: Prevents XSS attacks from stealing refresh tokens.

- Refresh token **never** in response body
- Never accessible via JavaScript (`HttpOnly`)
- Sent only to `/api/auth` endpoints (`Path=/api/auth`)
- HTTPS-only in production (`Secure` flag)

## 2. Hashed Storage: Never Store Plaintext

**Why**: If database is compromised, attacker cannot use stolen refresh tokens.

Workflow:

**Issuance**:
1. Generate UUID → this is the raw token (sent in cookie)
2. SHA-256 hash the UUID → `tokenHash` stored in MySQL
3. Store raw token in Redis (fast lookup)

**Validation**:
1. Read token from cookie
2. SHA-256 hash it
3. Look up hash in MySQL
4. Also verify Redis entry matches (sliding window)

## 3. Rotation: Replay Protection

Every refresh rotates the token:

1. Invalidate old token:
   - `revoked=true` in MySQL `RefreshTokenRecord`
   - `DEL refresh:{userId}` from Redis

2. Issue new refresh token:
   - New UUID
   - New MySQL record (same `tokenFamily` UUID)
   - New raw token in Redis (TTL: 7 days)
   - Set new HttpOnly cookie

**Result**: Legitimate client always gets new token. Old token becomes unusable.

## 4. Sliding Window: Inactivity Expiry

Two independent expiry tracks:

| Mechanism | Duration | Purpose |
|---|---|---|
| Hard expiry | 30 days | Absolute lifetime limit (MySQL `expiresAt`) |
| Inactivity expiry | 7 days | Session ends after 7 days of no use (Redis TTL) |

Redis key: `refresh:{userId}`

On every successful refresh:
- Delete old Redis key
- Set new Redis key with 7-day TTL

If user stops using the app for 7 days, Redis key expires → session ends.

If user continues to use the app, sliding window resets → session remains active (up to 30 days max).

## 5. Revocation Triggers

Tokens are revoked immediately on:

- **Logout** (single session)
- **Password change** (all sessions)
- **Account deactivation** by admin (all sessions)
- **Replay attack detected** (entire token family)

Revocation actions:
- MySQL: `revoked=true` on all relevant `RefreshTokenRecord`s
- Redis: `DEL refresh:{userId}`
- Blacklist: `SETEX blacklist:{jti} {remainingTTL} "1"`

## 6. Token Family Tracking

**Why**: Detect when an attacker reuses a stolen refresh token after the legitimate user has rotated it.

- Each login creates a fresh `tokenFamily` UUID
- All refresh tokens from that login (including rotated ones) share the same `tokenFamily`
- If any token in a family is **revoked** (already invalidated) and then presented again:
  - Entire family is revoked (all `RefreshTokenRecord` with same `tokenFamily`)
  - `DEL refresh:{userId}`
  - Return `401 "Session compromise détectée"`
  - Log incident with userId and IP

This ensures that if a refresh token is stolen and the legitimate user logs out (rotating tokens), the stolen token becomes useless and any attempt to use it triggers a full session shutdown.
