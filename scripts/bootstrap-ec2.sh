#!/usr/bin/env bash
# Initial EC2 host preparation for EnvGuard (Amazon Linux 2023 / Ubuntu).
set -euo pipefail

APP_ROOT=/opt/envguard
sudo mkdir -p "${APP_ROOT}"/{scripts,releases,runtime,deploy/env,logs}
sudo chmod 700 "${APP_ROOT}/runtime"

if command -v apt-get >/dev/null 2>&1; then
  sudo apt-get update -y
  sudo apt-get install -y openjdk-21-jre-headless nginx python3 python3-pip curl unzip
elif command -v dnf >/dev/null 2>&1; then
  sudo dnf install -y java-21-amazon-corretto-headless nginx python3 python3-pip curl unzip
fi

python3 -m pip install --user pyyaml

# Install AWS CLI v2 if missing
if ! command -v aws >/dev/null 2>&1; then
  curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
  unzip -q /tmp/awscliv2.zip -d /tmp
  sudo /tmp/aws/install
fi

echo "Copy deploy scripts into ${APP_ROOT}/scripts and enable systemd units from infrastructure/aws/"
