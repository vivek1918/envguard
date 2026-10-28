# AWS deployment (EnvGuard)

**Prerequisite:** a working AWS identity. If `aws sts get-caller-identity` fails with an expired/missing session, run:

```powershell
aws login
```

Do not invent instance IDs, role ARNs, or claim deploy success without that identity.

## Recommended shape

1. **EC2** (Amazon Linux 2023) — runs MySQL (or RDS), EnvGuard JAR, Nginx + SPA.
2. **IAM role on EC2** — read Secrets Manager; no long-lived keys on disk.
3. **GitHub OIDC** — Actions assumes a deploy role (`AWS_DEPLOY_ROLE_ARN`); uploads artifacts to S3; SSM Run Command or deploy script on the instance.
4. **Secrets Manager** — DB password, JWT secret, pipeline token (or inject at bootstrap).
5. **Nginx** — TLS (Let's Encrypt or ACM + ALB); proxy `/api` to `127.0.0.1:8080`.

Example unit files and policies live under `infrastructure/aws/` and `infrastructure/nginx/`. Scripts: `scripts/bootstrap-ec2.sh`, `scripts/deploy-from-s3.sh`, `scripts/render-runtime-env.sh`.

## GitHub secrets (EnvGuard repo)

| Secret | Meaning |
|--------|---------|
| `ENVGUARD_API_URL` | Public API base **including** `/api` (e.g. `https://envguard.example.com/api`) |
| `ENVGUARD_PIPELINE_TOKEN` | Same value as server `ENVGUARD_PIPELINE_TOKEN` |
| `ENVGUARD_PROJECT_ID` | UUID of the EnvGuard project that represents this product (optional if name resolves) |
| `AWS_DEPLOY_ROLE_ARN` | OIDC role for deploy |
| `AWS_REGION` | e.g. `us-east-1` |
| `EC2_INSTANCE_ID` | Target instance |
| `DEPLOY_ARTIFACT_BUCKET` | Private S3 bucket for tarballs |

Trust policy example: `infrastructure/aws/iam-github-oidc-trust-policy.example.json`.

## First-time host bootstrap (outline)

1. Launch EC2 in a private/public subnet as required; security group: 443 (and 22 from your IP only).
2. Attach instance profile with Secrets Manager + S3 read (and SSM).
3. Install Java 21, Nginx, (optional) MySQL or point to RDS.
4. Copy systemd unit from `infrastructure/aws/envguard-backend.service`.
5. Render runtime env with `scripts/render-runtime-env.sh` (mode `600`, outside web root).
6. Place SPA under Nginx root; use `infrastructure/nginx/nginx.conf` as a starting point.
7. Obtain TLS certificates; reload Nginx.

Exact commands depend on your AMI and network. Prefer SSM Session Manager over open SSH.

## Employee app deploy

The employee service uses **its own** GitHub repo + workflow. It calls EnvGuard’s `/api/pipeline/*` endpoints, waits for `APPROVED`, then uses its own `AWS_ROLE_ARN` / `EC2_INSTANCE_ID` secrets. See `../employee-management-service/README.md`.

## Verification after deploy

```bash
curl -fsS https://YOUR_HOST/api/health
curl -fsS https://YOUR_HOST/   # SPA
```

Approve a staging request in the UI, confirm CI proceeds only after `APPROVED` + verify.
