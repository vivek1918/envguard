#!/usr/bin/env bash
# Renders a runtime .env from non-secret YAML + Secrets Manager references.
# Secret values are written only to the destination file (mode 600), never echoed.
set -euo pipefail

TARGET_ENV="${1:?environment}"
OUT_FILE="${2:?output file}"
CONFIG_FILE="${ENVGUARD_CONFIG_FILE:-/opt/envguard/deploy/env/${TARGET_ENV}.yaml}"

if [[ ! -f "${CONFIG_FILE}" ]]; then
  echo "Missing config: ${CONFIG_FILE}" >&2
  exit 1
fi

python3 - <<'PY' "${CONFIG_FILE}" "${OUT_FILE}"
import sys, yaml, subprocess, tempfile, os

config_path, out_path = sys.argv[1], sys.argv[2]
with open(config_path, "r", encoding="utf-8") as f:
    data = yaml.safe_load(f)

lines = []
for k, v in (data.get("variables") or {}).items():
    lines.append(f"{k}={v}")

for k, ref in (data.get("secretReferences") or {}).items():
    # Fetch secret value without printing it
    proc = subprocess.run(
        ["aws", "secretsmanager", "get-secret-value", "--secret-id", ref, "--query", "SecretString", "--output", "text"],
        check=True,
        capture_output=True,
        text=True,
    )
    value = proc.stdout.rstrip("\n")
    # basic escaping for .env
    value = value.replace("\\", "\\\\").replace("\"", "\\\"")
    lines.append(f'{k}="{value}"')

fd, tmp = tempfile.mkstemp(dir=os.path.dirname(out_path) or ".", text=True)
try:
    with os.fdopen(fd, "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")
    os.chmod(tmp, 0o600)
    os.replace(tmp, out_path)
finally:
    if os.path.exists(tmp):
        try:
            os.remove(tmp)
        except OSError:
            pass
PY
