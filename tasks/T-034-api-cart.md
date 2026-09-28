# T-034 — `services/api`: the cart

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#27](https://github.com/rednavis/metal-desk/issues/27)

**Milestone:** M2 Services and admin API · **Estimate:** 3 h

**Preconditions** — `T-031` merged (catalog and price derivation), `T-032` merged (the principal), `T-030`
merged.

**Goal** — Implement [BRD §7.3](../docs/business-requirements.md#73-cart--checkout-entry): an idempotent
add-to-cart, buy-now, an editable capped line list, an explicit empty state, and a cart that survives sign-out.

## 1. Why this task exists

Three requirements here each rule out the obvious implementation.

**[FR-3.1](../docs/business-requirements.md#73-cart--checkout-entry) makes add-to-cart idempotent** — "repeat
clicks on an already-added item do not duplicate the line". A naive `lines.add(...)` satisfies the happy path
and fails the requirement.

**[FR-2.7](../docs/business-requirements.md#73-cart--checkout-entry) makes the cart survive sign-out**, so the
cart cannot be keyed on the authenticated customer alone. It needs an identity that exists before sign-in and
survives after sign-out, plus a merge when an anonymous cart meets an account.

**[FR-3.2](../docs/business-requirements.md#73-cart--checkout-entry)'s buy-now "skips the cart entirely"** — so
checkout cannot assume its input is the persistent cart.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Add to cart is idempotent; updates the badge without leaving the page | [FR-3.1](../docs/business-requirements.md#73-cart--checkout-entry) |
| Buy now skips the cart and opens checkout for one unit | [FR-3.2](../docs/business-requirements.md#73-cart--checkout-entry) |
| Line shows identity, editable quantity capped (illustrative 10), unit price, line tax, running total; removal is confirmed | [FR-3.3](../docs/business-requirements.md#73-cart--checkout-entry) |
| Empty cart shows an explicit empty state | [FR-3.4](../docs/business-requirements.md#73-cart--checkout-entry) |
| Signed-in user's saved delivery profile is pre-filled but editable | [FR-3.5](../docs/business-requirements.md#73-cart--checkout-entry) |
| Signing out mid-checkout does not discard the cart | [FR-2.7](../docs/business-requirements.md#73-cart--checkout-entry) |
| Buy-now is only for priced products | [FR-3.2](../docs/business-requirements.md#73-cart--checkout-entry), [FR-1.2](../docs/business-requirements.md#71-home--market-data) |
| Totals follow BR-5; prices derive through `T-013` | [BR-5](../docs/business-requirements.md#8-business-rules), [BR-3](../docs/business-requirements.md#8-business-rules) |

## 3. Deliverables

Under `services/api/src/main/java/com/rednavis/metaldesk/api/cart/`:

| Path | What |
|---|---|
| `CartController.java` | Add, update quantity, remove, read, clear |
| `CartService.java` | Idempotent add, cap enforcement, merge on sign-in |
| `Cart.java`, `CartLine.java` | The cart model — **not** in `libs/share`; see §4 |
| `CartId.java` | The pre-authentication cart identity |
| `CartResolver.java` | Resolves the acting cart from the request: token, cart cookie, or new |
| `CheckoutBasket.java` | The uniform input to checkout: a cart **or** a buy-now single line |
| `dto/CartView.java`, `dto/CartLineView.java` | Response DTOs including the running total and the empty state |
| Tests | Idempotency, cap, merge, buy-now, empty state, price refresh |

## 4. Specification

**The cart is not a domain aggregate, and it does not belong in `libs/share`.** A cart is a transient,
pre-order artefact; `Order` is the aggregate. `T-021`'s rule 3 forbids duplicating aggregate names, and a
`Cart` in `libs/share` would be a shared type only one service uses. Keep it in `services/api` and say so in
the Javadoc.

**Idempotency is keyed on the product, and the semantics need stating.** `add(productId)` on a cart that
already has a line for that product must not create a second line. FR-3.1 says a repeat click "does not
duplicate the line" — it does **not** say the quantity increments. Read FR-3.1 and FR-3.3 together: FR-3.3
makes quantity separately editable, which suggests add is a no-op when the line exists. **Implement no-op,
document the reading, and flag it as a BRD ambiguity in your pull request** — the alternative (increment)
changes customer-visible behaviour.

**Cart identity survives authentication changes.** `CartResolver` resolves, in order: an explicit cart
reference from the request, the authenticated customer's active cart, or a newly created cart. On sign-in, an
anonymous cart merges into the customer's cart — union by product, quantity capped, and the merge must be
idempotent so a repeated sign-in does not double quantities. On sign-out the cart persists and remains
reachable by its own reference. This is FR-2.7, and it is the part most likely to be missed because the happy
path never exercises it.

**The cap comes from `T-014`'s `Quantity`.** Do not re-declare 10 here. Exceeding it is a
`ValidationException` → 400 through `T-031`'s envelope, not a silent clamp: a silently clamped quantity tells
the customer they bought ten when they asked for twelve.

**Prices are re-derived on every read, never stored on the cart line.** A cart line holds a `ProductId` and a
`Quantity`. The unit price, line tax and running total are computed at read time through `T-013`, exactly as
`T-031` does. [BR-2](../docs/business-requirements.md#8-business-rules)'s finality applies at *order* time —
`T-014`'s `OrderLine` is where the snapshot happens. A cart that snapshots price will show a stale price after
a spot move, which is wrong in both directions.

**`CheckoutBasket` unifies the two entry paths.** FR-3.2's buy-now constructs a single-line basket without
touching the persistent cart; the cart path constructs one from the cart. `T-035` accepts only
`CheckoutBasket`, so it never needs to know which path it came from. Buy-now on an unpriced product is a
`ValidationException` — FR-3.2 applies to priced products only.

**FR-3.4's empty state is a real response, not a 404.** An empty cart returns 200 with an empty line list and
an explicit flag or a zero-item count the client can render from. FR-3.3's removal confirmation is a client
concern (`T-053`); the API just removes.

**FR-3.5 is a read from `T-011`'s `primaryAddress(DELIVERY)`.** Expose it so the checkout form can pre-fill;
the field stays editable, so nothing here may treat it as authoritative.

## 5. Acceptance criteria

1. `./gradlew :services:api:build` is `BUILD SUCCESSFUL`.
2. Adding the same product twice leaves exactly one line, with the documented quantity semantics asserted.
3. Setting a quantity above the `Quantity` cap returns 400 with the envelope's code; the cart is unchanged.
4. Removing a line removes it; removing an absent line is either a no-op or 404 — documented and asserted.
5. Reading an empty cart returns 200 with zero lines and an explicit empty indicator — not 404.
6. A cart created anonymously is still readable after sign-in **and** after sign-out — the FR-2.7 assertion.
7. Merging an anonymous cart into a customer cart unions by product, respects the cap, and is idempotent
   across two merges.
8. Buy-now on a priced product yields a one-line `CheckoutBasket` and leaves the persistent cart untouched;
   buy-now on an unpriced product returns 400.
9. A cart line stores no price: asserted by reading the persisted document and by a test where the reference
   price changes between two cart reads and the returned unit price changes with it.
10. Running total equals BR-5's net + tax for the cart's lines, with no delivery component yet.

## 6. Verification

```
cd <repo>
./gradlew :services:api:build :services:api:test
grep -rn 'Money\|SellablePrice' services/api/src/main/java/com/rednavis/metaldesk/api/cart/CartLine.java   # expect nothing
grep -rn 'class Cart' libs/share/src/main   # expect nothing
grep -rn 'Quantity' services/api/src/main/java/com/rednavis/metaldesk/api/cart/
```

Expected: `BUILD SUCCESSFUL`; no price on the cart line; no `Cart` in `libs/share`; the shared `Quantity` used
rather than a local cap.

## 7. Out of scope

Checkout itself (`T-035` onward). Delivery tiering (`T-036`). Cart expiry or abandoned-cart mail. Stock
reservation — nothing in the BRD asks for it, and adding it here creates a distributed-locking problem out of
scope for this build. The client badge and confirmation prompt (`T-053`).

## 8. Hazards

- **Keying the cart on the authenticated customer only** makes FR-2.7 impossible and is the default shape of
  every cart tutorial. AC-6 is the guard.
- A non-idempotent merge doubles quantities when a user signs in twice, which looks like a phantom bug weeks
  later.
- Snapshotting the price on the cart line looks like a performance win and shows the customer a stale price;
  BR-2 applies to orders, not carts.
- Silently clamping an over-cap quantity produces an order that does not match what the customer asked for.
- Returning 404 for an empty cart forces the client to treat a normal state as an error, and FR-3.4 explicitly
  wants an empty state.
- Putting `Cart` in `libs/share` trips `T-021`'s duplication rule and puts a non-domain type in the domain
  module.

## 9. On completion

Mark the T-034 row done in [`README.md`](README.md). Record the FR-3.1 idempotency reading (no-op versus
increment) and raise it as a BRD ambiguity on issue #3 — `T-053` renders the resulting behaviour.
