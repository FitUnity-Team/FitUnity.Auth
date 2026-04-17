# Auth Service SRP + Performance + OAuth Design

Date: 2026-04-17
Status: Approved design (pre-implementation)
Owner: FitUnity.Auth

## 1) Goal

Improve backend speed and maintainability with minimal churn while keeping existing API contracts stable.

Primary targets:

- Throughput target: ~200 req/s stable
- Latency target: p95 <= 500ms
- Traffic mix target: 70% `/profile`, 20% `/admin/users`, 10% `/login`
- Error-rate target: < 1%

Secondary enhancement:

- Add Google OAuth 2.0 login using backend redirect + callback flow

## 2) Constraints And Non-Goals

Constraints:

- Keep endpoint contracts backward-compatible (paths, payloads, status behavior)
- Code-only optimization pass (no DB schema/index changes)
- Keep changes readable and minimal; avoid broad rewrites

Non-goals:

- No frontend architecture changes
- No multi-provider OAuth in this pass (Google only)
- No major platform/runtime migration

## 3) Design Principles

- Strict SRP boundaries: entities = data only; logic lives in services/factories/mappers
- Keep controllers thin
- Keep repositories focused on persistence queries only
- Optimize hot paths with small, measurable improvements over speculative complexity

## 4) Architecture Boundaries (Target State)

### 4.1 Controllers

- Keep `AuthController`, `ProfileController`, `AdminController`
- Responsibilities: HTTP orchestration only (input/output handoff)
- No DTO/map construction logic inside controllers beyond simple delegation

### 4.2 Entities

- `Utilisateur`, `RefreshTokenRecord` are anemic JPA models only
- Remove behavioral defaults/lifecycle business decisions from entities
- Service/factory layer assigns defaults and timestamps explicitly

### 4.3 Repositories

- Query and persistence operations only
- No business branching or response shaping

### 4.4 Services

- `AuthService` remains the facade/orchestrator for auth use cases
- Internal details move to focused collaborators (below)

## 5) Component Map (Minimal-Churn Refactor)

### 5.1 Keep

- `AuthService` (workflow orchestration)
- Existing interfaces (`AuthFacade`, `TokenProvider`, `RedisStore`, `UserIdentityService`)

### 5.2 Add

- `AuthResponseMapper`
  - Builds `AuthResponse`, `RefreshResponse`, and user payload maps reused by profile/admin
- `RefreshTokenManager`
  - Handles hashing/compare, record creation/rotation, family revocation helpers
- `RefreshCookieService`
  - Single place for set/clear/extract refresh cookie behavior
- `SubscriptionStatusResolver`
  - Normalizes values to `ACTIVE|EXPIREE|NONE`
  - Uses tiny in-process cache (very short TTL) for burst read efficiency
- `UserViewMapper` (or similarly named)
  - Central mapping for profile/admin user output shape

## 6) Data And Request Flow

### 6.1 Login

1. Validate attempts, credentials, and account status
2. Resolve subscription status via resolver
3. Issue access token via existing token provider
4. Create + persist refresh-token record via manager
5. Set refresh cookie via cookie service
6. Build response via mapper

Performance effect:

- Fewer duplicate code paths
- Less repeated mapping and normalization logic

### 6.2 Refresh

1. Extract cookie token via cookie service
2. Hash + lookup + verify via manager/repositories
3. Keep replay and rotation semantics unchanged
4. Re-issue tokens and response through shared collaborators

Performance effect:

- Reduced object churn and duplicated branching
- Clear single-path token lifecycle logic

### 6.3 Profile/Admin Reads (Hot Paths)

- Shared user mapping logic
- Shared subscription-status normalization
- Short-lived local cache for normalized subscription status to reduce repetitive Redis reads under bursts

## 7) Error Handling Contract

No contract change.

Error payload remains:

```json
{
  "status": 401,
  "error": "ERROR_CODE",
  "message": "...",
  "timestamp": "ISO-8601"
}
```

Existing status semantics are preserved.

## 8) OAuth 2.0 Enhancement (Google Only)

### 8.1 Endpoints (New)

- `GET /oauth2/authorize/google`
  - Starts OAuth flow by redirecting to Google auth endpoint
- `GET /oauth2/callback/google`
  - Handles callback, exchanges code, validates Google token claims, resolves local user, issues local JWT + refresh cookie

### 8.2 Flow Model

- Backend redirect + callback (server-side)
- State parameter required and validated
- Claim checks required: issuer, audience, expiry, `email_verified`

### 8.3 Account Linking Rule

- If Google email already exists in local DB: auto-link and sign in
- Else create local user:
  - `email` from Google
  - `nom` from Google profile (fallback email prefix)
  - `role=CLIENT`
  - `active=true`

### 8.4 Service Boundaries For OAuth

- `GoogleOAuthService`
  - authorize URL generation, callback exchange, token validation
- `OAuthAccountResolver`
  - local-user link-or-create policy
- Reuse existing local token issuance and session/cookie behavior

### 8.5 Compatibility

- Existing local login/register/refresh/logout remain unchanged

### 8.6 Callback Frontend Handoff

- Callback endpoint returns HTTP redirect to configured frontend URL (environment-driven)
- Success redirect includes `oauth=success`
- Failure redirect includes `oauth=error` plus a short error code (`invalid_state`, `token_validation_failed`, `email_not_verified`, `internal_error`)
- Access token remains delivered via backend JSON endpoints; refresh token remains cookie-based

## 9) Performance Validation Plan

### 9.1 Baseline And After-Change Runs

- Same environment and settings for before/after runs
- Load mix: 70/20/10 (`/profile`, `/admin/users`, `/login`)
- Measure: throughput, p95 latency, error rate

### 9.2 Acceptance Criteria

- >= 200 req/s stable
- p95 <= 500ms
- error rate < 1%
- no API contract regressions

### 9.3 Measurement Method

- Use the existing load scripts and the same machine/container profile for all comparisons
- Record before/after numbers for throughput, p95, and error rate in the PR description
- Treat any run with token-auth failures as invalid and rerun with a fresh token

## 10) Testing Strategy

- Keep existing integration tests as contract guard
- Add unit tests for new collaborators:
  - `RefreshTokenManager`
  - `RefreshCookieService`
  - `SubscriptionStatusResolver`
  - `AuthResponseMapper`/`UserViewMapper`
  - `GoogleOAuthService` and `OAuthAccountResolver`
- Add focused OAuth tests:
  - happy path login/link/create
  - invalid state
  - invalid/expired token claims
  - unverified email rejection behavior

## 11) Rollout Plan

1. SRP refactor with no contract change
2. OAuth endpoints and services (Google only)
3. Performance run + regression test run
4. If target missed, do one additional minimal tuning iteration based on measured bottleneck

## 12) Risks And Mitigations

- Risk: hidden behavioral changes during refactor
  - Mitigation: preserve integration tests + payload snapshots
- Risk: OAuth callback security mistakes
  - Mitigation: strict state and claim validation tests
- Risk: stale local cache values for subscription status
  - Mitigation: very short TTL; Redis remains source of truth

## 13) Definition Of Done

- SRP boundaries improved as specified
- Entity classes are data-only
- Endpoints and payloads remain backward-compatible
- Google OAuth endpoints functional with auto-link-by-email
- Performance acceptance criteria met on agreed load mix
