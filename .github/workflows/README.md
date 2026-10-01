# CI workflows

`ci.yml` is the only workflow. Jobs:

| Job | Runs | What it does |
|---|---|---|
| `changes` | every push to `master` and every pull request | Decides which parts of the repository the change affects and exposes that as job outputs. Build jobs added by `T-061` (JVM) and `T-062` (frontend) read these with `needs: changes`; none of them re-implements path matching. |
| `jvm-build` | every PR/push that affects a JVM module | One matrix entry per affected module, running `./gradlew :<module>:build` (Spotless, Checkstyle, SpotBugs, PMD, tests with Testcontainers, Jacoco). **Skipped** when no JVM module is affected. |
| `JVM build` (`jvm-build-result`) | always | The one check name to require in branch protection (`T-065`). Passes when `jvm-build` succeeded **or was skipped**; fails if `changes` or any module failed. |
| `frontend-build` | every PR/push that affects a pnpm app | `pnpm install --frozen-lockfile`, a build of each affected app, then the root-scoped typecheck, lint, format check and test. **Skipped** when no app is affected. |
| `Frontend build result` (`frontend-build-result`) | always | The check name to require in branch protection (`T-065`) for the frontend; passes when `frontend-build` succeeded **or was skipped**. |
| `docs` | every pull request and push | Builds the Jekyll site in `docs/` to catch config and front-matter errors. **Deliberately not path-filtered** — see below. |
| `secrets-scan` | every pull request and push | TruffleHog filesystem scan. **Never path-filtered**: a secret can be committed in any file. |

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
| `libs:share` | all seven JVM modules |
| `libs:payments` | itself, `services:api` |
| `libs:mail` | itself, `services:api`, `apps:admin` |
| `libs:persistence` | itself, `services:api`, `apps:admin` |
| `apps:admin` | itself, `services:api` (its tests run the admin application, `adminRuntime`) |
| `services:api`, `services:pricing-bridge` | itself only |

Verified against Gradle, not by reading build files:

```
for m in libs:share libs:payments libs:mail libs:persistence services:api services:pricing-bridge apps:admin; do
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
- No build cache yet — runs are slow until `T-063`.

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
