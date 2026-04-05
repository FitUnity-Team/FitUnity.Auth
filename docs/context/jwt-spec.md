# JWT Specification

## Access Token

- **Algorithm**: HS256
- **Expiry**: 15 minutes (configurable via properties)
- **Required Claims**:

| Claim | Value | Notes |
|---|---|---|
| `userId` | Utilisateur.id | String, NOT standard sub |
| `role` | Utilisateur.role | String, e.g., "CLIENT" |
| `statutAbonnement` | Abonnement.statut | String, e.g., "ACTIVE" |
| `jti` | UUID | Unique token ID for blacklisting |
| `iss` | "fitunity-auth" | Issuer |
| `aud` | "fitunity-client" | Audience |
| `exp` | Timestamp | Expiry |
| `iat` | Timestamp | Issued at |

**IMPORTANT**: The API Gateway reads these exact claim names: `userId`, `role`, `statutAbonnement`, `jti`. Do not rename or use standard claim aliases.

## Refresh Token

- Stored **only** in HttpOnly cookie
- Format: Random UUID string
- Storage: Only its SHA-256 hash in MySQL `RefreshTokenRecord.tokenHash`
- Redis: Raw token stored temporarily (TTL: 7 days sliding window)

## Cookie Spec

```
Set-Cookie: refreshToken={token}; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=2592000
```

### Attributes

| Attribute | Value | Purpose |
|---|---|---|
| HttpOnly | true | JavaScript cannot read |
| Secure | true (dev: false) | HTTPS-only |
| SameSite | Strict | CSRF protection |
| Path | /api/auth | Only sent to auth endpoints |
| Max-Age | 2592000 (30 days) | Hard maximum lifetime |
