# Cost awareness and cleanup

EnvGuard’s AWS footprint is intentionally small for a lab/demo, but **idle resources still bill**.

## Typical bill drivers

| Resource | Notes |
|----------|--------|
| EC2 | Largest cost if left running 24/7. Stop or terminate when idle. |
| EBS volumes | Persist after instance terminate unless deleted. |
| RDS / MySQL on EC2 | RDS has hourly + storage; on-box MySQL is mostly EC2 cost. |
| Secrets Manager | Per-secret monthly + API calls. |
| S3 | Artifact storage + requests; enable lifecycle to expire old builds. |
| Data transfer / ALB | Only if you add a load balancer or heavy egress. |
| Elastic IPs | Charged when **not** attached to a running instance. |

Prefer **one** small instance (e.g. `t3.small` / `t4g.small`) for demos. Use AWS Free Tier only within its limits and region rules.

## Safe cleanup checklist

1. Confirm no production traffic depends on the stack.
2. In GitHub Actions, disable or delete workflows that deploy.
3. Empty and delete the artifact S3 bucket (or apply a 7–14 day lifecycle).
4. Delete Secrets Manager secrets you created for the demo (after rotating anything reused elsewhere).
5. Stop or terminate EC2; delete unattached EBS volumes and unused Elastic IPs.
6. Remove IAM OIDC roles/policies created only for this lab (after confirming nothing else uses them).
7. Locally: `docker compose down -v` in `envguard/` (and employee compose) to drop containers and volumes.

## Cost guardrails

- Tag resources (`Project=EnvGuard`, `Env=lab`).
- Set a billing alarm / budget in AWS Billing.
- Do not leave `ENVGUARD_AWS_ENABLED=true` against a personal account with production secrets by accident.

## What this repo does **not** do automatically

Scripts do not delete AWS resources for you. Cleanup is a deliberate operator action after `aws login` succeeds.
