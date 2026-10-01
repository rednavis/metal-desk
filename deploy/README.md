# deploy/

How the three JVM services become container images. Written by [`T-073`](../tasks/T-073-tf-artifact-registry.md); the
registries they are pushed to are in [`infra/`](../infra/README.md).

```bash
# From the repository root: it is the build context.
docker build --build-arg MODULE=services/api            -f deploy/images/Dockerfile -t metaldesk-api .
docker build --build-arg MODULE=services/pricing-bridge -f deploy/images/Dockerfile -t metaldesk-pricing-bridge .
docker build --build-arg MODULE=apps/admin              -f deploy/images/Dockerfile -t metaldesk-admin .
```

One `Dockerfile` serves all three: the only difference between the services is which Spring Boot jar they run, so a
single build argument, `MODULE` (the module's directory), selects it. Three Dockerfiles would drift, and the drift would
show up as one service behaving differently in production.

## Build strategy: multi-stage, building the jar inside Docker

The `build` stage runs `./gradlew :<module>:bootJar -x test`; the `runtime` stage copies the one jar in. **Chosen over
building the jar first and copying it in** because it is self-contained: one `docker build` from a clean checkout yields
the image, with no step that must run first and no stale jar to ship by accident. The price is a slower build, softened
by BuildKit's cache mount for Gradle's dependency cache and by CI's layer cache. Tests and quality gates are not part of
the image build; the JVM build job (`ci.yml`) runs them.

## The image

| Property | Value | Why |
|---|---|---|
| Base | `eclipse-temurin:25.0.4.1_1-jre-noble` (JDK variant to build) | Java 25 (ADR-0004), a full Ubuntu userland so the image can be inspected |
| User | `app`, uid/gid **10001**, no shell, no home | Not root |
| PID 1 | `java`, exec-form `ENTRYPOINT` | SIGTERM reaches the JVM, not a shell |
| Jar | `/app/application.jar`, root-owned, mode 444 | Runnable, not writable by the application user |
| Heap | `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError` | Sized from the container's limit, not the host's; an OOM ends the process so Cloud Run replaces it |
| Shutdown | `SERVER_SHUTDOWN=graceful`, `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=8s` | Cloud Run allows about ten seconds after SIGTERM; in-flight requests (a payment in progress) finish first |

The shutdown settings are environment variables so the applications need no change; Spring's relaxed binding maps them to
`server.shutdown` and `spring.lifecycle.timeout-per-shutdown-phase`. Override either at deploy time if needed.

**Port.** The applications pin `server.port` (8081 / 8082 / 8083). Run the image with the port the application uses and
set the Cloud Run module's `container_port` to match (see `T-072`).

### Where the base-image version lives

**In the `Dockerfile`, on the two `FROM` lines, and nowhere else.** `gradle/libs.versions.toml` governs Gradle
dependencies, and a container base image is not one; so this is a second place a version lives, by design and documented
here. Dependabot's `docker` ecosystem watches `deploy/images/` (`.github/dependabot.yml`) and proposes bumps. Move the
JDK and JRE tags together.

## The build-context filter: `Dockerfile.dockerignore`, not `.dockerignore`

The context is the repository root and the Dockerfile lives in `deploy/images/`. **BuildKit reads
`<dockerfile>.dockerignore` beside the Dockerfile**, here [`deploy/images/Dockerfile.dockerignore`](images/Dockerfile.dockerignore).
A plain `.dockerignore` placed beside the Dockerfile is **silently ignored**. Verified empirically on this repository:

| Filter file | Context sent to the builder | `.env` in it |
|---|---|---|
| `deploy/images/Dockerfile.dockerignore` (used) | **4 MB** | no |
| a plain `deploy/images/.dockerignore`, same content | **352 MB** | **yes** |

It excludes `build/`, `.gradle/`, `node_modules/`, `.git`, the frontends, `docs/`, `infra/`, `tasks/`, every `.env*`,
`application-local.yml`, key and state files. To check it after changing it, export what the build would receive:

```bash
docker build --target build-context --output type=local,dest=./ctx -f deploy/images/Dockerfile .
du -sh ctx && ls -a ctx          # then delete ./ctx
```

## No secret reaches an image

There is no build argument carrying a credential (build arguments are recorded in the image history for good), the
filter keeps `.env*` and local configuration out of the context, and nothing is copied but the jar. Secrets arrive as
Cloud Run environment variables from Secret Manager at deploy time (`T-075`). `image.yml` fails if a credential-looking
string appears in an image's history or a `.env*` or `application-local*` file in its filesystem.

## CI: `.github/workflows/image.yml`

On every pull request and push to `master` that touches an image's inputs, it builds each service's image and checks
that it runs as non-root with `java` as the entrypoint, carries no credential, and, for `pricing-bridge` (the one
service that needs no database), starts, answers `/actuator/health` and shuts down gracefully on SIGTERM. `api` and
`admin` need MongoDB at startup, which the JVM build's Testcontainers tests already cover.

**Pushing authenticates with Workload Identity Federation** ([`T-077`](../tasks/T-077-tf-workload-identity.md)), with no stored
credential. On a push to `master` (never on a pull request) the `push` job, which declares the GitHub environment `dev`,
exchanges GitHub's short-lived token for the deployer account's and pushes
`<region>-docker.pkg.dev/<project>/metaldesk-dev-<service>/<service>:<commit-sha>`; then `deploy-dev` rolls the new revisions out
through [`deploy.yml`](../.github/workflows/deploy.yml). The jobs read the **`dev` GitHub environment's variables**, set from
`terraform output github_actions` after the first apply; until they exist the jobs say so and do nothing (there is no separate
on/off switch):

| Variable (environment `dev`) | Meaning |
|---|---|
| `WIF_PROVIDER`, `WIF_SERVICE_ACCOUNT` | the federation provider and the deployer account |
| `GCP_PROJECT_ID`, `GCP_REGION` | where the repositories and services live |

Tags are immutable, so a re-run for a commit that is already pushed finds the tag and stops. The push adds build
provenance and an SBOM attestation, which cost nothing extra.

## Not covered

Image vulnerability scanning (`T-064` scans dependencies; image scanning is a follow-up worth doing), deploying the
images (`T-072` consumes them, `T-077` deploys), and the frontends (`T-074`).
