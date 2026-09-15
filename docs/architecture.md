---
title: Architecture
nav_order: 3
---

# Architecture — MetalDesk Reference Platform
{: .no_toc }

<details open markdown="block">
  <summary>Table of contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

## 0. What this document is

A target reference architecture for the requirements in [Business Requirements](business-requirements.md),
built as a **monorepo from day one** — every module in one build graph, one version policy, one CI
pipeline — specifically to avoid a failure mode described in [Lessons Learned](lessons-learned.md):
a shared domain library that nobody actually depends on, so its types get copy-pasted into every
consumer instead and drift apart silently. That failure is the single biggest influence on the
module boundaries below.

External dependencies (payment gateways, mail delivery, market-data feeds) are **mocked at the
boundary** — WireMock for HTTP-based providers, a fake in-process implementation for anything
without a wire protocol — so the whole system builds, starts, and passes its test suite with zero
external accounts, credentials, or network access. See [ADR-0002](adr/0002-mocked-external-dependencies.md).

## 1. C4 Level 1 — System context

```
                    ┌─────────────────────────────────────────────┐
                    │                                               │
   ┌─────────┐      │              MetalDesk Platform              │      ┌──────────────────┐
   │ Customer │─────▶│                                               │◀─────│  Market-data feed │
   │ (web)    │      │   catalog · cart · checkout · order history   │      │  (spot prices)    │
   └─────────┘      │                                               │      └──────────────────┘
                    │                                               │
   ┌─────────┐      │                                               │      ┌──────────────────┐
   │  Staff   │─────▶│      quotes · fulfillment tiers · orders      │◀─────│  Payment providers │
   │ (admin)  │      │                                               │      │  (gateway / wallet /│
   └─────────┘      │                                               │      │   invoice)          │
                    └─────────────────────────────────────────────┘      └──────────────────┘
                                          │
                                          ▼
                                 ┌──────────────────┐
                                 │   Mail delivery    │
                                 │  (transactional)   │
                                 └──────────────────┘
```

