# T-015 — `libs/share`: the OrderStatus state machine

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#16](https://github.com/rednavis/metal-desk/issues/16)

**Milestone:** M1 Domain model and libs · **Estimate:** 90 min

**Preconditions** — `T-010` merged. `T-014` in progress or merged; this task owns the transitions that
`T-014` deliberately left off `Order`.

**Goal** — Transcribe the state machine in
[Architecture §6](../docs/architecture.md#6-order-state-machine) into a type that makes an illegal
transition a compile-or-throw failure, with `AWAITING_MANAGER_QUOTE` as a first-class state.

## 1. Why this task exists

[Architecture §6](../docs/architecture.md#6-order-state-machine) is explicit about why the handoff state
is not a side channel:

> The manager-handoff path (`AWAITING_MANAGER_QUOTE`) is a first-class state, not a side channel — which
> is what lets order history (FR-10.1) show a consistent status for every order regardless of which path
> it took.

If the transition table lives in `services/api`'s checkout service instead of in `libs/share`, then
`apps/admin` (which sets quote terms and moves the order back to `AWAITING_PAYMENT`) has to reimplement
it, and the two drift. That is the
[shared-library failure](../docs/lessons-learned.md#the-shared-library-nobody-depends-on) this phase
exists to prevent, in its most consequential single instance.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| The exact states and edges | [Architecture §6](../docs/architecture.md#6-order-state-machine) |
| `AWAITING_MANAGER_QUOTE` is a state, not a flag | [Architecture §6](../docs/architecture.md#6-order-state-machine), [BR-10](../docs/business-requirements.md#8-business-rules) |
| Handoff is the direct outcome of exceeding a fulfillment tier | [BR-10](../docs/business-requirements.md#8-business-rules), [FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Order history shows a status for every order | [FR-10.1](../docs/business-requirements.md#710-order-history) |
| Payment failure returns the user to payment selection with data intact | [FR-6.3](../docs/business-requirements.md#76-checkout--step-2-payment-method) |

## 3. Deliverables

All under `libs/share/src/main/java/com/rednavis/metaldesk/share/domain/order/`:

| Path | What |
|---|---|
| `OrderStatus.java` | The eight states from Architecture §6 |
| `OrderTransition.java` | A legal edge: from, to, and the trigger that causes it |
| `OrderStateMachine.java` | The transition table and the `canTransition` / `transition` API |
| `TransitionTrigger.java` | The named causes: payment captured, payment failed, tier exceeded, quote set, quote declined, cancelled, shipped, delivered |
| `IllegalTransitionException.java` | Extends `ConflictException` from `T-010` |
| `OrderStatusTest.java`, `OrderStateMachineTest.java` | Under `src/test/java/...` — the exhaustive table test, see §4 |

## 4. Specification

**Transcribe, do not design.** The states and edges are exactly those in
[Architecture §6](../docs/architecture.md#6-order-state-machine):

```
CREATED → AWAITING_PAYMENT → PAID → FULFILLING → SHIPPED → DELIVERED
              │
              ├──▶ AWAITING_MANAGER_QUOTE ──▶ AWAITING_PAYMENT
              │
              └──▶ CANCELLED
```

Eight states: `CREATED`, `AWAITING_PAYMENT`, `AWAITING_MANAGER_QUOTE`, `PAID`, `FULFILLING`, `SHIPPED`,
`DELIVERED`, `CANCELLED`.

**Read the diagram carefully before encoding it.** Both branches leave `AWAITING_PAYMENT`, and `CANCELLED`
is reachable from it for three distinct reasons — "payment failure, customer cancellation, or quote
declined". The quote-declined edge means `AWAITING_MANAGER_QUOTE → CANCELLED` must also exist, even though
the ASCII diagram routes the annotation through `AWAITING_PAYMENT`. **Where the diagram is ambiguous, say
so in your pull request and encode the reading the prose supports** — do not silently pick one.

**`TransitionTrigger` is why the edge fired, and the table is keyed on `(from, trigger)`.** A bare
`(from, to)` table cannot distinguish the three roads into `CANCELLED`, and FR-8.1 needs a
support-actionable reason. The trigger is what `T-037`, `T-038` and `T-040` pass.

**`transition` returns the new status or throws `IllegalTransitionException`.** Signature:
`OrderStatus transition(OrderStatus from, TransitionTrigger trigger)`. Also expose
`boolean canTransition(OrderStatus, TransitionTrigger)` and
`Set<TransitionTrigger> availableFrom(OrderStatus)` — the last one is what drives FR-7.1's "go back and
edit any prior step" and the admin UI's available actions (`T-056`).

**Terminal states are terminal.** `DELIVERED` and `CANCELLED` accept no trigger. `availableFrom` returns an
empty set for both, and `OrderStatus.isTerminal()` says so.

**`FR-6.3` is not a transition.** A failed payment returning the user to payment selection with the cart
intact keeps the order in `AWAITING_PAYMENT` — it is a *failed* transition attempt, not a state change.
Model the payment-failed trigger as one that leaves `AWAITING_PAYMENT` unchanged, or as one that is simply
not in the table from that state; document which reading you chose and why, citing FR-6.3.

**The table test is exhaustive, not sampled.** Iterate every `(OrderStatus, TransitionTrigger)` pair —
8 × the trigger count — and assert each is either a declared legal edge or throws. This is the test that
catches a typo in the table, and it is cheap because the domain is small.

**No Spring, no Lombok, no persistence.** As `T-010`.

## 5. Acceptance criteria

1. `./gradlew :libs:share:build` is `BUILD SUCCESSFUL`.
2. `OrderStatus` declares exactly the eight states named in §4 and no others.
3. `AWAITING_MANAGER_QUOTE` is a member of `OrderStatus` — not a boolean on `Order`, not a subtype.
4. The exhaustive pair test covers every `(status, trigger)` combination, and every pair is either legal or
   throws `IllegalTransitionException`.
5. `DELIVERED.isTerminal()` and `CANCELLED.isTerminal()` are true; `availableFrom` is empty for both.
6. `CANCELLED` is reachable by at least the three triggers FR-5.3/FR-6.3 name, and the triggers are
   distinguishable in the resulting exception or audit value.
7. `AWAITING_MANAGER_QUOTE → AWAITING_PAYMENT` is legal on the quote-set trigger.
8. `IllegalTransitionException` extends `ConflictException`, so `services/api` maps it to the same HTTP
   class as any other state conflict.
9. `Order` (from `T-014`) still exposes no transition method — the machine is the only entry point.
10. No Spring artifact on `:libs:share`'s compile classpath.

## 6. Verification

```
cd <repo>
./gradlew :libs:share:build :libs:share:test
grep -c '  [A-Z_]*,\?$' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/order/OrderStatus.java
grep -rn 'AWAITING_MANAGER_QUOTE' libs/share/src/main/java | wc -l    # expect several: enum, table, tests
grep -rn 'extends ConflictException' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/order/IllegalTransitionException.java
```

Expected: `BUILD SUCCESSFUL`; eight enum constants; the handoff state present in the enum, the table and
the tests; the exception on the `ConflictException` branch.

## 7. Out of scope

Persisting a status history or audit trail (`T-030`). Who is allowed to fire a trigger — authorisation is
`T-032` and `T-040`. Notifications on transition (`T-038`). The tier evaluation that decides whether the
handoff trigger fires at all (`T-016` models the tier, `T-036` evaluates it). Any UI.

## 8. Hazards

- **Keying the table on `(from, to)`** loses the reason for the transition and makes FR-8.1's actionable
  error impossible without a second mechanism.
- Treating the handoff as `AWAITING_PAYMENT` plus a `needsManagerQuote` boolean is the exact design
  Architecture §6 rejects, and it breaks FR-10.1's consistent status.
- The ASCII diagram's `CANCELLED` annotation lists three causes but draws one arrow. Encoding only the
  arrow loses the quote-declined path and strands orders in `AWAITING_MANAGER_QUOTE`.
- A non-exhaustive test suite will pass with a transposed table row. Enumerate all pairs.
- Letting `transition` return `Optional.empty()` instead of throwing turns an illegal transition into a
  silently ignored no-op at every call site.

## 9. On completion

Mark the T-015 row done in [`README.md`](README.md). Record in its Notes column the FR-6.3 reading you
chose and whether the diagram's `CANCELLED` edges needed interpretation — `T-037`, `T-038` and `T-040` all
fire triggers against this table.
