# T-041 — Phase 3 backend exit gate: end-to-end checkout, both outcomes

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/modernization-plan.md`](../docs/modernization-plan.md), the plan wins** — it defines the exit
> criterion this task proves. Update this task's row in [the ledger](README.md) in the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#34](https://github.com/rednavis/metal-desk/issues/34)

**Milestone:** M2 Services and admin API · **Estimate:** 4 h

**Preconditions** — `T-030` … `T-040` merged. Every backend piece of Phase 3 must exist.

**Goal** — Prove Phase 3's backend exit criterion with an automated test: the full checkout flow, **including the
manager-handoff path**, running end to end against mocked externals, covering **both** outcomes.

## 1. Why this task exists

The [Modernization Plan](../docs/modernization-plan.md) makes this the phase's yes/no question:

> **Exit criteria:** the full checkout flow (catalog → cart → checkout → payment → confirmation), including the
> manager-handoff path, runs end-to-end against mocked externals with an automated test covering both outcomes.

"Both outcomes" is the part that gets dropped. The self-service path is the one everyone builds and demos; the
handoff path touches `T-036`, `T-040` and `T-038` together and is where the integration actually fails. A gate
that exercises only the happy path does not close this phase.

This task exists as its own entry because a test that spans nine tasks belongs to none of them, and because
[`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules)'s standard — the flow must run "without any external account
from day one" — is a property of the assembled system, not of any single module.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| The exact exit criterion | [Modernization Plan, Phase 3](../docs/modernization-plan.md), issue #3 |
| Every external dependency mocked from the first commit | [ADR-0002](../docs/adr/0002-mocked-external-dependencies.md) |
| Manager handoff is a first-class path | [Architecture §6](../docs/architecture.md#6-order-state-machine), [BR-10](../docs/business-requirements.md#8-business-rules) |
| `order_total = Σ(unit_price × quantity) + tax + delivery_cost` | [BR-5](../docs/business-requirements.md#8-business-rules) |
| Price finality | [BR-2](../docs/business-requirements.md#8-business-rules) |
| Order numbering | [BR-6](../docs/business-requirements.md#8-business-rules) |
| No real credentials, synthetic fixtures | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |
| CI does not build the code — local verification is the gate | `CLAUDE.md`; changes with `T-060`…`T-065` |

## 3. Deliverables

| Path | What |
|---|---|
| `services/api/src/test/java/.../e2e/CheckoutSelfServiceE2ETest.java` | The FR-5.2 path: catalog → cart → checkout → payment → confirmation |
| `services/api/src/test/java/.../e2e/CheckoutHandoffE2ETest.java` | The FR-5.3 path, including the `apps/admin` quote step |
| `services/api/src/test/java/.../e2e/E2ETestSupport.java` | Shared harness: Mongo container, WireMock, fake feed, fake mail, seeded fixtures |
| `services/api/src/test/resources/fixtures/*.json` | Synthetic catalog, tiers, customers — small |
| `docs/architecture.md` (modify) | A short note that the flow is covered end to end, per `CLAUDE.md`'s sync rule |
| `tasks/README.md` (modify) | Mark Phase 3 backend closed with the evidence |

## 4. Specification

**One harness, reused by both tests.** `E2ETestSupport` starts the Mongo container from `T-030`'s shared
singleton (do not start a second one), the WireMock server with `T-018`/`T-019`'s stubs, the fake market-data feed
from `T-039`, and `T-020`'s `InProcessMailSender`. Seed a synthetic catalog, at least two fulfillment tiers, and a
verified customer. Nothing may reach the network; the tests must pass with networking unavailable apart from
loopback.

**The self-service test walks the real HTTP surface.** Not service classes — the endpoints, through the web test
client, in order:

1. read the catalog and pick a priced product (`T-031`);
2. add it to the cart, and add it twice to prove FR-3.1 idempotency in situ (`T-034`);
3. submit step 1 with a destination **inside** the tier ceilings (`T-035`);
4. evaluate delivery and assert `PAYMENT_ALLOWED` with the configured tier price (`T-036`);
5. read the offered methods, pick a gateway method, read the overview (`T-037`);
6. execute payment against the success stub and assert `Captured` → `PAID`;
7. assert the confirmation: order number present and BR-6-shaped, one customer mail, one staff mail (`T-038`);
8. read order history and assert the order appears with its total and status (`T-038`).

Then assert the invariants that span the flow:

- the order total equals BR-5 computed from the snapshotted lines plus the quote's delivery cost;
- the amount sent to the provider equals that total — read it from the WireMock request journal, not from the
  application;
- changing the reference price **after** the order does not change the order's total (BR-2, end to end);
- exactly one order number was allocated.

**The handoff test spans two services.** Same start, then:

1. submit step 1 with a destination and basket that **exceed** a ceiling;
2. assert `HANDOFF_REQUIRED` with the expected `CeilingKind`;
3. **assert that calling the payment endpoint returns 409 and that WireMock recorded zero requests** — the
   FR-5.3 no-inline-payment invariant, end to end;
4. assert the order is `AWAITING_MANAGER_QUOTE`, and that customer and staff handoff mails were sent with the
   bound ceiling in the staff body;
5. through the `apps/admin` API (`T-040`), set quote terms;
6. assert the order returns to `AWAITING_PAYMENT` with the human-set delivery price;
7. pay, and assert the total reflects the human-set price, not the automatic one;
8. assert confirmation and history, and that history shows a consistent status throughout (FR-10.1).

**Also cover the declined path, because FR-6.3 is an exit-criterion behaviour.** Execute against the decline stub
and assert the basket, step-1 details, delivery quote and selected method all survive, and that the order stays
`AWAITING_PAYMENT`. A "both outcomes" gate that cannot survive a decline has not tested the flow customers
actually hit.

**Cross-service testing is the one genuine design question here.** Step 5 of the handoff test needs
`apps/admin`. Options: run both Spring contexts in one test, call `T-040`'s service layer directly, or split the
test across modules. **Choose the one that exercises the most real surface you can afford, and record what you
did and did not cover.** Calling the admin service layer rather than its HTTP endpoint is an acceptable
compromise; silently skipping the admin step is not — that is the half of the criterion most likely to be
quietly dropped.

**Keep it honest about runtime.** If the suite is slow, say how slow in the ledger. Do not tag it as excluded
from `./gradlew build` — `CLAUDE.md` records that CI does not build the code yet, so a test excluded from the
local build runs nowhere at all.

## 5. Acceptance criteria

1. `./gradlew clean build` is `BUILD SUCCESSFUL` for all six JVM modules with both E2E tests included in `test`.
2. The self-service test drives HTTP endpoints only — no direct service-class call in the flow steps.
3. The self-service flow reaches `PAID` and a confirmation with a BR-6-shaped order number.
4. The order total equals BR-5 over the snapshotted lines plus the delivery cost, and equals the amount in
   WireMock's recorded request.
5. A reference-price change after the order leaves the order total unchanged.
6. The handoff test reaches `AWAITING_MANAGER_QUOTE`, and the payment endpoint returns 409 with **zero** WireMock
   requests recorded.
7. Staff and customer handoff mails are asserted through `InProcessMailSender`, and the staff body names the bound
   ceiling.
8. After quote terms are set the order is `AWAITING_PAYMENT`, and paying yields a total reflecting the human-set
   delivery price.
9. The declined path retains basket, step-1 details, delivery quote and selected method, and leaves the order in
   `AWAITING_PAYMENT`.
10. Both tests pass with no outbound network access beyond loopback.
11. Exactly one Mongo container runs for the suite.
12. `docs/architecture.md` records the end-to-end coverage, and the ledger records Phase 3 backend closed with the
    command output.

## 6. Verification

```
cd <repo>
./gradlew clean build
./gradlew :services:api:test --tests '*E2ETest' --info | tail -40
grep -rn 'CheckoutService\|PaymentExecutionService' services/api/src/test/java/**/e2e/*E2ETest.java   # expect nothing in flow steps
grep -rn '@Disabled\|@Tag' services/api/src/test/java/**/e2e/   # expect nothing that excludes them from build
```

Expected: `BUILD SUCCESSFUL`; both E2E tests executed; no service-class shortcuts in the flow; no exclusion
annotations.

## 7. Out of scope

The SPAs and any browser-driven test (`T-050`…`T-056`). CI wiring of this suite (`T-060`…`T-065`). Load or
performance testing. Deployment (`T-070`…`T-078`). Product CRUD, which no FR requires.

## 8. Hazards

- **Testing only the self-service path** leaves the phase's exit criterion unmet while looking complete. The
  handoff path is half the criterion.
- Driving service classes instead of endpoints skips the web layer, the envelope, the stage gate and the
  serialisation — exactly the integration this gate exists to check.
- Computing the expected total with the same helper the application uses proves nothing. Read the charged amount
  from WireMock's journal.
- Starting a second Mongo container multiplies suite time and will be blamed on the tests rather than the harness.
- Marking the suite `@Disabled` or tagging it out of `build` means it never runs anywhere, because CI does not
  build the code yet.
- Skipping the `apps/admin` step and asserting the transition directly on the state machine tests `T-015`, not the
  handoff.
- A fixture catalog large enough to be realistic violates the synthetic-and-small rule and slows every run.

## 9. On completion

Mark the T-041 row done in [`README.md`](README.md) and record **Phase 3 backend closed**, with the `clean build`
output, both test names, the suite runtime, and exactly what the admin step did and did not exercise. Comment the
same evidence on [issue #3](https://github.com/rednavis/metal-desk/issues/3) — but **do not close it**: the SPA
half (`T-050`…`T-056`) belongs to the same issue.
