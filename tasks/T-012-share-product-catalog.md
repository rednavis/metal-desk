# T-012 — `libs/share`: Product, Category and tax treatment

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#13](https://github.com/rednavis/metal-desk/issues/13)

**Milestone:** M1 Domain model and libs · **Estimate:** 75 min

**Preconditions** — `T-010` merged (`Money`, `Weight`, `Purity`, `ProductId`, `CategoryId`).

**Goal** — Model `Product ──── Category` from [Architecture §3](../docs/architecture.md#3-domain-model),
including the product-specification fields FR-1.3 shows, the priced/unpriced distinction FR-1.2 depends
on, and the category-driven tax treatment that BR-4 pins.

## 1. Why this task exists

Two things in this model are easy to get wrong and expensive to change later.

**A product may have no price.** [FR-1.2](../docs/business-requirements.md#71-home--market-data) shows
"a fixed price for priced products and a *request price* affordance for products without one", and
[FR-9.1](../docs/business-requirements.md#79-manager-mediated-inquiries) makes the price inquiry a
first-class flow. If `Product.price` is a non-null `Money`, the unpriced case becomes a sentinel value —
zero, or `null` with a boolean beside it — and every caller has to remember the convention.

**Tax is a property of the category, not the product.**
[BR-4](../docs/business-requirements.md#8-business-rules) zero-rates investment-grade metal products and
standard-rates everything else. Copying a tax rate onto each product guarantees drift the first time a
rate changes, and [BR-2](../docs/business-requirements.md#8-business-rules)'s price finality means
historical orders must keep the old treatment anyway — which only works if the order line snapshots it
(`T-014`) rather than reading it live.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| `Product ──── Category` and `PriceRule` hang off `Product` | [Architecture §3](../docs/architecture.md#3-domain-model) |
| Catalog is grouped by category; unpriced products show "request price" | [FR-1.2](../docs/business-requirements.md#71-home--market-data) |
| Detail page shows purity, weight/size, stock status, price, tax treatment | [FR-1.3](../docs/business-requirements.md#71-home--market-data) |
| Related products come from the same category, capped | [FR-1.4](../docs/business-requirements.md#71-home--market-data), [BR-1](../docs/business-requirements.md#8-business-rules) |
| Investment-grade is zero-rated; everything else standard-rated | [BR-4](../docs/business-requirements.md#8-business-rules) |
| Invoice splits into two documents when a cart mixes tax treatments | [FR-6.2](../docs/business-requirements.md#76-checkout--step-2-payment-method), [BR-3](../docs/business-requirements.md#8-business-rules) |

## 3. Deliverables

All under `libs/share/src/main/java/com/rednavis/metaldesk/share/domain/catalog/`:

| Path | What |
|---|---|
| `Product.java` | Aggregate: id, name, specification, category, stock, optional price |
| `ProductSpecification.java` | Purity, weight, dimensions, metal — the FR-1.3 field set |
| `Metal.java` | The supported metals |
| `Category.java` | Id, name, parent, tax category |
| `TaxCategory.java` | `INVESTMENT_GRADE` (zero-rated) / `STANDARD`, each carrying its rate source |
| `StockStatus.java` | In stock / out of stock / on request |
| `PricingMode.java` | `FIXED` / `ON_REQUEST` — see §4 |
| `package-info.java` | Package Javadoc, including the BR-1 related-products cap |

## 4. Specification

**`Product.price` is `Optional<Money>`, and `PricingMode` is derived from it, not stored beside it.**
A product with a price is `FIXED`; one without is `ON_REQUEST`. Expose `pricingMode()` as a computed
accessor so the two can never disagree. Document that `ON_REQUEST` is the FR-9.1 inquiry path, not an
error state.

**`TaxCategory` lives on `Category`, and `Product` reads it through its category.** Do not put a
`TaxCategory` field on `Product`. The Javadoc must state, citing BR-4, that a product's tax treatment is
its category's, and that an `OrderLine` snapshots the resolved treatment at order time (`T-014`) so that
a later category change cannot alter a settled order.

**`TaxCategory` carries a rate reference, not a rate.** A literal percentage in an enum is a redeploy to
change and wrong for any second jurisdiction. Model the zero-rated case as genuinely zero and the
standard case as a reference the pricing layer resolves (`T-013`). State in the Javadoc which of the two
is a domain constant (zero-rating is a legal classification) and which is configuration.

**`ProductSpecification` is a separate record, not fifteen fields on `Product`.** FR-1.3 lists purity,
weight/size and stock as one coherent block; keeping them together is what lets `T-031`'s catalog
projection expose a specification without exposing the aggregate.

**`Category` supports a parent.** FR-1.2 groups the catalog by category and FR-1.4 draws related
products from the same category. A nullable `Optional<CategoryId> parent` is enough — do not build a
tree type, and do not let a category be its own ancestor (validate the direct self-reference only; cycle
detection belongs to whatever persists it).

**BR-1's cap is not enforced here.** BR-1 caps related products at a fixed count (illustrative: 20).
That is a query concern (`T-031`). Record the number in this package's Javadoc so `T-031` does not
invent a different one.

## 5. Acceptance criteria

1. `./gradlew :libs:share:build` is `BUILD SUCCESSFUL`.
2. `Product.price()` returns `Optional<Money>`; there is no `boolean hasPrice` and no zero sentinel.
3. `product.pricingMode()` is `ON_REQUEST` exactly when `price()` is empty — asserted for both cases.
4. `Product` exposes **no** `TaxCategory` field of its own; the only path to a tax category is through
   `category()` — asserted reflectively.
5. `TaxCategory.INVESTMENT_GRADE` resolves to a zero rate, and its Javadoc cites BR-4.
6. `Category` rejects a parent equal to its own id.
7. `ProductSpecification` rejects a null purity, a null weight and a null metal.
8. `Product` rejects a blank name and a null category.
9. No Spring artifact on `:libs:share`'s compile classpath.

## 6. Verification

```
cd <repo>
./gradlew :libs:share:build
grep -rn 'TaxCategory' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/catalog/Product.java   # expect nothing
grep -rn 'Optional<Money>' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/catalog/Product.java
grep -rn 'BR-4\|BR-1' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/catalog/
```

Expected: `BUILD SUCCESSFUL`; no `TaxCategory` in `Product`; an `Optional<Money>` price; BR-4 and BR-1
cited in Javadoc.

## 7. Out of scope

Sellable-price derivation from a reference price and a margin — that is BR-3 and belongs to `T-013`.
Tax *computation* on an order (`T-014`). Search and the related-products query (`T-031`). The inquiry
flow itself (`T-038`). Any Mongo mapping (`T-030`).

## 8. Hazards

- **A `BigDecimal taxRate` field on `Product`** will pass every test here, satisfy FR-1.3, and then make
  BR-2's price finality unimplementable without a migration. The snapshot belongs on the order line.
- Making `price` a nullable `Money` instead of `Optional<Money>` reintroduces the null check this task
  exists to remove, and SpotBugs at `Confidence.LOW` will flag the dereferences one by one.
- Building a full category tree type invites a cycle bug and is not needed by any FR in scope. One
  optional parent id.
- Hard-coding `20` for BR-1 in this module puts the cap in the wrong layer; document it, do not enforce
  it.

## 9. On completion

Mark the T-012 row done in [`README.md`](README.md). Record how `TaxCategory`'s rate reference was
modelled — `T-013` and `T-014` both depend on that decision.
