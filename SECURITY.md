# EnvGuard Security

## Principles

- Secrets never live in Git, approval reports, audit payloads, Angular responses, or CI logs.
- Non-secret config is versioned YAML under `deploy/env/`; secret values live in AWS Secrets Manager (or local env) and are referenced by name only.
- Deployment requires an EnvGuard approval bound to commit SHA, configuration revision checksum, and artifact digest.
- Pipelines authenticate with a narrowly scoped `X-EnvGuard-Pipeline-Token`; browsers cannot skip the approval gate.
- Prefer IAM roles / GitHub OIDC over long-lived AWS access keys.
- First registered user (empty DB) becomes `ADMINISTRATOR`; later self-registration is `DEVELOPER`. Prefer bootstrap env vars in shared environments.

## Roles

| Role | Capabilities |
|------|----------------|
| ADMINISTRATOR | Users, integrations, environments, approver policy, system settings |
| DEVELOPER | Authorized projects, submit config changes, approve/reject per policy |
| VIEWER | Read projects, reviews, deployment history |

Production can require a second distinct approver (`requiresSecondApprover` on the environment). Self-approval for production can be rejected by configuration (`rejectSelfApprovalProduction`).

## Secret handling

- Store only secret **references** (and optional version metadata) in MySQL / YAML.
- Runtime `.env` files are generated on the host under a non-web path (e.g. `/opt/envguard/runtime`) with mode `600`, never under the Nginx docroot, never as build artifacts.
- Secret values are redacted from logs and API errors (`SecretRedactor`).
- If a pinned secret version is unavailable, deployment **fails closed** (no silent substitution).

## API authentication

| Client | Mechanism |
|--------|-----------|
| Browser / Angular | JWT (`Authorization: Bearer`) from `/api/auth/login` |
| CI pipelines | `X-EnvGuard-Pipeline-Token` on `/api/pipeline/**` |
| GitHub webhooks | HMAC `X-Hub-Signature-256` |

## Webhooks and pipelines

- Duplicate `delivery_id` / idempotency keys are rejected or replayed safely.
- Treat YAML, webhook payloads, and build output as untrusted input; never execute shell from config files.
- Approve/reject requires matching checksum + artifact digest so a swapped artifact cannot ride an old approval.

## Transport and exposure

- Terminate TLS at Nginx (or ALB) on EC2.
- Do not expose MySQL or Spring Actuator publicly. Compose binds MySQL to `127.0.0.1:3306`.
- Restrict SSH to your IP; prefer SSM Session Manager. Expose only 80/443 as needed.

## Separation of duties (production)

- App developers propose configuration and code.
- Production approvers are a distinct set where policy requires it.
- Infrastructure admins manage IAM, Secrets Manager, and hosts.

## Frontend notes

- Tokens are stored in `localStorage` for the SPA session — use HTTPS in any shared environment.
- The UI never displays secret values; only digests, checksums, and secret *names*.

## Reporting issues

Rotate any exposed credential immediately, invalidate pipeline tokens, and review `audit_events` for the affected window.
