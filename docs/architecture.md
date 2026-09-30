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

Product ──── Category (tax category — BR-4)
        └── PriceRule (margin % — BR-3)

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

The one pricing formula lives in `libs/share` (`PriceDerivation`), so `services/api` and
`services/pricing-bridge` cannot disagree. BR-3 says only "reference price plus margin"; the
implemented basis is the value of the product's *fine weight* — reference price per gram × (weight in
grams × fineness ÷ 1000) — times `1 + margin`, rounded once at the end. The BRD does not state the
purity basis; if this reading is wrong, BR-3 needs amending, not the code tweaking.

Delivery tiers are evaluated by one pure function in `libs/share` (`TierSelector`) over the tiers
staff have configured, on the order value **before tax** (FR-5.1, BR-8). The BRD leaves two things
open, so the implemented rules are stated here: when several tiers in a region qualify, **the cheapest
delivery price wins** (ties go to the faster tier, then the lower tier id, so the result never depends
on list order); and when no tier qualifies, the outcome names the region's most permissive tier and
the ceiling exceeded, with **value taking precedence when both are exceeded**. A region with no
configured tier is its own outcome, never a zero-cost quote.

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

The interface (`libs/payments`) is two reactive operations, `authorise` and `confirm`, each returning
`Mono<PaymentOutcome>`. A **decline is a value, not an error**: `PaymentOutcome` is sealed
(`Captured`, `RedirectRequired`, `ElementRequired`, `DocumentIssued`, `Declined`, `Failed`), and a
`Declined` returns the customer to payment selection (FR-6.3). Only a provider that cannot be reached
or understood ends the `Mono` with `PaymentProviderException`, so "the bank said no" and "we could not
reach the bank" differ by type. The request type carries no instrument data, by the same rule as
`PaymentRecord`.

The gateway adapter (`GatewayProvider`) talks to a gateway whose address, timeout and retry budget are
configuration. **An authorisation is never retried**: once the request may have reached the gateway, a
timeout does not prove the payment failed, and taking the money twice is worse than a failed
checkout. Confirmation only reads a payment's state, so it is retried on a transport failure up to the
configured budget.

The wallet adapter uses the same client and rules. **Invoice makes no outbound call at all**: it reads the
order, splits its lines by the tax category snapshotted on each line, renders one document, or two when
the cart mixes tax-exempt and taxable categories (FR-6.2), and reports `DocumentIssued`, whose status is
`PENDING` — an invoice is a promise to pay, never a capture. The cap of two documents is tied to
`TaxCategory` having exactly two members, so a third category would mean revisiting that rule. Both
documents share one invoice number, derived from the order number, and the delivery cost is shown on the
first, so the documents' totals add up to the order's. Invoice is the path for orders above the BR-9
value ceiling; it is not a fallback.

## 5. Request flow & the reactive stack

`controller → service → repository`, fully non-blocking end-to-end (reactive types throughout, no
blocking JDBC/servlet code in the request path), backed by a document store. The reason to commit to
a reactive stack here specifically: `pricing-bridge`'s market-data connection is a long-lived
streaming subscription, and a thread-per-request blocking model wastes a thread per idle
subscriber — the container that most needs backpressure-aware I/O gets it for free by having the
whole stack share one execution model, instead of bolting reactive streams onto one service and
blocking everywhere else.

**Persistence** (task T-030) lives in `services/api` under `com.rednavis.metaldesk.api.persistence`, with
`apps/admin` to share it through a `libs/persistence` extraction (T-040). The `libs/share` domain types carry
no storage annotations (§8); the service has separate document types that carry them, and a hand-written
mapper per aggregate converts in one direction each way, so a field added to an aggregate is a compile error
in the mapper rather than a silently dropped column. Repositories are reactive Spring Data (`Mono`/`Flux`) and
the blocking MongoDB driver is not on the classpath. The order number of BR-6 is allocated by one atomic
`findAndModify` increment on a per-day counter, backed by a unique index on the order number; gaps are
allowed, reuse is not.

**HTTP conventions** (task T-031): every error response is one envelope, `{code, message, correlationId}`, produced
by a single `@RestControllerAdvice` in `api` (domain `ValidationException` → 400, `NotFoundException` → 404,
`ConflictException` → 409, anything else → 500 with no detail). Responses are separate view types, never domain
aggregates, so the wire format is not coupled to the domain and the margin behind a sellable price is not exposed.
A product without a derivable price is `ON_REQUEST` and carries no price field. `api` reads reference prices
through a `MarketDataClient` port, polled into an in-memory latest-and-previous cache; a stale or failing feed
keeps the last known prices rather than failing requests.

