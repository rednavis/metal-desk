# T-062 — CI: the frontend build job with per-app filtering

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with `CLAUDE.md` or
> [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md), that document wins.** Update this task's row
> in [the ledger](README.md) in the same pull request.

**Parent issue:** [#4 — Phase 4 — CI/CD](https://github.com/rednavis/metal-desk/issues/4)

**This task:** [#44](https://github.com/rednavis/metal-desk/issues/44)

**Milestone:** M4 CI/CD · **Estimate:** 2 h

**Preconditions** — `T-060` merged. `T-050` merged, so there is a frontend test command to run.

**Goal** — Build, typecheck, lint and test both pnpm apps in CI, filtered so a JVM-only change runs no frontend work
and vice versa.

## 1. Why this task exists

The frontend has never been built in CI, and it has a constraint that makes a naive job fail in a confusing way.
`CLAUDE.md`:

> ESLint config is a single root `eslint.config.mjs` … don't run `eslint` from inside an app directory (flat config
> resolution is root-scoped here; **always run from repo root**).

A job that does `cd apps/web && pnpm lint` will fail for reasons that look like a lint error. The root scripts exist
precisely to avoid this, and this job must use them.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| `pnpm install` then `pnpm -r run build`; root scripts for lint, typecheck, format | `CLAUDE.md` |
| Single root ESLint config; always run from the repo root | `CLAUDE.md` |
| Both apps stay out of the Gradle graph | [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md), `CLAUDE.md` |
| `packageManager: pnpm@12.4.2`, `engines.node >= 22` | root `package.json` |
| Prettier does not own `*.md` or `docs/` | `.prettierignore` |
| A frontend-only change must not trigger a JVM build | [Plan Phase 4](../docs/modernization-plan.md), issue #4 |
| `pnpm-workspace.yaml` carries a `minimumReleaseAgeExclude` | `pnpm-workspace.yaml` |

## 3. Deliverables

| Path | What |
|---|---|
| `.github/workflows/ci.yml` (modify) | The `frontend-build` job |
| `.github/workflows/README.md` (modify) | Document the job and the root-only-ESLint constraint |

## 4. Specification

**Every command runs from the repository root.** `pnpm install --frozen-lockfile`, then `pnpm -r run build`,
`pnpm run typecheck`, `pnpm run lint`, `pnpm run format:check` and `pnpm run test`. Never `cd` into an app. Put a
comment in the workflow saying why, citing `CLAUDE.md` — this is the single most likely thing for a future editor to
"simplify".

**Pin pnpm from `packageManager` rather than hard-coding a version.** The root `package.json` declares
`pnpm@12.4.2`; let corepack or the setup action read it, so there is one source of truth. Pin Node to the `engines`
range's major.

**`--frozen-lockfile` is required.** A CI install that silently updates the lockfile makes the build
non-reproducible and hides a dependency change from review.

**Be aware of `minimumReleaseAge`.** `pnpm-workspace.yaml` carries `minimumReleaseAgeExclude: [prettier@3.9.8]` but
declares no `minimumReleaseAge` — the policy is inherited from user or global pnpm config. On a clean CI runner that
global setting does not exist, so behaviour may differ from a developer machine. Check whether the install behaves
identically in CI, and if the policy matters, declare it in `pnpm-workspace.yaml` rather than relying on an
ambient setting. Record what you found either way.

**Filter per app where it is meaningful, and be honest where it is not.** `T-060` emits a frontend output. Within the
frontend, a change to only `apps/web/src` need not rebuild `apps/admin-web` — but `pnpm run lint`, `typecheck` and
`format:check` are root-scoped scripts covering both apps, and splitting them per app would mean adding per-app
tooling invocations, which the root-only ESLint constraint makes awkward. **Build per affected app; run the
root-scoped checks once for any frontend change.** Document this asymmetry; pretending to filter the root checks
would be worse than not filtering them.

**Cache the pnpm store**, keyed on the lockfile. This is cheap and uncontroversial, unlike `T-063`'s Gradle cache.

**Upload nothing on success.** If a build artifact is needed later for deployment, that is `T-072`/`T-074`'s
concern.

**Explicit minimal `permissions:`, and no credentials.**

## 5. Acceptance criteria

1. `actionlint` reports no findings.
2. A pull request touching only `apps/web/src` runs the frontend job and **no** JVM build.
3. A pull request touching only `libs/share` runs no frontend job.
4. A pull request touching the root `package.json` or `eslint.config.mjs` runs the frontend job and no JVM build.
5. The job runs `pnpm install --frozen-lockfile` and every check from the repository root — no `cd` into an app
   directory anywhere in the workflow.
6. `pnpm -r run build`, `typecheck`, `lint`, `format:check` and `test` all run, and each failure fails the job —
   proven once on a scratch branch with a deliberate error of each kind.
7. pnpm's version comes from `packageManager`, not a literal in the workflow.
8. The pnpm store is cached and the cache key includes `pnpm-lock.yaml`.
9. The install behaves identically with and without an ambient `minimumReleaseAge`, or the policy is declared in
   `pnpm-workspace.yaml` — whichever was found, recorded.
10. `permissions:` is explicit and minimal; no credentials are added.
11. A lockfile that does not match `package.json` fails the job.

## 6. Verification

```
cd <repo>
docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint -color
grep -n 'cd apps' .github/workflows/ci.yml            # expect nothing
grep -nE 'pnpm/action-setup|corepack|packageManager' .github/workflows/ci.yml
grep -n 'frozen-lockfile' .github/workflows/ci.yml
pnpm install --frozen-lockfile && pnpm -r run build && pnpm run typecheck && pnpm run lint && pnpm run format:check && pnpm run test
```

Expected: actionlint clean; no `cd` into an app; pnpm resolved from `packageManager`; frozen lockfile; every check
green locally.

## 7. Out of scope

The JVM job (`T-061`). Gradle caching (`T-063`). Dependency scanning of npm packages (`T-064`). Branch protection
(`T-065`). Building or uploading deployable frontend bundles (`T-074`). Browser-based end-to-end tests.

## 8. Hazards

- **`cd apps/web && pnpm lint`** fails because flat-config resolution is root-scoped here, and the error will read
  as a lint problem rather than a working-directory problem. `CLAUDE.md` documents this exactly.
- Omitting `--frozen-lockfile` lets CI resolve different versions than the developer did, which turns a green local
  build into an unreproducible CI result.
- Hard-coding the pnpm version creates a second source of truth against `packageManager`.
- Relying on an ambient `minimumReleaseAge` that exists only on a developer machine makes CI and local installs
  diverge in a way that is very hard to diagnose.
- Claiming the root-scoped checks are per-app filtered when they are not is a misleading workflow README.
- Triggering the frontend job from a JVM-only change wastes the second half of the exit criterion.

## 9. On completion

Mark the T-062 row done in [`README.md`](README.md). Record the `minimumReleaseAge` finding and the
root-scoped-checks asymmetry — `T-065` proves the exit criterion against both this job and `T-061`.
