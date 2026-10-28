#!/usr/bin/env bash
# Bootstrap an administrator via environment variables on first start.
# Never commit real passwords. Prefer one-time use then unset the vars.
set -euo pipefail

if [[ -z "${ENVGUARD_BOOTSTRAP_ADMIN_USER:-}" || -z "${ENVGUARD_BOOTSTRAP_ADMIN_PASSWORD:-}" ]]; then
  echo "Set ENVGUARD_BOOTSTRAP_ADMIN_USER and ENVGUARD_BOOTSTRAP_ADMIN_PASSWORD"
  exit 1
fi

export ENVGUARD_BOOTSTRAP_ADMIN_EMAIL="${ENVGUARD_BOOTSTRAP_ADMIN_EMAIL:-admin@example.com}"
echo "Start the backend with these env vars set. The AdminBootstrap runner creates the user only if no ADMINISTRATOR exists."
echo "After successful login, unset the bootstrap variables and restart."
