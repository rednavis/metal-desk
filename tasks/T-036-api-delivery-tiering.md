# T-036 — `services/api`: delivery tiering and the manager handoff

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#29](https://github.com/rednavis/metal-desk/issues/29)

**Milestone:** M2 Services and admin API · **Estimate:** 3 h

**Preconditions** — `T-035` merged. `T-016` (`TierSelector`) and `T-015` (the state machine) merged.

**Goal** — Wire [BRD §7.5](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) into
checkout: evaluate the configured tiers, price delivery automatically when within ceilings, and route the order
to staff as a first-class `AWAITING_MANAGER_QUOTE` when a ceiling binds.

## 1. Why this task exists

This is the requirement the whole architecture is shaped around.
[BR-10](../docs/business-requirements.md#8-business-rules) states it plainly: the manager handoff is not a
separate rule, it "is the direct" outcome of exceeding a fulfillment tier. And
[FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) is unusually specific
about what must happen:

> the primary call-to-action changes from "Pay" to a manager-handoff action. The system routes the order and
> full context to staff, confirms receipt to the customer with a reference number, and **does not attempt to
> collect payment inline** — final price and terms are set by a human.

Three separate obligations: change the available action, notify both sides, and *refuse to take money*. The last
one is a server-side invariant — a client that offers "Pay" anyway must be rejected, not trusted.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Three evaluation inputs: region, order value **ex-tax**, order weight | [FR-5.1](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Within ceilings → automatic pricing, self-service payment | [FR-5.2](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Exceeding → handoff, reference number, **no inline payment** | [FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Insurance folded into delivery price | [FR-5.4](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule), [BR-7](../docs/business-requirements.md#8-business-rules) |
| Delivery cost from the tier configuration | [BR-8](../docs/business-requirements.md#8-business-rules) |
| Handoff is a fulfillment-tier outcome, not a separate rule | [BR-10](../docs/business-requirements.md#8-business-rules) |
| `AWAITING_MANAGER_QUOTE` is a first-class state | [Architecture §6](../docs/architecture.md#6-order-state-machine) |
| Tiers are staff-configured data evaluated live | [Architecture §3](../docs/architecture.md#3-domain-model) |

## 3. Deliverables

Under `services/api/src/main/java/com/rednavis/metaldesk/api/checkout/delivery/`:

| Path | What |
|---|---|
| `DeliveryEvaluationService.java` | Loads configured tiers, calls `T-016`'s `TierSelector`, records the result on the session |
| `HandoffService.java` | Creates the handoff, allocates the reference, notifies both parties |
| `HandoffReference.java` | The customer-facing reference number from FR-5.3 |
| `CheckoutStage.java` | The stage gate: which actions the session currently permits |
| `dto/DeliveryEvaluationView.java` | The result: quote, or exceeded-ceiling with which ceiling bound |
| Tests | Within-ceiling, over-value, over-weight, no-tier-for-region, and the payment-refusal assertion |

## 4. Specification

**Evaluate against live configuration, every time.** `DeliveryEvaluationService` loads the current tiers for the
destination region from the repository (`T-030`) and passes them to `TierSelector` as a parameter — `T-016` made
the selector pure precisely so this is the only place configuration is read. Do not cache tiers for the
duration of a checkout session without a stated invalidation policy; staff editing a tier mid-checkout is
ordinary.

**Use the ex-tax value.** FR-5.1 is explicit. Compute it from the basket's net (BR-5's `Σ(unit_price × quantity)`
**without** tax), and compute the weight from each line's product specification times its quantity. Both are
derived here and passed in; `TierSelector` does no loading.

**The result drives a stage gate, and the gate is enforced server-side.** Add `CheckoutStage` to the session:
after evaluation it is either `PAYMENT_ALLOWED` or `HANDOFF_REQUIRED`. `T-037`'s payment endpoints must reject
any request on a `HANDOFF_REQUIRED` session with a `ConflictException` → 409. **This is the enforcement of
FR-5.3's "does not attempt to collect payment inline"** — it cannot live in the client, because the client is
not a trust boundary. Test it by calling the payment endpoint directly on a handoff session.

**Handoff fires the state-machine trigger, it does not set a status.** Use `T-015`'s
`OrderStateMachine.transition(..., TIER_EXCEEDED)` to reach `AWAITING_MANAGER_QUOTE`. No direct status
assignment anywhere — `T-015` AC-9 keeps `Order` free of transition methods for this reason.

**The reference number is customer-facing and distinct from the order number.** FR-5.3 promises the customer "a
reference number" at handoff, before any order number exists in the normal flow. Decide whether the handoff
reference *is* the `OrderNumber` (`T-014`/`T-030`) or a separate value, **document it, and justify it** — if the
order is created at handoff then `OrderNumber` is the natural answer and BR-6 applies; if not, this is a second
identifier the customer will quote to support.

**Both parties are notified, through `libs/mail`.** `HANDOFF_RECEIPT_CUSTOMER` and
`HANDOFF_NOTIFICATION_STAFF` from `T-020`, in the customer's locale, and the staff message carries the "full
context" FR-5.3 requires: the lines, the destination, the ex-tax value, the weight, and **which ceiling bound**.
A staff notification that says only "handoff needed" is not full context.

**Which ceiling bound is surfaced to the customer too**, at least as a reason code — FR-5.3 changes the
call-to-action, and the client (`T-054`) needs to explain why.

**No tier configured for the region** is not an exception. `T-016` defined that outcome; map it to
`HANDOFF_REQUIRED` with a distinct reason, because a human must price it. Returning a zero-cost quote would ship
metal for free.

## 5. Acceptance criteria

1. `./gradlew :services:api:build` is `BUILD SUCCESSFUL`.
2. A basket inside both ceilings yields a `DeliveryQuote`, a delivery cost matching the tier's configured price,
   and `CheckoutStage.PAYMENT_ALLOWED`.
3. Exceeding the value ceiling yields `HANDOFF_REQUIRED` with `CeilingKind.VALUE`; exceeding weight yields
   `WEIGHT`.
4. The ex-tax value is used: a basket whose gross total crosses a ceiling but whose net does not stays
   `PAYMENT_ALLOWED` — asserted with a fixture straddling the ceiling.
5. Calling any `T-037` payment endpoint on a `HANDOFF_REQUIRED` session returns 409 and initiates no provider
   call — asserted with a WireMock server that records zero requests.
6. Handoff reaches `AWAITING_MANAGER_QUOTE` via the state machine; no code path assigns an `OrderStatus`
   directly — asserted by grep.
7. Handoff sends exactly one `HANDOFF_RECEIPT_CUSTOMER` and one `HANDOFF_NOTIFICATION_STAFF`; the staff body
   contains the lines, destination, ex-tax value, weight and bound ceiling.
8. The customer response carries the handoff reference and a reason code naming the bound ceiling.
9. A region with no configured tier yields `HANDOFF_REQUIRED` with its own reason, never a zero-cost quote.
10. Editing a tier between two evaluations of the same session changes the second result.

## 6. Verification

```
cd <repo>
./gradlew :services:api:build :services:api:test
grep -rniE 'setStatus|status *= *OrderStatus\.' services/api/src/main   # expect nothing
grep -rn 'TierSelector' services/api/src/main/java/com/rednavis/metaldesk/api/checkout/delivery/
grep -rn 'HANDOFF_REQUIRED' services/api/src/main | sort -u
grep -rniE 'grossTotal|withTax' services/api/src/main/java/com/rednavis/metaldesk/api/checkout/delivery/   # inspect each hit
```

Expected: `BUILD SUCCESSFUL`; no direct status assignment; the shared selector used; the stage gate referenced by
the payment layer; no gross-total basis in the evaluation.

## 7. Out of scope

Payment selection and execution (`T-037`). The staff side of the handoff — setting terms and returning the order
to `AWAITING_PAYMENT` (`T-040`). Tier configuration CRUD (`T-040`). The client's call-to-action switch
(`T-054`). Shipping-carrier integration.

## 8. Hazards

- **Enforcing the no-inline-payment rule only in the client** is the most likely failure and the most serious:
  it lets a crafted request take money for an order a human was supposed to price. AC-5 is the guard.
- Using the gross total instead of the ex-tax value shifts every handoff boundary by the tax rate.
- Assigning `AWAITING_MANAGER_QUOTE` directly bypasses the transition table and will diverge from `T-040`'s
  return path.
- A zero-cost quote for an unconfigured region is a silent free-shipping bug that a test with a configured
  fixture never sees.
- A staff notification without the bound ceiling and the numbers forces staff back into the admin UI to
  reconstruct context, which defeats FR-5.3's "routes … full context".
- Caching tiers for the session duration makes Architecture §3's "evaluates against whatever is currently
  configured" false.

## 9. On completion

Mark the T-036 row done in [`README.md`](README.md). Record whether the handoff reference is the `OrderNumber` or
a separate identifier, and the reason codes you defined — `T-040`, `T-041` and `T-054` all consume them.
