#!/usr/bin/env bash
# Runs on the EC2 host via SSM. Does not print secret values.
set -euo pipefail

BUCKET="${1:?bucket}"
KEY="${2:?key}"
EXPECTED_DIGEST="${3:?digest}"
TARGET_ENV="${4:?env}"

APP_ROOT="/opt/envguard"
RUNTIME_DIR="${APP_ROOT}/runtime"
RELEASES_DIR="${APP_ROOT}/releases"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
RELEASE_DIR="${RELEASES_DIR}/${TS}"

umask 077
mkdir -p "${RELEASE_DIR}" "${RUNTIME_DIR}"
chmod 700 "${RUNTIME_DIR}"

TMP_TAR="$(mktemp)"
trap 'rm -f "${TMP_TAR}"; rm -f "${RUNTIME_DIR}/.env.tmp" 2>/dev/null || true' EXIT

aws s3 cp "s3://${BUCKET}/${KEY}" "${TMP_TAR}"
DIGEST="$(sha256sum "${TMP_TAR}" | awk '{print $1}')"
if [[ "${DIGEST}" != "${EXPECTED_DIGEST}" ]]; then
  echo "Artifact digest mismatch" >&2
  exit 1
fi

tar -xzf "${TMP_TAR}" -C "${RELEASE_DIR}"

# Service runs as envguard — release tree must be traversable/readable by that user.
chown -R envguard:envguard "${RELEASE_DIR}"
find "${RELEASE_DIR}" -type d -exec chmod 755 {} +
find "${RELEASE_DIR}" -type f -exec chmod 644 {} +

# Resolve secrets into a restrictive runtime env file (never under web root).
# Prefer AWS Secrets Manager + IAM role on the instance.
if [[ -x "${APP_ROOT}/scripts/render-runtime-env.sh" ]]; then
  "${APP_ROOT}/scripts/render-runtime-env.sh" "${TARGET_ENV}" "${RUNTIME_DIR}/.env"
  chmod 600 "${RUNTIME_DIR}/.env"
  chown envguard:envguard "${RUNTIME_DIR}/.env" || true
fi

ln -sfn "${RELEASE_DIR}" "${APP_ROOT}/current"
chown -h envguard:envguard "${APP_ROOT}/current" || true

# Publish SPA for Nginx.
if [[ -d "${RELEASE_DIR}/frontend" ]]; then
  mkdir -p /var/www/envguard
  rsync -a --delete "${RELEASE_DIR}/frontend/" /var/www/envguard/
fi

sudo systemctl restart envguard-backend || true
sudo systemctl reload nginx || true

echo "Deploy complete: ${RELEASE_DIR}"
