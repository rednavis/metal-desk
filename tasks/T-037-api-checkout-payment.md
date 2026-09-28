# T-037 — `services/api`: checkout steps 2–4 — payment selection, overview and execution

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#30](https://github.com/rednavis/metal-desk/issues/30)

**Milestone:** M2 Services and admin API · **Estimate:** 4 h

**Preconditions** — `T-036` merged (the stage gate). `T-017`, `T-018` and `T-019` merged (the SPI and all three
adapters).

**Goal** — Implement [BRD §7.6](../docs/business-requirements.md#76-checkout--step-2-payment-method) and
[§7.7](../docs/business-requirements.md#77-checkout--steps-34-overview--payment): offer the permitted payment
methods, show a final overview, execute payment through the `PaymentProvider` abstraction, and handle failure
without losing anything.

## 1. Why this task exists

This task is where the payment abstraction earns its keep or leaks. Three requirements constrain it:

[FR-6.1](../docs/business-requirements.md#76-checkout--step-2-payment-method) routes every method through "a
single payment provider abstraction **rather than hard-coding a specific vendor into the checkout flow**".
[BR-9](../docs/business-requirements.md#8-business-rules) restricts gateway methods above a configured order
value, which means the *offered* method list is computed, not static. And
[FR-6.3](../docs/business-requirements.md#76-checkout--step-2-payment-method) requires that a payment failure
returns the user to selection "with cart and delivery data intact — nothing already entered is lost", which is a
statement about server-side state, not about the client.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| All methods route through one abstraction; no vendor in checkout code | [FR-6.1](../docs/business-requirements.md#76-checkout--step-2-payment-method), [Architecture §4](../docs/architecture.md#4-payments-as-a-provider-abstraction) |
| Invoice is first-class, two documents max on mixed tax treatment | [FR-6.2](../docs/business-requirements.md#76-checkout--step-2-payment-method), [BR-3](../docs/business-requirements.md#8-business-rules) |
| Failure returns to selection; nothing entered is lost | [FR-6.3](../docs/business-requirements.md#76-checkout--step-2-payment-method) |
| Final overview shows every component, with back-and-edit to any prior step | [FR-7.1](../docs/business-requirements.md#77-checkout--steps-34-overview--payment) |
| Execution delegated to the provider flow; result surfaced into the same session | [FR-7.2](../docs/business-requirements.md#77-checkout--steps-34-overview--payment) |
| Gateway methods restricted above a configured value ceiling | [BR-9](../docs/business-requirements.md#8-business-rules) |
| `order_total = Σ(unit_price × quantity) + tax + delivery_cost` | [BR-5](../docs/business-requirements.md#8-business-rules) |
| Price finality at order time | [BR-2](../docs/business-requirements.md#8-business-rules) |
| No inline payment on a handoff session | [FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule), `T-036` |

## 3. Deliverables

Under `services/api/src/main/java/com/rednavis/metaldesk/api/checkout/payment/`:

| Path | What |
|---|---|
| `PaymentMethodOfferService.java` | Computes the permitted methods for this session, applying BR-9 |
| `PaymentMethodPolicy.java` | The BR-9 ceiling, externally configured |
| `ProviderRegistry.java` | `PaymentMethod` → `PaymentProvider`, resolved by capability, not by `switch` |
| `CheckoutOverviewService.java` | The FR-7.1 overview, computed from the session |
| `PaymentExecutionService.java` | Order creation, provider call, outcome handling |
| `PaymentCallbackController.java` | The out-of-band confirm endpoint for redirect and element flows |
| `dto/PaymentMethodsView.java`, `dto/OverviewView.java`, `dto/PaymentResultView.java` | Wire types |
| Tests | Method offering, BR-9 restriction, all six outcomes, FR-6.3 state retention, the handoff refusal |

## 4. Specification

**`ProviderRegistry` resolves by capability, never by a `switch` on method.** Inject all `PaymentProvider`
beans and select the one whose `supports(method)` is true. A `switch` naming `GatewayProvider` in the checkout
package is exactly the vendor coupling FR-6.1 forbids — a grep for adapter class names in this package must come
back empty. Ambiguity (two providers claiming one method) is a startup failure, not a runtime coin flip.

**The offered method list is computed per session.** Start from every method any registered provider supports,
then apply BR-9: above the configured order-value ceiling, gateway-group methods are removed and invoice (and
wallet, if the policy allows) remain. The ceiling is configuration with a documented default. Invoice must never
be filtered out by BR-9 — it is the high-value path, and FR-6.2 makes it first-class.

**Order creation happens before the provider call, and this ordering is the crux.** Create the `Order` with
`T-014`'s snapshotted lines — this is the moment BR-2's price finality takes effect — allocate the `OrderNumber`
via `T-030`'s sequence, transition to `AWAITING_PAYMENT`, and only then call the provider. If the provider
outcome never arrives, an order exists in `AWAITING_PAYMENT` that can be reconciled. The reverse order loses the
customer's intent on every failure.

**Map every `PaymentOutcome` variant, and let the compiler prove it.** `T-017` sealed the type for this:

| Outcome | Behaviour |
|---|---|
| `Captured` | Record the `PaymentRecord`, fire the payment-captured trigger → `PAID`, hand off to `T-038` |
| `RedirectRequired` | Return the URI; session stays `AWAITING_PAYMENT` awaiting the callback |
| `ElementRequired` | Return the client secret; same |
| `DocumentIssued` | Record a pending invoice `PaymentRecord`; the order stays `AWAITING_PAYMENT`; hand the document to `T-038` to mail |
| `Declined` | Return to method selection with the `DeclineReason`; **session state fully retained** |
| `Failed` / `PaymentProviderException` | Same retention, but an actionable error and a logged incident |

Use an exhaustive switch over the sealed type with **no default branch**, so adding a variant is a compile error.

**FR-6.3 is a retention assertion, and it is the test that matters most.** After a decline, the session must
still hold the basket, the step-1 customer details, the delivery quote and the previously selected method. Assert
every one of them individually — and assert the order is **not** cancelled: a declined payment leaves the order in
`AWAITING_PAYMENT` per `T-015`'s FR-6.3 reading, so the customer can retry.

**The overview is computed, never stored.** FR-7.1's totals come from `T-014`'s `OrderTotalsCalculator` over the
session's snapshotted lines plus the `DeliveryQuote` cost. It must reconcile exactly with what the provider is
asked to charge — assert equality between the overview's grand total and the `PaymentIntent` amount, because a
mismatch here is the worst class of bug in the system.

**Back-and-edit invalidates forward state.** FR-7.1 allows editing any prior step. Editing step 1 or the basket
must clear the delivery evaluation and force `T-036` to re-run, since a changed address or basket changes the
tier outcome. Wire that invalidation explicitly.

**The callback endpoint is unauthenticated but verified.** Redirect and element flows return out of band.
`PaymentCallbackController` must not trust its input: resolve the `ProviderReference`, call the provider's
`confirm`, and derive the outcome from the provider's answer — never from a query parameter. A callback claiming
success is not evidence of success.

**Refuse payment on a handoff session.** Re-assert `T-036`'s gate on every endpoint here.

## 5. Acceptance criteria

1. `./gradlew :services:api:build` is `BUILD SUCCESSFUL`.
2. No adapter class name (`GatewayProvider`, `WalletProvider`, `InvoiceProvider`) appears anywhere in the
   `checkout` package — asserted by grep.
3. Below the BR-9 ceiling all supported methods are offered; above it gateway methods are absent and invoice is
   present.
4. Each of the six `PaymentOutcome` variants is handled, and the switch has no default branch — asserted by
   inspection and by a test per variant.
5. A declined payment leaves the basket, step-1 details, delivery quote and selected method intact, and the
   order in `AWAITING_PAYMENT` — five separate assertions.
6. A timeout (`PaymentProviderException`) is not reported to the client as a decline, and produces the same
   retention.
7. `Captured` records a `PaymentRecord` with the provider reference and transitions to `PAID` via the state
   machine.
8. `DocumentIssued` leaves the order in `AWAITING_PAYMENT` with a pending invoice record.
9. The overview's grand total equals the `PaymentIntent` amount for the same session — asserted.
10. Editing step 1 after a delivery evaluation clears it and requires re-evaluation before payment is allowed.
11. A callback carrying a success parameter but whose provider `confirm` says otherwise does **not** mark the
    order paid.
12. Every payment endpoint returns 409 on a `HANDOFF_REQUIRED` session, with zero provider requests recorded.

## 6. Verification

```
cd <repo>
./gradlew :services:api:build :services:api:test
grep -rnE 'GatewayProvider|WalletProvider|InvoiceProvider' services/api/src/main/java/com/rednavis/metaldesk/api/checkout/   # expect nothing
grep -rn 'default ->' services/api/src/main/java/com/rednavis/metaldesk/api/checkout/payment/PaymentExecutionService.java   # expect nothing
grep -rniE 'setStatus|status *= *OrderStatus\.' services/api/src/main   # expect nothing
```

Expected: `BUILD SUCCESSFUL`; no adapter names in checkout; no default branch on the outcome switch; no direct
status assignment.

## 7. Out of scope

Confirmation, notifications and order history (`T-038`). Refunds and cancellations after capture. Webhook
signature verification against a real vendor — the `confirm` round trip is the mechanism here. The checkout UI
(`T-054`). 3-D Secure step-up beyond what `ElementRequired`/`RedirectRequired` already model.

## 8. Hazards

- **Calling the provider before creating the order** loses the order on every failure and makes reconciliation
  impossible. The ordering in §4 is deliberate.
- A `switch` on `PaymentMethod` naming adapter classes recreates the vendor coupling the whole abstraction
  exists to remove, and it will look harmless.
- A `default ->` branch on the sealed outcome silently swallows a variant added later — `T-017` sealed the type
  specifically to prevent this.
- Trusting a callback query parameter lets anyone mark an order paid. This is the highest-severity mistake
  available in this task.
- Cancelling the order on a decline contradicts FR-6.3 and `T-015`, and destroys the retry path.
- A drift between the overview total and the charged amount is invisible in tests that compute both from the
  same helper — AC-9 must compare the two real values.
- Filtering invoice out by BR-9 removes the only permitted method for a high-value order.

## 9. On completion

Mark the T-037 row done in [`README.md`](README.md). Record the BR-9 ceiling default, whether wallet is allowed
above it, and the callback verification approach — `T-041` asserts the full flow and `T-054` drives it.
