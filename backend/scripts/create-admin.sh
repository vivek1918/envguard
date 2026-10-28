#!/usr/bin/env bash
# Bootstrap an EnvGuard administrator.
# Preferred options:
#   1) First POST /api/auth/register creates ADMINISTRATOR automatically when no users exist.
#   2) Set ENVGUARD_BOOTSTRAP_ADMIN_USER + ENVGUARD_BOOTSTRAP_ADMIN_PASSWORD before starting the app.
#   3) This script registers via HTTP (first user path).
set -euo pipefail

ENVGUARD_URL="${ENVGUARD_URL:-http://localhost:8080}"
USERNAME="${ENVGUARD_BOOTSTRAP_ADMIN_USER:?Set ENVGUARD_BOOTSTRAP_ADMIN_USER}"
PASSWORD="${ENVGUARD_BOOTSTRAP_ADMIN_PASSWORD:?Set ENVGUARD_BOOTSTRAP_ADMIN_PASSWORD}"
EMAIL="${ENVGUARD_BOOTSTRAP_ADMIN_EMAIL:-${USERNAME}@localhost}"

curl -sS -X POST "${ENVGUARD_URL}/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${USERNAME}\",\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}"
echo
