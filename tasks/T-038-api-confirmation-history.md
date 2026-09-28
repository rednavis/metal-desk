# T-038 — `services/api`: confirmation, notifications, order history and inquiries

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#31](https://github.com/rednavis/metal-desk/issues/31)

**Milestone:** M2 Services and admin API · **Estimate:** 3 h

**Preconditions** — `T-037` merged. `T-019` merged (invoice documents) and `T-020` merged (`libs/mail`).

**Goal** — Close the customer-facing flow: [BRD §7.8](../docs/business-requirements.md#78-checkout--step-5-confirmation)
confirmation and notifications, [§7.9](../docs/business-requirements.md#79-manager-mediated-inquiries) inquiries,
and [§7.10](../docs/business-requirements.md#710-order-history) order history.

## 1. Why this task exists

[FR-8.1](../docs/business-requirements.md#78-checkout--step-5-confirmation) bundles four obligations into one
sentence — generate the order number, show a confirmation, notify both parties, and make a failed order produce
"a support-actionable error message rather than a silent failure". The last clause is the one that needs
deliberate work: it is the reason `T-010`'s exceptions carry codes and `T-031`'s envelope carries a correlation
id, and this is where that chain has to actually connect.

[FR-10.1](../docs/business-requirements.md#710-order-history) is the payoff for `T-015`'s first-class handoff
state: history must "show a consistent status for every order regardless of which path it took", which only
works because `AWAITING_MANAGER_QUOTE` is a status rather than a flag.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Order number generated on success or successful handoff; confirmation page; both parties notified | [FR-8.1](../docs/business-requirements.md#78-checkout--step-5-confirmation) |
| A failed order gives a support-actionable error, never a silent failure | [FR-8.1](../docs/business-requirements.md#78-checkout--step-5-confirmation) |
| Order number is `<date><daily-sequence>` | [BR-6](../docs/business-requirements.md#8-business-rules) |
| Invoice emailed to customer and staff, in the customer's locale | [FR-6.2](../docs/business-requirements.md#76-checkout--step-2-payment-method) |
| Price inquiry from catalog, product page, or a tier handoff; both parties notified | [FR-9.1](../docs/business-requirements.md#79-manager-mediated-inquiries) |
| Free-form message to staff, same notification guarantees | [FR-9.2](../docs/business-requirements.md#79-manager-mediated-inquiries) |
| History: number, date, item count, total, status, carrier and tracking once shipped; drill-down | [FR-10.1](../docs/business-requirements.md#710-order-history) |
| Consistent status for every order, whichever path | [FR-10.1](../docs/business-requirements.md#710-order-history), [Architecture §6](../docs/architecture.md#6-order-state-machine) |

## 3. Deliverables

Under `services/api/src/main/java/com/rednavis/metaldesk/api/`:

| Path | What |
|---|---|
| `checkout/confirmation/ConfirmationService.java` | The FR-8.1 completion: notify, attach invoice, expose the confirmation view |
| `checkout/confirmation/OrderNotificationService.java` | Customer and staff notifications, per template |
| `order/OrderHistoryController.java`, `OrderHistoryService.java` | FR-10.1 list and detail |
| `order/ShipmentDetails.java` | Carrier and tracking reference, present only once shipped |
| `inquiry/InquiryController.java`, `InquiryService.java` | FR-9.1 and FR-9.2 |
| `inquiry/Inquiry.java`, `inquiry/InquirySource.java` | The inquiry model; source = catalog, product, or handoff |
| `dto/ConfirmationView.java`, `dto/OrderSummaryView.java`, `dto/OrderDetailView.java` | Wire types |
| Tests | Both confirmation paths, notification assertions, history for each status, inquiry from all three sources |

## 4. Specification

**Confirmation covers both success paths.** FR-8.1 says "on successful payment **or** successful manager
handoff". The order number is allocated in both — so if `T-036`'s handoff already allocated it, this task must
not allocate a second one. Read `T-036`'s recorded decision and reconcile explicitly; a double allocation burns
a daily sequence number and produces two identifiers for one order.

**Notifications are idempotent per order and per template.** A retried confirmation, a duplicate provider
callback, or a reconciliation job must not send a second confirmation email. Record what has been sent against
the order and check before sending. This is the sort of thing that only shows up in production, so assert it:
calling confirmation twice sends one pair of mails.

**The invoice is attached, not re-rendered.** `T-019` produced the `InvoiceDocument`(s) during payment
execution. Attach them to the `INVOICE_CUSTOMER` and `INVOICE_STAFF` mails as `MailAttachment`s. Where FR-6.2's
split produced two documents, both go to both recipients. Do not call the renderer again here — a second render
could produce a different document for the same order.

**FR-8.1's actionable error is a concrete mechanism, not a message.** A failed order must yield: the envelope's
machine-readable code, a correlation id that appears in both the response and the server log, and a logged event
at error level carrying the order reference. Assert that the correlation id in the response is findable in the
captured log output — otherwise "support-actionable" is a claim, not a property.

**History is a projection, and it reads only snapshotted data.** `OrderSummaryView` carries the order number,
creation date, item count, grand total and status — all from the order's own snapshotted lines (`T-014`), never
re-derived from current product prices. A test must prove this: change a product's price after the order, and the
history total must not move. This is BR-2 at the read layer, and it is where a violation would actually become
visible to a customer.

**Every status renders, including the handoff.** FR-10.1 wants a consistent status for every order. Map all eight
`OrderStatus` values to a customer-facing label; a test iterating the enum guarantees a new state cannot be added
without a label. Carrier and tracking appear **only** from `SHIPPED` onward — `Optional`, absent before, and
asserted absent for an `AWAITING_PAYMENT` order.

**History is scoped to the caller.** FR-10.1 is for "a signed-in customer". Requesting another customer's order
must be a 404, not a 403 — a 403 confirms the order exists. Assert it, for the list and the drill-down.

**Inquiries have three sources and one notification contract.** FR-9.1 allows a price inquiry from the catalog,
the product page, or a tier handoff; FR-9.2 allows a free-form message with "the same notification guarantees".
Model the source explicitly, notify customer and staff for every kind, and give the customer a reference.
An inquiry from an unauthenticated visitor must be possible — FR-9.1 does not require sign-in — so validate the
supplied email rather than reading a principal.

## 5. Acceptance criteria

1. `./gradlew :services:api:build` is `BUILD SUCCESSFUL`.
2. Confirmation after a captured payment sends exactly one `ORDER_CONFIRMATION_CUSTOMER` and one
   `ORDER_NOTIFICATION_STAFF`; a second confirmation call sends none.
3. Confirmation after a handoff also produces a confirmation, and exactly one order number exists for that order.
4. An invoice order attaches the `T-019` documents to both mails; a mixed-tax order attaches two, and the
   renderer is not invoked during confirmation — asserted with a spy.
5. A failed confirmation returns the envelope code plus a correlation id that is present in the captured log
   output.
6. History returns number, date, item count, total and status for every order of the caller.
7. Changing a product's price after an order does not change that order's history total.
8. All eight `OrderStatus` values have a customer-facing label — asserted by iterating the enum.
9. Carrier and tracking are absent for a pre-`SHIPPED` order and present for a shipped one.
10. Requesting another customer's order returns 404, both in the list scope and the detail endpoint.
11. An inquiry from each of the three sources notifies customer and staff and returns a reference; an
    unauthenticated inquiry succeeds.
12. Every mail assertion is made against `InProcessMailSender`, not a log.

## 6. Verification

```
cd <repo>
./gradlew :services:api:build :services:api:test
grep -rn 'InvoiceRenderer' services/api/src/main/java/com/rednavis/metaldesk/api/checkout/confirmation/   # expect nothing
grep -rn 'PriceDerivation' services/api/src/main/java/com/rednavis/metaldesk/api/order/   # expect nothing
grep -rn 'OrderStatus' services/api/src/main/java/com/rednavis/metaldesk/api/order/ | head
```

Expected: `BUILD SUCCESSFUL`; no re-rendering in confirmation; no live price derivation in history; every status
mapped.

## 7. Out of scope

Staff-side order management, status advancement and shipment entry (`T-040`). Setting quote terms on a handoff
(`T-040`). Invoice rendering (`T-019`). The confirmation and history UI (`T-054`, `T-055`). Push or SMS
notification. Refunds.

## 8. Hazards

- **Allocating a second order number** when `T-036` already allocated one at handoff gives the customer two
  references and quietly breaks BR-6's daily sequence.
- Non-idempotent notifications mean a duplicate provider callback emails the customer twice, which is a support
  contact rather than a silent bug.
- Re-rendering the invoice at confirmation can produce a document that disagrees with the one referenced by the
  `PaymentRecord`.
- Computing history totals from live product prices is the most customer-visible possible BR-2 violation: an
  order whose total changes after the fact. AC-7 is the guard.
- Returning 403 for another customer's order leaks its existence.
- Showing carrier and tracking fields as empty strings before shipping makes the client render an empty tracking
  link. Keep them absent.
- Requiring sign-in for an inquiry contradicts FR-9.1 and blocks the catalog inquiry path for visitors.

## 9. On completion

Mark the T-038 row done in [`README.md`](README.md). Record how order-number allocation was reconciled with
`T-036`, and the status-label mapping — `T-055` renders it and `T-041` asserts both confirmation paths.
