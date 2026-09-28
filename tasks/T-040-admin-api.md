# T-040 — `apps/admin`: tier configuration, quote handling and order management

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or an [ADR](../docs/adr/), that document wins** —
> open an issue rather than implementing either version. Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#33](https://github.com/rednavis/metal-desk/issues/33)

**Milestone:** M2 Services and admin API · **Estimate:** 4 h

**Preconditions** — `T-036` merged (the handoff) and `T-030` merged. Read `T-030`'s recorded decision about where
the persistence layer lives before starting — it determines this module's shape.

**Goal** — Build the staff API on port 8081: configure fulfillment tiers, handle the manager quotes that
[FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) routes to staff, and
manage order lifecycle. **API only — no server-rendered UI.**

## 1. Why this task exists

`apps/admin` is what makes [Architecture §3](../docs/architecture.md#3-domain-model)'s second load-bearing
decision real:

> **`FulfillmentTier` is data, not code.** Staff configure ceilings and prices; the checkout flow evaluates
> against whatever is currently configured.

Without this module, tiers are fixtures and the claim is untrue. It is also the other half of `T-036`: the handoff
routes an order to staff, and nothing yet lets staff act on it or return it to `AWAITING_PAYMENT`.

This module is also the one architectural oddity in the build, and `CLAUDE.md` is explicit about it:
`apps:admin` is the **only non-reactive** module — `spring-boot-starter-web` (MVC) with
`spring.threads.virtual.enabled: true`, per [ADR-0004](../docs/adr/0004-java25-spring-boot4-runtime.md) — while
both services are WebFlux. Do not "fix" that by switching it to WebFlux.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| `apps/admin` is a pure API with no server-rendered UI; `apps/admin-web` is its sole client | `CLAUDE.md`, [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md) |
| MVC + virtual threads, not WebFlux | [ADR-0004](../docs/adr/0004-java25-spring-boot4-runtime.md), `CLAUDE.md` |
| Staff configure one or more tiers per region: value ceiling, weight ceiling, price, transit time | [FR-5.1](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Final price and terms on a handoff are set by a human | [FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Quote set returns the order to `AWAITING_PAYMENT`; declined cancels it | [Architecture §6](../docs/architecture.md#6-order-state-machine) |
| Tiers are data, evaluated live | [Architecture §3](../docs/architecture.md#3-domain-model) |
| Internal-only, behind Identity-Aware Proxy in deployment | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Port 8081 | `CLAUDE.md` |

## 3. Deliverables

Under `apps/admin/src/main/java/com/rednavis/metaldesk/admin/`:

| Path | What |
|---|---|
| `tier/TierController.java`, `TierService.java` | Tier CRUD, with the overlap validation from §4 |
| `tier/dto/*.java` | Tier request and response views |
| `quote/QuoteController.java`, `QuoteService.java` | List handoffs, set terms, decline |
| `quote/ManagerQuoteRequest.java` | Final delivery price, terms, validity |
| `order/OrderAdminController.java`, `OrderAdminService.java` | Order list, detail, status advancement, shipment entry |
| `order/ShipmentEntry.java` | Carrier and tracking reference for FR-10.1 |
| `security/StaffPrincipalResolver.java` | Reads the staff identity from the IAP-supplied header — see §4 |
| Tests | Tier validation, both quote outcomes, status advancement, and the tier-visible-to-checkout assertion |

## 4. Specification

**Resolve the persistence question first.** `T-030` recorded where the persistence layer lives. If it is inside
`services/api`, this module cannot reach it directly and must either call `services/api` over HTTP or get its own
mapping layer against the same collections. **Both are defensible; sharing documents by copy-paste is not.**
Implement `T-030`'s recorded decision, and if it turns out to be unworkable, raise it rather than silently
duplicating document classes — `T-021`'s rule 3 will catch the duplication anyway.

**Keep MVC and virtual threads.** Blocking repository calls are *correct* here: `spring.threads.virtual.enabled:
true` is already set in `apps/admin/src/main/resources/application.yml`. Do not add WebFlux, do not add a reactive
Mongo driver to this module, and do not `.block()` your way out of a reactive API — if this module calls
`services/api`, use a blocking HTTP client.

**Tier validation is the substance of this task.** FR-5.1 allows several tiers per region, and `T-016`'s selector
picks among them by the rule `T-016` recorded. That makes bad configuration dangerous rather than merely untidy:

- ceilings must be positive; price must be non-negative; transit time must be positive;
- the region must resolve;
- **overlapping tiers within a region must be rejected or explicitly allowed** — if two tiers cover the same
  value-and-weight rectangle, the selector's outcome depends on `T-016`'s tie-break. Read what `T-016` recorded,
  then either reject overlaps here or document that overlaps are resolved by that rule. Do not leave it
  unexamined.
- Deleting or narrowing the last tier covering a region silently pushes every order there into handoff. Warn on
  it in the response, and say so in the API documentation.

**A tier change is immediately visible to checkout, and a test proves it.** Write a tier through this API, then
evaluate a basket through `T-036`'s path and assert the new price applies with no restart and no cache flush. That
assertion is the whole point of Architecture §3's "data, not code" claim.

**Quote handling fires state-machine triggers.** Setting terms uses `T-015`'s quote-set trigger to return the
order to `AWAITING_PAYMENT`; declining uses the quote-declined trigger to reach `CANCELLED`. No direct status
assignment — the same rule as `T-036`. Setting terms must write the human-decided delivery price onto the order so
`T-037`'s overview and `PaymentIntent` pick it up; assert that the resulting total reflects it.

**Notify the customer when terms are set.** FR-5.3 has the human setting final price and terms; the customer then
has to pay. Reuse `T-020`'s templates rather than adding one, or add a template in `libs/mail` if genuinely
needed — do not inline mail content here.

**Order management covers the statuses staff drive.** `PAID → FULFILLING → SHIPPED → DELIVERED`, each through the
state machine. Shipment entry supplies carrier and tracking, which FR-10.1 shows from `SHIPPED` onward. Reject a
shipment entry on an order that is not being fulfilled.

**Authentication is Identity-Aware Proxy, not application auth.** Architecture §7 puts this service behind IAP.
This module must **not** implement its own login, and must not accept `T-032`'s customer JWT — a customer token
must never grant staff access. `StaffPrincipalResolver` reads the identity IAP supplies; for local dev, allow an
explicitly configured development override that is off by default and obviously non-production. Assert that a
customer JWT is rejected.

## 5. Acceptance criteria

1. `./gradlew :apps:admin:build` is `BUILD SUCCESSFUL`, and `:apps:admin:bootRun` serves `/actuator/health` on
   8081.
2. `apps/admin` has no WebFlux and no reactive Mongo dependency — asserted from its runtime classpath.
3. Creating a tier and then evaluating a basket through the checkout path applies the new tier with no restart.
4. Invalid tiers are rejected: non-positive ceilings, negative price, non-positive transit time, unresolvable
   region — one assertion each.
5. Overlapping tiers behave as documented — either rejected, or accepted with the `T-016` tie-break asserted.
6. Removing the last tier for a region returns a warning in the response body.
7. Setting quote terms returns the order to `AWAITING_PAYMENT` via the state machine, writes the human-set
   delivery price, and the resulting order total reflects it.
8. Declining a quote reaches `CANCELLED` via the state machine.
9. No direct `OrderStatus` assignment anywhere in the module — asserted by grep.
10. Advancing `PAID → FULFILLING → SHIPPED` works in order and is rejected out of order; shipment entry on a
    non-fulfilling order is rejected.
11. A valid customer JWT from `T-032` is rejected by every admin endpoint.
12. The development identity override is disabled by default — asserted by starting the app with no override
    configuration and receiving 401/403.

## 6. Verification

```
cd <repo>
./gradlew :apps:admin:build
./gradlew :apps:admin:dependencies --configuration runtimeClasspath | grep -iE 'webflux|reactivestreams'   # expect nothing
grep -rniE 'setStatus|status *= *OrderStatus\.' apps/admin/src/main   # expect nothing
grep -rn '\.block()' apps/admin/src/main   # expect nothing
grep -rn 'virtual' apps/admin/src/main/resources/application.yml
```

Expected: `BUILD SUCCESSFUL`; no WebFlux or reactive driver; no direct status assignment; no `.block()`; virtual
threads still enabled.

## 7. Out of scope

The staff SPA (`T-056`). Identity-Aware Proxy provisioning (`T-076`). Product and category CRUD — no FR in scope
requires staff product management; note the gap rather than inventing it. Staff roles and permissions beyond
"authenticated staff". Reporting and analytics. Inquiry replies — `T-038` records inquiries; replying is not
specified by any FR.

## 8. Hazards

- **Converting this module to WebFlux** because "everything else is reactive" contradicts ADR-0004 and `CLAUDE.md`
  and will be reverted. The MVC-plus-virtual-threads shape is deliberate.
- Duplicating `T-030`'s document classes here is the shortest path and trips `T-021`'s duplication rule — and if
  it somehow does not, it creates two mapping layers that drift.
- Accepting the customer JWT gives every signed-in customer staff powers. AC-11 is not optional.
- A development identity override that defaults to enabled is a production authentication bypass one
  misconfiguration away.
- Allowing overlapping tiers without examining `T-016`'s tie-break makes delivery price depend on insertion
  order.
- Deleting the last tier for a region silently routes every order there to handoff, which looks like a checkout
  bug.
- Writing the quote's delivery price somewhere `T-037` does not read means the customer is charged the automatic
  price, not the human-set one.

## 9. On completion

Mark the T-040 row done in [`README.md`](README.md). Record how this module reaches persistence, the overlap
policy, and the local staff-identity mechanism — `T-056` consumes this API and `T-076` replaces the override.
