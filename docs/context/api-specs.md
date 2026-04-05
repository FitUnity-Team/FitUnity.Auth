# API Specifications

## Endpoint Overview

The API Gateway strips `/api/auth` prefix before forwarding to this service.

| Path | Method | Auth Required | Access |
|---|---|---|---|
| `/register` | POST | No | Public |
| `/login` | POST | No | Public |
| `/refresh` | POST | No (uses cookie) | Public |
| `/logout` | POST | Yes (JWT) | Authenticated |
| `/profile` | GET | Yes (JWT) | Authenticated |
| `/profile` | PUT | Yes (JWT) | Authenticated |
| `/admin/users` | GET | Yes (ADMIN) | Admin only |
| `/admin/users/{id}/role` | PUT | Yes (ADMIN) | Admin only |
| `/admin/users/{id}/activate` | PUT | Yes (ADMIN) | Admin only |
| `/admin/users/{id}/deactivate` | PUT | Yes (ADMIN) | Admin only |

## Error Format

All errors return JSON:

```json
{
  "error": "ERROR_CODE",
  "message": "Human readable message",
  "timestamp": "2025-12-19T10:30:00Z"
}
```

### Status Codes

| Code | Meaning |
|---|---|
| 200 | Success |
| 201 | Created |
| 400 | Validation error |
| 401 | Authentication failed |
| 403 | Forbidden (disabled account or insufficient role) |
| 409 | Conflict (email exists) |
| 429 | Too many requests (login throttling) |

## GET /profile

Returns current user's profile with subscription info.

**Headers**: `Authorization: Bearer {accessToken}`

**Response** (200):

```json
{
  "id": "uuid-string",
  "email": "user@example.com",
  "nom": "Full Name",
  "role": "CLIENT",
  "active": true,
  "dateCreation": "2025-01-15T10:00:00Z",
  "abonnement": {
    "statut": "ACTIVE",
    "dateDebut": "2025-01-15T00:00:00Z",
    "dateFin": "2025-02-15T00:00:00Z"
  }
}
```

**Never includes `motDePasse`**.

## PUT /profile

Updates current user's display name.

**Headers**: `Authorization: Bearer {accessToken}`

**Body**:

```json
{
  "nom": "New Display Name"
}
```

**Response** (200): Updated user object (same format as GET).

## GET /admin/users

List all users with roles and subscription status. Paginated.

**Headers**: `Authorization: Bearer {accessToken}` (ADMIN required)

**Query params**:
- `page` (default 0)
- `size` (default 20)

**Response** (200):

```json
{
  "content": [
    {
      "id": "uuid",
      "email": "...",
      "nom": "...",
      "role": "CLIENT",
      "active": true,
      "dateCreation": "...",
      "abonnement": {
        "statut": "NONE",
        "dateDebut": null,
        "dateFin": null
      }
    }
  ],
  "pageable": { ... },
  "totalElements": 150
}
```

## PUT /admin/users/{id}/role

Change a user's role.

**Headers**: `Authorization: Bearer {accessToken}` (ADMIN required)

**Body**:

```json
{
  "role": "COACH"  // or "SUB_ADMIN"
}
```

**Response** (200): Updated user object.

**Also**:
- Updates MySQL
- SET `user_role:{userId}` in Redis (5 min TTL)

## PUT /admin/users/{id}/activate

Set `active=true` in MySQL. Returns 200 with updated user.

## PUT /admin/users/{id}/deactivate

Set `active=false` in MySQL and revoke all sessions:

- Find all non-revoked `RefreshTokenRecord` for user
- Set `revoked=true` on all
- `DEL refresh:{userId}` from Redis
- `DEL sessions:{userId}` from Redis

Returns 200 with updated user.
