# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Documentation-first as of 2026-09: `docs/` (business-requirements, architecture, ADRs, modernization-plan)
is written before most code. Implementation proceeds one PR per `docs/modernization-plan.md` phase.
All implementation code is authored by Claude Code from human direction, not hand-written — see
`docs/meetings/2026-09-17-kickoff.md` and `CONTRIBUTING.md`.

## Module structure — Gradle vs pnpm boundary is deliberate

JVM modules, all under group `com.rednavis.metaldesk`, listed in `settings.gradle.kts`:
- `libs:share`, `libs:payments`, `libs:mail` — shared domain/abstractions
- `services:api` (port 8082), `services:pricing-bridge` (port 8083), `apps:admin` (port 8081)

`apps/web` and `apps/admin-web` (React 19/TS/Vite, pnpm workspace) are **intentionally excluded**
from the Gradle build graph — see ADR-0005 and Modernization Plan Phase 1. Never add a Gradle module
for them or expect `./gradlew projects` to list them. `apps/admin-web` is the sole client of
`apps/admin`, which is a pure API with no server-rendered UI.

## Build commands

- Backend: `./gradlew build` (or `./gradlew clean build` for a full verification pass) — runs
  Spotless, Checkstyle, SpotBugs, tests, and Jacoco for all 6 JVM modules via the `metaldesk.quality-conventions`
  convention plugin.
- Frontend: `pnpm install` then `pnpm -r run build` (root scripts: `build`, `typecheck`, `lint`,
  `lint:fix`, `format`, `format:check`). ESLint config is a single root `eslint.config.mjs` covering
  both `apps/web/src` and `apps/admin-web/src` — don't add a per-app config, and don't run `eslint`
  from inside an app directory (flat config resolution is root-scoped here; always run from repo root).
- Run a service: `./gradlew :apps:admin:bootRun` / `:services:api:bootRun` / `:services:pricing-bridge:bootRun`.
  **Don't pass multiple `bootRun` targets to one Gradle invocation** — `bootRun` is long-running and
  blocks, so the second task never starts. Launch each as a separate background process instead.
- Frontend dev server: `pnpm --filter web run dev` / `pnpm --filter admin-web run dev` (Vite).
- `./gradlew clean` also cleans `build-logic/build` — `build-logic` is an *included build*
  (`includeBuild("build-logic")`), not a subproject, so the root `build.gradle.kts` explicitly wires
  `clean` to depend on it. Don't remove that wiring.

## Versions and conventions

- All JVM dependency/plugin versions live in `gradle/libs.versions.toml` — never pin a version in a
  module's `build.gradle.kts`. The one deliberate exception is the `foojay-resolver-convention`
  plugin version in `settings.gradle.kts` (a settings-level plugin can't reference a catalog it helps
  resolve).
- Shared build behavior lives in `build-logic/src/main/kotlin/metaldesk.*.gradle.kts` (java-conventions,
  spring-boot-conventions, quality-conventions), applied by every module — don't duplicate that
  config in a module's own `build.gradle.kts`.
- Lombok's `annotationProcessor` is wired in `java-conventions.gradle.kts` for every module, not just
  `libs:share` (which exposes it on the *compile* classpath via `compileOnlyApi`) — Gradle never
  resolves `annotationProcessor` transitively (gradle/gradle#2510), so both wirings are required.
  Lombok usage is limited to `@RequiredArgsConstructor` and `@Slf4j`; prefer records and constructor
  injection first.

## Checkstyle: a real Gradle bug, not a config mistake

`checkstyle.xml`'s `SuppressionFilter`/`SuppressionXpathFilter` read custom property names
(`org.checkstyle.google.*.config`), not the standard `${config_loc}` — Gradle's `config_loc`
auto-wiring is broken on this Gradle version (gradle/gradle#9761, #9762, #11058, #16837) and Gradle
hard-rejects setting `config_loc` directly in `configProperties`. The real paths are injected via
`checkstyle.configProperties` in `metaldesk.quality-conventions.gradle.kts`. If Checkstyle starts
failing with "Unable to create Root Module", check that wiring before assuming the XML is wrong.
Checkstyle's formatting-related rules are kept in sync with Spotless's `googleJavaFormat()` — don't
add a Checkstyle rule that conflicts with Google Java Format's actual output (e.g. don't import
Sun-style 4-space indent or 140-col line length rules).

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
