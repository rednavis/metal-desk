---
title: Modernization Plan
nav_order: 4
---

# Modernization Plan
{: .no_toc }

<details open markdown="block">
  <summary>Table of contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

## Where this repository is today

This repository currently holds documentation only: the [Business Requirements](business-requirements.md),
the [Architecture](architecture.md), and the [Lessons Learned](lessons-learned.md) that shape it.
No application code has been written yet. This page is the build plan for turning the architecture
into a working system — the order of work, and the standards each piece is held to.

**This supersedes an earlier, unpublished plan** that would have imported a real prior codebase's
git history wholesale into this repository. That approach was abandoned before anything was pushed
— see the [provenance note](../README.md#provenance) — in favor of the from-scratch build described
below, which reuses the *design lessons* (documented on the [Lessons Learned](lessons-learned.md)
page) without reusing anyone else's copyrighted source, data, or business configuration.

## Build order

Phases are ordered so that each one's exit criteria are checkable before the next starts — the
point of writing them down is to make "are we done with this phase" a yes/no question, not a vibe.

### Phase 1 — Build graph and conventions

- Root `settings.gradle` listing every JVM module ([Architecture §8](architecture.md#8-build-graph)),
  targeting Java 25 on Spring Boot 4 throughout — see
  [ADR-0004](adr/0004-java25-spring-boot4-runtime.md).
- `gradle/libs.versions.toml` as the single place any dependency version is written.
- `build-logic/` included build with convention plugins (`java-conventions`,
  `spring-boot-conventions`, `quality-conventions` — checkstyle + jacoco applied from one place).
- `pnpm-workspace.yaml` for `apps/web` and `apps/admin-web`
  ([ADR-0005](adr/0005-consolidated-react-frontend.md)), kept out of the Gradle graph entirely.

**Exit criteria:** `./gradlew projects` lists every module from the architecture doc; a trivial
change in any one module builds without touching any other module's build file.

### Phase 2 — Domain model and libs

- `libs/share`: the domain aggregates from [Architecture §3](architecture.md#3-domain-model).
- `libs/payments`: the `PaymentProvider` interface and its WireMock-backed adapters
  ([Architecture §4](architecture.md#4-payments-as-a-provider-abstraction)).
- `libs/mail`: transactional mail abstraction, with a fake in-process sender for tests/dev.
- The architectural rule from [Lessons Learned](lessons-learned.md#the-shared-library-nobody-depends-on):
  wire the "no domain type outside `libs/share`" check into CI in this phase, not later — it only
  has value if it exists before there's anything to violate it.

**Exit criteria:** every downstream service module compiles against `libs/*` with zero duplicated
domain types.

### Phase 3 — Services and apps

- `services/api` — catalog reads, cart/checkout orchestration, auth, order lifecycle
  ([Architecture §5](architecture.md#5-request-flow--the-reactive-stack)).
- `services/pricing-bridge` — market-data subscription and spot→sellable price computation, with a
  fake market-data source for local dev/CI (no external feed dependency to build or test).
- `apps/admin` — fulfillment-tier configuration, quote handling, order management (API only).
- `apps/admin-web` — the staff-facing SPA consuming `apps/admin`
  ([ADR-0005](adr/0005-consolidated-react-frontend.md)).
- `apps/web` — the customer-facing SPA.

Each service's external dependencies (payment providers, mail, market data) are WireMock/fake-backed
from the first commit — see [ADR-0002](adr/0002-mocked-external-dependencies.md) — so the system is
runnable end-to-end locally and in CI without any external account from day one.

**Exit criteria:** the full checkout flow (catalog → cart → checkout → payment → confirmation),
including the manager-handoff path, runs end-to-end against mocked externals with an automated test
covering both outcomes.

### Phase 4 — CI/CD

- GitHub Actions, path-filtered so a change scoped to one module only triggers that module's build —
  a monorepo without affected-target filtering means every PR rebuilds everything, which is exactly
  the kind of avoidable friction [Lessons Learned](lessons-learned.md) is about.
- Gradle build cache (local + remote) for reasonable build times as the module count grows.
- Dependency and secret scanning on every PR from the start, not bolted on later.

**Exit criteria:** a PR touching only `apps/web` does not trigger a JVM build; a PR touching
`libs/share` triggers every downstream module's build.

### Phase 5 — GCP infrastructure

- Terraform modules per [Architecture §7](architecture.md#7-reference-deployment-gcp): Cloud Run
  service (reusable across `api`/`pricing-bridge`/`admin`), Artifact Registry, the web CDN stack,
  networking, Secret Manager, Workload Identity Federation for CI→CD auth.
- Environments as thin root modules (`dev`, `staging`, `prod`), state in a versioned GCS backend.

**Exit criteria:** `terraform plan` is clean for a `dev` environment; deploying `api` to Cloud Run
serves a health check with no manual configuration step beyond `terraform apply`.

## Standards that apply across every phase

- **Single-version policy.** A dependency version is declared exactly once
  (`gradle/libs.versions.toml`); nothing pins its own copy.
- **No committed secrets, ever** — not even test/sandbox keys. Every external credential comes from
  Secret Manager (deployed) or a local `.env` that is gitignored (local dev). WireMock stubs remove
  the need for real sandbox credentials in the first place.
- **No production-scale data of any kind is ever committed** — seed/fixture data for local dev and
  tests is synthetic, generated, and small.
- **Fix forward, not backward**, when unifying a version across modules that started out different —
  see [Lessons Learned](lessons-learned.md#prefer-fix-forward-over-pinning-back-when-unifying-versions).

---

See also: [Architecture](architecture.md) · [Business Requirements](business-requirements.md) ·
[Lessons Learned](lessons-learned.md) · [ADRs](adr/)
