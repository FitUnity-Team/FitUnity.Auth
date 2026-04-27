#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@test.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-AdminPass123!}"
ADMIN_NAME="${ADMIN_NAME:-Admin User}"

echo "==> Waiting for API at $BASE_URL ..."
for i in {1..60}; do
  if curl -fsS "$BASE_URL/v3/api-docs" >/dev/null 2>&1; then
    echo "API is up."
    break
  fi
  sleep 2
  if [[ "$i" -eq 60 ]]; then
    echo "ERROR: API did not become ready."
    exit 1
  fi
done

echo "==> Registering admin user (ok if already exists)..."
REGISTER_HTTP_CODE="$(curl -s -o /tmp/register_admin_response.json -w "%{http_code}" \
  -X POST "$BASE_URL/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"motDePasse\":\"$ADMIN_PASSWORD\",\"nom\":\"$ADMIN_NAME\"}")"

if [[ "$REGISTER_HTTP_CODE" == "201" ]]; then
  echo "Admin user registered."
elif [[ "$REGISTER_HTTP_CODE" == "409" ]]; then
  echo "Admin user already exists (409)."
else
  echo "ERROR: register failed (HTTP $REGISTER_HTTP_CODE)"
  cat /tmp/register_admin_response.json || true
  exit 1
fi

echo "==> Promoting admin in MySQL..."
docker compose exec -T mysql mysql -ufitunity -pfitunity_pass fitunity_auth \
  -e "UPDATE utilisateurs SET role='ADMIN', active=true WHERE email='${ADMIN_EMAIL}';"

echo "==> Verifying admin row..."
docker compose exec -T mysql mysql -ufitunity -pfitunity_pass fitunity_auth \
  -e "SELECT id,email,role,active FROM utilisateurs WHERE email='${ADMIN_EMAIL}';"

echo "==> Logging in as admin..."
LOGIN_RESPONSE="$(curl -s -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"motDePasse\":\"$ADMIN_PASSWORD\"}")"

echo "$LOGIN_RESPONSE" > /tmp/admin_login_response.json
ACCESS_TOKEN="$(python3 - <<'PY'
import json
try:
    with open('/tmp/admin_login_response.json') as f:
        data = json.load(f)
    print(data.get('accessToken', ''))
except Exception:
    print('')
PY
)"

if [[ -z "$ACCESS_TOKEN" ]]; then
  echo "ERROR: login did not return accessToken"
  cat /tmp/admin_login_response.json || true
  exit 1
fi

echo "Admin login OK."
echo "ACCESS_TOKEN saved to /tmp/admin_access_token.txt"
printf '%s\n' "$ACCESS_TOKEN" > /tmp/admin_access_token.txt

echo "==> Testing /admin/users ..."
curl -s -i "$BASE_URL/admin/users?page=0&size=20" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | sed -n '1,20p'

echo
echo "Done."
echo "Token file: /tmp/admin_access_token.txt"
echo "Use: export ADMIN_TOKEN=\"$(cat /tmp/admin_access_token.txt)\""
