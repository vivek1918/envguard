# EnvGuard

EnvGuard is an **environment configuration approval and deployment gate**. Pipelines (GitHub Actions, Jenkins, Bitbucket) register builds and deployment requests; humans approve bound digests in the UI; only then does the pipeline deploy.

This repository is **EnvGuard itself**. The sample business application lives next door in `../employee-management-service` and is deployed *via* EnvGuard.

## Stack

- Backend: Java 21, Spring Boot, MySQL 8
- Frontend: Angular 22 (Vitest unit tests)
- Local: Docker Compose (MySQL + API + Nginx UI)
- CI: GitHub Actions (builds EnvGuard; optional self-gated deploy)

## Local ports

| Component | Port |
|-----------|------|
| API | 8080 |
| UI (`ng serve`) | 4200 |
| UI (Compose / Nginx) | 80 |
| MySQL | 3306 (localhost only) |

## Quick start

See [docs/SETUP_GUIDE.md](docs/SETUP_GUIDE.md). Short version (PowerShell):

```powershell
cd C:\Users\DELL\Desktop\Task\envguard
Copy-Item .env.example .env
# Edit .env — set ENVGUARD_JWT_SECRET and ENVGUARD_PIPELINE_TOKEN (long random strings)
docker compose up -d --build
```

Or run API + UI without Compose frontend:

```powershell
docker compose up -d mysql
cd backend; mvn spring-boot:run
cd ..\frontend; npm install; npm start
```

First user to register becomes **ADMINISTRATOR** (or set `ENVGUARD_BOOTSTRAP_ADMIN_*` in `.env`).

## Docs

| Doc | Contents |
|-----|----------|
| [docs/SETUP_GUIDE.md](docs/SETUP_GUIDE.md) | Full beginner setup (local, GitHub, AWS, HTTPS, approve/reject, troubleshooting) |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Components, flows, API overview |
| [docs/AWS_DEPLOYMENT.md](docs/AWS_DEPLOYMENT.md) | EC2, IAM/OIDC, Secrets Manager, Nginx |
| [docs/COST_AND_CLEANUP.md](docs/COST_AND_CLEANUP.md) | Cost awareness and teardown |
| [SECURITY.md](SECURITY.md) | Security principles |

## Pipeline contract (apps calling EnvGuard)

Authenticate with header `X-EnvGuard-Pipeline-Token`.

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/pipeline/builds` | Register/update build |
| POST | `/api/pipeline/deployments` | Create deployment request (`environment`, `idempotencyKey`, `requesterIdentity`, config YAML, digests) |
| GET | `/api/pipeline/deployments/{id}/status` | Poll status |
| POST | `/api/pipeline/deployments/{id}/verify` | Verify approved digests before deploy |
| POST | `/api/pipeline/deployments/{id}/progress` | Report progress |
| POST | `/api/pipeline/deployments/{id}/complete` | Mark deployed/failed |

Responses wrap payloads in `{ "success": true, "data": { ... } }`.

## Note on `sample-app`

An empty `sample-app/` folder was removed. Use `../employee-management-service` as the guarded application.
