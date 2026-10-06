---
title: "ADR-0004: Java 25 and Spring Boot 4 as the uniform runtime target"
parent: ADRs
nav_order: 4
---

# ADR-0004: Java 25 and Spring Boot 4 as the uniform runtime target

**Status:** Accepted

## Context

A 2022-era version of a system like this typically shipped on whatever Java/Spring Boot versions
were current when each service was first written, and then stayed there — and when a system has
more than one deployable, "current when first written" tends to differ module to module. That's a
runtime-version instance of the same drift pattern
[Lessons Learned](../lessons-learned.md#configuration-and-tooling-drift-compounds-silently)
describes for tooling config: nobody decides to fragment the runtime, it just happens one service
at a time.

## Decision

Every JVM module in this repository — `libs/*`, `services/*`, `apps/admin` — targets **Java 25**
(the current LTS release) on **Spring Boot 4**, declared once in `gradle/libs.versions.toml`
([Architecture §8](../architecture.md#8-build-graph)) and enforced by the `java-conventions` /
`spring-boot-conventions` plugins from
[Modernization Plan, Phase 1](../modernization-plan.md#phase-1--build-graph-and-conventions) — not
chosen per module, and not left to whatever happened to be current when a given module was
scaffolded.

This is a **fresh build on a current baseline**, not an upgrade path from any prior system's
runtime — see [ADR-0001](0001-monorepo-from-day-one.md) and the
[provenance note](../../README.md#provenance) for why there is no "migrate the existing services"
phase here.

## Consequences

**Positive:**
- One toolchain version means the "toolchain parity is a precondition" point in
  [Lessons Learned](../lessons-learned.md#the-shared-library-nobody-depends-on) is satisfied by
  construction — no module can be stuck on an older language level that blocks it from adopting a
  shared library's newer idioms.
- Java 25 makes virtual threads, records, pattern matching for switch, and sequenced collections
  available everywhere uniformly, including in `apps/admin`, the one module in
  [Architecture §2](../architecture.md#2-c4-level-2--containers) that isn't reactive — giving it a
  concurrency model better than thread-per-request blocking I/O, without adopting WebFlux for a
  module that doesn't need it.
- Spring Boot 4's baseline is comfortably satisfied by Java 25, leaving headroom to move the floor
  forward again later without a framework-major jump at the same time.

**Costs, accepted deliberately:**
- No migration guide from any prior Spring Boot 2.x baseline is in scope here, because there is no
  prior codebase in this repository to migrate — see [ADR-0001](0001-monorepo-from-day-one.md#context).
  Anyone adapting this reference architecture to an actual in-place upgrade needs their own
  migration plan; that is a materially different, and harder, problem than greenfield version
  selection.
- Committing to the newest LTS on day one means dependency-ecosystem lag (a library not yet
  published for the current Spring Boot major) is a real, recurring risk to budget for across
  [Modernization Plan](../modernization-plan.md), not a one-time cost paid at the start.
