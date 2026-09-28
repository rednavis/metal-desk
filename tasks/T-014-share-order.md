# T-014 — `libs/share`: Order, OrderLine, totals and order numbering

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#15](https://github.com/rednavis/metal-desk/issues/15)

**Milestone:** M1 Domain model and libs · **Estimate:** 2 h

**Preconditions** — `T-010`, `T-011`, `T-012` and `T-013` merged. This task consumes all four.

**Goal** — Model `Order ──┬── OrderLine` from
[Architecture §3](../docs/architecture.md#3-domain-model): the price-snapshotting order line that makes
[BR-2](../docs/business-requirements.md#8-business-rules) true at the data-model level, the total
formula from [BR-5](../docs/business-requirements.md#8-business-rules), and the order number format from
[BR-6](../docs/business-requirements.md#8-business-rules).

## 1. Why this task exists

[Architecture §3](../docs/architecture.md#3-domain-model) names this the first of "three modeling
decisions that carry the rest of the design":

> **`OrderLine` snapshots price and tax category at order time.** A later change to a product's margin or
> tax rule must never retroactively change a historical order's total.

Every other way of modelling this — a line that holds a `ProductId` and resolves the price on read, or a
line that holds a price but resolves tax live — produces an order history (FR-10.1) whose totals change
under the customer's feet. The snapshot is the requirement, not an optimisation.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| `Order ──┬── OrderLine (product, qty, unit price at time of order)` | [Architecture §3](../docs/architecture.md#3-domain-model) |
| Price finality: the settling price is the price at the moment of ordering | [BR-2](../docs/business-requirements.md#8-business-rules) |
| `order_total = Σ(unit_price × quantity) + tax + delivery_cost` | [BR-5](../docs/business-requirements.md#8-business-rules) |
| Order number is `<date><daily-sequence>` | [BR-6](../docs/business-requirements.md#8-business-rules) |
| Per-line quantity cap (illustrative: 10) | [FR-3.3](../docs/business-requirements.md#73-cart--checkout-entry) |
| Delivery cost is a single component, insurance folded in | [BR-7](../docs/business-requirements.md#8-business-rules), [FR-5.4](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Order history shows number, date, item count, total, status | [FR-10.1](../docs/business-requirements.md#710-order-history) |

## 3. Deliverables

All under `libs/share/src/main/java/com/rednavis/metaldesk/share/domain/order/`:

| Path | What |
|---|---|
| `Order.java` | Aggregate root: id, number, customer, lines, quote, payment, status, timestamps |
| `OrderLine.java` | Product reference **plus the snapshot**: unit price, tax category, tax amount, quantity |
| `Quantity.java` | Validated positive integer with the FR-3.3 cap |
| `OrderNumber.java` | The BR-6 `<date><daily-sequence>` value type, with parsing and formatting |
| `OrderTotals.java` | The BR-5 computation result: net, tax, delivery, grand total |
| `OrderTotalsCalculator.java` | The pure function implementing BR-5 |
| `package-info.java` | Package Javadoc: the snapshot rule, quoting Architecture §3 |

## 4. Specification

**`OrderLine` is a snapshot, and nothing on it is resolved on read.** Components: `ProductId`, a
denormalised product name for display, `Quantity`, the `SellablePrice` from `T-013` (which itself carries
the rule and reference price), the resolved `TaxCategory`, and the computed `TaxAmount` for the line.
`OrderLine` has **no** reference to `Product` and no method that takes one — asserted in §5. Give it
`lineNet()` (`unitPrice × quantity`) and `lineTax()`.

Snapshotting the whole `SellablePrice` rather than a bare `Money` is deliberate: it is what lets a support
query answer "which margin and which spot price produced this line" years later, and it costs nothing now.

**`OrderTotalsCalculator` implements BR-5 literally.** `OrderTotals compute(List<OrderLine> lines,
Money deliveryCost)`. Net is `Σ lineNet()`; tax is `Σ lineTax()`; grand total is `net + tax + delivery`.
All three sums go through `Money.plus`, so a mixed-currency order throws rather than producing a
plausible wrong number. Delivery cost is one `Money` — BR-7 folds insurance in, so there is no insurance
component and no place to add one.

**`OrderNumber` is `<date><daily-sequence>`.** Fix the concrete format — the BRD gives the shape, not the
widths — and document it: date as `yyyyMMdd`, sequence zero-padded to a stated width, concatenated with
no separator unless you justify one. Provide `format()` and `parse(String)`, and make `parse` reject
anything that does not round-trip. **`OrderNumber` does not allocate sequences.** A daily sequence needs a
transactional counter, which is persistence (`T-030`) — this type only represents and validates. Say so in
the Javadoc.

**`Quantity` carries the FR-3.3 cap** as a documented constant with the FR cited. Reject zero, negative
and above-cap with `ValidationException`.

**`Order` composes, it does not orchestrate.** Components: `OrderId`, `OrderNumber`, `CustomerId`, the
delivery `Address`, an immutable `List<OrderLine>`, an `Optional<DeliveryQuote>` (`T-016`), an
`Optional<PaymentRecord>` (`T-016`), an `OrderStatus` (`T-015`), and created/updated instants. Both
optionals are genuinely absent early: a freshly `CREATED` order has neither a quote nor a payment.

Expose `totals()` delegating to `OrderTotalsCalculator`, and `itemCount()` for FR-10.1. Do **not** put
state-transition methods on `Order` in this task — `OrderStatus` and its legal transitions are `T-015`,
and a `markPaid()` here would duplicate that machine.

**An order with zero lines is invalid.** Reject an empty line list in the canonical constructor.

## 5. Acceptance criteria

1. `./gradlew :libs:share:build` is `BUILD SUCCESSFUL`.
2. `OrderLine` declares no field or method whose type is `Product` — asserted reflectively.
3. Changing a `Product`'s price or its category's `TaxCategory` after an `OrderLine` is built does not
   change `lineNet()`, `lineTax()` or the order's totals — asserted by a test that mutates the source data
   and re-reads the order.
4. `OrderTotalsCalculator` reproduces BR-5 on a worked two-line example with delivery, committed as a test.
5. A line list mixing two currencies throws `ValidationException` rather than returning a total.
6. `Quantity` rejects `0`, `-1` and cap+1; accepts `1` and the cap.
7. `OrderNumber.parse(n.format())` equals `n` for a generated sample set, and `parse` rejects a malformed
   string. `OrderNumber` exposes no sequence-allocating method.
8. `Order` rejects an empty line list; a `CREATED` order returns `Optional.empty()` for both quote and
   payment.
9. `Order` exposes no status-transition method.
10. No Spring artifact on `:libs:share`'s compile classpath.

## 6. Verification

```
cd <repo>
./gradlew :libs:share:build :libs:share:test
grep -rn 'Product ' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/order/OrderLine.java   # expect nothing
grep -rnE 'markPaid|transitionTo|setStatus' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/order/   # expect nothing
grep -rn 'BR-2\|BR-5\|BR-6\|FR-3.3' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/order/
```

Expected: `BUILD SUCCESSFUL`; no `Product` in `OrderLine`; no transition methods; the business rules cited.

## 7. Out of scope

`OrderStatus` and its transition table (`T-015`). `DeliveryQuote` and `PaymentRecord` (`T-016`) — this task
references them as optionals, so land `T-016` first or stub the two types and note it. Daily-sequence
allocation (`T-030`). Cart-to-order conversion (`T-036`). Invoice document generation (`T-019`).

## 8. Hazards

- **A `Product` field on `OrderLine`** is the single change that breaks BR-2, and it will look like a
  convenience while every test still passes. AC-3's mutation test is the only thing that catches it.
- Summing with `BigDecimal` directly instead of `Money.plus` silently permits mixed currencies.
- Implementing daily-sequence allocation here produces a counter that is correct in a unit test and races
  in production. It is persistence.
- Adding `markPaid()` / `cancel()` to `Order` splits the state machine across two tasks; `T-015` then has
  to either duplicate or unpick it.
- `OrderTotals` must not recompute lazily from live data on each call. Compute from the snapshot.

## 9. On completion

Mark the T-014 row done in [`README.md`](README.md). Record the chosen `OrderNumber` format and sequence
width in its Notes column — `T-030` allocates against it and `T-055` displays it.
