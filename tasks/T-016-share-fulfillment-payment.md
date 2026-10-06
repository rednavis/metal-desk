# T-016 — `libs/share`: FulfillmentTier, DeliveryQuote and PaymentRecord

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#17](https://github.com/rednavis/metal-desk/issues/17)

**Milestone:** M1 Domain model and libs · **Estimate:** 2 h

**Preconditions** — `T-010`, `T-011`, `T-013` merged. Needs `Money`, `Weight`, `Region` and
`FulfillmentTierId`.

**Goal** — Model the delivery-tiering data that
[FR-5.1](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) puts under staff
control, the quote it produces, and the `PaymentRecord` that holds a provider reference and never an
instrument.

## 1. Why this task exists

Two of [Architecture §3](../docs/architecture.md#3-domain-model)'s three load-bearing decisions land here:

> **`FulfillmentTier` is data, not code.** Staff configure ceilings and prices; the checkout flow evaluates
> against whatever is currently configured. This is the difference between "a business rule the platform
> enforces" and "a business rule the platform's engineers have to redeploy to change."

> **`PaymentRecord` never stores raw payment instrument data** — only a provider reference (token, charge
> ID, invoice number). This makes the NFR ("the platform itself never stores raw card data") a
> data-model-level guarantee, not just a policy.

The second one is the reason this task is worth reviewing carefully: a data model that *can* hold a card
number will eventually hold one.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| `FulfillmentTier ── Region, value ceiling, weight ceiling, price, ETA` | [Architecture §3](../docs/architecture.md#3-domain-model) |
| Tier is data staff configure; evaluation uses current configuration | [Architecture §3](../docs/architecture.md#3-domain-model) |
| Three evaluation inputs: destination region, order value **ex-tax**, order weight | [FR-5.1](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Within ceilings → automatic pricing; exceeding → manager handoff | [FR-5.2](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule), [FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Insurance is folded into delivery price, never a separate line | [FR-5.4](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule), [BR-7](../docs/business-requirements.md#8-business-rules) |
| Delivery cost comes from the tier configuration | [BR-8](../docs/business-requirements.md#8-business-rules) |
| `PaymentRecord (provider, method, status)` | [Architecture §3](../docs/architecture.md#3-domain-model) |
| Gateway methods restricted above a configured value ceiling | [BR-9](../docs/business-requirements.md#8-business-rules) |

## 3. Deliverables

Under `libs/share/src/main/java/com/rednavis/metaldesk/share/domain/fulfillment/`:

| Path | What |
|---|---|
| `FulfillmentTier.java` | Id, region, value ceiling, weight ceiling, delivery price, ETA |
| `TransitTime.java` | The ETA value type |
| `DeliveryQuote.java` | The tier applied, the resulting cost, the ETA, and the instant quoted |
| `TierEvaluation.java` | The sealed result: `Priced(DeliveryQuote)` or `ExceedsCeiling(which, tier)` |
| `CeilingKind.java` | `VALUE` / `WEIGHT` — which ceiling bound first (FR-5.3) |
| `TierSelector.java` | The pure function: region + ex-tax value + weight + configured tiers → `TierEvaluation` |
| `ManagerQuote.java` | Staff-set final price and terms for the handoff path |

Under `.../domain/payment/`:

| Path | What |
|---|---|
| `PaymentRecord.java` | Provider id, method, status, provider reference, amount |
| `PaymentMethod.java` | Card, bank debit, bank redirect, bank transfer, wallet, invoice (FR-6.1) |
| `PaymentMethodGroup.java` | `GATEWAY` / `WALLET` / `INVOICE` — the Architecture §4 taxonomy |
| `PaymentStatus.java` | Pending, captured, declined, failed, refunded |
| `ProviderReference.java` | The opaque token / charge id / invoice number |
| `package-info.java` (both packages) | Package Javadoc, including the no-instrument-data guarantee |

## 4. Specification

**`TierSelector` is a pure function over *supplied* configuration.** Signature shape:
`TierEvaluation select(Region region, Money exTaxValue, Weight weight, List<FulfillmentTier> configured)`.
It must not load tiers itself — no repository, no static registry. That is what makes "evaluates against
whatever is currently configured" true, and it is what lets `T-040`'s admin API change tiers without a
deploy.

**The ex-tax basis is explicit in FR-5.1** ("order value (ex-tax)"). Name the parameter so it cannot be
misread, and assert it in a test: the same order evaluated on its gross total must be able to produce a
different outcome, and passing the gross total is a caller bug this signature discourages.

**Selection rule when several tiers match.** FR-5.1 allows "one or more fulfillment tiers per region".
The BRD does not state how to pick among them. Choose the cheapest qualifying tier, document the choice in
the Javadoc, and **flag it as a BRD gap in your pull request** — an arbitrary pick here becomes a customer
-visible price difference.

**`ExceedsCeiling` names which ceiling bound.** FR-5.3 says "value or weight, whichever binds first", and
the handoff message to staff needs it. When both are exceeded, document the precedence you chose.

**`DeliveryQuote` carries one cost.** BR-7 folds insurance in, so there is exactly one `Money` and **no**
`insuranceCost` component. State in the Javadoc that adding one would violate BR-7 and FR-5.4.

**`PaymentRecord` is the guarantee, so shape it defensively.** Components: a provider id, a
`PaymentMethod`, a `PaymentStatus`, a `ProviderReference`, and the `Money` amount. `ProviderReference`
wraps a single opaque `String`. The record must have:

- no field of any type named for an instrument — no `cardNumber`, `pan`, `iban`, `cvv`, `expiry`;
- no map, no `Map<String, String> metadata`, no `String rawResponse`, and no free-form `details` field.
  A generic bag is how instrument data arrives in practice, and AC-6 tests for its absence.

`PaymentMethodGroup` mirrors [Architecture §4](../docs/architecture.md#4-payments-as-a-provider-abstraction):
the gateway group covers card, bank debit, bank redirect, bank transfer and saved wallet; `WALLET` is the
separate account-based provider; `INVOICE` generates a document with no live gateway call. Expose
`PaymentMethod.group()`.

**BR-9 is modelled, not enforced here.** BR-9 restricts gateway-processed methods above a configured
order-value ceiling. `PaymentMethod` and `PaymentMethodGroup` give `T-037` what it needs to apply that;
the ceiling itself is configuration. Do not hard-code a threshold.

## 5. Acceptance criteria

1. `./gradlew :libs:share:build` is `BUILD SUCCESSFUL`.
2. `TierSelector` declares no instance field and takes its tier list as a parameter — asserted reflectively.
3. An order inside both ceilings yields `Priced`; one over the value ceiling yields
   `ExceedsCeiling(VALUE, …)`; one over the weight ceiling yields `ExceedsCeiling(WEIGHT, …)`.
4. With two qualifying tiers, the documented selection rule is applied — asserted with a two-tier fixture.
5. A region with no configured tier yields `ExceedsCeiling` or a documented distinct outcome, never a
   silent zero-cost quote.
6. `PaymentRecord` has no component of type `Map`, no component named `metadata`, `details`, `raw` or
   `response`, and no component whose name matches `(?i)card|pan|cvv|iban|expiry` — asserted reflectively.
7. `DeliveryQuote` has exactly one monetary component.
8. `PaymentMethod.group()` returns `GATEWAY` for all five gateway methods, `WALLET` for the wallet
   provider and `INVOICE` for invoice.
9. No Spring artifact on `:libs:share`'s compile classpath.

## 6. Verification

```
cd <repo>
./gradlew :libs:share:build :libs:share:test
grep -rniE 'cardnumber|\bpan\b|cvv|iban|expiry' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/payment/   # expect nothing
grep -rn 'Map<' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/payment/                                   # expect nothing
grep -rni 'insurance' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/fulfillment/   # expect only a BR-7 Javadoc note
grep -rn 'BR-7\|BR-8\|BR-9\|FR-5.3' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/
```

Expected: `BUILD SUCCESSFUL`; no instrument-shaped names; no map in the payment package; insurance
mentioned only as the BR-7 prohibition; the rules cited.

## 7. Out of scope

The `PaymentProvider` SPI and every adapter (`T-017`, `T-018`, `T-019`). Evaluating tiers during checkout
and firing the handoff trigger (`T-036`). The admin API that configures tiers (`T-040`) and its UI
(`T-056`). Persistence (`T-030`). The BR-9 ceiling value.

## 8. Hazards

- **A `Map<String, String>` on `PaymentRecord`** turns the Architecture §3 guarantee into a comment. It
  will be added for a plausible reason — "the provider returns extra fields we might need" — and then
  carries a PAN within a release. AC-6 is the guard; do not weaken it.
- Loading tiers inside `TierSelector` makes the "data, not code" property untrue and the function
  untestable without a database.
- Using the gross total instead of the ex-tax value changes handoff outcomes near the ceiling. FR-5.1 is
  explicit; name the parameter accordingly.
- Adding an `insuranceCost` field satisfies an intuition and breaks BR-7 and FR-5.4 simultaneously.
- Returning the *first* matching tier from an unordered list makes delivery price depend on configuration
  order. Pick deterministically and document it.

## 9. On completion

Mark the T-016 row done in [`README.md`](README.md). Record the multi-tier selection rule and the
both-ceilings-exceeded precedence in its Notes column, and raise both as BRD gaps on issue #2 — `T-036`
implements against them.
