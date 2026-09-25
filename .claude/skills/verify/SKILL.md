---
name: verify
description: Full backend + frontend build verification for metal-desk — clean Gradle build, pnpm build, optionally boot the Spring Boot services and hit their health endpoints, then clean up generated artifacts. Use before declaring a change done, or when asked to verify the project builds/runs.
---

Run a full verification pass of the metal-desk monorepo. Steps:

1. **Backend**: `./gradlew clean build` from the repo root. This runs Spotless, Checkstyle, SpotBugs,
   tests, and Jacoco across all 6 JVM modules (`libs:share`, `libs:payments`, `libs:mail`,
   `services:api`, `services:pricing-bridge`, `apps:admin`) via the `metaldesk.quality-conventions`
   convention plugin. Must end in `BUILD SUCCESSFUL`.

2. **Frontend**: `pnpm install --frozen-lockfile` then `pnpm -r run build` from the repo root. This
   builds both `apps/web` and `apps/admin-web` via `tsc -b && vite build`.

3. **If asked to verify the services actually run** (not just build), launch each Spring Boot service
   as a *separate* background process — do not pass multiple `bootRun` targets to one Gradle
   invocation, since `bootRun` is long-running and blocks the next task in the same invocation:
   - `./gradlew :apps:admin:bootRun` → poll `http://localhost:8081/actuator/health` for `"status":"UP"`
   - `./gradlew :services:api:bootRun` → poll `http://localhost:8082/actuator/health`
   - `./gradlew :services:pricing-bridge:bootRun` → poll `http://localhost:8083/actuator/health`

   For frontend dev servers: `pnpm --filter web run dev` / `pnpm --filter admin-web run dev`, then
   curl the printed local URL for a 200.

   Stop everything afterward: `./gradlew --stop`, then kill the backgrounded service/dev-server
   processes by PID.

4. **Clean up**: remove generated build output so `git status` stays clean —
   `rm -rf build build-logic/build */build **/build dist */dist` (scope to actual module build dirs;
   don't touch anything not under `build/`, `dist/`, or `.gradle`). Never commit build artifacts.

Report which of build/run steps passed or failed, with the actual command output for any failure —
don't just say "it works."
