---
title: "ADR-0001: Monorepo from day one"
parent: ADRs
nav_order: 1
---

# ADR-0001: Monorepo from day one

**Status:** Accepted

## Context

The reference system in [Architecture](../architecture.md) has four deployable services and a
shared domain library (`libs/share`) that every one of them depends on. The alternative to a
monorepo — one repository per service, sharing the domain library as a published/resolved
dependency — is a legitimate, common choice. It was rejected here specifically because of the
failure mode documented in [Lessons Learned](../lessons-learned.md#the-shared-library-nobody-depends-on):
in a multi-repo setup, actually consuming a shared library costs more (in process, review, and
release-coordination overhead) than copying the few classes a new service needs — so under any real
delivery pressure, teams copy instead of depend, and the "shared" library silently stops being
shared.

## Decision

**One repository, one build graph, one version policy.** Every JVM module — `libs/*`,
`services/*`, `apps/admin` — is listed in a single root `settings.gradle`; `apps/web` lives in the
same repository in a separate pnpm workspace, kept out of the Gradle graph. No git submodules: a
submodule still requires a deliberate "update the pointer" step to consume a change, which
reintroduces exactly the friction this decision exists to remove.

An architectural rule (an ArchUnit or Checkstyle `IllegalImport` check, enforced in CI) forbids any
module outside `libs/share` from declaring a type in the shared domain package. This is the part
that actually prevents the fork from re-forming — a monorepo alone only makes the problem visible,
it doesn't stop it (see [Lessons Learned](../lessons-learned.md#a-monorepo-doesnt-fix-anything-by-itself)).

## Consequences

**Positive:**
- Adding a real dependency on `libs/share` and copying its classes now cost the same number of
  keystrokes (one line in `build.gradle` either way), removing the delivery-pressure incentive to
  copy.
- One PR can make an atomic, reviewable, cross-cutting change (e.g., a field added to a shared
  domain type and every consumer updated to use it) instead of N uncoordinated PRs across N
  repositories.
- One CI pipeline can see the whole build graph, which is what makes affected-target build
  filtering (Modernization Plan, Phase 4) possible to implement at all.

**Costs, accepted deliberately:**
- Every contributor's clone contains every service, even one they never touch — mitigated by
  affected-target CI filtering, not by splitting the repository back apart.
- A monorepo needs build-graph tooling (a version catalog, convention plugins, a build cache) from
  the start, or build times degrade as modules are added. This cost is paid explicitly in
  [Modernization Plan, Phase 1](../modernization-plan.md#phase-1--build-graph-and-conventions),
  not deferred.

**Explicitly not decided here:** a build-system change beyond Gradle (e.g., Bazel) is a separate,
later decision, not bundled into this one.
