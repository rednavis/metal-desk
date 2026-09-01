# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

`docs/` (business-requirements, architecture, ADRs, modernization-plan) is the source of truth and is kept in step
with the code. Work lands one PR per task spec (`tasks/T-xxx-*.md`), titled `T-0xx — <title>`, referencing its
`docs/modernization-plan.md` phase. All implementation code is authored by Claude Code from human direction, not hand-written — see
`docs/meetings/2026-09-17-kickoff.md` and `CONTRIBUTING.md`.

## Module structure — Gradle vs pnpm boundary is deliberate

JVM modules, all under group `com.rednavis.metaldesk`, listed in `settings.gradle.kts`:
- `libs:share`, `libs:payments`, `libs:mail`, `libs:persistence`, `libs:migrations` — shared domain/abstractions; `libs:persistence`
  holds the Mongo documents and mappers only (no repositories, no driver: `services:api` has reactive
  repositories over them, `apps:admin` blocking ones)
- `libs:migrations` holds the Mongock change units (ADR-0006); `services:api` and `apps:admin` import its
  `MigrationsConfiguration`, so migrations run at startup in both (Mongock's DB lock makes that safe). Add a
  migration as a class in `migrations.changes` plus a line in `MongoMigrations`; never edit one that has run.
  Not Flyway: its MongoDB support needs an external `mongosh`.
- Staff auth is **application login** (ADR-0006), not IAP: `apps:admin` signs in `users` and issues its own JWT
  (audience `metal-desk-staff`, key `ADMIN_JWT_SIGNING_KEY`); `services:api` signs in customers only. The two
  tokens must never be interchangeable — keep issuer/audience/key separate and the cross-rejection tests passing.
  The first migration creates the development users `admin/admin` and `manager/manager`.
  **No IAP is deployed** (T-076 decided against it; `tasks/T-076-*` still describes the old design — `docs/` wins).
  `apps/admin` must ignore IAP headers (`IapHeadersIgnoredTest`); staff access is network-restricted (`infra/README.md`).
  Staff and customer tokens expire after 30 min idle and are renewed via each service's `/auth/refresh`.
- `services:api` (port 8082), `services:pricing-bridge` (port 8083), `apps:admin` (port 8081)

`apps/web` and `apps/admin-web` (React 19/TS/Vite, pnpm workspace) are **intentionally excluded**
from the Gradle build graph — see ADR-0005 and Modernization Plan Phase 1. Never add a Gradle module
for them or expect `./gradlew projects` to list them. `apps/admin-web` is the sole client of
`apps/admin`, which is a pure API with no server-rendered UI.

`apps:admin` is the **only non-reactive** module: `spring-boot-starter-web` (MVC) with
`spring.threads.virtual.enabled: true`, per ADR-0004. `services:api` and `services:pricing-bridge`
are WebFlux. README and `docs/architecture.md` say "reactive end-to-end" — that is the target, not
the current state. Jar names are `metal-*` (`base { archivesName }`), not the module names.
`settings.gradle.kts` sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS` — a module cannot declare its own
repositories.

## Build commands

- Backend: `./gradlew build` (or `./gradlew clean build` for a full verification pass) — runs
  Spotless, Checkstyle, SpotBugs, PMD, tests, and Jacoco for all 8 JVM modules via the `metaldesk.quality-conventions`
  convention plugin.
  `./gradlew projects` lists **11** projects, not 8 — `:apps`, `:libs`, `:services` are empty
  container projects with no build file. That is not a misconfiguration.
- `gradle.properties` turns on the build cache, parallel execution and the **configuration cache**. A task
  action must not capture the script object (e.g. a `doFirst { }` reading a configuration) — pass values
  through a provider or a `CommandLineArgumentProvider` (see `services/api/build.gradle.kts`). Bypass with
  `--no-build-cache` / `--no-configuration-cache`. Root plugins add `dependencyUpdates` (ben-manes) and
  `cyclonedxBom` (the SBOM CI's dependency scan reads).
- Fix formatting: `./gradlew spotlessApply` for Java, `pnpm run format` for TS/React. There is no
  single command — a full pass is both. Prettier deliberately skips `*.md` and `docs/`
  (`.prettierignore`), so `pnpm run format` will not touch documentation.
- Frontend: `pnpm install` then `pnpm -r run build` (root scripts: `build`, `typecheck`, `lint`,
  `lint:fix`, `format`, `format:check`, `test`; `pnpm run test` runs each app's Vitest suite once (`vitest run`; `pnpm --filter <app> test` is watch mode), which includes a
  contract test that reads the Java DTOs — run it from the repo root, as it reads paths relative to it). ESLint config is a single root `eslint.config.mjs` covering
  both `apps/web/src` and `apps/admin-web/src` (it also bans literal strings in rendered positions in `apps/web`'s
  `.tsx`, `react/jsx-no-literals`: user-facing text is an i18n key; `*.test.tsx` is exempt) — don't add a per-app config, and don't run `eslint`
  from inside an app directory (flat config resolution is root-scoped here; always run from repo root).
- `.github/scripts/*.mjs` (CI's affected-module filter and OSV gate; Node standard library only) are tested with
  `node --test .github/scripts/` from the repo root, and **Prettier covers them** — `format:check` fails CI if
  they are not formatted, so run `pnpm run format` after editing them.
- Local MongoDB: `docker compose up -d` (`compose.yaml`, throwaway `admin/admin`). The apps default `MONGODB_URI` to it,
  so `bootRun` needs it running; `docker compose down -v` wipes the data.
- Run a service: `./gradlew :apps:admin:bootRun` / `:services:api:bootRun` / `:services:pricing-bridge:bootRun`.
  **Don't pass multiple `bootRun` targets to one Gradle invocation** — `bootRun` is long-running and
  blocks, so the second task never starts. Launch each as a separate background process instead.
- Frontend dev server: `pnpm --filter web run dev` / `pnpm --filter admin-web run dev` (Vite).
- `build-logic` is an *included build*, not a subproject, so the root `build.gradle.kts` explicitly
  wires `clean` to reach it. Don't remove that wiring.

## Frontend styling conventions

Both apps style through CSS custom properties: `apps/web/src/theme/tokens.css` and `apps/admin-web/src/ui/tokens.css`
(palette, shadows, base type), then `ui/ui.css` for the kit and a feature `*.css` per feature. Use a token, never a
literal colour, so dark mode keeps working. Both switch theme on `<html data-theme>`: `web` from its preferences, `admin-web` from a
self-contained switch in the header (`ui/theme.ts`, saved in localStorage; "system" follows `prefers-color-scheme`). Class names are `md-*`. Status and direction are never colour alone (label
or glyph too). The brand mark and tokens are deliberately duplicated between the apps (T-079): a shared package would
need `.github/scripts/affected.mjs` changed first.

## Quality gates are strict — what actually fails the build

- Checkstyle: `maxWarnings = 0` and `severity` defaults to `warning`, so **every** violation fails.
  Config lives in `config/checkstyle/` (not a root `checkstyle.xml`).
- Javadoc is mandatory: `MissingJavadocType` (scope=protected, excludeScope=nothing) requires a doc
  comment on every public/protected type; `MissingJavadocMethod` covers protected+ members of public
  types. New Java without Javadoc will not build.
- `System.out`/`System.err` (`noSystemOutErr`) and `printStackTrace` (`noPrintStackTrace`) are custom
  Regexp rules that fail the build — use `@Slf4j`.
- SpotBugs runs at `Effort.MAX` / `Confidence.LOW` with `ignoreFailures = false`, and
  `config/spotbugs/spotbugs_exclude.xml` is empty — any low-confidence finding fails.
- PMD runs every Java category ruleset (`config/pmd/pmd-ruleset.xml`) with `ignoreFailures = false`;
  the few rules that contradict Checkstyle/Google Java Format/Lombok are `<exclude>`d there with a
  reason. Relax rules there, not with `@SuppressWarnings("PMD.*")` annotations in Java files.
- **Architecture rule (ArchUnit).** `DomainBoundaryTest` (rules and the reserved-name list in `DomainBoundaryRules`) fails the build if a class is declared in
  `com.rednavis.metaldesk.share.domain..` outside `libs/share`, if the domain depends on Spring or another
  module, or if a class named `Order`, `Customer`, `Product`, `OrderLine`, `PaymentRecord`,
  `FulfillmentTier` or `DeliveryQuote` exists outside `libs/share`. It lives once in `libs/share`'s tests and
  `metaldesk.quality-conventions` compiles it into every module's tests — don't add a per-module copy, and a
  new module gets it for free. The domain model belongs in `libs/share`; reuse it, never copy it.
- **Coverage floor is 80%, always.** `jacocoTestCoverageVerification` is wired into `check` in
  `metaldesk.quality-conventions`: every JVM module must cover at least 80% of lines **and** branches or `build` fails.
  Frontend: `test:run` runs `vitest run --coverage` with 80% thresholds (lines, statements, functions, branches) in each
  app's `vite.config.ts`. Never lower a threshold or exclude code to pass; add tests. New code ships with its tests.
  Branches are the usual gap (record compact-constructor validation, `null`/blank checks): test each refusal.
  Test-code lint is as strict as main: method names may not start with a one-letter word (`aKey…`, `anId…`) or hold two
  adjacent capitals (`…AByte…`), no `throws Exception`, no `var`, no `= null` assignments, no objects created in loops,
  no repeated string literals (use constants), at most 4 static imports.
- `services:api` and `apps:admin` tests run against a real MongoDB through Testcontainers, so `./gradlew build`
  needs a running Docker daemon. Every Mongo test goes through `SharedMongo` (`libs:persistence` test fixtures),
  which shares one container per JVM — don't start a container per class. `apps:admin` must never have WebFlux or
  the reactive Mongo driver on its classpath (`AdminClasspathTest`); its `RestTestClient`, not `WebTestClient`. On OrbStack, if Testcontainers can't find Docker, export
  `DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock` and
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
- **CI is path-filtered** (`.github/workflows/ci.yml`, details in `.github/workflows/README.md`). Required checks on
  `master` are `JVM build`, `Frontend build result`, `Scan for committed secrets` and `Scan dependencies` (OSV, fails
  on CVSS ≥ 7.0) — never the per-module `JVM build :<module>` matrix names. Still build locally before pushing.
- An unfixable advisory is suppressed in `osv-scanner.toml` only with both `reason` and `ignoreUntil` (at most
  90 days out); otherwise `osv-gate.mjs` fails the job. Never suppress just to get a green build.

## Versions and conventions

- All JVM dependency/plugin versions live in `gradle/libs.versions.toml` — never pin a version in a
  module's `build.gradle.kts`. The one deliberate exception is the `foojay-resolver-convention`
  plugin version in `settings.gradle.kts` (a settings-level plugin can't reference a catalog it helps
  resolve).
- `jacksonFloor` / `tomcatFloor` in the catalog raise jackson and Tomcat above what Spring Boot 4.1.1's BOM manages, to
  clear high-severity advisories the dependency scan blocks on (applied in `metaldesk.spring-boot-conventions` and in
  `libs:payments`, which imports the BOM itself). Remove them once Boot manages versions at or above the floor. Check a
  change locally by running `cyclonedxBom`, then the same `osv-scanner` + `osv-gate.mjs` pair CI runs.
- Shared build behavior lives in `build-logic/src/main/kotlin/metaldesk.*.gradle.kts` (java-conventions,
  spring-boot-conventions, quality-conventions), applied by every module — don't duplicate that
  config in a module's own `build.gradle.kts`.
- Lombok needs *both* the per-module `annotationProcessor` wiring in `java-conventions.gradle.kts`
  and `libs:share`'s `compileOnlyApi` — Gradle doesn't resolve `annotationProcessor` transitively
  (gradle/gradle#2510). Don't remove either. Usage is limited to `@RequiredArgsConstructor` and
  `@Slf4j`; prefer records and constructor injection.

## Container images

`deploy/images/Dockerfile` builds all three services (`--build-arg MODULE=services/api|services/pricing-bridge|apps/admin`, from
the repo root). Its build-context filter must be named **`Dockerfile.dockerignore`** beside it: a plain `.dockerignore`
there is silently ignored and sends the whole repo (and any `.env`) to the builder. The Temurin base-image tags live
only in the Dockerfile (not the version catalog). Never pass a credential as a build argument. See `deploy/README.md`.

## Infrastructure (`infra/terraform`)

Runbook is `infra/README.md`. CI (`infra.yml`, not a required check) runs `terraform fmt -check -recursive`,
`infra/scripts/check-env-parity.sh`, `init -backend=false && validate` per root, and `tflint --recursive` — run the same locally.
- **Never `terraform apply`** — it costs real money; only the T-078 flow applies (budget first), and CI never does.
- Env roots (`envs/dev|staging|prod`) stay in parity: same modules, differing only in `values.tf`, backend and tfvars
  example. Logic goes in `modules/`. Names are `metaldesk-<env>-<resource>` via `local.name_prefix`.
- The Terraform version lives in both `envs/*/versions.tf` and `infra.yml` — edit both. Lock files cover 4 platforms
  (`terraform providers lock -platform=...`).
- Secrets by reference only (`{secret, version}`); images by commit SHA or digest, never a mutable tag.
- App ports are hard-coded (`server.port`, not `PORT`), so Cloud Run `container_port` must match.
- `image.yml` / `deploy.yml` / `frontend-deploy.yml` authenticate via Workload Identity Federation (no SA keys) and
  deploy from `master` only.

## Checkstyle: a real Gradle bug, not a config mistake

If Checkstyle fails with "Unable to create Root Module", the suppression-path wiring in
`metaldesk.quality-conventions.gradle.kts` is the cause, not the XML — Gradle's `config_loc`
auto-wiring is broken here and the paths are injected manually. The full explanation is in that
file's comments; don't re-derive it.
Checkstyle's formatting rules are kept in sync with Spotless's `googleJavaFormat()` (100 cols,
2-space indent) — never add a rule that conflicts with Google Java Format's real output.

## Repo etiquette (from CONTRIBUTING.md)

- No real external credentials or sandbox keys, ever — every external dependency is mocked
  (WireMock or an in-process fake, ADR-0002).
- Keep `docs/architecture.md` in sync in the same PR as the code change it describes.
- Task specs are `tasks/T-xxx-*.md`; update that task's row in the `tasks/README.md` ledger in the same PR.
  If a spec and `docs/` disagree, `docs/` wins.
- Legacy repos added with `--add-dir` (e.g. dealboard) are behavioural reference only — never copy their code,
  data or configuration (the IP belongs to a former client; see `CONTRIBUTING.md`).
- `docs/` is a Jekyll site (`remote_theme: just-the-docs`) — new pages need front matter (`title`,
  `nav_order`, `parent` if nested under ADRs).
- Project skills: `/verify` (full build + cleanup), `/feature-issue <n>` — the GitHub issue number is not the task id;
  the `T-xxx` comes from the issue title.

## Subdirectory CLAUDE.md

None exist yet. `infra/` is the first candidate (the Infrastructure section above could move there), then
`libs/share`'s domain rules or `apps/web`'s component conventions — prefer a scoped `CLAUDE.md` over growing
this file; ask if you want one created.
