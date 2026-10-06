# T-010 — `libs/share`: money, weight and identity primitives

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#11](https://github.com/rednavis/metal-desk/issues/11)

**Milestone:** M1 Domain model and libs · **Estimate:** 90 min

**Preconditions** — Phase 1 is merged (`cb30935`). `libs/share` exists with `ShareModule.java` and
`src/main/java/com/rednavis/metaldesk/share/domain/package-info.java`, and nothing else.

**This task blocks every other M1 task.** Nothing in `libs/share`, `libs/payments` or `libs/mail` can
be typed without the value types it defines.

**Goal** — Define the value types every aggregate is built from — money, weight, purity, region, and
the typed identifiers — plus the shared exception taxonomy, so that no later task invents a second
`Money` or passes a bare `String` where an identifier belongs.

## 1. Why this task exists

[Lessons Learned](../docs/lessons-learned.md#the-shared-library-nobody-depends-on) names the failure
this whole phase exists to prevent: a shared library that nobody depends on, because each service found
it easier to declare its own copy of a type. The cheapest guard is to make the shared copy exist
*first* and make it obviously complete — a service author who needs a monetary amount on day one finds
one rather than writing `BigDecimal`.

Money in particular cannot be retrofitted. [BR-5](../docs/business-requirements.md#8-business-rules)
defines `order_total` as a sum of products of amounts, and
[FR-1.8](../docs/business-requirements.md#71-home--market-data) lets a customer switch display
currency. An amount without a currency attached is a defect waiting for the first multi-currency order.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Domain model lives in one library; every service depends on it | [Architecture §3](../docs/architecture.md#3-domain-model) and [§8](../docs/architecture.md#8-build-graph) |
| Totals are computed, currency is switchable | [BR-5](../docs/business-requirements.md#8-business-rules), [FR-1.8](../docs/business-requirements.md#71-home--market-data) |
| Purity, weight/size and stock are product specification fields | [FR-1.3](../docs/business-requirements.md#71-home--market-data) |
| Delivery is evaluated on region, order value ex-tax, and order weight | [FR-5.1](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Records and constructor injection first; Lombok only `@RequiredArgsConstructor` / `@Slf4j` | `CLAUDE.md`, [`CONTRIBUTING.md`](../CONTRIBUTING.md#code-style) |
| `libs/share` must not depend on Spring | [Architecture §8](../docs/architecture.md#8-build-graph) — every service depends on this module |

## 3. Deliverables

All under `libs/share/src/main/java/com/rednavis/metaldesk/share/`:

| Path | What |
|---|---|
| `domain/money/Money.java` | Amount + `Currency`. Immutable record, arithmetic that refuses mixed currencies |
| `domain/money/Currency.java` | The supported display currencies (FR-1.8). Enum, ISO-4217 codes |
| `domain/measure/Weight.java` | Amount + unit, with a canonical unit for tier comparison (FR-5.1) |
| `domain/measure/WeightUnit.java` | Gram / kilogram / troy ounce, each with its conversion factor to the canonical unit |
| `domain/measure/Purity.java` | Fineness (e.g. 999.9 parts per thousand), validated range |
| `domain/Region.java` | Delivery region identifier (FR-5.1). Enum or record — see §4 |
| `domain/id/CustomerId.java`, `ProductId.java`, `OrderId.java`, `CategoryId.java`, `FulfillmentTierId.java` | Typed identifiers |
| `domain/id/EntityId.java` | The shared sealed/abstract contract the five identifiers satisfy |
| `error/DomainException.java` | Base unchecked supertype for every domain failure |
| `error/ValidationException.java`, `NotFoundException.java`, `ConflictException.java` | The three failure kinds every service maps to HTTP |
| `error/package-info.java`, `domain/money/package-info.java`, `domain/measure/package-info.java`, `domain/id/package-info.java` | Package Javadoc |
| `domain/package-info.java` (modify) | Extend the existing file: state the "no domain type outside this package" rule that `T-021` will enforce |

## 4. Specification

**`Money` is the load-bearing type.** A record of `BigDecimal amount` and `Currency currency`.
Validation in a compact canonical constructor: non-null currency, non-null amount, and the amount
scaled to the currency's minor-unit digits with `RoundingMode.HALF_UP` applied **once, at
construction**. Expose `plus`, `minus`, `multiply(BigDecimal)`, `isZero`, `isNegative`, and a
`compareTo`. `plus` and `minus` throw `ValidationException` when the currencies differ — never coerce,
never pick a side. Equality is the record default, which is why the scale must be normalised in the
constructor: `1.5 USD` and `1.50 USD` must be `equals`.

**Do not implement currency conversion here.** Switching display currency (FR-1.8) needs a rate from
outside the domain; this task defines the type that a converter will later consume. State that in the
Javadoc so nobody adds a `convertTo` with a hard-coded rate.

**`Weight` carries a unit and a canonical form.** Tier evaluation compares an order's weight against a
ceiling (FR-5.1), and those two values will not arrive in the same unit. Give `Weight` a
`toCanonical()` returning grams and make `compareTo` operate on the canonical value. Troy ounce is
required — it is the unit a precious-metals catalog actually quotes.

**Typed identifiers, not `String`.** Each identifier is a record wrapping a single `String value`,
validated non-blank. They exist so that `orderFor(customerId, productId)` cannot be called with the
arguments swapped. Whether `EntityId` is a sealed interface or an abstract record component contract is
yours to choose — document which and why. Do **not** give them a `random()` factory that generates an
id: identifier generation belongs to whatever persists the aggregate, and
[BR-6](../docs/business-requirements.md#8-business-rules) gives order numbering a specific format
handled in `T-014`.

**`Region`** — FR-5.1 makes region an input to tier lookup, and
[FR-5.1](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) has staff
configuring tiers *per region*. If regions are staff-configurable data, `Region` is a record wrapping a
code, not an enum. The BRD does not say outright. **Read FR-5.1 and §7.5, decide, and record the
decision in the class Javadoc** — an enum here forces a redeploy to add a country, which is exactly the
mistake [Architecture §3](../docs/architecture.md#3-domain-model) calls out for `FulfillmentTier`.

**The exception taxonomy is three kinds, not one per aggregate.** `ValidationException` (the caller sent
something the domain rejects), `NotFoundException` (a referenced aggregate does not exist),
`ConflictException` (the request is valid but the current state forbids it — a closed order, an expired
quote). All extend `DomainException extends RuntimeException`. Each carries a stable machine-readable
code alongside its message, because `services/api` will map these to an HTTP error envelope and
[FR-8.1](../docs/business-requirements.md#78-checkout--step-5-confirmation) requires a
support-actionable error rather than a silent failure. Do not add a fourth kind in this task.

**Zero Spring, zero Lombok in this package.** `libs/share`'s `build.gradle.kts` currently declares only
`compileOnlyApi(libs.lombok)`; leave it that way. Records need neither. Add the test dependencies if
they are not already coming from the convention plugin, and nothing else.

**Javadoc is a deliverable, not decoration** — `MissingJavadocType` with `excludeScope=nothing` and
`maxWarnings = 0` means an undocumented public type fails the build. Each type's Javadoc states what it
is, what it refuses, and which FR or BR it serves.

## 5. Acceptance criteria

1. `./gradlew :libs:share:build` is `BUILD SUCCESSFUL`, including Checkstyle, SpotBugs and Spotless.
2. `Money.of("1.5", USD).equals(Money.of("1.50", USD))` is true, and `plus` across two currencies throws
   `ValidationException`.
3. `Weight` of 1 troy ounce and 31.1034768 g compare equal through `toCanonical()`.
4. `Purity` rejects a fineness outside its documented range and accepts 999.9.
5. Every identifier rejects blank and null, and no two identifier types are assignment-compatible —
   asserted by a test that would not compile if they were.
6. `ValidationException`, `NotFoundException` and `ConflictException` are distinguishable by type alone,
   with no boolean flag and no string matching, and each exposes its code.
7. `./gradlew :libs:share:dependencies --configuration compileClasspath` shows no Spring artifact.
8. Test coverage exists for every validation branch named above; `jacocoTestReport` runs clean.
9. No file outside `libs/share/` is added or modified, except `gradle/libs.versions.toml` if a test
   dependency alias genuinely had to be added.

## 6. Verification

```
cd <repo>
./gradlew :libs:share:build
./gradlew :libs:share:dependencies --configuration compileClasspath | grep -i spring   # expect nothing
grep -rn 'BigDecimal' libs/share/src/main/java --include=*.java | grep -v 'money/'      # expect nothing
grep -rln 'class .*Exception' libs/share/src/main/java/com/rednavis/metaldesk/share/error
find libs/share/src/main/java -name 'package-info.java'
```

Expected: `BUILD SUCCESSFUL`; no Spring on the compile classpath; `BigDecimal` confined to the `money`
package; four exception files; a `package-info.java` per new package.

## 7. Out of scope

Every aggregate — `Customer` (`T-011`), `Product`/`Category` (`T-012`), `PriceRule` (`T-013`),
`Order`/`OrderLine` (`T-014`), `OrderStatus` (`T-015`), `FulfillmentTier` (`T-016`). Currency
conversion rates. Persistence annotations or Mongo documents (`T-030`). The architectural boundary
rule itself (`T-021`) — this task only *documents* it in `package-info.java`.

## 8. Hazards

- **`BigDecimal.equals` compares scale**, so `1.5` and `1.50` are unequal. Normalising in the canonical
  constructor is what makes the record's generated `equals` correct. Getting this wrong produces an
  order whose total is right and whose line comparison is wrong.
- **Never use `double` for money or purity.** SpotBugs at `Confidence.LOW` may not catch it, and the
  rounding error surfaces first in a tax calculation (BR-4) months later.
- `MissingJavadocType` fails the build for every public type without a doc comment. Write the Javadoc as
  you go; retrofitting it across 15 types is a miserable second pass.
- A `record` with a validating compact constructor is the intended shape. Do not reach for Lombok
  `@Value` or `@Builder` — `CLAUDE.md` limits Lombok to `@RequiredArgsConstructor` and `@Slf4j`.
- Resist adding a fourth exception kind "while we are here". Each one becomes a `catch` in every
  service; the three named here are the ones the HTTP envelope needs.

## 9. On completion

Mark the T-010 row done in [`README.md`](README.md). Record in its Notes column the `Region`
enum-versus-record decision and its reasoning, since `T-016` and `T-040` both build on it, and note any
catalog alias you had to add.
