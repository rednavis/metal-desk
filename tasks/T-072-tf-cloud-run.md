# T-072 — Terraform module: the reusable Cloud Run service

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md` §7](../docs/architecture.md#7-reference-deployment-gcp) or
> [ADR-0003](../docs/adr/0003-gcp-target-architecture.md), that document wins.** Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#5 — Phase 5 — GCP infrastructure](https://github.com/rednavis/metal-desk/issues/5)

**This task:** [#50](https://github.com/rednavis/metal-desk/issues/50)

**Milestone:** M5 GCP infrastructure · **Estimate:** 4 h

**Preconditions** — `T-070` and `T-071` merged. Consumes the network module's outputs.

> [!WARNING]
> **Write-and-validate only.** Applying this module creates billable services — and `api` is specified with
> `min-instances ≥ 1`, which bills continuously. Do not apply from this task.

**Goal** — Build the one Cloud Run module that serves all three JVM services, parameterised over the differences
Architecture §7 names, so `api`, `pricing-bridge` and `admin` are three invocations rather than three copies.

## 1. Why this task exists

The plan asks for exactly this reuse:

> Cloud Run service (**reusable across** `api`/`pricing-bridge`/`admin`)

And Architecture §7 gives the three services genuinely different requirements — which is precisely what makes a single
parameterised module valuable and slightly awkward:

| Service | Architecture §7 requirement |
|---|---|
| `api` | Stateless, reactive; **min-instances ≥ 1** to avoid cold-start latency on the checkout path |
| `pricing-bridge` | **Always-on if the feed is a persistent subscription**; scheduled if poll-based |
| `admin` | **Internal-only, behind Identity-Aware Proxy** |

Three different ingress and scaling postures from one module. If the module cannot express all three, it will be
copied, and `prod` will drift.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| The three services, their scaling and ingress | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `api` min-instances ≥ 1 | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `admin` internal-only behind IAP | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `pricing-bridge` is a long-lived subscription in this build | `T-039` — it recorded that the always-on shape was built |
| Secrets injected as env vars at deploy time, never baked into an image | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Immutable image tags: commit SHA, never `latest` | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Observability: Cloud Logging + Cloud Trace, Actuator on `/actuator` | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `admin` is MVC + virtual threads; the other two are WebFlux | [ADR-0004](../docs/adr/0004-java25-spring-boot4-runtime.md), `CLAUDE.md` |
| Ports 8081 / 8082 / 8083 locally | `CLAUDE.md` |

## 3. Deliverables

| Path | What |
|---|---|
| `infra/terraform/modules/cloud-run-service/main.tf` | The service, its revision template and IAM |
| `.../cloud-run-service/variables.tf` | Image, scaling, ingress, env, secrets, egress, resources, health checks |
| `.../cloud-run-service/outputs.tf`, `README.md` | URL, service account email, and the module contract |
| `infra/terraform/envs/dev/services.tf` | Three invocations: `api`, `pricing-bridge`, `admin` |
| `infra/README.md` (modify) | The per-service posture table and its cost implications |

## 4. Specification

**One service account per service, created by the module, with no project-level roles.** Each service gets its own
identity so that `pricing-bridge` cannot read what only `api` should. Grant nothing broad here — `T-075` grants
per-secret access and `T-073` grants registry pull. A single shared service account with `roles/editor` is the default
shortcut and it undoes every other boundary in the deployment.

**The container port is a variable, and the local ports are irrelevant.** Cloud Run injects `PORT`; the application
must listen on it. `CLAUDE.md`'s 8081/8082/8083 are local development ports. Make sure the Spring configuration honours
`PORT` — if `application.yml` pins `server.port`, that is an application change to raise, not something to work around
here.

**Ingress and IAM differ per service, and that is the point of the parameters.** `api` is public. `admin` is
internal-only: restrict ingress and do **not** grant public invoke. `pricing-bridge` has no public consumer in this
build — `T-031` calls it service-to-service — so it should not be publicly invokable either. Default the module to
**private** and make public access an explicit opt-in, so a new service is never accidentally open.

**Scaling is parameterised, and `api`'s minimum has a standing cost.** `api` gets `min_instance_count = 1` per
Architecture §7. State plainly in `infra/README.md` that this bills continuously even with no traffic — it is the
single largest idle cost in the deployment, and it is a deliberate latency decision, not an oversight.
`pricing-bridge` also needs a minimum of 1, because `T-039` built a persistent subscription and a scaled-to-zero
service holds no subscription. Record that reasoning; it is the deployment consequence of `T-039`'s design.

**Health checks use Actuator.** Architecture §7 puts Actuator on `/actuator`. Configure a startup probe and a liveness
probe against `/actuator/health`, with a startup timeout generous enough for a JVM cold start — a probe tuned for a
Go binary will kill a Spring Boot container during startup, repeatedly, and it looks like a crash loop.

**Secrets are references, never values.** Accept a map of environment-variable name to Secret Manager secret version
and wire them as secret references. The module must accept **no** plaintext secret variable at all — if it can take
one, someone will pass one. `T-075` creates the secrets.

**Images are pinned by digest or commit SHA.** Architecture §7 forbids `latest`. Make the image variable required with
no default, and validate the format so a tag ending in `:latest` is rejected by `terraform validate` rather than by a
reviewer.

**Egress uses `T-071`'s outputs.** The VPC connector or direct egress settings come in as variables sourced from the
network module, with egress restricted to private ranges rather than all traffic.

**Do not model `pricing-bridge` as a Cloud Run Job.** Architecture §7 leaves that open, and `T-039` recorded that the
always-on streaming shape was built. Note in the module README that a poll-based feed would want the job shape and that
this module does not cover it.

## 5. Acceptance criteria

1. `terraform fmt -check -recursive`, `init -backend=false`, `validate` and the linter pass over the whole tree.
2. One module, invoked three times in `envs/dev/services.tf` — no duplicated service resource.
3. Each service gets its own service account, and the module grants no project-level role.
4. The module defaults to private ingress and no public invoker; `api` opts in explicitly.
5. `admin` is internal-ingress only and not publicly invokable — asserted by reading the plan output or the resource
   arguments.
6. `api` and `pricing-bridge` both set a minimum instance count of at least 1, each with its reason recorded.
7. The image variable is required, has no default, and a `:latest` tag fails validation — proven with a deliberate bad
   value.
8. The module accepts no plaintext secret value — asserted by grep over `variables.tf`; secrets are references only.
9. Startup and liveness probes target `/actuator/health` with a JVM-appropriate startup timeout.
10. Egress is wired from `T-071`'s outputs and restricted to private ranges.
11. The `dev` root declares no Cloud Run resource directly; it only calls the module.
12. Nothing was applied, and `git status` is clean.

## 6. Verification

```
cd <repo>/infra/terraform
terraform fmt -check -recursive
(cd envs/dev && terraform init -backend=false && terraform validate)
tflint --recursive
grep -rn 'resource "google_cloud_run' envs/dev   # expect nothing: the root only calls the module
grep -rniE 'password|secret_value|plaintext' modules/cloud-run-service/variables.tf   # expect nothing
grep -rn 'latest' modules/cloud-run-service   # expect only the validation that rejects it
grep -rn 'roles/editor\|roles/owner' modules/   # expect nothing
```

Expected: all checks green; no service resource in the root; no plaintext secret variable; `latest` only in the
rejecting validation; no broad roles.

## 7. Out of scope

Artifact Registry and building the images (`T-073`). The static-site CDN stack (`T-074`) — `web` and `admin-web` are not
Cloud Run services. Secret creation (`T-075`). IAP configuration (`T-076`) — this module only makes `admin` internal.
CI deployment (`T-077`). Any `apply`. Cloud Run Jobs and Cloud Scheduler.

## 8. Hazards

- **A shared service account with a broad role** collapses every identity boundary in the deployment, and it is the
  path of least resistance.
- Defaulting ingress to public makes the next service added accidentally internet-facing; defaulting to private makes
  the mistake visible instead.
- Allowing `:latest` contradicts Architecture §7 and makes a rollback impossible to reason about.
- A startup probe tuned for a fast binary kills a JVM container mid-startup and presents as a crash loop.
- Scaling `pricing-bridge` to zero silently drops the market-data subscription `T-039` built, and the storefront shows
  a frozen price.
- A plaintext secret variable will eventually be passed a real secret, which then sits in state — and Terraform state
  is not encrypted at the value level.
- Hard-coding the container port instead of honouring `PORT` produces a service that never passes its health check.
- Not recording `api`'s continuous min-instance cost leaves a standing charge nobody chose knowingly.

## 9. On completion

Mark the T-072 row done in [`README.md`](README.md). Record the per-service posture table, both min-instance decisions
with their cost, and that the Cloud Run Job shape for `pricing-bridge` is explicitly not covered.
