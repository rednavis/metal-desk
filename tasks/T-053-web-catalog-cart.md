# T-053 — `apps/web`: catalog, product detail, search and cart

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with the
> [business requirements](../docs/business-requirements.md), that document wins** — open an issue rather
> than implementing either version. Update this task's row in [the ledger](README.md) in the same pull
> request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#38](https://github.com/rednavis/metal-desk/issues/38)

**Milestone:** M3 Frontends · **Estimate:** 4 h

**Preconditions** — `T-050`, `T-051` merged. `T-031` (catalog) and `T-034` (cart) merged.

**Goal** — Build the storefront browsing and cart experience:
[BRD §7.1](../docs/business-requirements.md#71-home--market-data) FR-1.2–FR-1.5 and
[§7.3](../docs/business-requirements.md#73-cart--checkout-entry) in full.

## 1. Why this task exists

This is where two server-side decisions become visible, and where they are easiest to undo by accident.

**FR-1.2's unpriced products.** `T-012` made price `Optional<Money>` and `T-031` carries an explicit
`pricingMode`. The client must render the "request price" affordance from `pricingMode`, never from a price
comparison — the moment a component writes `if (price === 0)` the whole chain is wasted.

**FR-3.1's idempotent add.** `T-034` implemented it server-side and recorded whether a repeat add is a no-op or an
increment. The cart badge must reflect what the server actually did, which means rendering from the server
response rather than optimistically incrementing a local count.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Catalog grouped by category; "request price" affordance for unpriced products | [FR-1.2](../docs/business-requirements.md#71-home--market-data) |
| Detail page: purity, weight/size, stock status, price, tax treatment | [FR-1.3](../docs/business-requirements.md#71-home--market-data) |
| Related products from the same category, capped | [FR-1.4](../docs/business-requirements.md#71-home--market-data), [BR-1](../docs/business-requirements.md#8-business-rules) |
| Free-text search opens the selected product's detail page | [FR-1.5](../docs/business-requirements.md#71-home--market-data) |
| Add to cart is idempotent and updates the badge **without leaving the page** | [FR-3.1](../docs/business-requirements.md#73-cart--checkout-entry) |
| Buy now skips the cart, opens checkout step 1 for one unit | [FR-3.2](../docs/business-requirements.md#73-cart--checkout-entry) |
| Cart lines: identity, editable capped quantity, unit price, line tax, running total; removal confirmed | [FR-3.3](../docs/business-requirements.md#73-cart--checkout-entry) |
| Empty cart shows an explicit empty state | [FR-3.4](../docs/business-requirements.md#73-cart--checkout-entry) |
| Cart survives sign-out | [FR-2.7](../docs/business-requirements.md#73-cart--checkout-entry), `T-034` |

## 3. Deliverables

| Path | What |
|---|---|
| `apps/web/src/routes/catalog/CategoryRoute.tsx` | Category-grouped, paginated listing |
| `apps/web/src/routes/catalog/ProductRoute.tsx` | Detail page with the full FR-1.3 specification |
| `apps/web/src/routes/catalog/SearchRoute.tsx` | Search results |
| `apps/web/src/features/catalog/ProductCard.tsx`, `RelatedProducts.tsx`, `PriceOrRequest.tsx` | Listing pieces |
| `apps/web/src/routes/cart/CartRoute.tsx` | The cart page |
| `apps/web/src/features/cart/CartProvider.tsx`, `useCart.ts` | Cart state, sourced from the server |
| `apps/web/src/features/cart/CartBadge.tsx`, `RemoveLineDialog.tsx`, `QuantityField.tsx` | Cart UI |
| Tests | Unpriced rendering, idempotent add, cap, removal confirmation, empty state, buy-now, related cap |

## 4. Specification

**`PriceOrRequest` is the single place the priced/unpriced decision is made**, and it switches on `pricingMode`.
Grep for a price comparison anywhere in the catalog feature and fix it; AC-2 asserts the absence.

**Cart state comes from the server response, always.** After every mutation, render from what the API returned.
Optimistic UI is acceptable **only** if the server response reconciles it, because FR-3.1's semantics live on the
server and a local increment will disagree with a no-op add. FR-3.1's "without leaving the page" means no
navigation and no full reload — the badge updates in place.

**The quantity field enforces the cap client-side and handles the server's rejection.** `T-034` returns 400 above
the cap rather than clamping. Prevent the invalid input where possible **and** render the server error when it
arrives — never silently swallow it, and never clamp locally in a way that hides what the customer asked for.

**Removal is confirmed, per FR-3.3.** A dialog, dismissible by keyboard, focus-trapped, with the destructive action
not focused by default.

**The empty cart is a designed state, not an absence.** `T-034` returns 200 with zero lines; render FR-3.4's
explicit empty state with a route back to the catalog. A blank page fails the requirement.

**Buy-now navigates straight to checkout step 1 and does not touch the cart.** Show it only for priced products —
`T-034` rejects it for unpriced ones, and offering a button that always fails is worse than not offering it.

**Related products display at most the BR-1 cap.** The server enforces it (`T-031`); the client must not display
more than it receives, and must handle fewer gracefully — do not pad the grid.

**Pagination, because `T-031` paginates.** A category listing must page rather than requesting everything.

**Search is honest about its semantics.** `T-031` recorded what search actually does. Do not present it as
relevance-ranked if it is substring matching. Empty results render an empty state, not an error — `T-031` returns
an empty list, not a 404.

**Stock status is shown on both the card and the detail page**, per FR-1.3. An out-of-stock product must not offer
add-to-cart.

## 5. Acceptance criteria

1. `pnpm -r run build`, `pnpm run typecheck`, `pnpm run lint` and `pnpm run test` pass from the repo root.
2. No price comparison decides the unpriced case: `grep` finds no `price === 0`, `price > 0` or `!price` in the
   catalog feature; rendering switches on `pricingMode`.
3. An unpriced product renders the request-price affordance and no price, and offers no add-to-cart or buy-now.
4. Adding the same product twice leaves the badge consistent with `T-034`'s recorded semantics — asserted against a
   mocked API reflecting them.
5. Add-to-cart causes no navigation and no page reload.
6. Entering a quantity above the cap is prevented, and a server 400 is rendered rather than swallowed.
7. Removal shows a confirmation dialog; cancelling leaves the line; the dialog is focus-trapped and keyboard
   dismissible.
8. An empty cart renders the explicit empty state with a route back to the catalog.
9. Buy-now navigates to checkout step 1 and leaves the cart unchanged — asserted.
10. Related products never exceed the BR-1 cap and render correctly with fewer.
11. Search with no matches renders an empty state, not an error.
12. Out-of-stock products offer no add-to-cart.

## 6. Verification

```
cd <repo>
pnpm install --frozen-lockfile && pnpm -r run build && pnpm run typecheck && pnpm run lint && pnpm run test
grep -rnE 'price *(===|==|>|<|!==) *0|!price' apps/web/src/features/catalog apps/web/src/routes/catalog   # expect nothing
grep -rn 'pricingMode' apps/web/src/features/catalog
grep -rnE 'Intl\.|toLocaleString' apps/web/src/features/cart apps/web/src/features/catalog   # expect nothing
```

Expected: all green; no price comparisons; `pricingMode` used; no direct formatting.

## 7. Out of scope

Checkout (`T-054`). Auth screens and order history (`T-055`). The price-inquiry submission for unpriced products —
the affordance links to it; the flow is `T-055`. Wishlists, comparisons, reviews, product imagery pipelines. Stock
reservation.

## 8. Hazards

- **Deciding "unpriced" by comparing the price to zero** re-creates the sentinel `T-012` and `T-031` removed, and a
  genuinely free product would then render as a quote request.
- Optimistically incrementing the badge disagrees with a server-side no-op add, and the mismatch is intermittent.
- Clamping the quantity locally means the customer asks for twelve and the cart shows ten with no explanation.
- A blank page for an empty cart fails FR-3.4, which asks for an explicit state.
- A confirmation dialog that is not focus-trapped is unusable by keyboard and traps screen-reader users behind it.
- Offering buy-now on an unpriced product produces a guaranteed 400.
- Requesting a whole category unpaginated works on fixtures and fails on a real catalog.

## 9. On completion

Mark the T-053 row done in [`README.md`](README.md). Record the add-to-cart badge strategy — optimistic with
reconciliation, or server-response only — since it encodes `T-034`'s FR-3.1 reading in the UI.
