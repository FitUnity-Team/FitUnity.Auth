#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@test.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-AdminPass123!}"
ADMIN_NAME="${ADMIN_NAME:-Admin User}"

SUFFIX="$(date +%s)"
USER_EMAIL="${USER_EMAIL:-e2e_user_${SUFFIX}@test.com}"
USER_PASSWORD="${USER_PASSWORD:-UserPass123!}"
USER_NAME="${USER_NAME:-E2E User}"

PASS=0
FAIL=0

ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

assert_code() {
  local name="$1" expected="$2" got="$3"
  if [[ "$got" == "$expected" ]]; then
    ok "$name (HTTP $got)"
  else
    fail "$name (expected $expected, got $got)"
  fi
}

extract_json_field() {
  local file="$1" field="$2"
  python3 - <<PY
import json
try:
    with open("$file") as f:
        data = json.load(f)
    print(data.get("$field", ""))
except Exception:
    print("")
PY
}

extract_refresh_cookie() {
  local header_file="$1"
  python3 - <<PY
import re
h = open("$header_file", "r", encoding="utf-8", errors="ignore").read()
m = re.search(r'(?im)^Set-Cookie:\\s*refreshToken=([^;]+);', h)
print(m.group(1) if m else "")
PY
}

echo "==> Waiting for API at $BASE_URL ..."
for i in {1..60}; do
  if curl -fsS "$BASE_URL/v3/api-docs" >/dev/null 2>&1; then
    ok "API readiness"
    break
  fi
  sleep 2
  if [[ "$i" -eq 60 ]]; then
    fail "API readiness timeout"
    echo "Summary: PASS=$PASS FAIL=$FAIL"
    exit 1
  fi
done

echo "==> Registering admin user (ok if already exists)..."
ADMIN_REGISTER_CODE="$(curl -s -o /tmp/admin_register.json -w "%{http_code}" \
  -X POST "$BASE_URL/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"motDePasse\":\"$ADMIN_PASSWORD\",\"nom\":\"$ADMIN_NAME\"}")"

if [[ "$ADMIN_REGISTER_CODE" == "201" || "$ADMIN_REGISTER_CODE" == "409" ]]; then
  ok "Admin register/create-or-exists (HTTP $ADMIN_REGISTER_CODE)"
else
  fail "Admin register/create-or-exists (HTTP $ADMIN_REGISTER_CODE)"
fi

echo "==> Promoting admin in MySQL..."
docker compose exec -T mysql mysql -ufitunity -pfitunity_pass fitunity_auth \
  -e "UPDATE utilisateurs SET role='ADMIN', active=true WHERE email='${ADMIN_EMAIL}';" >/dev/null
ok "Admin promotion SQL"

echo "==> Verifying admin row..."
docker compose exec -T mysql mysql -ufitunity -pfitunity_pass fitunity_auth \
  -e "SELECT id,email,role,active FROM utilisateurs WHERE email='${ADMIN_EMAIL}';"

echo "==> Registering standard user..."
USER_REGISTER_CODE="$(curl -s -o /tmp/user_register.json -w "%{http_code}" \
  -X POST "$BASE_URL/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$USER_EMAIL\",\"motDePasse\":\"$USER_PASSWORD\",\"nom\":\"$USER_NAME\"}")"
assert_code "POST /register (new user)" "201" "$USER_REGISTER_CODE"

DUP_REGISTER_CODE="$(curl -s -o /tmp/user_register_dup.json -w "%{http_code}" \
  -X POST "$BASE_URL/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$USER_EMAIL\",\"motDePasse\":\"$USER_PASSWORD\",\"nom\":\"$USER_NAME\"}")"
assert_code "POST /register (duplicate user)" "409" "$DUP_REGISTER_CODE"

echo "==> Admin login..."
ADMIN_LOGIN_CODE="$(curl -s -D /tmp/admin_login_headers.txt -o /tmp/admin_login.json -w "%{http_code}" \
  -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"motDePasse\":\"$ADMIN_PASSWORD\"}")"
assert_code "POST /login (admin)" "200" "$ADMIN_LOGIN_CODE"

ADMIN_TOKEN="$(extract_json_field /tmp/admin_login.json accessToken)"
ADMIN_REFRESH="$(extract_refresh_cookie /tmp/admin_login_headers.txt)"
[[ -n "$ADMIN_TOKEN" ]] && ok "Admin access token extracted" || fail "Admin access token extracted"
[[ -n "$ADMIN_REFRESH" ]] && ok "Admin refresh cookie extracted" || fail "Admin refresh cookie extracted"

echo "==> User login..."
USER_LOGIN_CODE="$(curl -s -D /tmp/user_login_headers.txt -o /tmp/user_login.json -w "%{http_code}" \
  -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$USER_EMAIL\",\"motDePasse\":\"$USER_PASSWORD\"}")"
assert_code "POST /login (user)" "200" "$USER_LOGIN_CODE"

USER_TOKEN="$(extract_json_field /tmp/user_login.json accessToken)"
USER_REFRESH="$(extract_refresh_cookie /tmp/user_login_headers.txt)"
[[ -n "$USER_TOKEN" ]] && ok "User access token extracted" || fail "User access token extracted"
[[ -n "$USER_REFRESH" ]] && ok "User refresh cookie extracted" || fail "User refresh cookie extracted"

BAD_LOGIN_CODE="$(curl -s -o /tmp/bad_login.json -w "%{http_code}" \
  -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$USER_EMAIL\",\"motDePasse\":\"wrongPass\"}")"
assert_code "POST /login (bad password)" "401" "$BAD_LOGIN_CODE"

