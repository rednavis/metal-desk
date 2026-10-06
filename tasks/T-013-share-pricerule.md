# T-013 — `libs/share`: PriceRule and sellable-price derivation

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#14](https://github.com/rednavis/metal-desk/issues/14)

**Milestone:** M1 Domain model and libs · **Estimate:** 90 min

**Preconditions** — `T-010` and `T-012` merged. `Money`, `Weight`, `Purity`, `TaxCategory` and
`Category` must exist.

**Goal** — Implement `PriceRule` — the margin and tax-category pairing from
[Architecture §3](../docs/architecture.md#3-domain-model) — and the pure function that turns a live
reference price into a sellable unit price, exactly as
[BR-3](../docs/business-requirements.md#8-business-rules) defines it.

## 1. Why this task exists

BR-3 is the only place in the system where a number the business controls (margin) meets a number it does
not (the reference metal price). It is also the calculation `services/pricing-bridge` exists to perform
(`T-039`) and the one `services/api` must never perform differently. Putting the derivation in
`libs/share` as a pure, dependency-free function is what makes "the same price everywhere" a property of
the build rather than of code review.

It is placed before `Order` (`T-014`) on purpose: BR-2's price finality means the order line snapshots
the *output* of this function, so the function has to exist and be trusted first.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| `Product └── PriceRule (margin %, tax category — BR-3, BR-4)` | [Architecture §3](../docs/architecture.md#3-domain-model) |
| Sellable unit price = live reference price + configured margin | [BR-3](../docs/business-requirements.md#8-business-rules) |
| Investment-grade zero-rated, everything else standard-rated | [BR-4](../docs/business-requirements.md#8-business-rules) |
| The price that settles an order is the price at the moment of ordering | [BR-2](../docs/business-requirements.md#8-business-rules) |
| Reference prices refresh on a short interval | [FR-1.1](../docs/business-requirements.md#71-home--market-data) |
| `pricing-bridge` computes spot → sellable | [Modernization Plan, Phase 3](../docs/modernization-plan.md) |

## 3. Deliverables

All under `libs/share/src/main/java/com/rednavis/metaldesk/share/domain/pricing/`:

| Path | What |
|---|---|
| `PriceRule.java` | Margin percentage + the rule's scope (product or category) |
| `Margin.java` | A validated percentage value type |
| `ReferencePrice.java` | Metal, `Money` per canonical weight unit, and the instant it was observed |
| `SellablePrice.java` | The derived unit price, the rule applied, and the reference price it came from |
| `PriceDerivation.java` | The pure function: `(ReferencePrice, ProductSpecification, PriceRule) -> SellablePrice` |
| `TaxRate.java` | Resolved rate for a `TaxCategory` |
| `TaxAmount.java` | Computed tax for an amount at a rate |
| `package-info.java` | Package Javadoc stating the purity/weight basis and the BR-2 snapshot rule |

## 4. Specification

**`PriceDerivation` is a static pure function with no state and no clock.** Signature shape:
`SellablePrice derive(ReferencePrice reference, ProductSpecification spec, PriceRule rule)`. It must be
deterministic — same inputs, same output, always — because `T-039` will call it on every feed tick and
`T-031` on every catalog read, and the two must agree.

**The weight and purity basis is the part the BRD leaves implicit.** A reference price is quoted per unit
weight of pure metal; a product has a gross weight and a purity below 1000 fineness. Therefore:

```
fine weight   = spec.weight().toCanonical() × (spec.purity().fineness() / 1000)
metal value   = reference.pricePerCanonicalUnit() × fine weight
sellable unit = metal value × (1 + rule.margin().asFraction())
```

Implement exactly that, put it in the Javadoc, and **state in your pull request that the BRD does not
spell out the purity basis** — BR-3 says only "a live reference price plus a configured margin". If a
reviewer disagrees with the fine-weight basis, that is a documentation gap to resolve in the BRD, not a
number to tweak here.

**Rounding happens once, at the end, through `Money`.** `Money`'s canonical constructor already normalises
scale (`T-010`); do not round intermediate values. Do not use `double` anywhere in this package.

**`Margin` validates.** A percentage, non-negative, with a documented upper sanity bound. Expose
`asFraction()` returning a `BigDecimal`. Reject a negative margin with `ValidationException` — a negative
margin sells below spot and is a configuration error, not a discount feature.

**`ReferencePrice` carries the observation instant.** FR-1.1 refreshes on a short interval and
[FR-1.1](../docs/business-requirements.md#71-home--market-data) requires showing the direction and
magnitude of the most recent change, so the instant is part of the value, not metadata. Do not put a
`Clock` in this package — the caller supplies the instant.

**`SellablePrice` is a provenance record, not just a number.** It carries the resulting unit `Money`, the
`PriceRule` applied and the `ReferencePrice` used. That is what makes BR-2's snapshot meaningful: `T-014`
stores the whole `SellablePrice` on the order line, so a settled order can answer "why this price".

**`TaxRate` / `TaxAmount`.** `TaxRate.forCategory(TaxCategory)` returns zero for `INVESTMENT_GRADE` per
BR-4. `TaxAmount.of(Money net, TaxRate rate)` computes the tax on a net amount. Keep the standard rate a
configurable value with a documented default rather than a compiled-in constant, consistent with the
decision `T-012` recorded.

## 5. Acceptance criteria

1. `./gradlew :libs:share:build` is `BUILD SUCCESSFUL`.
2. `PriceDerivation` is stateless: it declares no instance fields and no `Clock` — asserted reflectively.
3. Given a 1 troy-ounce, 999.9-fine product, a reference price per gram and a 5% margin, `derive` returns
   the hand-computed value from §4 to `Money`'s scale — a worked example committed as a test.
4. A 0% margin yields exactly the metal value; the same inputs called twice yield `equals` results.
5. `Margin` rejects a negative percentage with `ValidationException`.
6. `TaxRate.forCategory(INVESTMENT_GRADE).isZero()` is true; `STANDARD` is non-zero.
7. `SellablePrice` exposes both the `PriceRule` and the `ReferencePrice` it derived from.
8. `grep -rn 'double\|float' ` over the `pricing` package returns nothing.
9. No Spring artifact on `:libs:share`'s compile classpath.

## 6. Verification

```
cd <repo>
./gradlew :libs:share:build :libs:share:test
grep -rnE '\bdouble\b|\bfloat\b' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/pricing/   # expect nothing
grep -rn 'Clock\|Instant.now' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/pricing/     # expect only ReferencePrice's field type
grep -rn 'BR-3\|BR-4\|BR-2' libs/share/src/main/java/com/rednavis/metaldesk/share/domain/pricing/
```

Expected: `BUILD SUCCESSFUL`; no binary floating point; no `Instant.now()` call inside the package; the
three business rules cited in Javadoc.

## 7. Out of scope

Subscribing to a market-data feed and the fake feed itself (`T-039`). Caching or refresh scheduling.
Currency conversion for FR-1.8 display switching. Order totals (`T-014`) — this task prices one unit.
The admin UI that configures margins (`T-040`, `T-056`).

## 8. Hazards

- **Forgetting purity** makes every price wrong by the fineness factor, and the error is plausible enough
  to survive review. The worked-example test in AC-3 is the guard.
- **Rounding per intermediate step** produces totals that disagree with BR-5's formula by cents, which
  surfaces as a failing end-to-end assertion in `T-041` and is painful to trace back here.
- Putting `Instant.now()` inside `PriceDerivation` makes it untestable and non-deterministic. The instant
  belongs to `ReferencePrice`, supplied by the caller.
- Compiling the standard tax rate in as a literal contradicts the `T-012` decision and forces a redeploy
  to change a tax rate. Keep it resolvable.
- A negative margin silently sells below spot. Reject it.

## 9. On completion

Mark the T-013 row done in [`README.md`](README.md). Record the fine-weight basis in its Notes column and
flag it as a BRD gap on issue #2, since `T-039` implements the same formula on the feed side and must not
diverge.