Two human actors (Customer, Staff — see [BRD §4](business-requirements.md#4-user-classes--roles)),
three external system dependencies (market data, payment providers, mail), all three mocked in
this reference build.

## 2. C4 Level 2 — Containers

| Container | Responsibility | Talks to |
|---|---|---|
| **web** | Customer-facing SPA — catalog, cart, checkout, order history. | `api` over HTTPS/JSON |
| **api** | The customer-facing backend: catalog reads, cart/checkout orchestration, auth, order lifecycle. Reactive end-to-end (see §5). | `libs/*`, MongoDB, market-data feed, mail, payment providers |
| **pricing-bridge** | Owns the market-data feed connection and the spot→sellable price computation (BR-3); publishes current prices for `api` to read. Isolated because its availability and update cadence requirements differ from the rest of the system — a stale price feed should degrade gracefully (NFR, §9 of the BRD), never take checkout down with it. | Market-data feed, MongoDB |
| **admin** | Staff-facing back office API: fulfillment-tier configuration, quote handling, order management. Deliberately a separate deployable from `api` — different auth model (staff SSO vs. customer JWT), different availability requirements (internal tool, not customer-facing uptime target). API-only — see [ADR-0005](adr/0005-consolidated-react-frontend.md) for why it has no server-rendered UI of its own. | `libs/*`, MongoDB |
| **admin-web** | Staff-facing SPA for everything `admin` exposes. Same frontend stack as `web`, not a second UI paradigm — see [ADR-0005](adr/0005-consolidated-react-frontend.md). | `admin` over HTTPS/JSON |

## 3. Domain model

The domain model lives in one library (`libs/share`), and every service depends on it — the thing
the original system got wrong (see [Lessons Learned](lessons-learned.md)). Its core aggregates:

```
Customer ──┬── Address (delivery / billing)
           └── AuthCredential

Product ──── Category
        └── PriceRule (margin %, tax category — BR-3, BR-4)

Order ──┬── OrderLine (product, qty, unit price at time of order)
        ├── DeliveryQuote (fulfillment tier applied, cost, ETA — BR-8)
        ├── PaymentRecord (provider, method, status)
        └── OrderStatus (state machine — §6)

FulfillmentTier ── Region, value ceiling, weight ceiling, price, ETA (§7.5 of the BRD)
```

Three modeling decisions carry the rest of the design:

1. **`OrderLine` snapshots price and tax category at order time.** A later change to a product's
   margin or tax rule must never retroactively change a historical order's total (this is what
   BR-2's "price finality" requirement actually implies at the data-model level).
2. **`FulfillmentTier` is data, not code.** Staff configure ceilings and prices; the checkout flow
   evaluates against whatever is currently configured. This is the difference between "a business
   rule the platform enforces" and "a business rule the platform's engineers have to redeploy to
   change."
3. **`PaymentRecord` never stores raw payment instrument data** — only a provider reference (token,
   charge ID, invoice number). This makes the NFR in the BRD ("platform itself never stores raw
   card data") a data-model-level guarantee, not just a policy.

## 4. Payments as a provider abstraction

```
checkout ──▶ PaymentProvider (interface)
                  ├── GatewayProvider    (card / bank-debit / bank-redirect / bank-transfer / wallet)
                  ├── WalletProvider     (separate account-based wallet payment)
                  └── InvoiceProvider    (PDF generation, no live gateway call)
```

`api` never calls a specific payment vendor's SDK directly from checkout orchestration code — it
calls `PaymentProvider`, and a provider-specific adapter implements it. In this reference build,
every adapter's outbound HTTP call is intercepted by WireMock with canned responses for the success,
decline, and timeout paths, so the full checkout flow — including failure handling (FR-6.3) — is
exercised in CI without a real payment account. Swapping WireMock for a real provider in a real
deployment is purely a configuration change, not a code change, which is the point of the
abstraction.

## 5. Request flow & the reactive stack

`controller → service → repository`, fully non-blocking end-to-end (reactive types throughout, no
blocking JDBC/servlet code in the request path), backed by a document store. The reason to commit to
a reactive stack here specifically: `pricing-bridge`'s market-data connection is a long-lived
streaming subscription, and a thread-per-request blocking model wastes a thread per idle
subscriber — the container that most needs backpressure-aware I/O gets it for free by having the
whole stack share one execution model, instead of bolting reactive streams onto one service and
blocking everywhere else.

**Auth** is stateless JWT, not server-side sessions — deliberately, so `api` can scale horizontally
with no shared session store. A bearer token is validated per request; CPU-bound crypto work is
explicitly scheduled off the reactive event loop rather than blocking it.

## 6. Order state machine

```
CREATED → AWAITING_PAYMENT → PAID → FULFILLING → SHIPPED → DELIVERED
              │                                       
              ├──▶ AWAITING_MANAGER_QUOTE (fulfillment tier exceeded — §7.5)
              │        └──▶ AWAITING_PAYMENT (once staff sets terms)
              │
              └──▶ CANCELLED (payment failure, customer cancellation, or quote declined)
```

The manager-handoff path (`AWAITING_MANAGER_QUOTE`) is a first-class state, not a side channel —
which is what lets order history (FR-10.1) show a consistent status for every order regardless of
which path it took.

## 7. Reference deployment (GCP)

| Component | Service | Notes |
|---|---|---|
| `api` | Cloud Run | Stateless, reactive; min-instances ≥ 1 to avoid cold-start latency on the checkout path. |
| `pricing-bridge` | Cloud Run (or Cloud Run Job + Cloud Scheduler) | Always-on if the feed is a persistent subscription; scheduled if it's poll-based — a real deployment's choice depends on its actual market-data source. |
| `admin` | Cloud Run, behind Identity-Aware Proxy | Internal-only; IAP replaces a hand-rolled staff auth flow. |
| `admin-web` | Cloud Storage + external HTTPS Load Balancer + Cloud CDN, behind the same Identity-Aware Proxy as `admin` | Static SPA, internal-only. |
| `web` | Cloud Storage + external HTTPS Load Balancer + Cloud CDN | Static SPA; no application server needed for the frontend. |
| Document store | MongoDB Atlas on GCP, via Private Service Connect | No first-party GCP document database with this data model's fit; Atlas keeps MongoDB without self-hosting it. |
| Images | Artifact Registry | Per-service repositories, immutable tags (commit SHA, never `latest`). |
| Secrets | Secret Manager | Injected as Cloud Run environment variables at deploy time — never baked into an image or committed to source. |
| CI → CD auth | Workload Identity Federation from GitHub Actions | No long-lived service-account JSON keys in CI. |
| Observability | Cloud Logging + Cloud Trace, Spring Actuator on `/actuator` | |
| IaC | Terraform, GCS backend, versioned state bucket per environment | |

This target was chosen over the AWS shape a 2022-era version of this kind of system would typically
use (ECS Fargate + CloudFormation) specifically as part of this reference build's modernization
exercise — see [Modernization Plan](modernization-plan.md) for the reasoning, and
[ADR-0003](adr/0003-gcp-target-architecture.md) for the decision record.

## 8. Build graph

Gradle multi-project, one root `settings.gradle`, one version catalog
(`gradle/libs.versions.toml` — the single place any dependency version is declared), Gradle
convention plugins in an included `build-logic` build rather than copy-pasted script plugins per
module. Every JVM module targets Java 25 on Spring Boot 4 — see
[ADR-0004](adr/0004-java25-spring-boot4-runtime.md) for why one current LTS/framework major,
applied uniformly, is itself part of the fix for the drift pattern in
[Lessons Learned](lessons-learned.md#configuration-and-tooling-drift-compounds-silently):

```
libs/share            ← domain model + shared exceptions/utils (§3) — every service depends on this
libs/payments          ← PaymentProvider interface + adapters (§4)
libs/mail              ← transactional mail abstraction

services/api
services/pricing-bridge
apps/admin
apps/admin-web         ← pnpm workspace — see ADR-0005
apps/web               ← pnpm workspace, kept separate from the JVM build graph
```

An architectural rule enforces the domain-model boundary in CI, not just in a code review comment:
no type outside `libs/share` may declare a class in the shared domain package — see
[Lessons Learned](lessons-learned.md) for why that rule exists at all.

---

See also: [Business Requirements](business-requirements.md) · [Modernization Plan](modernization-plan.md) ·
[Lessons Learned](lessons-learned.md) · [ADRs](adr/)
