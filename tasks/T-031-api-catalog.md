# T-031 — `services/api`: catalog, search and the market-data read surface

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#24](https://github.com/rednavis/metal-desk/issues/24)

**Milestone:** M2 Services and admin API · **Estimate:** 3 h

**Preconditions** — `T-030` merged. `T-012` and `T-013` merged.

**Goal** — Serve the read side of the storefront: the category-grouped catalog, product detail, related
products, free-text search, and the live reference-price panel — the whole of
[BRD §7.1](../docs/business-requirements.md#71-home--market-data) except the UI.

## 1. Why this task exists

This is the first task that puts an HTTP surface on the domain, so it sets the conventions every later
endpoint follows: the error envelope, the DTO boundary, pagination, and how a reactive handler is written
here. Getting them settled once is the point of doing catalog first — it is the simplest surface with real
queries behind it.

It also decides something FR-1.2 makes unavoidable: the catalog lists both priced and unpriced products, and
the client must be able to tell them apart without inspecting a sentinel. `T-012` made `price` an
`Optional<Money>`; this task has to carry that distinction across the wire.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Live reference prices, short refresh interval, direction and magnitude of last change | [FR-1.1](../docs/business-requirements.md#71-home--market-data) |
| Catalog grouped by category; "request price" for unpriced products | [FR-1.2](../docs/business-requirements.md#71-home--market-data) |
| Detail page: purity, weight/size, stock status, price, tax treatment | [FR-1.3](../docs/business-requirements.md#71-home--market-data) |
| Related products from the same category, capped | [FR-1.4](../docs/business-requirements.md#71-home--market-data), [BR-1](../docs/business-requirements.md#8-business-rules) |
| Free-text search returns matches | [FR-1.5](../docs/business-requirements.md#71-home--market-data) |
| Sellable price is derived, never stored ad hoc | [BR-3](../docs/business-requirements.md#8-business-rules), `T-013` |
| `controller → service → repository`, non-blocking | [Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack) |
| Market data comes from a fake feed in dev/CI | [ADR-0002](../docs/adr/0002-mocked-external-dependencies.md), [Plan Phase 3](../docs/modernization-plan.md) |

## 3. Deliverables

Under `services/api/src/main/java/com/rednavis/metaldesk/api/`:

| Path | What |
|---|---|
| `web/ApiErrorEnvelope.java` | The shared error body — code, message, correlation id |
| `web/DomainExceptionHandler.java` | `@RestControllerAdvice` mapping `T-010`'s three exception kinds to HTTP |
| `catalog/CatalogController.java` | Category listing, product detail, related, search |
| `catalog/CatalogService.java` | Query orchestration and sellable-price derivation |
| `catalog/dto/*.java` | `CategoryView`, `ProductSummaryView`, `ProductDetailView`, `PriceView` |
| `marketdata/MarketDataController.java` | The reference-price panel endpoint |
| `marketdata/ReferencePriceCache.java` | Last-known price per metal, plus previous, for FR-1.1's delta |
| `marketdata/MarketDataClient.java` | Port onto `pricing-bridge`; fake-backed in dev/CI |
| Tests | Controller tests per endpoint; a `CatalogServiceTest`; the BR-1 cap test |

## 4. Specification

**Settle the error envelope here.** `DomainExceptionHandler` maps `ValidationException` → 400,
`NotFoundException` → 404, `ConflictException` → 409, and anything else → 500 with a correlation id and no
stack detail. FR-8.1 requires support-actionable errors, so the envelope always carries the machine-readable
code `T-010` put on the exceptions. Every later M2 task reuses this; do not let a second envelope appear.

**DTOs are separate from domain types, always.** A `ProductDetailView` is not a `Product`. Serialising the
aggregate leaks internal structure and couples the wire format to the domain, and it will expose the whole
`SellablePrice` provenance record to the storefront.

**Carry the priced/unpriced distinction explicitly.** `ProductSummaryView.price` is a nullable `PriceView`
plus an explicit `pricingMode` field (`FIXED` / `ON_REQUEST`) — do **not** emit a zero price for an unpriced
product. The client renders FR-1.2's "request price" affordance from `pricingMode`, never from a price
comparison.

**Sellable price is derived through `T-013`, not recomputed.** `CatalogService` calls
`PriceDerivation.derive(...)` with the current `ReferencePrice` from the cache. No second formula. If a
product's category has no `PriceRule`, it is `ON_REQUEST` — that is the FR-9.1 inquiry path, not an error.

**BR-1's cap is enforced here** — this is the layer `T-012` deferred it to. Related products are drawn from
the same category, exclude the product itself, and are limited to the documented count (illustrative: 20).
Read the value `T-012` recorded in its package Javadoc; do not invent a second number.

**Search is a documented, honest implementation.** FR-1.5 asks only that free-text search returns matching
products. Implement it against the index `T-030` declared, state its semantics in the Javadoc
(case-insensitive substring, or a Mongo text index — say which), and do not claim relevance ranking you have
not implemented.

**FR-1.1's delta needs two observations.** `ReferencePriceCache` keeps the latest and the previous
`ReferencePrice` per metal so the endpoint can report direction and magnitude. On a cold start there is no
previous value: return the price with an explicitly absent delta rather than a zero delta, and document it.

**The market-data source is a port.** `MarketDataClient` is an interface; in dev and CI it is backed by the
fake from `T-039`. This task must not open a connection to any external feed.

**Everything is non-blocking.** `Mono`/`Flux` end to end; no `.block()`. Pagination on the catalog listing —
a category with 10,000 products must not be returned in one response.

## 5. Acceptance criteria

1. `./gradlew :services:api:build` is `BUILD SUCCESSFUL`.
2. Category listing returns products grouped by category, paginated, with `pricingMode` on every entry.
3. An unpriced product returns `pricingMode = ON_REQUEST` and **no** price field — asserted, including that
   the JSON contains no zero amount.
4. Product detail returns purity, weight, stock status, tax treatment and price — every FR-1.3 field.
5. Related products exclude the subject product, share its category, and never exceed the BR-1 cap — asserted
   with a fixture of cap+5 products in one category.
6. Search returns a product by a substring of its name and returns an empty list, not a 404, for no match.
7. The market-data endpoint reports direction and magnitude after two observations, and an explicitly absent
   delta after one.
8. `NotFoundException` for an unknown product id yields a 404 carrying the envelope's code; a malformed id
   yields 400.
9. Sellable prices in responses equal `PriceDerivation.derive(...)` for the same inputs — asserted, not
   eyeballed.
10. No `.block()` in `services/api/src/main`; no domain aggregate appears in any response DTO.

## 6. Verification

```
cd <repo>
./gradlew :services:api:build :services:api:test
grep -rn '\.block()' services/api/src/main   # expect nothing
grep -rn 'Product\b' services/api/src/main/java/com/rednavis/metaldesk/api/catalog/dto/   # expect nothing
grep -rn 'BR-1\|FR-1\.' services/api/src/main/java/com/rednavis/metaldesk/api/catalog/
./gradlew :services:api:bootRun &   # then: curl -s localhost:8082/actuator/health
```

Expected: `BUILD SUCCESSFUL`; no blocking; no domain type in DTOs; the FRs cited; a healthy service.

## 7. Out of scope

Auth on these endpoints (`T-032`) — catalog reads are public per FR-1.x, but the security configuration that
makes that explicit is `T-032`'s. Cart (`T-034`). The inquiry flow for unpriced products (`T-038`). The
`pricing-bridge` feed itself (`T-039`). Theme, language and currency switching (`T-035` server-side,
`T-051` client-side). Any SPA.

## 8. Hazards

- **Emitting a zero price for an unpriced product** makes FR-1.2 unimplementable in the client without a
  convention, and the convention will be wrong somewhere.
- Serialising `Product` directly exposes the `SellablePrice` provenance — the margin the business configured —
  to the public storefront. That is a commercial leak, not just a design smell.
- Recomputing the sellable price inline "because it is two lines" creates the second formula `T-013` exists to
  prevent, and it will drift from `pricing-bridge`.
- Returning a zero delta on a cold start tells the customer the price is unchanged when it is unknown.
- An unpaginated category listing works on a fixture of 12 products and falls over on a realistic catalog.
- Hard-coding a second related-products cap contradicts `T-012`'s recorded value.

## 9. On completion

Mark the T-031 row done in [`README.md`](README.md). Record the search semantics you implemented and the error
envelope's shape — every later M2 task reuses the envelope, and `T-052`/`T-053` consume these DTOs.
