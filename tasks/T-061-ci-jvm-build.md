# T-061 — CI: the JVM build job with per-module filtering

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/modernization-plan.md`](../docs/modernization-plan.md), the plan wins.** Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#4 — Phase 4 — CI/CD](https://github.com/rednavis/metal-desk/issues/4)

**This task:** [#43](https://github.com/rednavis/metal-desk/issues/43)

**Milestone:** M4 CI/CD · **Estimate:** 3 h

**Preconditions** — `T-060` merged. The `changes` job must emit per-module outputs.

**Goal** — Give the repository its first CI job that actually compiles and tests the JVM code, driven by `T-060`'s
matrix so that only affected modules build.

## 1. Why this task exists

Today `./gradlew build` runs only on a developer's machine. `CLAUDE.md` says so, and
[`CONTRIBUTING.md`](../CONTRIBUTING.md) asks contributors to certify "CI green" in the pull-request template — a
checkbox that currently certifies a Jekyll build and a secret scan. This task makes that checkbox mean something.

It is also where the quality gates `CLAUDE.md` documents start protecting the repository rather than only the last
person to run a build: Checkstyle at `maxWarnings = 0`, SpotBugs at `Effort.MAX`/`Confidence.LOW`, Spotless, and the
architecture rule from `T-021`.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| A change scoped to one module triggers only that module's build | [Plan Phase 4](../docs/modernization-plan.md), issue #4 |
| `./gradlew build` runs Spotless, Checkstyle, SpotBugs, tests and Jacoco | `CLAUDE.md`, `metaldesk.quality-conventions.gradle.kts` |
| Java 25 toolchain, resolved via foojay | [ADR-0004](../docs/adr/0004-java25-spring-boot4-runtime.md), `settings.gradle.kts` |
| Versions come from the catalog only | `CLAUDE.md` |
| Testcontainers is used by `T-030` and the E2E suite | `T-030`, `T-041` |
| No long-lived credentials in CI | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `apps/web`/`apps/admin-web` are not Gradle projects | [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md) |

## 3. Deliverables

| Path | What |
|---|---|
| `.github/workflows/ci.yml` (modify) | The `jvm-build` job, matrixed over affected modules |
| `.github/workflows/README.md` (modify) | Document the job, its matrix and its caching boundary |

## 4. Specification

**Matrix over affected modules, and skip cleanly when none are affected.** Read `T-060`'s outputs, build the matrix,
and if it is empty the job must **not** fail — it must not run, or must succeed trivially. A required status check
that cannot pass on a frontend-only PR blocks every such PR; a job that is simply skipped reports as skipped, which
`T-065` has to account for when configuring required checks. Decide and document which shape you use, because it
determines how branch protection is set up.

**Run `:<module>:build` per matrix entry, not the root `build`.** The whole point is per-module scoping. The root
`build` builds everything and makes the matrix decorative.

But be honest about what that means: `:services:api:build` will also build `libs:share` because Gradle resolves the
project dependency. **That is correct and expected** — a downstream module cannot be built without its upstreams.
The filtering saves work on *unaffected* modules, not on dependencies. State this in the workflow README, because
otherwise the first person to read the logs will conclude the filtering is broken.

**Pin the Java version to what the toolchain requires.** ADR-0004 fixes Java 25. The version catalog has **no Java
row** — the toolchain is declared in `metaldesk.java-conventions.gradle.kts`. So the workflow's `java-version` is a
literal that must stay in step with the convention plugin. Record this as a known duplication and name both files in
a comment, so a Java upgrade edits both.

**Use the Gradle wrapper, and verify it.** Run through `./gradlew`, and enable wrapper validation so a tampered
wrapper jar fails the build. `gradle-wrapper.properties` should carry a distribution checksum; if it does not, say
so rather than adding one silently as part of this task.

**Testcontainers needs Docker on the runner.** `T-030`'s harness and `T-041`'s E2E suite both need it. Confirm the
runner image provides Docker and that the suite passes in CI — a Testcontainers suite that passes locally and fails
on a runner is the most common Phase 4 surprise. If it cannot work, say so explicitly rather than excluding the
tests, because they are the Phase 3 exit gate.

**Publish test and report artifacts on failure.** Checkstyle, SpotBugs and test reports must be retrievable, or a red
build is a guessing game. Upload them only on failure to keep runs cheap.

**No credentials.** No registry login, no cloud auth, no tokens beyond the default `GITHUB_TOKEN` with the minimum
permissions the job needs. Set `permissions:` explicitly at job level.

**Concurrency: cancel superseded runs on the same pull request**, but never cancel a `master` run mid-flight.

## 5. Acceptance criteria

1. `actionlint` reports no findings on all workflow files.
2. A pull request touching only `libs/share` builds all five downstream modules plus `libs:share`.
3. A pull request touching only `apps/web` runs **no** JVM build — the job is skipped or trivially green, as
   documented.
4. A pull request touching only `services/pricing-bridge` builds that module and does not build `services:api` or
   `apps:admin`.
5. A pull request touching `build-logic/**` builds every JVM module.
6. The job invokes `:<module>:build`, not the root `build` — asserted by reading the workflow.
7. Checkstyle, SpotBugs and Spotless all run in CI, and a deliberate violation fails the job — proven once on a
   scratch branch.
8. `T-021`'s architecture test runs in CI — proven from the job log.
9. `T-041`'s E2E suite, with Testcontainers, passes on the runner — or its failure is documented with the reason.
10. Reports are uploaded on failure and retrievable.
11. `permissions:` is set explicitly and grants no write scope the job does not need.
12. No secret, token or credential is added to the workflow.

## 6. Verification

```
cd <repo>
docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint -color
grep -n 'java-version' .github/workflows/ci.yml
grep -n 'toolchain\|JavaLanguageVersion' build-logic/src/main/kotlin/metaldesk.java-conventions.gradle.kts
grep -n 'permissions:' .github/workflows/ci.yml
grep -nE 'gradlew .*:build|gradlew build' .github/workflows/ci.yml
```

Expected: actionlint clean; the workflow's Java version matching the convention plugin's toolchain; explicit
permissions; per-module `:build` invocations rather than a bare root `build`.

## 7. Out of scope

The frontend job (`T-062`). Build caching (`T-063`) — this job will be slow until then, and that is expected.
Dependency and secret scanning (`T-064`). Branch protection (`T-065`). Publishing artifacts or images. Deployment.

## 8. Hazards

- **A required job that cannot pass on a frontend-only PR** blocks every frontend change. Decide the
  skip-versus-trivially-green shape here, not in `T-065`.
- Running the root `build` makes the matrix cosmetic and forfeits the exit criterion while appearing to satisfy it.
- Reading the logs and concluding the filter is broken because `libs:share` compiled during `:services:api:build` —
  document the distinction between dependencies and unaffected modules.
- The Java version in the workflow and the toolchain in `java-conventions` are two places for one fact; a bump to one
  alone produces a confusing CI-only failure.
- Excluding the Testcontainers tests to get CI green removes the Phase 3 exit gate from the only place it would run
  automatically.
- A broad `permissions:` default gives every job write access to the repository.
- Cancelling in-progress `master` runs loses the only signal that `master` is broken.

## 9. On completion

Mark the T-061 row done in [`README.md`](README.md). Record the empty-matrix behaviour, whether Testcontainers works
on the runner, and the Java-version duplication — `T-063` tunes this job and `T-065` makes it required.