**Auth** is stateless JWT, not server-side sessions — deliberately, so `api` can scale horizontally
with no shared session store. A bearer token is validated per request; CPU-bound crypto work is
explicitly scheduled off the reactive event loop rather than blocking it.

Concretely (task T-032): sign-in returns a 15-minute HS256 token carrying only the customer id and their
verification state; a filter chain that is default-deny, with an explicit allowlist of public routes, validates
signature, expiry, issuer and audience on every other request. Password hashing runs on the bounded-elastic
scheduler inside one adapter. A failed sign-in is one response whatever the cause (BRD FR-2.2), and repeated
failures from a source are throttled — by a per-instance, in-memory counter, which is weaker than the FR
implies in a scaled deployment and needs a shared store before it is a real control. There are no refresh
tokens and no revocation yet.

The account lifecycle (task T-033) sits on a purpose-agnostic verification primitive (issue a code by mail,
bind it to a subject, confirm it), so registration, password reset and checkout's quick registration share one
mechanism. Codes are stored hashed, expire, are single-use and limit attempts; every failure to confirm, and every
request that could reveal whether an address is registered, gets one indistinguishable answer. Switching account
mints a new token for a permitted target rather than mutating the current one.

The cart (task T-034) is not a domain aggregate and lives in `api`. It holds only products and quantities and
derives every price on read, so it cannot go stale; price finality is applied when an order snapshots its lines.
A cart is found by an opaque reference in a cookie that works as a capability, which is what lets it survive
sign-out, and an anonymous cart is adopted or merged (once, atomically) into a customer's cart on first
authenticated use. Checkout accepts a single `CheckoutBasket`, built from the cart or from a buy-now.

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

The diagram is implemented in `libs/share` (`OrderStateMachine`), keyed on *(status, trigger)* so the
several roads into `CANCELLED` stay distinguishable. Two readings of the diagram are worth stating:

- **`CANCELLED` has three causes and three edges.** Payment abandoned and customer cancellation
  leave `AWAITING_PAYMENT`; quote declined leaves `AWAITING_MANAGER_QUOTE` (a declined quote
  would otherwise strand the order).
- **A single failed payment attempt does not cancel** (FR-6.3: the customer returns to payment
  selection with everything intact). It is a `PAYMENT_FAILED` event that leaves the order in
  `AWAITING_PAYMENT`; the diagram's "payment failure" cancellation is the separate case of the payment
  being given up.

`DELIVERED` and `CANCELLED` are terminal.

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

An architectural rule enforces the domain-model boundary in the build, not just in a code review comment:
no type outside `libs/share` may declare a class in the shared domain package — see
[Lessons Learned](lessons-learned.md) for why that rule exists at all.

**Where it is enforced.** The rules are ArchUnit tests in `libs/share`
(`share/architecture/DomainBoundaryRules` and `DomainBoundaryTest`). The `metaldesk.quality-conventions`
plugin, which every JVM module applies, compiles them into that module's tests, so they run in each module's
`test` task and therefore in `./gradlew build`. That placement is what makes them non-vacuous — `libs/share`
cannot see the classes of the modules that might violate the rule — and it means a module added later
inherits the rule without editing its own build file. There are three rules:

1. No class outside `libs/share` may be declared in the shared domain package. "Declared in `libs/share`" is
   judged from where the class file was loaded, not from its package name, which is what a copy would keep.
2. The domain (`share.domain`, `share.error`) may not depend on Spring, a service or app module,
   `libs/payments` or `libs/mail`: it is the leaf of the graph.
3. No class named `Order`, `Customer`, `Product`, `OrderLine`, `PaymentRecord`, `FulfillmentTier` or
   `DeliveryQuote` may exist outside `libs/share`.

`DomainBoundaryViolationTest` compiles deliberate violations at test time and asserts each rule rejects them, so
the rules are known to fail as well as to pass. The Phase 4 workflow will run `./gradlew build` on every pull
request; until then the local build is the gate.

---

See also: [Business Requirements](business-requirements.md) · [Modernization Plan](modernization-plan.md) ·
[Lessons Learned](lessons-learned.md) · [ADRs](adr/)
