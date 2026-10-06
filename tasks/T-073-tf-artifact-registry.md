# T-073 — Terraform module: Artifact Registry and the container image build

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md` §7](../docs/architecture.md#7-reference-deployment-gcp), that document wins.** Update
> this task's row in [the ledger](README.md) in the same pull request.

**Parent issue:** [#5 — Phase 5 — GCP infrastructure](https://github.com/rednavis/metal-desk/issues/5)

**This task:** [#51](https://github.com/rednavis/metal-desk/issues/51)

**Milestone:** M5 GCP infrastructure · **Estimate:** 3 h

**Preconditions** — `T-070` merged; `T-072` merged, since its image variable is what this feeds.

> [!WARNING]
> Creating a registry is cheap; **storing images is not free**, and an unbounded registry grows without limit. The
> cleanup policy in §4 is part of the deliverable, not an optimisation.

**Goal** — Create per-service Artifact Registry repositories with immutable tags, and define how a service image is
built and tagged by commit SHA.

## 1. Why this task exists

Architecture §7 is specific in a way that constrains both the registry and the build:

> **Images** — Artifact Registry. **Per-service repositories, immutable tags (commit SHA, never `latest`).**

Immutable tags are an Artifact Registry setting, not a convention — enabling it makes the guarantee enforceable rather
than aspirational, and it is what lets `T-072`'s digest-pinned deployments be meaningful.

There is also no Dockerfile in this repository yet. `T-072` requires an image, so something has to build one, and how
the six Gradle modules map onto images is a decision that belongs here.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Per-service repositories, immutable tags, commit SHA, never `latest` | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Three deployable JVM services: `api`, `pricing-bridge`, `admin` | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp), `CLAUDE.md` |
| Java 25, Spring Boot 4 | [ADR-0004](../docs/adr/0004-java25-spring-boot4-runtime.md) |
| No secrets in an image | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp), [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |
| CI authenticates via Workload Identity Federation, not JSON keys | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Versions from the catalog; base image versions too | `CLAUDE.md`, `T-018` precedent |

## 3. Deliverables

| Path | What |
|---|---|
| `infra/terraform/modules/artifact-registry/` | `main.tf`, `variables.tf`, `outputs.tf`, `README.md` |
| `infra/terraform/envs/dev/registry.tf` | Three repositories, one per deployable service |
| `deploy/images/Dockerfile` | One parameterised image build for all three services |
| `deploy/images/Dockerfile.dockerignore` or `.dockerignore` | The build-context filter — see §4 |
| `.github/workflows/image.yml` | Build (and conditionally push) per service |
| `gradle/libs.versions.toml` (modify) | The base-image version, if it is pinned there |
| `infra/README.md` (modify) | Tagging scheme, retention policy and the pull permissions |

## 4. Specification

**One Dockerfile, parameterised by module.** The three services differ only in which Spring Boot jar they run. A build
argument naming the Gradle module keeps one file; three near-identical Dockerfiles diverge. Build the jar with
`bootJar` and copy it in, or use a multi-stage build that runs Gradle — a multi-stage build is self-contained and
slower; building the jar first is faster and needs the CI job to sequence it. **Choose, and say why.**

**Pin the base image, and put the version where versions live.** `CLAUDE.md` puts every version in
`gradle/libs.versions.toml`. A container base image is not a Gradle dependency, so decide: add it to the catalog as a
version-only entry that the Dockerfile references via a build argument set from the catalog, or pin it in the Dockerfile
and document that it is a second place a version lives. Either is defensible; leaving it undocumented is not.

**Run as a non-root user, and make the JVM container-aware.** A distroless or slim JRE base, a non-root user, `java` as
PID 1 so SIGTERM reaches it, and heap settings derived from the container limit rather than the host. Cloud Run sends
SIGTERM on scale-down; a JVM that is not PID 1 and does not shut down gracefully drops in-flight requests — which on
the checkout path means a payment in an unknown state.

**Immutable tags on every repository.** Enable the Artifact Registry immutable-tags setting so a pushed tag cannot be
overwritten. This is what makes "commit SHA, never `latest`" enforceable.

**A cleanup policy is required, not optional.** Without one the registry grows forever. Keep the most recent N images
per repository plus anything currently deployed, and delete untagged images after a short window. State the numbers.

**The build-context filter matters more than it looks.** If the Docker build context is the repository root and the
Dockerfile lives in `deploy/images/`, a `.dockerignore` placed beside the Dockerfile is **not** what BuildKit reads —
it reads one at the context root, or a `<dockerfile>.dockerignore` beside it. Verify empirically which one takes effect
in the build you configure, and record it. Without a working filter, the whole repository — including `build/`
directories and any local `.env` — ends up in the build context.

**Assert that no secret reaches the image.** No build argument carrying a credential (build args are visible in image
history), no copied `.env`, no `application-local.yml`. Secrets arrive as Cloud Run environment variables at deploy
time per `T-075`.

**Pull permission is granted per service account.** Each Cloud Run service account from `T-072` gets reader access to
its own repository only. Push permission belongs to CI (`T-077`) — do not grant it to a long-lived key here.

**The push is conditional until CI can authenticate.** `T-077` sets up Workload Identity Federation. Until then, the
workflow builds on every pull request and pushes only when an explicit repository variable is set and the branch is
`master`. Do not add a credential to make the push work sooner.

## 5. Acceptance criteria

1. `terraform fmt -check -recursive`, `init -backend=false`, `validate` and the linter pass.
2. Three repositories are declared, one per deployable service, with immutable tags enabled.
3. A cleanup policy is configured with stated retention numbers.
4. `docker build --build-arg <module>=services/api -f deploy/images/Dockerfile .` produces an image for each of the
   three services.
5. The resulting container runs as a non-root user, with `java` as PID 1, and shuts down on SIGTERM without killing
   in-flight requests — verified by sending SIGTERM to a running container.
6. The image serves `/actuator/health` when run locally with the required environment supplied.
7. No secret, credential or `.env` file is present in the image — verified by inspecting the filesystem and the image
   history for build arguments.
8. The build-context filter demonstrably excludes `build/`, `.gradle/`, `node_modules/` and any `.env` — verified by
   checking the context size or the image contents, and the effective filename recorded.
9. The base image version is pinned, and where it is pinned is documented.
10. Each Cloud Run service account has reader access to only its own repository; no push role is granted to any
    long-lived identity.
11. The workflow builds on pull requests and pushes only under the documented condition.
12. Nothing was applied; `git status` is clean.

## 6. Verification

```
cd <repo>
docker build --build-arg MODULE=services/api -f deploy/images/Dockerfile -t metaldesk-api:test .
docker run --rm -d --name t metaldesk-api:test && docker exec t ps -o pid,user,comm | head
docker kill --signal=TERM t; docker logs t 2>&1 | tail -5
docker history metaldesk-api:test | grep -iE 'secret|password|key'   # expect nothing
docker run --rm metaldesk-api:test sh -c 'ls -a / ; find / -name ".env" 2>/dev/null' | grep -c '\.env'   # expect 0
cd infra/terraform && terraform fmt -check -recursive && (cd envs/dev && terraform init -backend=false && terraform validate)
```

Expected: the image builds; PID 1 is `java` under a non-root user; a graceful shutdown log on SIGTERM; no secrets in
history or filesystem; Terraform checks green.

## 7. Out of scope

Deploying the images (`T-072` consumes them; CI deployment is `T-077`). Image vulnerability scanning — `T-064` covers
dependencies; image scanning is a follow-up worth naming. SBOM and provenance attestation, unless cheap to add — if
added, say so. Frontend bundles (`T-074`). Any `apply`.

## 8. Hazards

- **A `.dockerignore` in the wrong place is silently ignored**, and the build context then includes `build/`,
  `.gradle/` and any local `.env`. The image gets large and may carry a secret. Verify which file BuildKit actually
  reads.
- A credential passed as a build argument is recorded in image history forever, even if the file is deleted in a later
  layer.
- A JVM that is not PID 1 never receives SIGTERM, so Cloud Run scale-down drops in-flight requests — on the checkout
  path that means a payment whose outcome is unknown.
- Omitting immutable tags makes the commit-SHA discipline unenforceable: a tag can be repointed and a rollback becomes
  meaningless.
- No cleanup policy means the registry grows unboundedly and the cost appears months later.
- Granting push to a service account with a JSON key contradicts Architecture §7 and creates the exact long-lived
  credential it forbids.
- Default JVM heap sizing ignores the container limit and gets the container OOM-killed under load.
- Three separate Dockerfiles drift, and the drift shows up as one service behaving differently in production.

## 9. On completion

Mark the T-073 row done in [`README.md`](README.md). Record the build strategy, where the base-image version is pinned,
the effective build-context filter filename, and the retention numbers — `T-077` pushes with these and `T-078` needs
the retention policy for `prod`.
