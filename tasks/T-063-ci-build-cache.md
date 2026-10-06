# T-063 — Gradle build cache, local and remote

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with `CLAUDE.md` or
> [`docs/modernization-plan.md`](../docs/modernization-plan.md), that document wins.** Update this task's row
> in [the ledger](README.md) in the same pull request.

**Parent issue:** [#4 — Phase 4 — CI/CD](https://github.com/rednavis/metal-desk/issues/4)

**This task:** [#45](https://github.com/rednavis/metal-desk/issues/45)

**Milestone:** M4 CI/CD · **Estimate:** 3 h

**Preconditions** — `T-061` merged, so there is a JVM build in CI whose time can be measured.

**Goal** — Enable the Gradle build cache locally and in CI, per the plan's "Gradle build cache (local + remote) for
reasonable build times as the module count grows" — and **measure** the effect rather than assuming it.

## 1. Why this task exists

The repository has **no `gradle.properties` at all** — no configuration cache, no parallel execution, no build cache,
no JVM arguments. Every build is a cold, serial build. With six modules, Spotless, Checkstyle, SpotBugs at
`Effort.MAX` and Jacoco all running, that is already slow, and `T-061` just put it on every pull request.

This task is also where an honest limitation has to be stated: a *remote* cache needs a cache backend, and this
repository has none. GitHub Actions cache can serve as one via a supported action, but that is a different thing from
a Gradle remote build-cache node, and the plan's wording should not be over-claimed.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Gradle build cache, local and remote | [Plan Phase 4](../docs/modernization-plan.md), issue #4 |
| Shared build behaviour lives in `build-logic`, not per module | `CLAUDE.md` |
| `./gradlew clean` must keep cleaning `build-logic/build` | `CLAUDE.md`, root `build.gradle.kts` |
| Versions only in the catalog | `CLAUDE.md` |
| No credentials in CI | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Developers verify with a real build before calling work done | `CLAUDE.local.md`-style practice; `CONTRIBUTING.md` |

## 3. Deliverables

| Path | What |
|---|---|
| `gradle.properties` | New: build cache, parallel execution, and JVM memory for the daemon |
| `settings.gradle.kts` (modify) | The `buildCache` block — local always, remote only in CI |
| `.github/workflows/ci.yml` (modify) | Cache wiring for the JVM job |
| `.github/workflows/README.md` (modify) | What is cached, what is not, and the measured effect |
| `CONTRIBUTING.md` (modify) | A short note that the local cache is on and how to bypass it |

## 4. Specification

**Measure first, then change, then measure again.** Record three numbers on the same machine: a cold `./gradlew
clean build`, a warm no-op `build`, and a `build` after touching one file in `libs/share`. Do the same after the
change. **Put the six numbers in the ledger.** Without them this task is unfalsifiable, and a build cache that helps
nothing is worth knowing about.

**Enable the build cache and parallel execution in `gradle.properties`.** `org.gradle.caching=true`,
`org.gradle.parallel=true`, and an explicit daemon heap — the default is often too small for SpotBugs at
`Effort.MAX` and produces a confusing OOM rather than a clear failure.

**Treat the configuration cache separately and carefully.** It is the larger win and the larger risk: it can be
incompatible with plugins in the build, and this build has Spotless, SpotBugs, Checkstyle and the Spring Boot plugin.
**Try it, and if anything is incompatible, leave it off and record which plugin and which error** — do not force it
with warning-suppression flags. An incompatibility documented here saves the next person an afternoon.

**The remote cache is CI-only and read-write only where it is safe.** In `settings.gradle.kts`, configure the local
cache always; enable a remote cache only when an environment variable indicates CI. Pull requests should **read**
from the cache and generally not write to it — a cache poisoned from an untrusted fork's pull request is a supply-chain
problem, and pull requests from forks must never have write credentials. Pushes to `master` may write. State this
policy in the workflow README.

**If no cache backend exists, say so plainly.** Using the Actions cache through a supported Gradle action is a
reasonable implementation of "remote" for this repository — but it is scoped per branch with GitHub's own eviction
rules, not a shared Gradle cache node. **Do not describe it as a Gradle remote build-cache node.** If the honest
answer is "local cache plus the Actions cache, and a true remote node is deferred", write that in the ledger and in
`docs/modernization-plan.md`'s Phase 4 section if it changes what the plan promises.

**Cache the right things, and nothing dangerous.** The Gradle user home dependency cache and the build cache.
**Never** cache `build/` directories directly — restored stale outputs make tasks `UP-TO-DATE` and produce falsely
green builds, which is worse than no cache.

**`clean` must still reach `build-logic`.** `CLAUDE.md` calls out that wiring explicitly. Verify `./gradlew clean`
still removes `build-logic/build` after the change.

**No credentials.** If a remote cache would need a token, it does not go in the repository; use the workflow's
built-in token or declare the feature deferred.

## 5. Acceptance criteria

1. `./gradlew clean build` succeeds locally after the change, for all six modules.
2. The six timing measurements from §4 are recorded in the ledger.
3. A no-op `./gradlew build` immediately after a successful one reports most tasks `UP-TO-DATE` or `FROM-CACHE`.
4. Touching one file in `libs/share` rebuilds it and its dependents, and does **not** rebuild unrelated tasks —
   observable from the task outcomes.
5. `org.gradle.caching` and `org.gradle.parallel` are enabled in `gradle.properties`, with an explicit daemon heap.
6. The configuration cache is either enabled and working, or disabled with the incompatible plugin and its exact
   error recorded.
7. The remote cache is enabled only in CI; pull requests do not write to it, and no fork pull request can obtain
   write credentials.
8. No `build/` directory is cached by the workflow — asserted by reading the cache configuration.
9. `./gradlew clean` still removes `build-logic/build`.
10. A deliberate Checkstyle violation still fails the build with caching on — proving the cache does not mask a
    failing gate.
11. No credential or token is added to any committed file.
12. `.gitignore` covers any new local cache artefact, and `git status` is clean after a build.

## 6. Verification

```
cd <repo>
time ./gradlew clean build
time ./gradlew build
touch libs/share/src/main/java/com/rednavis/metaldesk/share/ShareModule.java && time ./gradlew build
./gradlew clean && ls build-logic/build 2>&1   # expect: no such file or directory
grep -nE 'caching|parallel|jvmargs|configuration-cache' gradle.properties
git status --short   # expect clean
```

Expected: the warm build markedly faster than cold; the touched-file build rebuilding only the affected graph;
`build-logic/build` removed by `clean`; the properties present; a clean tree.

## 7. Out of scope

A dedicated remote build-cache node or Develocity/Build Scan integration. Caching the pnpm store (`T-062` already
does). Test parallelism tuning. Branch protection (`T-065`). Reducing SpotBugs effort to go faster — that would trade
a quality gate for speed and is a separate decision with its own justification.

## 8. Hazards

- **Caching `build/` directories** restores stale outputs, marks tasks `UP-TO-DATE`, and produces a green build that
  verified nothing. This is the most dangerous mistake available here.
- Letting fork pull requests write to a shared cache lets an attacker poison the build outputs of `master`.
- Forcing the configuration cache past a plugin incompatibility with suppression flags produces intermittent,
  unexplainable failures.
- Enabling parallel execution without raising the daemon heap turns a slow build into an OOM that looks like a test
  failure.
- Claiming a "remote cache" when the implementation is the Actions cache overstates what was delivered and will
  mislead whoever reads the plan's exit criteria.
- Removing the `clean` → `build-logic` wiring while editing build files leaves stale convention-plugin output, which
  `CLAUDE.md` warns about.
- Reporting an improvement without before-and-after numbers.

## 9. On completion

Mark the T-063 row done in [`README.md`](README.md). Record the six timings, the configuration-cache outcome, and the
precise nature of the "remote" cache — and if it is not a true remote node, amend
[`docs/modernization-plan.md`](../docs/modernization-plan.md)'s Phase 4 wording in the same pull request.
