# T-056 — `apps/admin-web`: tier configuration, quote queue and order management

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md) or `CLAUDE.md`, that document wins** — open
> an issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#41](https://github.com/rednavis/metal-desk/issues/41)

**Milestone:** M3 Frontends · **Estimate:** 4 h

**Preconditions** — `T-050` merged and `T-040` merged (`apps/admin`'s API).

**This task closes the SPA half of Phase 3.**

**Goal** — Build the staff SPA: configure fulfillment tiers, work the manager-quote queue
[FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) feeds, and manage order
lifecycle — as the sole client of `apps/admin`.

## 1. Why this task exists

`apps/admin-web` is what makes staff-configurable tiers real. [Architecture §3](../docs/architecture.md#3-domain-model)
says staff configure ceilings and prices; `T-040` built the API; without this screen the claim rests on curl.

It is also the screen where FR-5.3's promise is kept. The handoff routes "the order and full context to staff"; the
quote queue is where that context has to actually be readable, and where a human sets the final price the customer
then pays.

`CLAUDE.md` is explicit about the relationship: `apps/admin-web` is the **sole** client of `apps/admin`, which is a
pure API with no server-rendered UI. And per `T-050`, this app carries **no** sign-in: `apps/admin` is behind
Identity-Aware Proxy (`T-076`).

## 2. Documents to obey

| What | Pinned by |
|---|---|
| `apps/admin-web` is the sole client of `apps/admin`; that service has no server-rendered UI | `CLAUDE.md`, [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md) |
| One React stack for both surfaces | [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md) |
| Single root ESLint config; run from repo root | `CLAUDE.md` |
| Out of the Gradle graph | [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md), `CLAUDE.md` |
| Staff configure tiers per region: value ceiling, weight ceiling, price, transit time | [FR-5.1](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Final price and terms on a handoff are set by a human | [FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Internal-only, behind IAP — no application login | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp), `T-040` |
| Order statuses and transitions | [Architecture §6](../docs/architecture.md#6-order-state-machine), `T-015` |
| `admin` is on port 8081 | `CLAUDE.md` |

## 3. Deliverables

| Path | What |
|---|---|
| `apps/admin-web/src/routes/tiers/TierListRoute.tsx`, `TierEditRoute.tsx` | Tier CRUD |
| `.../tiers/TierCoverageView.tsx` | Which value/weight rectangles a region covers — see §4 |
| `.../quotes/QuoteQueueRoute.tsx` | The handoff queue |
| `.../quotes/QuoteDetailRoute.tsx` | Full context plus set-terms and decline |
| `.../orders/OrderListRoute.tsx`, `OrderDetailRoute.tsx` | Order management, status advancement |
| `.../orders/ShipmentForm.tsx` | Carrier and tracking entry |
| `apps/admin-web/src/features/*/` | Hooks and API bindings against `T-040` |
| Tests | Tier validation, coverage gap warning, both quote outcomes, transition gating, shipment entry |

## 4. Specification

**No sign-in route, no `Authorization` header.** `T-050` AC-8 already asserts this; keep it true. Staff identity
comes from IAP. Show the resolved staff identity in the shell so a user knows who they are acting as, read from
whatever `T-040`'s principal endpoint exposes.

**Tier editing must show coverage, not just rows.** A region's tiers form a set of value-and-weight rectangles, and
`T-016`'s selector picks among overlapping ones by the recorded tie-break. A flat table hides both overlaps and
gaps. Render coverage so a human can see:

- where two tiers overlap, with the tie-break outcome stated;
- where a region has **no** coverage above a ceiling, which means every such order goes to handoff.

This is the difference between an editor and a form. It does not need to be a chart — a sorted, annotated list of
bands is enough — but the overlap and gap conditions must be visible, because `T-040` only warns on the last-tier
case.

**Surface `T-040`'s validation and warnings verbatim.** Field-level errors bind to inputs; the last-tier-removal
warning is shown prominently and requires explicit confirmation, since deleting it silently routes a whole region to
manual pricing.

**The quote queue shows the full FR-5.3 context on the detail screen**, not just an order number: the lines, the
destination region, the ex-tax value, the weight, **which ceiling bound**, and the customer's contact details.
`T-036` put all of it in the staff notification; a queue that makes staff hunt for it defeats the requirement.

**Setting terms is a deliberate, confirmable action.** The form takes the final delivery price and terms. Show the
resulting order total before submitting, because that is the number the customer will be asked to pay — a staff
member who cannot see the total is setting a delivery price blind. Declining requires a reason and a confirmation;
it cancels the customer's order.

**Order management renders only legal transitions.** `T-015`'s `availableFrom(status)` gives the permitted triggers;
derive the available actions from the server rather than hard-coding a status order. A disabled-looking button for
an illegal transition is better than an enabled one that 409s, and a hard-coded sequence will drift from the table.

Shipment entry is available only where the transition to `SHIPPED` is legal, and requires carrier and tracking
together — FR-10.1 shows both, so neither alone is useful.

**Lists paginate and are filterable by status.** A staff queue with no status filter is unusable after a week.

**Destructive and financial actions confirm.** Declining a quote, deleting a tier and cancelling an order all need
explicit confirmation naming the consequence.

## 5. Acceptance criteria

1. `pnpm -r run build`, `pnpm run typecheck`, `pnpm run lint` and `pnpm run test` pass from the repo root.
2. No sign-in route exists and no request carries an `Authorization` header — asserted by grep and a client test.
3. No `eslint.config.*` inside `apps/admin-web`, and the app does not appear in `./gradlew projects`.
4. Creating a tier with an invalid ceiling, price or transit time renders the server's field-level error.
5. The coverage view shows an overlap between two tiers and states the tie-break outcome.
6. The coverage view shows a region's uncovered band, and removing the last tier requires explicit confirmation of
   the warning.
7. The quote detail screen renders lines, destination, ex-tax value, weight, bound ceiling and customer contact —
   each asserted present.
8. Setting terms shows the resulting order total before submission and, on submit, the order returns to
   `AWAITING_PAYMENT`.
9. Declining requires a reason and a confirmation, and results in `CANCELLED`.
10. Available order actions are derived from the server's permitted triggers; no hard-coded status sequence exists —
    asserted by grep.
11. Shipment entry requires both carrier and tracking and is offered only where `SHIPPED` is legal.
12. Both lists paginate and filter by status.

## 6. Verification

```
cd <repo>
pnpm install --frozen-lockfile && pnpm -r run build && pnpm run typecheck && pnpm run lint && pnpm run test
grep -rniE 'Authorization|signin|sign-in|login' apps/admin-web/src   # expect nothing
find apps/admin-web -name 'eslint.config.*'   # expect nothing
./gradlew projects | grep -i admin-web        # expect nothing
grep -rnE "'(PAID|FULFILLING|SHIPPED)'.*'(FULFILLING|SHIPPED|DELIVERED)'" apps/admin-web/src   # expect no hard-coded sequence
```

Expected: all green; no auth or login in admin-web; no per-app ESLint config; not a Gradle project; no hard-coded
transition order.

## 7. Out of scope

Identity-Aware Proxy provisioning (`T-076`). Product and category management — `T-040` did not build the API for it.
Staff roles and permissions. Reporting and dashboards. Inquiry replies. Localization of the staff UI (`T-051` covers
`apps/web` only; note the omission).

## 8. Hazards

- **Adding a sign-in screen** duplicates an authentication path IAP owns; it will work locally and be dead or,
  worse, a bypass in production.
- A flat tier table hides overlaps and gaps, so a misconfiguration ships and surfaces as customers being quoted the
  wrong delivery price — or all being routed to handoff.
- A quote screen without the bound ceiling and the numbers sends staff back to the customer to re-ask, which is what
  FR-5.3's "full context" exists to prevent.
- Letting a staff member set delivery terms without seeing the resulting total invites a price nobody intended.
- Hard-coding the status sequence drifts from `T-015`'s table the first time a state is added.
- Deleting the last tier for a region without a confirmed warning quietly makes every order there manual.
- Accepting a tracking reference without a carrier gives FR-10.1 half a shipment.

## 9. On completion

Mark the T-056 row done in [`README.md`](README.md) and record **the SPA half of Phase 3 closed**. Comment the
evidence on [issue #3](https://github.com/rednavis/metal-desk/issues/3) and close it — `T-041` closed the backend
half, and with this task the whole phase and its exit criterion are met.
