# CI workflows

Four workflows: `ci.yml` (below), [`infra.yml`](infra.yml), which checks the Terraform under `infra/`, [`image.yml`](image.yml), which builds the service images, and [`frontend-deploy.yml`](frontend-deploy.yml), which builds and uploads the two SPAs; the last three are described at the end. `ci.yml` jobs:

| Job | Runs | What it does |
|---|---|---|
| `changes` | every push to `master` and every pull request | Decides which parts of the repository the change affects and exposes that as job outputs. Build jobs added by `T-061` (JVM) and `T-062` (frontend) read these with `needs: changes`; none of them re-implements path matching. |
| `jvm-build` | every PR/push that affects a JVM module | One matrix entry per affected module, running `./gradlew :<module>:build` (Spotless, Checkstyle, SpotBugs, PMD, tests with Testcontainers, Jacoco). **Skipped** when no JVM module is affected. |
| `JVM build` (`jvm-build-result`) | always | The one check name to require in branch protection (`T-065`). Passes when `jvm-build` succeeded **or was skipped**; fails if `changes` or any module failed. |
| `frontend-build` | every PR/push that affects a pnpm app | `pnpm install --frozen-lockfile`, a build of each affected app, then the root-scoped typecheck, lint, format check and test. **Skipped** when no app is affected. |
| `Frontend build result` (`frontend-build-result`) | always | The check name to require in branch protection (`T-065`) for the frontend; passes when `frontend-build` succeeded **or was skipped**. |
| `docs` | every pull request and push | Builds the Jekyll site in `docs/` to catch config and front-matter errors. **Deliberately not path-filtered** — see below. |
| `secrets-scan` | every pull request and push | TruffleHog git scan of the pull-request (or pushed) commit range; fails on a finding. **Never path-filtered**: a secret can be committed in any file. |
| `dependency-scan` | every pull request and push | Exports the resolved JVM dependencies as a CycloneDX SBOM, scans it and `pnpm-lock.yaml` with OSV-Scanner, and fails on CVSS ≥ 7.0. **Never path-filtered.** See [Scanning](#scanning). |

## How the `changes` job decides

On a **pull request** it diffs the PR against its base (`git diff --name-only <base-sha> HEAD`) and
filters. On a **push to `master`** there is no sensible base, so everything is marked affected.

All logic is in [`scripts/affected.mjs`](scripts/affected.mjs) (Node standard library only, no install
step; the job pins Node 26 with `actions/setup-node`, matching local development), driven by [`path-filters.yml`](../path-filters.yml). The same script runs locally:

```
node .github/scripts/affected.mjs <base-sha> HEAD     # what would a PR against <base-sha> affect?
node .github/scripts/affected.mjs --all               # what a push to master does
node --test .github/scripts/                         # the assertions; the job runs these first
```

### Outputs

| Output | Meaning |
|---|---|
| `libs-share`, `libs-payments`, `libs-mail`, `libs-persistence`, `services-api`, `services-pricing-bridge`, `apps-admin` | `true` when that module must be built |
| `jvm` | `true` when any JVM module is affected |
| `jvm-modules` | JSON array of affected Gradle project paths, e.g. `[":libs:share",":services:api"]`, ready for a matrix |
| `frontend` | `true` when any pnpm app must be built and tested |
| `frontend-apps` | JSON array of affected app directories, e.g. `["apps/web"]`, read from `pnpm-workspace.yaml` |
| `docs` | `true` when `docs/**` changed (informational; the `docs` job does not use it) |

### The reverse-dependency table is derived, not written

A module is affected when its own directory changes **or** anything it depends on changes. The
dependency edges are read from the build itself: the modules from `settings.gradle.kts`, and each
module's `project(":x")` references from its `build.gradle.kts`. Nothing in this repository lists the
reverse dependencies by hand, so a module that gains a dependency is picked up automatically.

Resulting closure (what a change to the module in the left column affects):

| Change in | Marks affected |
|---|---|
| `libs:share` | all eight JVM modules |
| `libs:payments` | itself, `services:api` |
| `libs:mail` | itself, `services:api`, `apps:admin` |
| `libs:persistence` | itself, `libs:migrations` (its tests use the persistence test fixtures), `services:api`, `apps:admin` |
| `libs:migrations` | itself, `services:api`, `apps:admin` |
| `apps:admin` | itself, `services:api` (its tests run the admin application, `adminRuntime`) |
| `services:api`, `services:pricing-bridge` | itself only |

Verified against Gradle, not by reading build files:

```
for m in libs:share libs:payments libs:mail libs:persistence libs:migrations services:api services:pricing-bridge apps:admin; do
  echo "== $m"; ./gradlew -q :$m:dependencies --configuration compileClasspath | grep -oE "project ':[a-z:-]+'" | sort -u
done
```

To update after adding a module: add it to `settings.gradle.kts` and the `outputs:` block of the
`changes` job (one line). The script reads the rest.

### Shared triggers and the frontend

- `jvm-shared` in `path-filters.yml` (version catalog, settings, root build file, `build-logic/`,
  Checkstyle/SpotBugs/PMD config, the workflow files) marks **every** JVM module — they change every
  module's build behaviour.
- Each app's own directory (`apps/web/**`) is derived from `pnpm-workspace.yaml`, not listed in
  `path-filters.yml`. `frontend` lists the root files that configure every app (`package.json`,
  `pnpm-lock.yaml`, `pnpm-workspace.yaml`, `eslint.config.mjs`, `.prettierrc.json`). None of them
  affects a JVM module.
- `frontend-contract` lists the Java sources the frontend contract tests read
  (`apps/*/src/api/contract.test.ts` compare the TypeScript DTOs to them). A change there marks the
  frontend too, because it can break that test run. The reverse never holds: frontend files never
  mark a JVM module.

### The `docs` job is not path-filtered

`CONTRIBUTING.md` used to say the site is built only on PRs touching `docs/`, while the job has always
run on every PR. The workflow is the authority, so `CONTRIBUTING.md` was corrected rather than the job
filtered: a Jekyll build is cheap, and gating it would change the `docs` job's behaviour, which this task
leaves alone.

## The `jvm-build` job

- **Empty matrix = skipped, not failed.** On a frontend-only or docs-only change `jvm-build` does not
  run, and GitHub reports it as skipped. Matrix jobs are named per module and that set changes with
  every PR, so branch protection should require the aggregate **`JVM build`** job instead: it always
  runs, and it is the check that is green for "built OK" and for "nothing to build", red otherwise.
- **`:<module>:build`, never the root `build`.** The root build would rebuild every module and make
  the matrix decorative.
- **Dependencies are built too, and that is correct.** `:services:api:build` compiles `libs:share`,
  `libs:payments`, ... because Gradle must build a module's upstreams. The filter spares
  *unaffected* modules, not dependencies — seeing `libs:share` in the `services:api` log does not
  mean the filter is broken.
- **Java version** is read from `java = "..."` in `gradle/libs.versions.toml`, the same row
  `metaldesk.java-conventions` uses for the toolchain (ADR-0004). There is no second copy to keep in
  step.
- **Wrapper** is validated by `gradle/actions/wrapper-validation`. `gradle-wrapper.properties` carries
  no `distributionSha256Sum`; adding one is a separate change.
- **Docker** is provided by the `ubuntu-latest` runner, which Testcontainers (`SharedMongo`) uses; no
  `DOCKER_HOST` override is needed there.
- **Reports** (`**/build/reports/`, `**/build/test-results/`) are uploaded as `reports-<n>` artifacts
  on failure only.
- **Permissions** are `contents: read`; there are no secrets or credentials in the workflow.
- **Concurrency:** a superseded run of the same pull request is cancelled; a `master` run never is.
- **Build cache:** see [The Gradle build cache](#the-gradle-build-cache) below.

## The `frontend-build` job

- **Every command runs from the repository root, never inside an app.** The ESLint flat config is
  root-scoped (`CLAUDE.md`); `cd apps/web && pnpm lint` fails with an error that reads like a lint
  problem. The job uses the root scripts, and the workflow carries a comment saying why.
- **Per-app filtering applies to the build only.** `frontend-apps` selects which apps get
  `pnpm --filter=./<app> run build`. `typecheck`, `lint`, `format:check` and `test` are root scripts that
  cover **both** apps, so they run once for any frontend change — a change to `apps/web/src` still
  typechecks, lints and tests `apps/admin-web`. Filtering them would need per-app tooling invocations,
  which the root-only ESLint config rules out; the job does not pretend otherwise.
- **What marks an app:** its own directory; or any root file shared by all apps (`package.json`,
  `pnpm-lock.yaml`, `pnpm-workspace.yaml`, `eslint.config.mjs`, Prettier config, the workflows); or the
  Java the contract tests read (both apps' contract tests read them, so it marks both). A JVM-only change
  marks no app and a frontend-only change marks no JVM module.
- **pnpm version** comes from `packageManager` in the root `package.json`: `pnpm/action-setup` is given no
  `version:`. **Node** is `26`, the major of `engines.node` (`>=26`), as in the `changes` job.
- **`--frozen-lockfile`** is required: a lockfile that does not match `package.json` fails the install.
- **Store cache:** `actions/setup-node` with `cache: pnpm`, keyed on `pnpm-lock.yaml`.
- **`minimumReleaseAge`:** `pnpm-workspace.yaml` excludes `prettier@3.9.8` from a policy it never declares.
  Checked with a clean `HOME`/`XDG_CONFIG_HOME` and `minimumReleaseAge` left unset, set to `0` and set to
  `100000`: `pnpm install --frozen-lockfile` succeeds identically each time, because a frozen install
  resolves nothing and so applies no release-age policy. Nothing is declared. The policy matters only to a
  non-frozen install (adding or updating a dependency on a developer machine), which CI never does.
- **Permissions** are `contents: read`; nothing is uploaded and there are no credentials.

## The Gradle build cache

`gradle.properties` turns on the build cache, parallel execution, an explicit daemon heap (`-Xmx3g`) and the
configuration cache. `settings.gradle.kts` enables the **local** build cache only.

- **What "remote" means here — be exact.** This repository has no Gradle remote build-cache node. In CI,
  `gradle/actions/setup-gradle` persists the *local* cache (`~/.gradle/caches/build-cache-1`) and the dependency
  cache through the **GitHub Actions cache**. That is scoped per branch with GitHub's eviction rules (a pull
  request can read `master`'s entries; sibling branches cannot see each other's). It is **not** a shared
  `HttpBuildCache`. A true remote node is deferred (T-063 §7: out of scope).
- **Write policy.** `cache-read-only: ${{ github.ref != 'refs/heads/master' }}` — only pushes to `master` write;
  pull requests only read. Fork pull requests get a read-only `GITHUB_TOKEN` and no secrets, so they cannot write
  either. There is no token, key or credential for the cache anywhere in the repository.
- **Never cached: `build/` directories.** Restoring them would mark tasks `UP-TO-DATE` against stale outputs and
  produce a green build that verified nothing. The cache is keyed on task inputs; the workflow caches no build output
  path.
- **A failing gate is never cached**, so the cache cannot mask a failing Checkstyle/Spotless/SpotBugs/PMD run
  (verified locally with a deliberate `LineLength` violation: `BUILD FAILED` with caching on).
- **Configuration cache:** enabled. Spotless, Checkstyle, SpotBugs, PMD, Jacoco and the Spring Boot plugin all pass
  with it. The one incompatibility was ours: `services/api/build.gradle.kts` passed the `adminRuntime` classpath to
  tests from a `doFirst` lambda that captured the script object. It now uses a `CommandLineArgumentProvider` over a
  file collection. No suppression flags are used.
- **Bypass locally:** `./gradlew build --no-build-cache` (and `--no-configuration-cache`).

## Scanning

### Secrets — `secrets-scan`

TruffleHog stays the scanner (gitleaks needs a paid licence on organisation repositories, commit `5923005`).
The job fetches full history (`fetch-depth: 0`) and scans `base..head` of the pull request — **every commit in the
range**, so a secret added in one commit and deleted in the next is still found. The TruffleHog action passes
`--fail`, so a finding fails the job. `--results=verified,unknown` is kept: verified-live and could-not-verify
findings block, results known to be dead credentials do not. The action is pinned to the commit SHA of
`v3.97.9` and `version: 3.97.9` pins the scanner image (the action's default would be a floating `latest`).

### Dependencies — `dependency-scan`

| | |
|---|---|
| Scanner | [OSV-Scanner](https://google.github.io/osv-scanner/) `v2.6.0` (container image). No account, no token. |
| JVM | Gradle has no lockfile here, so `./gradlew cyclonedxBom` exports the resolved graph of all eight modules (CycloneDX plugin, version in the catalog) to `build/reports/cyclonedx/bom.json`. |
| npm | `pnpm-lock.yaml` directly. |
| Permissions | `contents: read` only; the default `GITHUB_TOKEN`. |

**Failure policy.** An advisory with **CVSS ≥ 7.0 (high, critical) fails** the job. Medium, low and *unscored*
advisories are listed in the log and the job summary but never block — a gate that blocks on every advisory in a
transitive test dependency is bypassed within a week. The threshold is `BLOCKING_SCORE` in
[`scripts/osv-gate.mjs`](scripts/osv-gate.mjs), unit-tested by `osv-gate.test.mjs`. OSV-Scanner's own exit code is
not the gate: it exits 1 on *any* advisory.

**Suppressing an unfixable advisory.** Add an `[[IgnoredVulns]]` entry to [`osv-scanner.toml`](../../osv-scanner.toml)
with an `id`, a `reason` and an `ignoreUntil` expiry date at most 90 days ahead. **An entry without a reason or
without an expiry is not acceptable** — `osv-gate.mjs` fails the job on it — and OSV-Scanner stops honouring an
entry after its date, so the advisory blocks again. Only for a real advisory with no available fix.

**Not covered.** A *new* advisory against an unchanged dependency is found only on the next pull request or push to
`master` — there is no scheduled scan. Dependabot (`.github/dependabot.yml`: `github-actions`, `gradle`, `npm`)
*proposes upgrades*; it does not gate. The `build-logic` plugin classpath is not in the SBOM. Container images are
out of scope until `T-073`.

**GitHub dependency review** (`actions/dependency-review-action`) is free for a public repository but needs the
repository's *Dependency graph* enabled, and for Gradle it would need a separate dependency-submission step
with `contents: write`, which a fork pull request cannot have. The graph appeared disabled on this repository when
this was written (the SBOM API returned 404), so it was **not** added rather than half-configured. Enabling the
graph in repository settings is a prerequisite if it is wanted later.

### Dependabot

Minor and patch updates are grouped into one pull request per ecosystem; a major update gets its own. The `gradle`
entry reads `gradle/libs.versions.toml`, so a bump raises the catalog, never a module build file. Docker image tags
inside workflow steps (OSV-Scanner, TruffleHog) are not visible to Dependabot and are bumped by hand.

## Required checks and the skipped-job caveat

A required check that never reports blocks a pull request forever, and `jvm-build` / `frontend-build` are skipped
on a change that does not affect them. The two **aggregating jobs** (`JVM build`, `Frontend build result`) always
run (`if: always()`) and pass when their conditional job succeeded *or was skipped*, so the skip semantics live in
the workflow, not in branch-protection settings. Never require the per-module `JVM build :<module>` matrix names.

Branch protection on `master` must require exactly these checks (**a maintainer with admin rights configures it;
a workflow cannot**):

| Required check | Job |
|---|---|
| `JVM build` | `jvm-build-result` |
| `Frontend build result` | `frontend-build-result` |
| `Scan for committed secrets` | `secrets-scan` |
| `Scan dependencies` | `dependency-scan` |

`Build docs site` is deliberately not required in this list; add it if a broken Jekyll build should block merges.
`Detect affected modules` is covered through both aggregating jobs (they fail if it does).

## `infra.yml` — Terraform checks

Runs on a pull request or push to `master` that touches `infra/**` or the workflow itself. One job, **`Terraform checks`**,
with no cloud credentials and no state: `terraform fmt -check -recursive`, then `terraform init -backend=false` and
`terraform validate` in **every directory that holds a `.tf` file**, then `tflint --recursive` (the built-in ruleset,
no plugin download). It never plans or applies. The Terraform version is pinned to an exact release here and constrained
with `~>` in `infra/terraform/envs/*/versions.tf`; an upgrade edits both. Third-party actions are pinned to a commit.

It is deliberately **not** one of the required checks yet: it is path-filtered, so a required path-filtered check would
block every pull request that does not touch `infra/`. Make it required only with the always-run result-job pattern
used for `JVM build` and `Frontend build result` (`T-078`).

## `image.yml` — service images

Builds `deploy/images/Dockerfile` for `api`, `pricing-bridge` and `admin` (one matrix entry each) on a pull request or
push to `master` that touches an image input (`deploy/**`, the JVM modules, `build-logic/**`, `gradle/**`, the root
Gradle files), and asserts that the image runs as non-root with `java` as its entrypoint and carries no credential;
`pricing-bridge` is also started and shut down with SIGTERM. A separate `push` job pushes `<sha>`-tagged images to
Artifact Registry **only on a push to `master` with the repository variable `IMAGE_PUSH_ENABLED=true`**, authenticating
by Workload Identity Federation (`T-077`, not yet present), so it is skipped today. No credential is stored. Details and
the variables: [`deploy/README.md`](../../deploy/README.md). Not a required check; it is path-filtered.

## `frontend-deploy.yml` — the two SPAs

On a pull request or push to `master` that touches `apps/web`, `apps/admin-web` or the pnpm root files, builds each app and
fails if the bundle contains a secret-looking string or a hard-coded local host. A separate `deploy` job uploads to the
buckets **only on `master` with the repository variable `FRONTEND_DEPLOY_ENABLED=true`**, authenticating by Workload Identity
Federation (`T-077`, not yet present), so it is skipped today; no credential is stored. The upload order is hashed assets,
other files, then `index.html` last, with `Cache-Control` set at upload (see `infra/terraform/modules/static-site/README.md`).
`API_BASE_URL` (optional repository variable) sets `VITE_API_BASE_URL` at build time. Not a required check; it is path-filtered.