PROFILE_CODE="$(curl -s -o /tmp/profile.json -w "%{http_code}" \
  -H "Authorization: Bearer $USER_TOKEN" \
  "$BASE_URL/profile")"
assert_code "GET /profile (user token)" "200" "$PROFILE_CODE"

PROFILE_NOAUTH_CODE="$(curl -s -o /tmp/profile_noauth.json -w "%{http_code}" \
  "$BASE_URL/profile")"
assert_code "GET /profile (no token)" "401" "$PROFILE_NOAUTH_CODE"

PROFILE_UPDATE_CODE="$(curl -s -o /tmp/profile_put.json -w "%{http_code}" \
  -X PUT "$BASE_URL/profile" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"nom\":\"$USER_NAME Updated\"}")"
assert_code "PUT /profile" "200" "$PROFILE_UPDATE_CODE"

REFRESH_CODE="$(curl -s -o /tmp/refresh.json -w "%{http_code}" \
  -X POST "$BASE_URL/refresh" \
  -H "Cookie: refreshToken=$USER_REFRESH")"
assert_code "POST /refresh (with cookie)" "200" "$REFRESH_CODE"

NEW_USER_TOKEN="$(extract_json_field /tmp/refresh.json accessToken)"
if [[ -n "$NEW_USER_TOKEN" ]]; then
  USER_TOKEN="$NEW_USER_TOKEN"
  ok "Refresh returned new access token"
else
  fail "Refresh returned new access token"
fi

REFRESH_NOCOOKIE_CODE="$(curl -s -o /tmp/refresh_no_cookie.json -w "%{http_code}" \
  -X POST "$BASE_URL/refresh")"
assert_code "POST /refresh (no cookie)" "401" "$REFRESH_NOCOOKIE_CODE"

ADMIN_USERS_CODE="$(curl -s -o /tmp/admin_users.json -w "%{http_code}" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE_URL/admin/users?page=0&size=50")"
assert_code "GET /admin/users (admin)" "200" "$ADMIN_USERS_CODE"

USER_ADMIN_USERS_CODE="$(curl -s -o /tmp/admin_users_user_token.json -w "%{http_code}" \
  -H "Authorization: Bearer $USER_TOKEN" \
  "$BASE_URL/admin/users?page=0&size=50")"
assert_code "GET /admin/users (non-admin)" "403" "$USER_ADMIN_USERS_CODE"

USER_ID="$(TARGET_EMAIL="$USER_EMAIL" python3 - <<'PY'
import json, os
target = os.environ["TARGET_EMAIL"]
try:
    data = json.load(open('/tmp/admin_users.json'))
    for u in data.get('content', []):
        if u.get('email') == target:
            print(u.get('id', ''))
            break
except Exception:
    pass
PY
)"

[[ -n "$USER_ID" ]] && ok "Resolved user ID for admin operations" || fail "Resolved user ID for admin operations"

if [[ -n "$USER_ID" ]]; then
  ROLE_COACH_CODE="$(curl -s -o /tmp/role_coach.json -w "%{http_code}" \
    -X PUT "$BASE_URL/admin/users/$USER_ID/role" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"role":"COACH"}')"
  assert_code "PUT /admin/users/{id}/role -> COACH" "200" "$ROLE_COACH_CODE"

  ROLE_ESTORE_CODE="$(curl -s -o /tmp/role_estore.json -w "%{http_code}" \
    -X PUT "$BASE_URL/admin/users/$USER_ID/role" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"role":"SUB_ADMIN-estore"}')"
  assert_code "PUT /admin/users/{id}/role -> SUB_ADMIN-estore" "200" "$ROLE_ESTORE_CODE"

  ROLE_TRAINING_CODE="$(curl -s -o /tmp/role_training.json -w "%{http_code}" \
    -X PUT "$BASE_URL/admin/users/$USER_ID/role" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"role":"SUB_ADMIN-training"}')"
  assert_code "PUT /admin/users/{id}/role -> SUB_ADMIN-training" "200" "$ROLE_TRAINING_CODE"

  DEACTIVATE_CODE="$(curl -s -o /tmp/deactivate.json -w "%{http_code}" \
    -X PUT "$BASE_URL/admin/users/$USER_ID/deactivate" \
    -H "Authorization: Bearer $ADMIN_TOKEN")"
  assert_code "PUT /admin/users/{id}/deactivate" "200" "$DEACTIVATE_CODE"

  ACTIVATE_CODE="$(curl -s -o /tmp/activate.json -w "%{http_code}" \
    -X PUT "$BASE_URL/admin/users/$USER_ID/activate" \
    -H "Authorization: Bearer $ADMIN_TOKEN")"
  assert_code "PUT /admin/users/{id}/activate" "200" "$ACTIVATE_CODE"
fi

LOGOUT_USER_CODE="$(curl -s -o /tmp/logout_user.json -w "%{http_code}" \
  -X POST "$BASE_URL/logout" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Cookie: refreshToken=$USER_REFRESH")"
assert_code "POST /logout (user)" "200" "$LOGOUT_USER_CODE"

LOGOUT_NOAUTH_CODE="$(curl -s -o /tmp/logout_noauth.json -w "%{http_code}" \
  -X POST "$BASE_URL/logout")"
assert_code "POST /logout (no auth)" "401" "$LOGOUT_NOAUTH_CODE"

LOGOUT_ADMIN_CODE="$(curl -s -o /tmp/logout_admin.json -w "%{http_code}" \
  -X POST "$BASE_URL/logout" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Cookie: refreshToken=$ADMIN_REFRESH")"
assert_code "POST /logout (admin)" "200" "$LOGOUT_ADMIN_CODE"

echo
echo "========================"
echo "Test summary: PASS=$PASS FAIL=$FAIL"
echo "========================"

if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi
