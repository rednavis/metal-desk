# T-060 — CI: the path-filtered workflow skeleton and the affected-module matrix

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/modernization-plan.md`](../docs/modernization-plan.md), the plan wins** — it defines the exit
> criterion. Update this task's row in [the ledger](README.md) in the same pull request.

**Parent issue:** [#4 — Phase 4 — CI/CD](https://github.com/rednavis/metal-desk/issues/4)

**This task:** [#42](https://github.com/rednavis/metal-desk/issues/42)

**Milestone:** M4 CI/CD · **Estimate:** 3 h

**Preconditions** — Phase 3 closed (`T-041` and `T-056`), so there is a real build worth filtering.

**This task blocks `T-061`…`T-065`.**

**Goal** — Build the mechanism the whole phase rests on: decide which modules a pull request affects, and expose
that as a matrix the build jobs consume — **without** breaking the two existing jobs.

## 1. Why this task exists

`CLAUDE.md` records the situation this phase changes:

> **CI does not build the code.** `.github/workflows/ci.yml` runs only a Jekyll docs build and a TruffleHog secret
> scan.

So there is no build to filter yet — this task creates the filtering layer that `T-061` and `T-062` plug into. Doing
it first, as its own task, is what keeps the filtering logic in one reviewable place instead of duplicated into two
jobs with slightly different path lists.

The parent issue is also explicit about not breaking what exists:

> the docs-only `secrets-scan` job in `ci.yml` is the seed for this — **extend it, don't replace it**

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Path-filtered so a change scoped to one module triggers only that module's build | [Plan Phase 4](../docs/modernization-plan.md), issue #4 |
| Extend the existing `secrets-scan` job; do not replace it | Issue #4 |
| Exit criterion: `apps/web`-only PR triggers no JVM build; `libs/share` PR triggers every downstream module | [Plan Phase 4](../docs/modernization-plan.md), issue #4 |
| The six JVM modules and their dependency directions | [Architecture §8](../docs/architecture.md#8-build-graph), `settings.gradle.kts` |
| `apps/web` and `apps/admin-web` are outside the Gradle graph | [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md), `CLAUDE.md` |
| Secret scanning stays TruffleHog | `CLAUDE.md`, commit `5923005` |
| No long-lived credentials in CI | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |

## 3. Deliverables

| Path | What |
|---|---|
| `.github/workflows/ci.yml` (modify) | Add a `changes` job; leave `docs` and `secrets-scan` intact |
| `.github/path-filters.yml` | The filter definitions, one per module plus shared triggers |
| `.github/workflows/README.md` | What each job does, and how the matrix is derived |
| `docs/architecture.md` (modify) | §8 gains a short note on how CI derives affected modules |

## 4. Specification

**The dependency closure is the hard part, not the path matching.** A change to `libs/share` must trigger
`libs/payments`? No — `libs/payments` depends on `libs:share`, so it must. Derive the closure from the actual build
graph:

```
libs:share      → libs:payments, libs:mail, services:api, services:pricing-bridge, apps:admin
libs:payments   → services:api
libs:mail       → services:api
services:api    → (nothing)
services:pricing-bridge → (nothing)
apps:admin      → (nothing)
```

Verify this against `./gradlew :<module>:dependencies` rather than reading build files by eye, and **state in the
workflow README that the table is derived, with the command that derives it** — a hand-maintained reverse-dependency
list is the thing that will silently rot when a module gains a dependency. If you can generate it from Gradle in
the job, better; if not, add a check that fails when a module's declared project dependencies change without the
table being updated.

**Shared triggers rebuild everything.** A change to `gradle/libs.versions.toml`, `settings.gradle.kts`, the root
`build.gradle.kts`, anything under `build-logic/`, `config/checkstyle/`, `config/spotbugs/`, or the workflow files
themselves must trigger **all** JVM modules. These are the files that change every module's build behaviour, and
filtering them per-module is how a convention-plugin change ships unverified.

**Frontend filters are separate and must not trigger JVM work.** `apps/web` and `apps/admin-web` sources, plus
`package.json`, `pnpm-lock.yaml`, `pnpm-workspace.yaml`, `eslint.config.mjs`, `.prettierrc.json`, and the root
`package.json`, trigger the frontend job only. This is literally half the exit criterion.

Note the asymmetry carefully: the **root** `package.json` and `eslint.config.mjs` affect both apps but **no** JVM
module. Getting this wrong in either direction fails the criterion.

**`docs/` changes trigger neither build.** The existing `docs` job already handles them — and note that it currently
has **no path filter**, so it runs on every PR. Whether to add one is a judgment call: `CONTRIBUTING.md` claims it
only runs on `docs/` changes, so the documentation and the workflow already disagree. Fix one or the other and say
which.

**Do not touch the `secrets-scan` job.** It must keep running on **every** PR regardless of paths — a secret can be
committed in any file, and filtering a secret scan by path defeats it. `T-064` extends it; this task leaves it
alone.

**The `changes` job outputs are consumed, not duplicated.** Emit one boolean or list output per module plus
`frontend`, and have `T-061`/`T-062` read them. No job re-implements path matching.

**Pull requests only, for now.** Filtering against a base branch needs a diff; on a push to `master` there may be no
sensible base. Build everything on `master` pushes, and filter only on pull requests. State this, because a reviewer
will otherwise read the filtering as unconditional.

## 5. Acceptance criteria

1. Every workflow file passes a YAML parse and `actionlint`.
2. `docs` and `secrets-scan` are unchanged in behaviour; `secrets-scan` still runs on every pull request with no
   path filter.
3. The `changes` job emits one output per JVM module plus a frontend output.
4. The reverse-dependency table matches `./gradlew :<module>:dependencies` for all six modules — verified and
   recorded.
5. A change to `libs/share` alone marks all five downstream modules affected.
6. A change to `apps/web` alone marks **no** JVM module affected and the frontend affected.
7. A change to `gradle/libs.versions.toml`, `build-logic/**` or `config/checkstyle/**` marks every JVM module
   affected — one assertion each.
8. A change to the root `package.json` or `eslint.config.mjs` marks the frontend affected and no JVM module.
9. A `docs/`-only change marks neither.
10. On a push to `master`, everything is marked affected.
11. No credential, token or long-lived secret is added to any workflow.
12. The workflow README states how the dependency table is derived and how to update it.

## 6. Verification

```
cd <repo>
docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint -color
python3 -c "import yaml;[yaml.safe_load(open(f)) for f in ['.github/workflows/ci.yml','.github/path-filters.yml']];print('ok')"
for m in libs:share libs:payments libs:mail services:api services:pricing-bridge apps:admin; do echo "== $m"; ./gradlew :$m:dependencies --configuration compileClasspath | grep -E 'project :' ; done
git log --oneline -1 -- .github/workflows/ci.yml
```

Expected: actionlint clean; both files parse; the printed project dependencies match the §4 table; the `ci.yml`
change is additive.

## 7. Out of scope

The JVM build job (`T-061`) and the frontend build job (`T-062`). Build caching (`T-063`). Dependency scanning
(`T-064`). Required status checks and the exit proof (`T-065`). Deployment (`T-070`…`T-078`). Container image
builds.

## 8. Hazards

- **A hand-written reverse-dependency table** is correct today and wrong the first time a module adds a dependency,
  and the symptom is a downstream module that silently stops being built. AC-4 plus the drift check is the guard.
- Filtering `build-logic/`, the version catalog or the Checkstyle config per-module ships a change to every module's
  build without verifying any of them.
- Path-filtering the secret scan means a secret committed in a filtered-out path is never scanned.
- Triggering a JVM build from the root `package.json` fails the exit criterion's first half in a way the second half
  hides.
- Filtering on a `master` push with no base to diff against silently builds nothing.
- Replacing `secrets-scan` rather than extending it is explicitly what issue #4 asks not to do.
- Leaving `CONTRIBUTING.md`'s claim about the `docs` job unreconciled keeps a known documentation defect.

## 9. On completion

Mark the T-060 row done in [`README.md`](README.md). Record the reverse-dependency table, how it is kept honest, and
the decision about the `docs` job's path filter — `T-061`, `T-062` and `T-065` all consume this job's outputs.
