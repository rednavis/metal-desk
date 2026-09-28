# T-054 — `apps/web`: the five-step checkout wizard

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with the
> [business requirements](../docs/business-requirements.md), that document wins** — open an issue rather
> than implementing either version. Update this task's row in [the ledger](README.md) in the same pull
> request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#39](https://github.com/rednavis/metal-desk/issues/39)

**Milestone:** M3 Frontends · **Estimate:** 5 h

**Preconditions** — `T-053` merged. `T-035`, `T-036`, `T-037` and `T-038` merged — the whole server-side checkout.

**Goal** — Build checkout steps 1–5 from [BRD §7.4](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data)
through [§7.8](../docs/business-requirements.md#78-checkout--step-5-confirmation), including the
**manager-handoff branch** where the primary call-to-action stops being "Pay".

## 1. Why this task exists

This is the screen the whole system exists for, and it is the only place where the manager handoff becomes visible
to a customer. [FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) is explicit
about the UI consequence:

> the primary call-to-action changes from "Pay" to a manager-handoff action.

The server already refuses inline payment on a handoff session (`T-036`'s stage gate), so the client cannot create
a payment where one is forbidden. But a client that still shows "Pay" produces a dead button and a confusing
experience — the branch has to be rendered, not merely blocked.

[FR-7.1](../docs/business-requirements.md#77-checkout--steps-34-overview--payment)'s "go back and edit any prior
step" is the other structural requirement: it interacts with `T-037`'s invalidation rule, where editing step 1
clears the delivery evaluation.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Step 1 field set, presence and format validation, optional company and note | [FR-4.1](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data) |
| "Remember me" quick registration without leaving checkout | [FR-4.2](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data) |
| Privacy acceptance is a **distinct** checkbox | [FR-4.3](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data) |
| Saved delivery profile pre-filled but editable | [FR-3.5](../docs/business-requirements.md#73-cart--checkout-entry) |
| Within ceilings → pay; exceeding → handoff CTA, no inline payment | [FR-5.2](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule), [FR-5.3](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Insurance never a separate line item | [FR-5.4](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule), [BR-7](../docs/business-requirements.md#8-business-rules) |
| Method choice per FR-6.1; invoice first-class | [FR-6.1](../docs/business-requirements.md#76-checkout--step-2-payment-method), [FR-6.2](../docs/business-requirements.md#76-checkout--step-2-payment-method) |
| Failure returns to selection with nothing lost | [FR-6.3](../docs/business-requirements.md#76-checkout--step-2-payment-method) |
| Overview shows every component; back-and-edit any step | [FR-7.1](../docs/business-requirements.md#77-checkout--steps-34-overview--payment) |
| Provider flow: redirect, embedded element, or invoice | [FR-7.2](../docs/business-requirements.md#77-checkout--steps-34-overview--payment) |
| Confirmation shows the order number | [FR-8.1](../docs/business-requirements.md#78-checkout--step-5-confirmation) |

## 3. Deliverables

| Path | What |
|---|---|
| `apps/web/src/routes/checkout/CheckoutRoute.tsx` | The wizard shell, step routing, progress |
| `.../steps/Step1CustomerDetails.tsx` | FR-4.1, FR-4.2, FR-4.3, FR-3.5 |
| `.../steps/Step2Delivery.tsx` | The `T-036` evaluation result and the CTA branch |
| `.../steps/Step3PaymentMethod.tsx` | FR-6.1 method selection from the server-offered list |
| `.../steps/Step4Overview.tsx` | FR-7.1 overview and execution |
| `.../steps/Step5Confirmation.tsx` | FR-8.1 confirmation |
| `.../HandoffOutcome.tsx` | The FR-5.3 handoff receipt with its reference number |
| `.../useCheckoutSession.ts` | Session state, step gating, invalidation |
| `.../PaymentFlowRouter.tsx` | Dispatch on `T-017`'s outcome variants |
| Tests | Validation, consent, the CTA branch, decline retention, back-and-edit invalidation, all payment flows |

## 4. Specification

**The server's session is the source of truth for which step is reachable.** `T-036`'s `CheckoutStage` and the
session state decide; the client renders them. Never let client state alone unlock a step — a reload mid-checkout
must resume from the server.

**Step 1 renders server validation field by field.** `T-035` returns a field-keyed violation list; bind each to its
input. Pre-fill from the saved delivery profile (FR-3.5) but keep every field editable, and send the full explicit
set on submit.

**Privacy acceptance is its own checkbox with its own label**, per FR-4.3 — never bundled with a newsletter or
terms toggle, and unchecked by default. "Remember me" reveals a password field and submits the FR-4.2 conversion;
checkout must continue without waiting for the verification email.

**Step 2 renders the branch, and this is the heart of the task.** On `PAYMENT_ALLOWED`: show the quoted delivery
cost and transit time, and the primary CTA continues to payment. On `HANDOFF_REQUIRED`: **replace** the primary
CTA with the handoff action, explain why using the reason code `T-036` returns — naming the bound ceiling — and
render **no** payment affordance at all. After the handoff succeeds, show the reference number `T-036` allocated.

Delivery appears as a single cost. FR-5.4 and BR-7 fold insurance in, so there must be no insurance line anywhere in
the UI. Grep for it in AC-6.

**Step 3 offers only what the server offers.** `T-037` computes the permitted list applying BR-9. Render exactly
that list; never hard-code the six methods, or a high-value order will show gateway options the server will reject.
Present invoice as an equal choice, not a fallback — FR-6.2 makes it first-class.

**Step 4's totals come from the server's overview.** Do not recompute in the client. `T-037` AC-9 ties the overview
total to the charged amount; a client-side recomputation can disagree and would be the worst kind of bug here.
Every prior step is editable from the overview, and editing step 1 or the basket must clear the delivery evaluation
and route the user back through step 2 — `T-037` invalidates server-side, and the client must not present a stale
quote.

**`PaymentFlowRouter` handles every `PaymentOutcome` variant exhaustively.** Redirect → navigate away, having
persisted enough to resume. Element → mount the embedded element. Document issued → confirmation explaining the
invoice was emailed and payment is pending. Declined → back to step 3 with the reason, **everything retained**.
Failed → an actionable error with the correlation id from `T-031`'s envelope. Use a discriminated union with an
exhaustiveness check so a new variant is a type error.

**A decline must visibly retain state.** FR-6.3 is a client obligation too: returning to step 3 must still show the
basket, the customer details and the delivery quote. Assert each.

**Confirmation shows the order number and what happens next**, distinguishing a captured payment from a pending
invoice. Both are successful outcomes and must not look like errors.

## 5. Acceptance criteria

1. `pnpm -r run build`, `pnpm run typecheck`, `pnpm run lint` and `pnpm run test` pass from the repo root.
2. Every mandatory FR-4.1 field renders its server-side violation next to the input; two violations render two
   messages.
3. The privacy checkbox is separate, unchecked by default, and blocks progress until accepted.
4. "Remember me" submits the conversion and checkout proceeds without waiting for verification.
5. On `HANDOFF_REQUIRED` there is **no** pay affordance anywhere in the DOM, the CTA is the handoff action, and the
   bound ceiling is explained — asserted by querying for any pay control.
6. No insurance line item appears in any checkout view — asserted by grep and a render assertion.
7. The method list renders exactly what the server offers; a high-value session shows no gateway method and shows
   invoice.
8. Step 4 totals come from the server payload — asserted by rendering a payload whose total differs from a naive
   client sum and checking the server value is displayed.
9. Editing step 1 from the overview clears the delivery quote and returns the user to step 2.
10. Each of the six payment outcomes renders its documented UI; the union is exhaustively handled.
11. After a decline, basket, customer details, delivery quote and selected method are all still present — four
    assertions.
12. A failed payment shows the correlation id.
13. Confirmation shows the order number, and distinguishes captured from pending-invoice.
14. A reload mid-checkout resumes from the server session at the correct step.

## 6. Verification

```
cd <repo>
pnpm install --frozen-lockfile && pnpm -r run build && pnpm run typecheck && pnpm run lint && pnpm run test
grep -rniE 'insurance' apps/web/src/routes/checkout   # expect nothing
grep -rnE "'(card|invoice|wallet|bank)" apps/web/src/routes/checkout/steps/Step3PaymentMethod.tsx   # expect no hard-coded list
grep -rn 'reduce\|+ *tax' apps/web/src/routes/checkout/steps/Step4Overview.tsx   # expect no client-side total
```

Expected: all green; no insurance line; no hard-coded method list; no client-computed total.

## 7. Out of scope

Order history (`T-055`). The staff side of a handoff (`T-040`, `T-056`). Real payment vendor SDKs — the element
flow mounts against the mocked provider. Address autocomplete. Saved payment instruments. Browser end-to-end tests.

## 8. Hazards

- **Leaving a pay affordance on a handoff session** contradicts FR-5.3's visible requirement and gives the customer
  a button the server will reject with 409. AC-5 is the guard.
- Recomputing totals in the client can disagree with the charged amount — the single most damaging discrepancy in
  the flow.
- Hard-coding the six payment methods breaks BR-9 silently for exactly the orders where it matters most.
- Presenting a stale delivery quote after an address edit charges the wrong delivery cost, or routes a
  handoff-required order to payment.
- Losing state on a decline is the exact failure FR-6.3 legislates against, and it is the default outcome of a
  client-side wizard that resets on error.
- A non-exhaustive outcome switch silently renders nothing for a variant added later.
- Rendering a pending invoice as an error makes a first-class payment method look broken.
- Bundling privacy consent with another toggle violates FR-4.3 and is legally meaningful.

## 9. On completion

Mark the T-054 row done in [`README.md`](README.md). Record how the redirect flow resumes, and confirm which
`PaymentOutcome` variants were exercised with real mocked responses versus rendered from fixtures.
