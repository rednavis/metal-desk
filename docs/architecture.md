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
| **pricing-bridge** | Owns the market-data feed connection and the spot→sellable price computation (BR-3); publishes current prices for `api` to read. Isolated because its availability and update cadence requirements differ from the rest of the system — a stale price feed should degrade gracefully (NFR, §9 of the BRD), never take checkout down with it. Today the feed is the in-process fake (ADR-0002), the latest and previous price per metal are held in memory per instance (no MongoDB yet), and staleness is reported at `/actuator/feed` rather than through `/actuator/health`. | Market-data feed (fake until a real adapter exists) |
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

**Persistence** (task T-030, extracted in T-040) is split by what is shared and what is not. The storage shape —
the document types and their mappers — lives in `libs/persistence`, used by both `services/api` and `apps/admin`;
the repositories do not, because a repository is an execution-model choice: `services/api` declares reactive ones,
`apps/admin` (MVC on virtual threads) blocking ones over the same collections, and `libs/persistence` has no
MongoDB driver on its classpath. The `libs/share` domain types carry
no storage annotations (§8); `libs/persistence` has separate document types that carry them, and a hand-written
mapper per aggregate converts in one direction each way, so a field added to an aggregate is a compile error
in the mapper rather than a silently dropped column. In `services/api` repositories are reactive Spring Data (`Mono`/`Flux`) and
the blocking MongoDB driver is not on its classpath; `apps/admin` has the blocking driver and no reactive one. The order number of BR-6 is allocated by one atomic
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

Checkout (task T-035) keeps its intermediate state in a persisted, expiring checkout session found by an opaque id.
That is server-side state, but it does not contradict the stateless-authentication decision above: nothing about a
checkout rides in the token, any instance can serve any step, and the customer can go back and edit an earlier one.
Validation of a step reports every invalid field at once in the shared error envelope, and a guest's "remember me"
reuses the verification primitive rather than verifying anything itself.

Delivery (task T-036) is evaluated against the fulfillment tiers as configured at that moment, from the destination
region, the order value before tax and the total weight, and the outcome is a stage on the checkout session:
payment allowed with an automatic quote, or handoff required (a ceiling exceeded, or no tier for the region) with a
reason code. The refusal to take payment on a handoff session is enforced on the server by a gate every payment
endpoint calls first, so a crafted request reaches no provider. A handoff creates the order and moves it to
`AWAITING_MANAGER_QUOTE` through the state machine; the customer's reference is its order number, and staff are
told everything they need to price it.

What staff see in the back office comes from `apps/admin`, not from the notification mail: the order detail carries the
customer's contact details, and for an order awaiting a quote a handoff context, the destination region, the total
weight and which ceiling the region's widest tier is exceeded on. None of that is stored on the order, so it is
derived when the order is shown, by the same `TierSelector` over the tiers *as configured now*; a tier widened since
the handoff shows as `WITHIN_TIERS` rather than repeating what the customer was told. `GET /api/admin/me` returns the
identity the proxy reported so the SPA can show whose name an action is recorded under, and declining a quote takes a
reason, which goes to the audit log beside who declined (task T-056).

Once staff answer (task T-040: the order returns to `AWAITING_PAYMENT` carrying their delivery quote) the session is
*adopted*: its delivery state becomes payment-allowed with that quote and its payment state is bound to the handed-off
order, so the ordinary payment path charges that very order at the total it carries instead of creating a second one
(task T-041). The terms carry a validity, checked on every payment-step call, after which the session is refused with
`checkout.quote-expired`.

**Display currency and preferences** (task T-051, BRD FR-1.6 to FR-1.8). The catalog and the cart can be shown in another
currency than orders are settled in: the server converts (`?currency=` on those endpoints), the client only formats, so
it never multiplies a price. Conversion is for display only: checkout, payment, confirmation and order history are
always in the settlement currency, because the overview total must equal the charged amount. The rates come from an
`ExchangeRates` port whose only implementation serves configured demo rates, reported to clients as `FAKE` and disclosed
in the UI; no rate source exists in this build. A signed-in customer's theme, language and currency are stored server-side
(`/api/account/preferences`) and win over the browser's copy on sign-in, field by field.

Payment (task T-037) completes the checkout. The offered methods are filtered by a business-rule policy: above a
configurable ceiling on the grand total (default 2500.00 EUR, strictly above) the wallet account is withheld unless
`wallet-high-value` is set, and invoice is never filtered. Choosing a method creates nothing; executing it requires the
customer to echo the total they saw (`confirmedTotal`), then creates the order *before* the provider is called, so the
price is final and the order number exists (BR-2, BR-6). The checkout package talks only to the `PaymentProvider` SPI;
the three adapters are wired in `api/payments`. A decline never cancels the order (a retry or another method reuses
it), a provider timeout is reported as "could not confirm", not as a decline, and editing an earlier step cancels the
unpaid order and freezes nothing stale: delivery evaluation is not re-run once an order or handoff exists. A
provider's return callback is only a claim: the server asks the provider to `confirm` the reference and ignores every
query parameter. Settlement (`OrderSettlement`) and invoice delivery (`InvoiceSink`) are the hooks the next paragraph fills in.

Confirmation (task T-038) closes the checkout on every success path (a captured payment, an issued invoice, a manager
handoff) and allocates no order number of its own: the order already has the one the payment step or the handoff gave
it, so there is one number per order. The mails (order confirmation, order notification, and for an invoice the
invoice to customer and staff) are claimed in a per-order-and-template ledger before they are sent, so a repeated
confirmation, a duplicate callback or a reconciliation sends each once, and a failed send releases its claim so the next
attempt delivers what is missing. The invoice documents are rendered during payment and archived then; confirmation
attaches the archived documents and never renders again, so the mail cannot disagree with the payment record. A
confirmation that cannot complete is an `OperationFailedException`: a 503 with a machine-readable code, and a correlation
id that appears in both the response and the error log beside the order number. Order history is a read projection of
the order's own snapshot, so a later price change cannot move a total (BR-2); every `OrderStatus` has a customer label;
carrier and tracking come from a separate shipment record and are shown only from `SHIPPED` onward; another customer's
order is a 404, never a 403. Inquiries (catalog, product, handoff) need no sign-in: the sender's email is validated,
the inquiry is stored, and the customer and staff are mailed, with a handoff inquiry having to name the order and the
email it was placed with.

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

**Covered end to end.** Both ways through checkout are exercised by automated tests in `services/api`
(`CheckoutSelfServiceE2eTest`, `CheckoutHandoffE2eTest`, task T-041): through the HTTP surface only, against the
in-process fakes and a WireMock provider stub, with the charged amount read from the stub's request journal. The
handoff test runs the real `apps/admin` for the staff step, started in a class loader of its own because the two
services cannot share a classpath, and called over HTTP.

## 7. Reference deployment (GCP)

| Component | Service | Notes |
|---|---|---|
| `api` | Cloud Run | Stateless, reactive; min-instances ≥ 1 to avoid cold-start latency on the checkout path. |
| `pricing-bridge` | Cloud Run (or Cloud Run Job + Cloud Scheduler) | Always-on if the feed is a persistent subscription; scheduled if it's poll-based — a real deployment's choice depends on its actual market-data source. The service is built as the always-on streaming shape (T-039); the job shape is not supported by it as it stands. |
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
libs/persistence       ← MongoDB documents and mappers shared by api and admin (no repositories, no driver)

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
