# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Documentation-first as of 2026-09: `docs/` (business-requirements, architecture, ADRs, modernization-plan)
is written before most code. Implementation proceeds one PR per `docs/modernization-plan.md` phase.
All implementation code is authored by Claude Code from human direction, not hand-written — see
`docs/meetings/2026-09-17-kickoff.md` and `CONTRIBUTING.md`.

## Module structure — Gradle vs pnpm boundary is deliberate

JVM modules, all under group `com.rednavis.metaldesk`, listed in `settings.gradle.kts`:
- `libs:share`, `libs:payments`, `libs:mail`, `libs:persistence` — shared domain/abstractions; `libs:persistence`
  holds the Mongo documents and mappers only (no repositories, no driver: `services:api` has reactive
  repositories over them, `apps:admin` blocking ones)
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
  Spotless, Checkstyle, SpotBugs, tests, and Jacoco for all 7 JVM modules via the `metaldesk.quality-conventions`
  convention plugin.
  `./gradlew projects` lists **10** projects, not 7 — `:apps`, `:libs`, `:services` are empty
  container projects with no build file. That is not a misconfiguration.
- Fix formatting: `./gradlew spotlessApply` for Java, `pnpm run format` for TS/React. There is no
  single command — a full pass is both. Prettier deliberately skips `*.md` and `docs/`
  (`.prettierignore`), so `pnpm run format` will not touch documentation.
- Frontend: `pnpm install` then `pnpm -r run build` (root scripts: `build`, `typecheck`, `lint`,
  `lint:fix`, `format`, `format:check`). ESLint config is a single root `eslint.config.mjs` covering
  both `apps/web/src` and `apps/admin-web/src` — don't add a per-app config, and don't run `eslint`
  from inside an app directory (flat config resolution is root-scoped here; always run from repo root).
- Run a service: `./gradlew :apps:admin:bootRun` / `:services:api:bootRun` / `:services:pricing-bridge:bootRun`.
  **Don't pass multiple `bootRun` targets to one Gradle invocation** — `bootRun` is long-running and
  blocks, so the second task never starts. Launch each as a separate background process instead.
- Frontend dev server: `pnpm --filter web run dev` / `pnpm --filter admin-web run dev` (Vite).
- `build-logic` is an *included build*, not a subproject, so the root `build.gradle.kts` explicitly
  wires `clean` to reach it. Don't remove that wiring.

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
- **Architecture rule (ArchUnit).** `DomainBoundaryTest` fails the build if a class is declared in
  `com.rednavis.metaldesk.share.domain..` outside `libs/share`, if the domain depends on Spring or another
  module, or if a class named `Order`, `Customer`, `Product`, `OrderLine`, `PaymentRecord`,
  `FulfillmentTier` or `DeliveryQuote` exists outside `libs/share`. It lives once in `libs/share`'s tests and
  `metaldesk.quality-conventions` compiles it into every module's tests — don't add a per-module copy, and a
  new module gets it for free. The domain model belongs in `libs/share`; reuse it, never copy it.
- Jacoco produces reports but **no** coverage threshold is wired (`jacocoTestCoverageVerification`
  is never invoked). Coverage can be 0% and `build` still passes.
- `services:api` and `apps:admin` tests run against a real MongoDB through Testcontainers, so `./gradlew build`
  needs a running Docker daemon. Every Mongo test goes through `SharedMongo` (`libs:persistence` test fixtures),
  which shares one container per JVM — don't start a container per class. `apps:admin` must never have WebFlux or
  the reactive Mongo driver on its classpath (`AdminClasspathTest`); its `RestTestClient`, not `WebTestClient`. On OrbStack, if Testcontainers can't find Docker, export
  `DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock` and
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
- **CI does not build the code.** `.github/workflows/ci.yml` runs only a Jekyll docs build and a
  TruffleHog secret scan. A local `./gradlew build` / `pnpm -r run build` is the only real gate.

## Versions and conventions

- All JVM dependency/plugin versions live in `gradle/libs.versions.toml` — never pin a version in a
  module's `build.gradle.kts`. The one deliberate exception is the `foojay-resolver-convention`
  plugin version in `settings.gradle.kts` (a settings-level plugin can't reference a catalog it helps
  resolve).
- Shared build behavior lives in `build-logic/src/main/kotlin/metaldesk.*.gradle.kts` (java-conventions,
  spring-boot-conventions, quality-conventions), applied by every module — don't duplicate that
  config in a module's own `build.gradle.kts`.
- Lombok needs *both* the per-module `annotationProcessor` wiring in `java-conventions.gradle.kts`
  and `libs:share`'s `compileOnlyApi` — Gradle doesn't resolve `annotationProcessor` transitively
  (gradle/gradle#2510). Don't remove either. Usage is limited to `@RequiredArgsConstructor` and
  `@Slf4j`; prefer records and constructor injection.

## Checkstyle: a real Gradle bug, not a config mistake

If Checkstyle fails with "Unable to create Root Module", the suppression-path wiring in
`metaldesk.quality-conventions.gradle.kts` is the cause, not the XML — Gradle's `config_loc`
auto-wiring is broken here and the paths are injected manually. The full explanation is in that
file's comments; don't re-derive it.
Checkstyle's formatting rules are kept in sync with Spotless's `googleJavaFormat()` (100 cols,
2-space indent) — never add a rule that conflicts with Google Java Format's real output.

## Repo etiquette (from CONTRIBUTING.md)

- No real external credentials or sandbox keys, ever — every external dependency is mocked
  (WireMock or an in-process fake, ADR-0002). CI runs a secret scan (TruffleHog) on every PR.
- No production-scale or real-company data — fixtures are synthetic and small.
- Fix forward, not backward, when unifying a dependency version (see `docs/lessons-learned.md`).
- One PR per Modernization Plan phase; reference the phase or ADR a change implements.
- Keep `docs/architecture.md` in sync in the same PR as the code change it describes.
- `docs/` is a Jekyll site (`remote_theme: just-the-docs`) — new pages need front matter (`title`,
  `nav_order`, `parent` if nested under ADRs).

## Subdirectory CLAUDE.md

None exist yet. As real per-module code lands (e.g. `libs/share`'s domain rules, or `apps/web`'s
component conventions), consider adding a scoped `CLAUDE.md` in that directory rather than growing
this file — ask if you want one created.
