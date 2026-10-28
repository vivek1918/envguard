# EnvGuard setup guide (beginner-friendly)

This guide walks from a blank machine to a working EnvGuard + optional employee-app gate. **Never paste real passwords or AWS keys into Git.**

## 1. Architecture (30-second version)

EnvGuard sits **between** CI and production hosts. CI builds an artifact, asks EnvGuard to open a deployment request (with config YAML + digests). A human approves in the UI. CI polls until `APPROVED`, verifies digests, then deploys.

Details: [ARCHITECTURE.md](ARCHITECTURE.md).

## 2. Prerequisites

- Java 21, Maven 3.9+
- Node.js 22+ (24 OK), npm
- Docker Desktop (recommended for MySQL)
- Git
- Optional: AWS CLI v2, GitHub account

## 3. Clone / folder layout

```
Task/
  envguard/                      ← this product
  employee-management-service/   ← separate app deployed via EnvGuard
```

## 4. Environment variables (`.env`)

```powershell
cd C:\Users\DELL\Desktop\Task\envguard
Copy-Item .env.example .env
```

Edit `.env` (placeholders only in the example):

| Variable | Purpose |
|----------|---------|
| `MYSQL_*` | Database credentials for Compose |
| `ENVGUARD_JWT_SECRET` | Long random string (≥32 chars) for JWT signing |
| `ENVGUARD_PIPELINE_TOKEN` | Shared secret for CI → EnvGuard pipeline API |
| `ENVGUARD_CORS_ORIGINS` | Allowed browser origins |
| `ENVGUARD_BOOTSTRAP_ADMIN_*` | Optional first admin if you prefer not to register |
| `ENVGUARD_AWS_ENABLED` | `false` locally; `true` when Secrets Manager is used |
| `AWS_REGION` | e.g. `us-east-1` |

Generate secrets locally (PowerShell):

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
```

## 5. Secrets Manager vs YAML

- Non-secret config: versioned under `deploy/env/*.yaml` (and per-app `deploy/env` in the employee repo).
- Secret **values**: AWS Secrets Manager (or local env vars).
- YAML may list `secretReferences:` names only — never secret values.

When `ENVGUARD_AWS_ENABLED=true`, EnvGuard can check that referenced secrets exist (fail closed if missing).

## 6. Local run — PowerShell

### Option A — full Compose

```powershell
cd C:\Users\DELL\Desktop\Task\envguard
docker compose up -d --build
# API http://localhost:8080/api/health
# UI  http://localhost/   (Nginx)
```

### Option B — MySQL in Docker, apps on host

```powershell
docker compose up -d mysql
cd backend
mvn spring-boot:run
# other terminal
cd frontend
npm install
npm start
# UI http://localhost:4200 → API http://localhost:8080/api
```

### Bootstrap admin

1. Open the UI → Register (first user becomes ADMINISTRATOR), **or**
2. Set `ENVGUARD_BOOTSTRAP_ADMIN_USER` / `_PASSWORD` / `_EMAIL` in `.env` and restart backend, **or**
3. Run `scripts/create-admin.sh` against a running API (see script comments).

## 7. Local run — Linux / macOS

```bash
cd envguard
cp .env.example .env
# edit .env
docker compose up -d mysql
cd backend && mvn spring-boot:run &
cd ../frontend && npm install && npm start
```

## 8. Approval workflow (manual test)

1. Create a project in the UI (e.g. name `employee-management-service`).
2. From a shell with the pipeline token:

```powershell
$token = $env:ENVGUARD_PIPELINE_TOKEN
$api = "http://localhost:8080/api"
# Register build + create deployment (see README pipeline table).
# Include: environment, idempotencyKey, requesterIdentity, configYaml, artifactDigest, commitSha, projectName or projectId
```

3. Open **Approvals** in the UI.
4. Select the request — UI sends approve with the same **checksum** and **artifactDigest**.
5. Poll `GET /api/pipeline/deployments/{id}/status` until `APPROVED` or `REJECTED`.

Reject path: same screen → Reject (comment optional). Status becomes `REJECTED`; CI must stop.

## 9. GitHub repositories

Create **two** private repos (names are yours to choose):

1. EnvGuard source (this folder).
2. Employee Management Service (`../employee-management-service`).

Push each separately. `gh` CLI is optional; GitHub web UI works.

### EnvGuard Actions secrets

See [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md). For CI-only (no AWS yet), leave AWS secrets empty — build/test still runs; remote approval job skips if `ENVGUARD_API_URL` is unset.

### Employee Actions secrets

| Secret | Purpose |
|--------|---------|
| `ENVGUARD_URL` | Base URL **without** trailing path, e.g. `https://envguard.example.com` (workflow appends `/api`) |
| `ENVGUARD_PIPELINE_TOKEN` | Must match EnvGuard server |
| `ENVGUARD_PROJECT_ID` | Optional UUID; else project name `employee-management-service` |
| `AWS_ROLE_ARN`, `EC2_INSTANCE_ID`, `AWS_REGION`, `ARTIFACT_S3_BUCKET`, `DEPLOY_HEALTH_URL` | Required only for EC2 deploy after approval |

## 10. IAM / EC2 / HTTPS

- Prefer **GitHub OIDC → IAM role** over access keys.
- EC2 instance profile for Secrets Manager + S3.
- Nginx terminates HTTPS; see `infrastructure/nginx/nginx.conf`.
- Full notes: [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md).

Authenticate before any AWS command:

```powershell
aws login
aws sts get-caller-identity
```

## 11. Logs

- Spring Boot logs: console / journald (`journalctl -u envguard-backend`).
- Nginx access/error logs.
- In-app **Audit** page (read-only).
- Never log JWT secrets, pipeline tokens, or Secrets Manager values.

## 12. Bitbucket / Jenkins

- Bitbucket provider is stubbed (`BitbucketRepositoryProvider`) — HMAC validation TODO; GitHub webhooks are implemented.
- Any CI can call the same `/api/pipeline/*` HTTP contract with `X-EnvGuard-Pipeline-Token`.
- Jenkins: use a secret credential for the pipeline token; mirror the curl steps from `.github/workflows/ci-cd.yml`.

## 13. Cost and cleanup

See [COST_AND_CLEANUP.md](COST_AND_CLEANUP.md). Stop EC2, empty S3, delete unused secrets when done.

## 14. Troubleshooting

| Symptom | Likely fix |
|---------|------------|
| CORS errors in browser | Add UI origin to `ENVGUARD_CORS_ORIGINS` |
| 401 on pipeline calls | Check `X-EnvGuard-Pipeline-Token` matches server |
| Approve fails “checksum mismatch” | UI must send revision checksum + digest from the deployment view |
| MySQL connection refused | Start Compose MySQL; check port 3306 vs 3307 for employee app |
| Frontend `/api` 502 in Docker | Backend unhealthy — `docker compose logs backend` |
| `aws sts` expired | Run `aws login` |
| Employee CI “no deployment id” | Response is under `.data.id`; ensure project exists in EnvGuard |

## 15. Verify builds locally

```powershell
cd envguard\backend; mvn -B test
cd ..\frontend; npm test; npm run build
cd ..\..\employee-management-service\backend; mvn -B test
cd ..\frontend; npm run build
```
