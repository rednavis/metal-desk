# Task board

**48 implementation specifications, `T-001` … `T-078`.** This file is the **ledger** — the single source of truth for
what is actually done.

Every task decomposes one of the open high-level issues in
[rednavis/metal-desk](https://github.com/rednavis/metal-desk/issues). Each specification links back to its parent
issue in its header; the **Parent** column below records the same link, and the **Issue** column links the task's own
child issue.

| Document | Answers |
|---|---|
| **This file** | What is *done*, and what is *claimed* |
| [`../docs/modernization-plan.md`](../docs/modernization-plan.md) | *Why* the work is ordered this way, and each phase's exit criteria |
| [`../docs/architecture.md`](../docs/architecture.md) | The design every task is held to |
| [`../docs/business-requirements.md`](../docs/business-requirements.md) | The FR and BR identifiers the tasks cite |
| [`../docs/lessons-learned.md`](../docs/lessons-learned.md) | Decisions not to relitigate |

---

## 1. How to use this board

**Task ids are identifiers, not a schedule.** `T-039` may legitimately land before `T-031`. What constrains order is
the **Blocked by** column — a task whose blockers are unmerged cannot be implemented, because the types or files it
edits do not exist yet.

1. Find a row whose **Blocked by** is `—` or fully merged, and whose Status is `Not started`.
2. Claim its child issue, and comment on the **parent issue** before starting — every parent issue asks for this.
3. Read the specification **and the documents it cites in §2**. Execute exactly that task.
4. Update your row here **in the same pull request**, and stop.

**If a specification and a document in `docs/` disagree, the document wins.** Report the mismatch in your pull request
rather than implementing either version.

**Verify with a real build before marking a row done.** For backend work that means `./gradlew build` (or
`./gradlew clean build`); for frontend work, `pnpm -r run build` plus the root `typecheck`, `lint` and `test` scripts.
Note that CI does not build the code until Phase 4 (`T-060`…`T-065`) lands, so until then a local build is the *only*
gate — see `CLAUDE.md`.

## 2. Status legend

| Status | Meaning |
|---|---|
| `Not started` | No work on disk. Default |
| `Claimed` | Assigned to a contributor. The Notes column names them and the date |
| `In review` | A pull request is open. Notes link it |
| `Done` | Every acceptance criterion verified by running the specification's §6 commands |
| `Deviated` | Done, but the specification was not followed exactly. Notes **must** state the deviation and why |
| `Blocked` | Cannot proceed. Notes **must** name what decision is needed and from whom |
| `Split` | Scope exceeded the specification. Notes point at the reserved id carrying the remainder |
| `Skipped` | Deliberately not done. Notes **must** name what the project can no longer demonstrate |

## 3. Ledger

**Progress: 2 of 48 done · 0 claimed.** Child issues
[#9](https://github.com/rednavis/metal-desk/issues/9) … [#56](https://github.com/rednavis/metal-desk/issues/56).

> The board and the GitHub issues are two views of the same thing. Issues are the working surface; **this ledger is the
> durable record.** If they disagree, this file is authoritative and the discrepancy is worth reporting.

### M0 — Repo hygiene

Independent of every phase. **Authorable today**, on a clean checkout, with no build tooling installed.

| Task | Issue | Title | Parent | Blocked by | Status | Notes |
|---|---|---|---|---|---|---|
| [T-001](T-001-editorconfig.md) | [#9](https://github.com/rednavis/metal-desk/issues/9) | EditorConfig for the whole tree | [#6](https://github.com/rednavis/metal-desk/issues/6) | — | Not started | `good first issue`. Must agree with Spotless and Prettier rather than add a third opinion |
| [T-002](T-002-dependabot-actions.md) | [#10](https://github.com/rednavis/metal-desk/issues/10) | Dependabot for GitHub Actions | [#7](https://github.com/rednavis/metal-desk/issues/7) | — | Not started | `good first issue`. **Issue #7 is stale**: it names `gitleaks-action`, removed in `5923005` and replaced by TruffleHog |

### M1 — Domain model and libs · Phase 2

Exit criterion: every downstream service module compiles against `libs/*` with **zero duplicated domain types**.
[`T-010`](T-010-share-primitives.md) blocks every other task in this milestone.

| Task | Issue | Title | Parent | Blocked by | Status | Notes |
|---|---|---|---|---|---|---|
| [T-010](T-010-share-primitives.md) | [#11](https://github.com/rednavis/metal-desk/issues/11) | `libs/share`: money, weight and identity primitives | [#2](https://github.com/rednavis/metal-desk/issues/2) | — | Done | **Fan-out: blocks all of M1.** `Region` is a **record wrapping a code**, not an enum: FR-5.1 has staff configure tiers per region, so regions are data and an enum would force a redeploy per country (the legacy `RegionType` EU/DE/OTHER mistake); the code is trimmed and upper-cased so equality is case-insensitive. `EntityId` is a sealed interface (records cannot extend a class; closed set gives exhaustive switches). `DomainException` is sealed with a package-private constructor, so the three kinds stay three. `BigDecimal` is also used in `domain/measure` (`Weight`, `Purity`, `WeightUnit`), so the spec's `grep BigDecimal … \| grep -v money/` check is not empty. No catalog alias added; `libs/share` declares `junit-platform-launcher` (BOM-managed) itself. `config/pmd/pmd-ruleset.xml` relaxed for `CommentSize` lines, `ShortMethodName` (`of`) and two test-assert rules |
| [T-011](T-011-share-customer.md) | [#12](https://github.com/rednavis/metal-desk/issues/12) | `libs/share`: Customer, Address, AuthCredential | [#2](https://github.com/rednavis/metal-desk/issues/2) | T-010 | Done | `AuthCredential` is separate from `Customer` because of FR-2.5 and carries no `CustomerId`. **`AuthIdentifier` is a sealed interface with nested `Email` and `Phone` records** (exhaustive `switch`); one `normalised()` lookup key (lower-cased email, digits-only phone with optional `+`) and `AuthIdentifier.parse(raw)`, which maps every bad input to the single code `auth-identifier.malformed` so FR-2.2 holds. `Address.country` is a `Region`, not a string, and `Address` carries its `AddressKind`; `Customer.addresses` is a list and `primaryAddress(kind)` returns the first of that kind. Credential state is the nested enum `AuthCredential.State` (`ACTIVE`/`DISABLED`, `canSignIn()`), not a new file. `AuthCredential.toString()` redacts the hash. `config/pmd/pmd-ruleset.xml`: `ShortVariable` minimum lowered to 2 for the aggregate `id` idiom |
| [T-012](T-012-share-product-catalog.md) | [#13](https://github.com/rednavis/metal-desk/issues/13) | `libs/share`: Product, Category, tax treatment | [#2](https://github.com/rednavis/metal-desk/issues/2) | T-010 | Done | ∥ with T-011. `price` is `Optional<Money>`; tax lives on the category (BR-4). **`TaxCategory`'s rate reference (T-013 and T-014 depend on this):** each constant carries a `TaxCategory.RateSource`, a nested sealed interface with two records — `Zero` (the `INVESTMENT_GRADE` case: a legal classification, so a domain constant with `rate()` = 0) and `Configured(String reference)` (the `STANDARD` case: reference key `"standard"`, which T-013's pricing layer resolves to a rate per jurisdiction). Consumers `switch` over `rateSource()` exhaustively; `isZeroRated()` is the shortcut. `Product` has no tax field or accessor — the only path is `product.category().taxCategory()`, asserted reflectively. `Category.parent` and `ProductSpecification.dimensions` are `Optional` record components (null is refused, not coerced). `Metal` is the six metals the legacy pricing bridge polled (gold, silver, platinum, palladium, rhodium, ruthenium). Architecture §3 draws "tax category" beside `PriceRule`; BR-4 ("category-level flag") and this task put it on `Category`, so T-013 must not add a second tax field to `PriceRule`. BR-1's cap (20) is documented in `package-info`, not enforced |
| [T-013](T-013-share-pricerule.md) | [#14](https://github.com/rednavis/metal-desk/issues/14) | `libs/share`: PriceRule and sellable-price derivation | [#2](https://github.com/rednavis/metal-desk/issues/2) | T-010, T-012 | Done | BR-3. **The BRD does not state the purity basis — this is a BRD gap; T-039 must implement the same formula.** Basis implemented: `fine weight = grams × (ppt ÷ 1000)`; `metal value = price per gram × fine weight`; `sellable = metal value × (1 + margin ÷ 100)`, all exact `BigDecimal`, rounded once by `Money` at the end (skipping purity is wrong by the fineness ratio). `ReferencePrice`'s price accessor is **`pricePerGram()`**, not the spec's `pricePerCanonicalUnit()` (PMD `LongVariable`; the canonical unit is the gram). It is a `Money`, so it is held to cents — coarse for cheap metals per gram; a feed adapter must not lose precision before it, and finer precision would need a `Money` change. `PriceRule(Margin, Scope)`, `Scope` a sealed `ForProduct`/`ForCategory`, carries **no tax field** (tax is on `Category`, T-012); product-vs-category precedence is not in the BRD and is left to the rule store (T-040). `Margin` is 0–100 %. Standard tax is resolved by a `TaxRate.Resolver` (`forCategory(category, resolver)`); the one-argument `forCategory` uses an illustrative 19 % default for tests and local runs — a deployment must supply its own. `TaxRate.ZERO` is not overridable by the resolver. `TaxAmount` refuses a tax inconsistent with its net and rate; `SellablePrice` refuses a currency mismatch with its reference |
| [T-014](T-014-share-order.md) | [#15](https://github.com/rednavis/metal-desk/issues/15) | `libs/share`: Order, OrderLine, totals, numbering | [#2](https://github.com/rednavis/metal-desk/issues/2) | T-010, T-011, T-012, T-013 | Done | **BR-2 price finality lives here.** `OrderLine` snapshots the whole `SellablePrice`, the resolved `TaxCategory` and the computed `TaxAmount`, and has no `Product` reference (asserted). **`OrderNumber` format (T-030 allocates against it, T-055 displays it): `ddMMyyyy` + a 4-digit zero-padded daily sequence, 12 digits, no separator, e.g. `080220220004`; sequence 1–9999 (a day holds at most 9999 orders).** The task text says `yyyyMMdd`, but BR-6's own example (`080220220004` = day 08, month 02, year 2022) is day-month-year, so the BRD wins; note the number is monotonic within a day only, as BR-6 says. `OrderNumber` has no allocating method. `Quantity` cap is `Quantity.MAX = 10` (FR-3.3, illustrative). `Order.totals()` computes from the line snapshots; before a quote exists the delivery cost is zero, so an unquoted total is goods + tax only. `itemCount()` sums quantities. `Order.created(...)` builds a `CREATED` order with no quote or payment; `Order` has no status-changing method. The delivery address must be `AddressKind.DELIVERY`. **Interim types:** T-014 added the `OrderStatus` enum (8 states, no transitions) and stubbed `DeliveryQuote(cost)` and `PaymentRecord(amount)` at their T-016 paths — T-015 and T-016 complete them. `config/pmd/pmd-ruleset.xml`: `LongVariable` limit raised to 22 for `AWAITING_MANAGER_QUOTE` |
| [T-015](T-015-share-order-status.md) | [#16](https://github.com/rednavis/metal-desk/issues/16) | `libs/share`: the OrderStatus state machine | [#2](https://github.com/rednavis/metal-desk/issues/2) | T-010 | Done | `AWAITING_MANAGER_QUOTE` is a state, not a flag. Table keyed on `(status, trigger)`; 11 triggers, each legal from exactly one status. **The diagram needed interpretation (T-037, T-038, T-040 fire these):** (1) `CANCELLED` has three edges, not one — `PAYMENT_ABANDONED` and `CUSTOMER_CANCELLED` from `AWAITING_PAYMENT`, `QUOTE_DECLINED` from `AWAITING_MANAGER_QUOTE`. (2) **FR-6.3 reading:** `PAYMENT_FAILED` is a *self-transition* (`AWAITING_PAYMENT` → `AWAITING_PAYMENT`) in the table, so a failed attempt returns the unchanged status instead of a conflict and the customer retries; the diagram's "payment failure" cancellation is the separate `PAYMENT_ABANDONED` (retries exhausted / session expired). (3) The task's trigger list has no cause for `CREATED`→`AWAITING_PAYMENT` or `PAID`→`FULFILLING`, so `CHECKOUT_SUBMITTED` and `FULFILLMENT_STARTED` were added. `CUSTOMER_CANCELLED` is only legal from `AWAITING_PAYMENT`; no edge cancels `CREATED`, `PAID` or later (not in the diagram). API: static `transition` / `canTransition` / `availableFrom` / `resolve` (returns the `OrderTransition`, the audit value) / `transitions()`. **`ConflictException` was made `non-sealed`** so `IllegalTransitionException` can extend it from `domain.order` (a sealed subclass must share its package); it is still the one conflict kind and one HTTP class. `OrderStatus.isTerminal()` (`DELIVERED`, `CANCELLED`) |
| [T-016](T-016-share-fulfillment-payment.md) | [#17](https://github.com/rednavis/metal-desk/issues/17) | `libs/share`: FulfillmentTier, DeliveryQuote, PaymentRecord | [#2](https://github.com/rednavis/metal-desk/issues/2) | T-010, T-011, T-013 | Done | Two of Architecture §3's three load-bearing decisions. **Two BRD gaps, both raised in the PR — T-036 implements against them:** (1) **multi-tier rule:** when several tiers in a region qualify, the *cheapest* delivery price wins; ties go to the faster tier (lower max, then min days), then the lower tier id — never list order. (2) **both ceilings exceeded:** `ExceedsCeiling` names the region's *most permissive* tier (highest value ceiling, then weight ceiling, then lower id) and *which* of its ceilings was exceeded; if both, **`VALUE` takes precedence**. **`TierSelector.select` takes a fifth parameter, `quotedAt`** (the caller supplies the instant; the domain has no clock, as in T-013). A region with no tier is a distinct `TierEvaluation.NoTier` outcome, never a zero quote — T-036 should treat it like a handoff. Ceilings are inclusive. `DeliveryQuote(tierId, cost, transit, quotedAt)` replaces T-014's interim one (one `Money`, no insurance; `cost()` kept for `Order.totals()`); `PaymentRecord(providerId, method, status, reference, amount)` replaces the interim one. **`PaymentMethod` names (T-017 and T-037 use them):** gateway `CARD`, `BANK_DEBIT`, `BANK_REDIRECT`, `BANK_TRANSFER`, `SAVED_WALLET`; `WALLET_ACCOUNT` (group `WALLET`); `INVOICE`. `ProviderReference` is printable ASCII, no whitespace, ≤128 chars, so it cannot carry a note or a spaced number; `PaymentRecord` has no map or free-form field, asserted reflectively. `ManagerQuote(finalPrice, terms, quotedAt)`: the BRD does not say whether the final price is the whole order or delivery only — modelled as one agreed figure, split left to T-040 |
| [T-017](T-017-payments-spi.md) | [#18](https://github.com/rednavis/metal-desk/issues/18) | `libs/payments`: the `PaymentProvider` SPI | [#2](https://github.com/rednavis/metal-desk/issues/2) | T-010, T-016 | Not started | **Fan-out: blocks T-018, T-019, T-037.** A decline is an outcome, not an exception |
| [T-018](T-018-payments-gateway-wiremock.md) | [#19](https://github.com/rednavis/metal-desk/issues/19) | `libs/payments`: the gateway provider on WireMock | [#2](https://github.com/rednavis/metal-desk/issues/2) | T-017 | Not started | The **timeout** path is the one that matters. Uses the unused `wiremock-standalone` catalog alias |
| [T-019](T-019-payments-wallet-invoice.md) | [#20](https://github.com/rednavis/metal-desk/issues/20) | `libs/payments`: the wallet and invoice providers | [#2](https://github.com/rednavis/metal-desk/issues/2) | T-012, T-017, T-018 | Not started | `InvoiceProvider` makes **no** outbound call — proven by a stubless WireMock |
| [T-020](T-020-mail-abstraction.md) | [#21](https://github.com/rednavis/metal-desk/issues/21) | `libs/mail`: mail SPI, in-process fake, localized templates | [#2](https://github.com/rednavis/metal-desk/issues/2) | T-010, T-011 | Not started | ∥ with the payments tasks. The fake is what makes `T-041` assertable |
| [T-021](T-021-domain-boundary-rule.md) | [#22](https://github.com/rednavis/metal-desk/issues/22) | The `no domain type outside libs/share` rule + Phase 2 gate | [#2](https://github.com/rednavis/metal-desk/issues/2) | T-010 … T-020 | Not started | **Closes Phase 2.** Removes the deferral comment in `quality-conventions`. A vacuous ArchUnit rule is the default outcome |

### M2 — Services and admin API · Phase 3 (backend half)

[`T-030`](T-030-persistence-baseline.md) blocks everything here. The checkout chain `T-035` → `T-036` → `T-037` →
`T-038` is strictly sequential; `T-039` and `T-040` run in parallel with it.

| Task | Issue | Title | Parent | Blocked by | Status | Notes |
|---|---|---|---|---|---|---|
| [T-030](T-030-persistence-baseline.md) | [#23](https://github.com/rednavis/metal-desk/issues/23) | Reactive MongoDB baseline and the Testcontainers harness | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-021 | Not started | **Fan-out: blocks all of M2.** Decides where persistence lives — `T-040` is blocked on that answer |
| [T-031](T-031-api-catalog.md) | [#24](https://github.com/rednavis/metal-desk/issues/24) | `services/api`: catalog, search, market-data read surface | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-030 | Not started | Settles the error envelope and DTO conventions every later endpoint reuses |
| [T-032](T-032-api-auth.md) | [#25](https://github.com/rednavis/metal-desk/issues/25) | `services/api`: stateless JWT auth and sign-in throttling | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-030, T-031 | Not started | `security`. One failure response, byte-identical. KDF **off** the event loop |
| [T-033](T-033-api-account-lifecycle.md) | [#26](https://github.com/rednavis/metal-desk/issues/26) | `services/api`: registration, verification, reset, switching | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-020, T-032 | Not started | `security`. FR-2.6's primitive must be reusable — `T-035` depends on it |
| [T-034](T-034-api-cart.md) | [#27](https://github.com/rednavis/metal-desk/issues/27) | `services/api`: the cart | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-031, T-032 | Not started | FR-2.7: the cart cannot be keyed on the customer alone. Records the FR-3.1 idempotency reading |
| [T-035](T-035-api-checkout-step1.md) | [#28](https://github.com/rednavis/metal-desk/issues/28) | `services/api`: checkout step 1 | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-033, T-034 | Not started | Must **reuse** `T-033`'s verification, not grow its own |
| [T-036](T-036-api-delivery-tiering.md) | [#29](https://github.com/rednavis/metal-desk/issues/29) | `services/api`: delivery tiering and the manager handoff | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-015, T-016, T-035 | Not started | ★ **The central business rule.** The no-inline-payment gate must be server-side |
| [T-037](T-037-api-checkout-payment.md) | [#30](https://github.com/rednavis/metal-desk/issues/30) | `services/api`: checkout steps 2–4 | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-017, T-018, T-019, T-036 | Not started | `critical-path`. Order created **before** the provider call. No vendor name in the checkout package |
| [T-038](T-038-api-confirmation-history.md) | [#31](https://github.com/rednavis/metal-desk/issues/31) | `services/api`: confirmation, notifications, history, inquiries | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-019, T-020, T-037 | Not started | Must reconcile order-number allocation with `T-036`'s handoff |
| [T-039](T-039-pricing-bridge.md) | [#32](https://github.com/rednavis/metal-desk/issues/32) | `services/pricing-bridge`: fake feed and spot→sellable | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-013, T-030 | Not started | ∥ with the checkout chain. Reuses `T-013` — no second BR-3 formula |
| [T-040](T-040-admin-api.md) | [#33](https://github.com/rednavis/metal-desk/issues/33) | `apps/admin`: tiers, quote handling, order management | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-030, T-036 | Not started | ∥. **The only non-reactive module** — MVC + virtual threads (ADR-0004). No customer JWT |
| [T-041](T-041-phase3-backend-gate.md) | [#34](https://github.com/rednavis/metal-desk/issues/34) | Phase 3 backend gate: end-to-end checkout, both outcomes | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-030 … T-040 | Not started | **Checkpoint.** "Both outcomes" includes the handoff path — the half usually dropped |

### M3 — Frontends · Phase 3 (SPA half)

Same parent issue as M2. [`T-050`](T-050-frontend-foundation.md) blocks everything here — both apps are currently
four-file Vite scaffolds with no router, no test runner and no API client.

| Task | Issue | Title | Parent | Blocked by | Status | Notes |
|---|---|---|---|---|---|---|
| [T-050](T-050-frontend-foundation.md) | [#35](https://github.com/rednavis/metal-desk/issues/35) | Frontend foundation: routing, API client, test baseline | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-031 | Not started | **Fan-out: blocks all of M3.** No per-app ESLint config; no Gradle module. Adds the first frontend test command |
| [T-051](T-051-web-preferences.md) | [#36](https://github.com/rednavis/metal-desk/issues/36) | `apps/web`: language, currency and theme switching | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-033, T-050 | Not started | FR-1.8 needs a conversion rate `T-013` deliberately did not implement — **a real BRD gap** |
| [T-052](T-052-web-home-marketdata.md) | [#37](https://github.com/rednavis/metal-desk/issues/37) | `apps/web`: home and the live market-data panel | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-039, T-050, T-051 | Not started | An absent delta is not zero. Direction must not be colour-only |
| [T-053](T-053-web-catalog-cart.md) | [#38](https://github.com/rednavis/metal-desk/issues/38) | `apps/web`: catalog, product detail, search and cart | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-034, T-050, T-051 | Not started | Render the unpriced case from `pricingMode`, never a price comparison |
| [T-054](T-054-web-checkout.md) | [#39](https://github.com/rednavis/metal-desk/issues/39) | `apps/web`: the five-step checkout wizard | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-035, T-036, T-037, T-038, T-053 | Not started | `critical-path`. On handoff there must be **no** pay affordance in the DOM |
| [T-055](T-055-web-account-history.md) | [#40](https://github.com/rednavis/metal-desk/issues/40) | `apps/web`: auth screens, order history, inquiries | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-032, T-033, T-038, T-053 | Not started | `security`. One sign-in failure message — the UI can undo `T-032` while its tests still pass |
| [T-056](T-056-admin-web.md) | [#41](https://github.com/rednavis/metal-desk/issues/41) | `apps/admin-web`: tiers, quote queue, order management | [#3](https://github.com/rednavis/metal-desk/issues/3) | T-040, T-050 | Not started | **Closes Phase 3 with `T-041`.** No sign-in route — IAP owns staff auth |

### M4 — CI/CD · Phase 4

Exit criterion: a PR touching only `apps/web` triggers no JVM build; a PR touching `libs/share` triggers every
downstream module. Until this milestone lands, **CI does not build the code at all**.

| Task | Issue | Title | Parent | Blocked by | Status | Notes |
|---|---|---|---|---|---|---|
| [T-060](T-060-ci-path-filtering.md) | [#42](https://github.com/rednavis/metal-desk/issues/42) | CI: path-filtered skeleton and the affected-module matrix | [#4](https://github.com/rednavis/metal-desk/issues/4) | T-041, T-056 | Not started | **Fan-out: blocks all of M4.** Extend `secrets-scan`, never replace it; never path-filter it |
| [T-061](T-061-ci-jvm-build.md) | [#43](https://github.com/rednavis/metal-desk/issues/43) | CI: the JVM build job with per-module filtering | [#4](https://github.com/rednavis/metal-desk/issues/4) | T-060 | Not started | First job that actually compiles the code. Decides the empty-matrix behaviour `T-065` needs |
| [T-062](T-062-ci-frontend-build.md) | [#44](https://github.com/rednavis/metal-desk/issues/44) | CI: the frontend build job with per-app filtering | [#4](https://github.com/rednavis/metal-desk/issues/4) | T-050, T-060 | Not started | ∥ with T-061. Every command runs from the repo root — flat-config resolution is root-scoped |
| [T-063](T-063-ci-build-cache.md) | [#45](https://github.com/rednavis/metal-desk/issues/45) | Gradle build cache, local and remote | [#4](https://github.com/rednavis/metal-desk/issues/4) | T-061 | Not started | No `gradle.properties` exists today. **Six timings required** — and never cache `build/` |
| [T-064](T-064-ci-scanning.md) | [#46](https://github.com/rednavis/metal-desk/issues/46) | CI: dependency scanning, and extending the secret scan | [#4](https://github.com/rednavis/metal-desk/issues/4) | T-061, T-062 | Not started | Completes `T-002`'s deferred ecosystems. Repins `trufflehog@main` to a SHA |
| [T-065](T-065-phase4-gate.md) | [#47](https://github.com/rednavis/metal-desk/issues/47) | Phase 4 gate: the two filtering proofs and required checks | [#4](https://github.com/rednavis/metal-desk/issues/4) | T-060 … T-064 | Not started | **Closes Phase 4.** Makes `CLAUDE.md`'s "CI does not build the code" obsolete. Branch protection needs admin rights |

### M5 — GCP infrastructure · Phase 5

> [!WARNING]
> **`T-070` … `T-077` are write-and-validate only and cost nothing.** No `terraform apply` is needed for any of them,
> and they can be done today by anyone with no cloud account. **[`T-078`](T-078-phase5-gate.md) is the first task that
> bills real money** — `api` runs with `min-instances ≥ 1` and two load balancers carry standing hourly charges. Apply
> the budget before anything else.

| Task | Issue | Title | Parent | Blocked by | Status | Notes |
|---|---|---|---|---|---|---|
| [T-070](T-070-tf-root-dev.md) | [#48](https://github.com/rednavis/metal-desk/issues/48) | Terraform root layout, GCS backend, dev environment | [#5](https://github.com/rednavis/metal-desk/issues/5) | T-041, T-056 | Not started | **Authorable now.** Reconcile the `infra/gcp/` path `README.md` already advertises. Documents the state-bucket bootstrap |
| [T-071](T-071-tf-networking.md) | [#49](https://github.com/rednavis/metal-desk/issues/49) | Terraform module: networking and Private Service Connect | [#5](https://github.com/rednavis/metal-desk/issues/5) | T-070 | Not started | **Authorable now.** The Atlas service attachment is a variable — do not fabricate one |
| [T-072](T-072-tf-cloud-run.md) | [#50](https://github.com/rednavis/metal-desk/issues/50) | Terraform module: the reusable Cloud Run service | [#5](https://github.com/rednavis/metal-desk/issues/5) | T-070, T-071 | Not started | **Authorable now.** One module, three postures. Default to private ingress. Secrets by reference only |
| [T-073](T-073-tf-artifact-registry.md) | [#51](https://github.com/rednavis/metal-desk/issues/51) | Terraform module: Artifact Registry and the image build | [#5](https://github.com/rednavis/metal-desk/issues/5) | T-070, T-072 | Not started | **Authorable now.** First Dockerfile in the repo. The build-context filter filename is a real trap |
| [T-074](T-074-tf-web-cdn.md) | [#52](https://github.com/rednavis/metal-desk/issues/52) | Terraform module: the web CDN stack | [#5](https://github.com/rednavis/metal-desk/issues/5) | T-050, T-070, T-071 | Not started | **Authorable now.** Public bucket objects would bypass IAP entirely. **Verify IAP over a backend bucket is supported** |
| [T-075](T-075-tf-secret-manager.md) | [#53](https://github.com/rednavis/metal-desk/issues/53) | Terraform module: Secret Manager and secret injection | [#5](https://github.com/rednavis/metal-desk/issues/5) | T-070, T-072 | Not started | **Authorable now.** `security` — a `secret_data` argument puts the value in state permanently |
| [T-076](T-076-tf-iap.md) | [#54](https://github.com/rednavis/metal-desk/issues/54) | Identity-Aware Proxy for `admin` and `admin-web` | [#5](https://github.com/rednavis/metal-desk/issues/5) | T-072, T-074 | Not started | `security`. **`T-040` and `T-056` have no staff auth until this lands.** Verify the signed assertion, not the header |
| [T-077](T-077-tf-workload-identity.md) | [#55](https://github.com/rednavis/metal-desk/issues/55) | Workload Identity Federation for CI → CD | [#5](https://github.com/rednavis/metal-desk/issues/5) | T-065, T-072, T-073 | Not started | `security`. A loose attribute condition lets any repository deploy — and everything still works |
| [T-078](T-078-phase5-gate.md) | [#56](https://github.com/rednavis/metal-desk/issues/56) | Phase 5 gate: a clean `dev` plan and a served health check | [#5](https://github.com/rednavis/metal-desk/issues/5) | T-070 … T-077 | Not started | **Closes Phase 5. `needs:cloud` — bills money.** Report the criterion with its manual-prerequisite exceptions named |

## 4. Reserved ids

`T-003`…`T-009` · `T-022`…`T-029` · `T-042`…`T-049` · `T-057`…`T-059` · `T-066`…`T-069` · `T-079`…`T-089`

**Split capacity, not spare scope.** When a task exceeds its specification, the remainder takes the next reserved id in
that milestone's gap. Never `T-037b`. They exist so that one split does not require renumbering 48 tasks and
invalidating every cross-reference.

Several tasks are likely split candidates: [`T-037`](T-037-api-checkout-payment.md) (payment selection, overview and
execution are three surfaces), [`T-054`](T-054-web-checkout.md) (five wizard steps),
[`T-040`](T-040-admin-api.md) (tiers, quotes and orders are three features) and
[`T-074`](T-074-tf-web-cdn.md) (two sites, and an unresolved IAP question).

## 5. Checkpoints worth pausing at

- **After [`T-021`](T-021-domain-boundary-rule.md)** — the domain model exists, in one place, with the boundary enforced
  by the build rather than by review. The [shared-library failure](../docs/lessons-learned.md#the-shared-library-nobody-depends-on)
  this whole design reacts to is now structurally prevented. A good moment to re-read Architecture §3 before layering
  services on top.
- **After [`T-041`](T-041-phase3-backend-gate.md)** — the full checkout flow runs end to end against mocked externals,
  including the manager handoff. **This is the first point at which the system does what the BRD describes.** If effort
  has to stop, stop here, not mid-M5.
- **After [`T-056`](T-056-admin-web.md)** — Phase 3 is closed and the platform is demonstrable to a person rather than to
  a test runner.
- **After [`T-065`](T-065-phase4-gate.md)** — the build is a gate rather than a habit. Everything after this is
  deployment.

## 6. Pointers

| Need | File |
|---|---|
| How to contribute, and the AI-assisted workflow | [`../CONTRIBUTING.md`](../CONTRIBUTING.md) |
| Repo-specific build conventions and known traps | [`../CLAUDE.md`](../CLAUDE.md) |
| Phase order and exit criteria | [`../docs/modernization-plan.md`](../docs/modernization-plan.md) |
| The design, and the FR/BR identifiers tasks cite | [`../docs/architecture.md`](../docs/architecture.md) · [`../docs/business-requirements.md`](../docs/business-requirements.md) |
| Decisions not to relitigate | [`../docs/adr/`](../docs/adr/) |
| Why some of these rules exist at all | [`../docs/lessons-learned.md`](../docs/lessons-learned.md) |

**If a specification and a document in `docs/` disagree, the document wins** — open an issue rather than reconciling
them in code.
