#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@test.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-AdminPass123!}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"
PROFILE_RATE="${PROFILE_RATE:-140}"
ADMIN_RATE="${ADMIN_RATE:-40}"
LOGIN_RATE="${LOGIN_RATE:-20}"
TEST_DURATION="${TEST_DURATION:-60s}"

extract_access_token_from_json() {
  local json_payload="$1"
  LOGIN_JSON_PAYLOAD="$json_payload" python3 - <<'PY'
import json
import os

raw = os.environ.get("LOGIN_JSON_PAYLOAD", "")
try:
    data = json.loads(raw)
    print(data.get("accessToken", ""))
except Exception:
    print("")
PY
}

echo "==> Checking API readiness..."
for i in {1..30}; do
  if curl -fsS "${BASE_URL}/v3/api-docs" >/dev/null 2>&1; then
    echo "API is up."
    break
  fi
  sleep 2
  if [[ "$i" -eq 30 ]]; then
    echo "ERROR: API is not reachable at ${BASE_URL}" >&2
    exit 1
  fi
done

if [[ -z "${ADMIN_TOKEN}" && -f /tmp/admin_access_token.txt ]]; then
  ADMIN_TOKEN="$(cat /tmp/admin_access_token.txt)"
fi

if [[ -z "${ADMIN_TOKEN}" ]]; then
  echo "==> No ADMIN_TOKEN provided; logging in as ${ADMIN_EMAIL}..."
  LOGIN_JSON="$(curl -sS -X POST "${BASE_URL}/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${ADMIN_EMAIL}\",\"motDePasse\":\"${ADMIN_PASSWORD}\"}")"

  ADMIN_TOKEN="$(extract_access_token_from_json "${LOGIN_JSON}")"
fi

if [[ -n "${ADMIN_TOKEN}" ]]; then
  TOKEN_STATUS="$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${BASE_URL}/profile")"

  if [[ "${TOKEN_STATUS}" != "200" ]]; then
    echo "==> Existing token is invalid/expired (HTTP ${TOKEN_STATUS}); refreshing via login..."
    LOGIN_JSON="$(curl -sS -X POST "${BASE_URL}/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"${ADMIN_EMAIL}\",\"motDePasse\":\"${ADMIN_PASSWORD}\"}")"

    ADMIN_TOKEN="$(extract_access_token_from_json "${LOGIN_JSON}")"
  fi
fi

if [[ -z "${ADMIN_TOKEN}" ]]; then
  echo "ERROR: Could not obtain admin access token." >&2
  echo "Run ./setup-admin-and-test.sh first or export ADMIN_TOKEN." >&2
  exit 1
fi

echo "==> Running mixed load test"
echo "    BASE_URL=${BASE_URL}"
echo "    TEST_DURATION=${TEST_DURATION}"
echo "    RATES profile/admin/login=${PROFILE_RATE}/${ADMIN_RATE}/${LOGIN_RATE} req/s"

if command -v k6 >/dev/null 2>&1; then
  BASE_URL="${BASE_URL}" \
  ADMIN_TOKEN="${ADMIN_TOKEN}" \
  ADMIN_EMAIL="${ADMIN_EMAIL}" \
  ADMIN_PASSWORD="${ADMIN_PASSWORD}" \
  PROFILE_RATE="${PROFILE_RATE}" \
  ADMIN_RATE="${ADMIN_RATE}" \
  LOGIN_RATE="${LOGIN_RATE}" \
  TEST_DURATION="${TEST_DURATION}" \
  k6 run k6-load-test.js
  exit 0
fi

if command -v docker >/dev/null 2>&1; then
  echo "k6 binary not found; using k6 Docker image..."
  docker run --rm --network host \
    -e BASE_URL="${BASE_URL}" \
    -e ADMIN_TOKEN="${ADMIN_TOKEN}" \
    -e ADMIN_EMAIL="${ADMIN_EMAIL}" \
    -e ADMIN_PASSWORD="${ADMIN_PASSWORD}" \
    -e PROFILE_RATE="${PROFILE_RATE}" \
    -e ADMIN_RATE="${ADMIN_RATE}" \
    -e LOGIN_RATE="${LOGIN_RATE}" \
    -e TEST_DURATION="${TEST_DURATION}" \
    -v "$(pwd)/k6-load-test.js:/k6-load-test.js:ro" \
    grafana/k6 run /k6-load-test.js
  exit 0
fi

echo "ERROR: Neither 'k6' nor Docker is installed." >&2
echo "Install one of them, then run ./load-test.sh again." >&2
exit 1
