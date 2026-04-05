# Domain Model

## MySQL Entities

### Utilisateur

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| email | String | Unique, NOT NULL |
| motDePasse | String | BCrypt hashed, NOT NULL |
| nom | String | NOT NULL |
| role | Enum | CLIENT, COACH, SUB_ADMIN, ADMIN (default: CLIENT) |
| active | Boolean | default true |
| dateCreation | DateTime | |

### Abonnement

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| utilisateurId | UUID | FK -> Utilisateur |
| statut | Enum | ACTIVE, EXPIREE, NONE (default: NONE) |
| dateDebut | DateTime | Nullable |
| dateFin | DateTime | Nullable |

### RefreshTokenRecord

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| utilisateurId | UUID | FK -> Utilisateur |
| tokenHash | String | SHA-256 hash of raw refresh token, NOT NULL |
| tokenFamily | UUID | Family ID for replay detection, NOT NULL |
| expiresAt | DateTime | Hard expiry: 30 days from issuance |
| lastUsedAt | DateTime | Sliding window tracking |
| revoked | Boolean | default false |

**CRITICAL**: The raw refresh token string is NEVER stored. Only its SHA-256 hash is persisted in MySQL. Redis holds the raw token temporarily for fast lookup.

## Roles Enum

`CLIENT`, `COACH`, `SUB_ADMIN`, `ADMIN`

## StatutAbonnement Enum

`ACTIVE`, `EXPIREE`, `NONE`

## Redis Keys

| Key | Value | TTL | Lifecycle |
|---|---|---|---|
| `refresh:{userId}` | SHA-256 hash of current refresh token | 7 days, sliding | Written on login/rotate, deleted on logout/password-change/replay |
| `blacklist:{jti}` | `"1"` | Remaining access token TTL | Written on logout only |
| `login_attempts:{email}` | Integer counter | 15 minutes | Incremented on failure, cleared on success. >5 => 429 |
| `sessions:{userId}` | Set of jti strings | 30 days | SADD on login, SREM on single logout, DEL on logout-all |
| `user_role:{userId}` | Role string | 5 minutes | Written when admin changes role |
| `user_sub:{userId}` | Subscription status string | Matches subscription period | Written when subscription changes |
