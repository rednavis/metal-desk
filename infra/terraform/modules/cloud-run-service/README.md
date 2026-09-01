# Module: `cloud-run-service`

One Cloud Run service with its own service account. `api`, `pricing-bridge` and `admin` are three calls of this module
in [`envs/dev/services.tf`](../../envs/dev/services.tf); what differs between them is a parameter, never a copy of the
resource. Written and validated by [`T-072`](../../../tasks/T-072-tf-cloud-run.md); **never applied** by that task.

## What it creates

- `google_service_account` — one per service, **with no project-level role**. Access is granted on the single
  resource that needs it (a secret in `T-075`, the image repository in `T-073`, another service via `invoker_members`).
- `google_cloud_run_v2_service` — the service and its revision template: Direct VPC egress (private ranges only),
  scaling, resources, environment and secret references, and the startup and liveness probes.
- `google_cloud_run_v2_service_iam_member` — `allUsers` as invoker **only when `exposure = "public"`**, plus one binding
  per entry of `invoker_members`.

## Postures

`exposure` is the one switch, and its default is the closed one.

| `exposure` | Ingress | Invoker | Used by |
|---|---|---|---|
| `private` (default) | internal only | none, except `invoker_members` | `pricing-bridge` |
| `internal-load-balancer` | internal and Google Cloud load balancers | none | `admin` (fronted by a load balancer and IAP in `T-076`) |
| `public` | all | `allUsers` | `api` |

## The contract

**Inputs** (`variables.tf`): `name_prefix`, `name`, `region`, `image`, `container_port`, `exposure`, `invoker_members`,
`min_instance_count`, `max_instance_count`, `cpu_always_allocated`, `cpu`, `memory`, `env`, `secret_env`, `vpc_egress`,
`health_path`, `startup_timeout_seconds`, `deletion_protection`.

**Outputs** (`outputs.tf`): `name`, `uri`, `location`, `service_account_email`, `service_account_member`.

Rules the module enforces, each with a validation that fails `terraform validate`/`plan` with an explanation:

- **`image` is required, has no default, and must be pinned**: a digest, or a tag that is a commit SHA (7 to 40 hex
  characters). Mutable tags, untagged names and version-looking tags are rejected (Architecture §7).
- **No credential can be passed as a plain value.** There is no variable for one. `env` rejects names that look like a
  credential or connection string (`secret`, `passw`, `token`, `key`, `credential`, `uri`, `dsn`), and `secret_env`
  takes **references only**: `{ secret = "<id>", version = "<number>" }`. A version is always a number, so a deploy is
  reproducible. `T-075` creates the secrets and grants each service's account access to its own.
- **Egress comes from `T-071`.** Pass the network module's `cloud_run_egress` output whole as `vpc_egress`. It must be
  `PRIVATE_RANGES_ONLY` and must carry the network tag, or the default-deny egress firewall rules would not apply.

## Health checks

Startup and liveness probes both call `health_path`, which defaults to the Actuator **liveness group**,
`/actuator/health/liveness`. Liveness answers "is the process healthy" and has **no external dependency**, so a database outage
does not restart instances or hold back a new revision. The **readiness** group (`/actuator/health/readiness`) and the aggregate
`/actuator/health` **include MongoDB** (`api` and `admin` configure that in `application.yml`) and report `DOWN`/`503` while it
is unreachable: that is a real signal and the MongoDB health indicator is **not** disabled. Cloud Run has no readiness probe,
so readiness is for people and dashboards, not for these probes.

The startup probe is generous on purpose: `startup_timeout_seconds` defaults to **180** (10-second period, 18 failures),
because a Spring Boot JVM takes far longer to start than a native binary, and a probe tuned for one kills the other mid-startup
and looks like a crash loop. Cloud Run caps it at 240. `startup_cpu_boost` is on for the same reason. The liveness probe
tolerates three consecutive failures at a 30-second period.

> **A database is still required to start.** `api` and `admin` run their Mongock migrations before the server accepts a request
> (ADR-0006), so with MongoDB unreachable they do not start at all and liveness never answers (verified: the process exits with
> `Application run failed`). The liveness/readiness split protects a *running* instance from a *later* outage; it cannot make a
> service start without its database.

## Container port

Cloud Run sends traffic to `container_port` and sets `PORT` to the same value. The applications **pin `server.port` in
`application.yml`** (8081, 8082, 8083) rather than reading `PORT`, so `container_port` must equal that value; the dev
root does. That works, but it means the port is stated twice. Reading `PORT` in the applications
(`server.port: ${PORT:8082}`) is an application change this task did not make; it is raised in the pull request.

## `pricing-bridge` as a job: not covered

Architecture §7 leaves open whether `pricing-bridge` runs as a service or as a Cloud Run Job. `T-039` built the
always-on, persistent-subscription shape, so this module models a service. **A poll-based feed would want the Cloud Run
Job (and Cloud Scheduler) shape, and this module does not cover it.**

## Also required before apply

The `run.googleapis.com` API enabled in the project, the images from `T-073`, and the secrets from `T-075`, whose ids
and version numbers `secret_env` refers to.
