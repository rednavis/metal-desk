# CI workflows

`ci.yml` is the only workflow. Three jobs:

| Job | Runs | What it does |
|---|---|---|
| `changes` | every push to `master` and every pull request | Decides which parts of the repository the change affects and exposes that as job outputs. Build jobs added by `T-061` (JVM) and `T-062` (frontend) read these with `needs: changes`; none of them re-implements path matching. |
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
node --test .github/scripts/                          # the assertions; the job runs these first
```

### Outputs

| Output | Meaning |
|---|---|
| `libs-share`, `libs-payments`, `libs-mail`, `libs-persistence`, `services-api`, `services-pricing-bridge`, `apps-admin` | `true` when that module must be built |
| `jvm` | `true` when any JVM module is affected |
| `jvm-modules` | JSON array of affected Gradle project paths, e.g. `[":libs:share",":services:api"]`, ready for a matrix |
| `frontend` | `true` when `apps/web` or `apps/admin-web` must be built and tested |
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
- `frontend` lists the app sources and the root files that configure both apps (`package.json`,
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
