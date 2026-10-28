# EnvGuard architecture

## High-level

```
Developer push
    → CI (GitHub Actions / Jenkins / Bitbucket)
         → POST /api/pipeline/builds
         → POST /api/pipeline/deployments  (+ config YAML)
    → EnvGuard API validates YAML, stores revision (checksum),
      creates DeploymentRequest (READY_FOR_APPROVAL)
    → Approver in Angular UI binds checksum + artifactDigest
    → Status APPROVED
    → CI polls /status, /verify, then deploys to EC2
    → CI reports /progress and /complete
    → Audit events appended (immutable)
```

## Components

| Layer | Responsibility |
|-------|----------------|
| **Angular UI** | Login/JWT, dashboard stats, projects, environments, approval inbox, deployment detail, audit |
| **Spring API** | Auth, RBAC, projects/envs, config validation, approvals, pipeline token endpoints, audit |
| **MySQL** | Users, roles, projects, environments, revisions, builds, deployment requests, approvals, audit |
| **AWS Secrets Manager** | Secret *values*; EnvGuard stores only references in config YAML |
| **Nginx** | Serves SPA; proxies `/api` to backend; TLS termination on EC2 |
| **GitHub Actions** | Builds this repo (EnvGuard); employee app has its own workflow that calls EnvGuard |

## Key domain objects

- **Project** — named app (e.g. `employee-management-service`); gets DEV / STAGING / PRODUCTION environments on create.
- **ConfigurationRevision** — immutable YAML + checksum + optional artifact digest + commit SHA.
- **BuildRecord** — pipeline run + artifact digest.
- **DeploymentRequest** — links project, environment, revision, build; status machine through approval and deploy.
- **ApprovalRecord** — who approved/rejected, with bound checksum and digest.
- **AuditEvent** — append-only; never updated or deleted by the app.

## Statuses (deployment request)

Typical path: `READY_FOR_APPROVAL` → `APPROVED` → `DEPLOYING` → `DEPLOYED`.

Terminal / block: `REJECTED`, `TIMED_OUT`, `FAILED`, `INVALIDATED`.

Production may require a second distinct approver (`requiresSecondApprover`).

## Security boundaries

- Browser users use JWT (`Authorization: Bearer`).
- Pipelines use `X-EnvGuard-Pipeline-Token` only on `/api/pipeline/**` (and related filters).
- Secret values never appear in API responses, audit JSON, or approval payloads.
- Config YAML may contain `secretReferences` names only.

## Frontend ↔ backend alignment

UI models match backend views:

- Auth: `{ token, user: { id, username, email, roles[], enabled } }`
- Dashboard: `GET /api/dashboard/stats`
- Deployments: `DeploymentView` (checksum, artifactDigest, requesterIdentity, …)
- Projects: `{ id, name, description, ownerId, defaultBranch, members[] }`

See `frontend/src/app/core/models/api.model.ts`.
