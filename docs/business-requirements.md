---
title: Business Requirements
nav_order: 2
---

# Business Requirements — MetalDesk Reference Platform
{: .no_toc }

<details open markdown="block">
  <summary>Table of contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

## About this document

This is a **reference requirements document** for a precious-metals e-commerce and trading
platform, which this repository calls **MetalDesk**. It is written in the voice of a real business
requirements specification because that is the artifact it is modernizing the *pattern* of — but
MetalDesk is not a real commercial product and this is not any specific company's specification.

The requirement categories, flows, and business-rule *shapes* here are representative of a class of
problem (metals e-commerce with live pricing, tiered fulfillment, and multi-provider payments) that
the repository's author worked on in a client-delivery context in 2022. Every specific number in
this document — thresholds, currencies, weights — is an **illustrative example**, chosen to make
the requirement concrete and testable, not a reproduction of any client's actual configuration. See
[the project README](../README.md#provenance) for the full provenance note.

---

## 1. Purpose & vision

MetalDesk lets retail and business customers see live precious-metal reference prices, buy
physical gold, silver and platinum products online, and have them delivered — with a graceful
fallback to a human-mediated quote whenever an order is too large, too heavy, or too far outside
standard shipping lanes for self-service checkout to safely price.

The platform's central tension, and the reason it is an interesting reference system, is that it is
**not quite a normal e-commerce cart**: price is time-sensitive (it tracks a spot-metal reference
price, not a fixed catalog price), the product is dense and valuable enough that shipping cost and
insurance are first-class pricing inputs rather than an afterthought, and a meaningful share of
orders are large enough that a human — not the checkout flow — should set the terms.

## 2. Scope

In scope for the MVP modeled here: the customer-facing buying flow (catalog, cart, checkout,
payment, order history) and the manager-mediated quote flow for orders outside self-service limits.

Out of scope for the MVP: a full admin/back-office application (order fulfillment, inventory
management, CMS for product content) is assumed to exist as a separate application consuming the
same domain model — see [Architecture](architecture.md) — but its own requirements are not detailed
here.

## 3. Glossary

| Term | Meaning |
|---|---|
| Spot price | The live reference price of a metal (per troy ounce or gram), refreshed on a short interval from a market-data source. |
| Margin | The platform's markup applied on top of spot price to derive a sellable unit price. |
| Investment-grade metal | Metal products (typically gold bullion/coins meeting a fineness threshold) that qualify for a VAT exemption under EU investment-gold rules — a category-level tax rule, not a platform-specific discount. |
| Manager-mediated quote | An order path where, instead of self-service checkout, a staff member sets final terms (price, delivery method, payment method) directly with the customer — used for no-price / request-only products and for orders that exceed self-service value or weight limits. |
| Fulfillment tier | A named combination of destination region, maximum order value, and maximum order weight that determines whether an order can be self-service-priced for delivery, or must be handed to a manager. |
| FR / BR / NFR | Functional Requirement / Business Rule / Non-Functional Requirement identifiers, used to cross-reference requirements from design and test artifacts. |

## 4. User classes & roles

| Class | Role | Description |
|---|---|---|
| Guest | — | Unauthenticated visitor. Can browse the catalog, view live prices, and complete a one-off purchase ("buy now") without creating an account. |
| Customer | User | Registered buyer, individual or business, typically 18+. Maintains a cart across sessions, a saved delivery profile, and an order history. |
| Staff | Manager / Admin | Internal user responsible for fulfillment-tier configuration, quote requests, and order/customer communication. Operates the back-office application referenced in §2. |

## 5. Design & implementation constraints

| Constraint | Detail |
|---|---|
| Data protection | The shopping and checkout flow must be GDPR-compliant by design — minimal data collection, explicit consent capture before account creation, a visible privacy notice at the point personal data is first requested. |
| Internationalization | UI and all transactional email/PDF content must support at least two locales end-to-end (this reference uses English/German as the illustrative pair, reflecting an EU-primary customer base). Prices, currency, and generated documents (invoices) render in the user's selected locale. |
| Responsive design | The shopping cart and checkout flow must be usable on mobile viewports, not just desktop — this is a purchase flow, not an informational site, so layout adaptivity is a functional requirement, not a nice-to-have. |
| Browser/OS support | Current-version evergreen browsers (Chrome, Safari) on current-version desktop and mobile OSes. No legacy-browser support requirement. |

## 6. Operating environment

Deployed as a public web application (see [Architecture](architecture.md) for the reference
deployment target), reachable over HTTPS only, with no client-installed software.

---

## 7. Functional requirements

Requirements are grouped by flow and numbered `FR-<flow>.<n>`, preserving the numbering style of
the original specification this pattern is drawn from, so that design and test documents can
cross-reference stably.

### 7.1 Home / market data

| ID | Requirement |
|---|---|
| FR-1.1 | The system displays live reference prices for each supported metal, refreshed on a short polling interval (illustrative: every 20 seconds), with the direction and magnitude of the most recent change visually indicated. |
| FR-1.2 | The system displays the product catalog grouped by category, showing a fixed price for priced products and a "request price" affordance for products without one. |
| FR-1.3 | Selecting a product opens a product detail page with full specification (purity, weight/size, stock status, price, applicable tax treatment). |
| FR-1.4 | The product detail page shows related products from the same category (capped at a fixed count, illustrative: 20). |
| FR-1.5 | Free-text product search returns matching products and opens the selected product's detail page. |
| FR-1.6 | The user can switch color theme; for a signed-in user, the preference persists across sessions. |
| FR-1.7 | The user can switch UI language; all catalog, cart, and transactional content re-renders in the selected language. |
| FR-1.8 | The user can switch display currency; prices, taxes, and totals recompute in the selected currency. |

### 7.2 Authentication

| ID | Requirement |
|---|---|
| FR-2.1 | An unauthenticated user can sign in with an identifier (email or phone) and password. |
| FR-2.2 | On failed credential match, the system shows a generic invalid-credentials message (never revealing which field was wrong) and throttles further attempts — illustrative policy: lock the source IP for a fixed cool-down period after 3 consecutive failures. |
| FR-2.3 | Registration collects the minimum viable profile, validates it, and sends an email verification code before the account is usable for checkout. |
| FR-2.4 | A user can request a password reset via a time-limited link sent to their verified email. |
| FR-2.5 | An authenticated user can switch between accounts they have access to without a full re-login. |
| FR-2.6 | Email verification is a reusable primitive (issue code → bind to user → confirm) used by both registration and quick-registration-at-checkout (§7.4). |
| FR-2.7 | Signing out mid-checkout does not discard the cart; the system offers to resume checkout or return home. |

### 7.3 Cart & checkout entry

| ID | Requirement |
|---|---|
| FR-3.1 | **Add to cart** from the catalog or product page adds one unit (idempotent — repeat clicks on an already-added item do not duplicate the line) and updates the cart badge without leaving the page. |
| FR-3.2 | **Buy now** on a priced product skips the cart entirely and opens checkout step 1 directly for that single unit. |
| FR-3.3 | The cart page lists each line with product identity, quantity (editable, capped at a fixed per-line maximum — illustrative: 10), unit price, line tax, and running total; lines are removable with a confirmation prompt. |
| FR-3.4 | An empty cart shows an explicit empty state rather than a blank page. |
| FR-3.5 | A signed-in user entering checkout has their saved delivery profile pre-filled but editable. |

### 7.4 Checkout — step 1: customer & delivery data

| ID | Requirement |
|---|---|
| FR-4.1 | The system collects name, email, phone, and a structured address (street, city, country, postal code) as mandatory fields, plus optional company name/address and an order note. Every mandatory field is both presence- and format-validated before proceeding. |
| FR-4.2 | A guest may check **"remember me"** to convert the checkout into a lightweight registration: the system prompts for a password, verifies the email belongs to them (reusing FR-2.6), and creates the account without leaving the checkout flow. |
| FR-4.3 | The user must affirmatively accept the privacy policy (a distinct checkbox, not bundled into an unrelated "I agree" toggle) before continuing. |

### 7.5 Delivery tiering — the manager-handoff rule

This is the most business-specific — and most interesting — requirement in the system, because it
decides which orders the software is allowed to price on its own.

| ID | Requirement |
|---|---|
| FR-5.1 | Delivery cost and eligibility are evaluated from three inputs: destination region, order value (ex-tax), and order weight. Staff configure one or more **fulfillment tiers** per region, each with a value ceiling, a weight ceiling, the resulting delivery price, and estimated transit time. |
| FR-5.2 | **If the order is within a tier's value and weight ceilings for its region**, the system prices delivery automatically and the checkout proceeds through payment self-service (§7.6). |
| FR-5.3 | **If the order exceeds the applicable ceiling** (value or weight, whichever binds first) **for its region**, the primary call-to-action changes from "Pay" to a manager-handoff action. The system routes the order and full context to staff, confirms receipt to the customer with a reference number, and does not attempt to collect payment inline — final price and terms are set by a human. |
| FR-5.4 | Insurance cost is a component of the delivery price, not a separate customer-facing line item. |

*Illustrative tier example (not a real configuration):* Region "EU-Core", ceiling €15,000 order
value **or** 25 kg order weight, whichever is reached first, above which the order moves to
manager handoff.

### 7.6 Checkout — step 2: payment method

| ID | Requirement |
|---|---|
| FR-6.1 | The system offers payment by card, by other card-network-adjacent methods (bank debit, bank redirect, bank transfer, saved wallet — grouped here as "gateway-processed" methods), by a separate wallet-payment provider, or by invoice — routed to a single **payment provider abstraction** rather than hard-coding a specific vendor into the checkout flow (see [Architecture §4](architecture.md#4-payments-as-a-provider-abstraction)). |
| FR-6.2 | **Invoice** is a first-class payment method, not a fallback: the system generates a PDF invoice, capped to two documents per order when the cart mixes tax-exempt and taxable product categories (§8, BR-3), and emails it to the customer and a copy to staff, in the customer's selected locale. |
| FR-6.3 | Whichever method is selected, payment failure returns the user to payment selection with cart and delivery data intact — nothing already entered is lost. |

### 7.7 Checkout — steps 3–4: overview & payment

| ID | Requirement |
|---|---|
| FR-7.1 | Before payment is captured, the system shows a final order overview — customer and delivery details, selected payment method, line items, tax, delivery cost, and grand total — with the ability to go back and edit any prior step. |
| FR-7.2 | Payment execution is delegated to the selected provider's flow (redirect, embedded element, or invoice generation per §7.6) and its result (success, decline, or an actionable error) is surfaced back into the same checkout session. |

### 7.8 Checkout — step 5: confirmation

| ID | Requirement |
|---|---|
| FR-8.1 | On successful payment or successful manager handoff, the system generates an order number (§8, BR-6), shows a confirmation page, and notifies both the customer and staff by email. A failed order generates a support-actionable error message rather than a silent failure. |

### 7.9 Manager-mediated inquiries

| ID | Requirement |
|---|---|
| FR-9.1 | For a product with no fixed price, a customer can send a price inquiry from the catalog, the product page, or a delivery-tier handoff (§7.5); staff and customer both receive a confirmation notification. |
| FR-9.2 | A customer can send a free-form message to staff outside the context of a specific order, with the same notification guarantees. |

### 7.10 Order history

| ID | Requirement |
|---|---|
| FR-10.1 | A signed-in customer can view their order history — number, date, item count and total, status, and (once shipped) carrier and tracking reference — and drill into any order for full detail. |

---

## 8. Business rules

Business rules encode pricing and tax logic that must produce identical results regardless of
which part of the system computes them (checkout preview, final invoice, order history) — so they
are specified once here and implemented once in the domain layer (see
[Architecture §3](architecture.md#3-domain-model)), never duplicated per consumer.

**BR-1 — Related products.** Products sharing the selected product's category are eligible as
"similar products"; the list is capped (illustrative: 20 items).

**BR-2 — Price finality.** The price that actually settles an order is the price at the moment
funds are received, not the price shown when the cart was built. The UI must say this plainly at
the point of sale, because for a spot-price-linked product the two can legitimately differ.

**BR-3 — Margin.** Sellable unit price is derived from a live reference price plus a configured
margin: `unit_price = spot_price + spot_price × margin_pct`. Margin is configured per product or
product category, not hard-coded.

**BR-4 — Tax treatment by category.** Investment-grade metal products are zero-rated; all other
products carry the standard rate. This is a category-level flag on the product, driven by
applicable tax law for the platform's home jurisdiction (see the EU investment-gold VAT treatment
referenced in §3) — it is a compliance mapping, not a platform business decision, and must be
reviewed against current tax rules for wherever a real deployment operates, not assumed evergreen.

**BR-5 — Order total.** `order_total = Σ(unit_price × quantity) + tax + delivery_cost`, computed
identically wherever an order total is shown or stored.

**BR-6 — Order numbering.** Order numbers are generated as `<date><daily-sequence>`
(e.g. `080220220004` = day 08, month 02, year 2022, 4th order created that day) — human-readable,
monotonic within a day, and collision-free without a central counter service.

**BR-7 — Insurance.** Insurance cost is folded into delivery cost; it is never a separate
customer-facing line (see FR-5.4).

**BR-8 — Delivery pricing basis.** Delivery cost is looked up from the fulfillment-tier
configuration (§7.5) by destination and **pre-tax** order value and weight — not the tax-inclusive
total, which would create a circular dependency between tax and delivery.

**BR-9 — High-value payment restriction.** Above a configured order-value ceiling, gateway-processed
payment methods (card, wallet, redirect) are disabled; invoice becomes the only payment path. This
is a fraud/chargeback-exposure control, not a technical limitation of the payment providers.

**BR-10 — Manager handoff is a fulfillment-tier outcome, not a separate rule.** It is the direct
consequence of BR-8 combined with the tier ceilings in §7.5 — restated here only because it is easy
to mis-implement as a second, divergent threshold check instead of one shared evaluation.

---

## 9. Non-functional requirements

| Category | Requirement |
|---|---|
| Usability | Checkout is a five-step linear flow with visible step position and full back-navigation; no step may discard already-entered data. |
| Performance | Live price updates must not block catalog or cart interaction — the reference price feed is a background stream, not a request-blocking dependency. |
| Security | All payment data is handled exclusively via the selected provider's own PCI-scoped flow; the platform itself never stores raw card data. Session auth is stateless (token-based), so no server-side session store is a single point of failure. |
| Reliability | A price-feed outage degrades to the last-known price with a visible staleness indicator, rather than blocking checkout entirely. |

---

## 10. Requirements that were still open at MVP freeze

Carried here deliberately, because "what wasn't resolved yet" is as informative as what was, and
because a reference requirements document that pretends everything was settled would be less
useful as a template than one that shows real unresolved edges:

1. Exact VAT display granularity in the cart (percentage vs. absolute, per line vs. once) needs a
   final UX decision.
2. Whether delivery cost is computed on the pre-tax or post-tax order value needs to be stated
   unambiguously in the UI, not just in the backend (BR-8 resolves the computation; the *display*
   of that choice to the customer was still open).
3. Platform operating hours (24/7 vs. a defined support window) were undecided at freeze.
4. Stock/availability tracking for non-price-locked products was not yet specified.
5. Whether pickup (as opposed to delivery) is a supported fulfillment method was undecided.
6. Whether a customer's "class" (retail vs. business) is fixed at registration or can change later
   was undecided — it affects which tax and fulfillment rules apply.
7. UX confusion risk: offering both "sign in" and "register" as adjacent top-level actions, where
   one leads to the current flow and the other to something else entirely, needs a clearer visual
   distinction than label text alone.

---

See also: [Architecture](architecture.md) · [Modernization Plan](modernization-plan.md) ·
[Lessons Learned](lessons-learned.md) · [ADRs](adr/)
