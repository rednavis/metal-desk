# T-055 — `apps/web`: auth screens, order history and inquiries

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with the
> [business requirements](../docs/business-requirements.md), that document wins** — open an issue rather
> than implementing either version. Update this task's row in [the ledger](README.md) in the same pull
> request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#40](https://github.com/rednavis/metal-desk/issues/40)

**Milestone:** M3 Frontends · **Estimate:** 4 h

**Preconditions** — `T-053` merged. `T-032`, `T-033` and `T-038` merged.

**Goal** — Complete the customer surface: [BRD §7.2](../docs/business-requirements.md#72-authentication) auth
screens, [§7.9](../docs/business-requirements.md#79-manager-mediated-inquiries) inquiries and
[§7.10](../docs/business-requirements.md#710-order-history) order history.

## 1. Why this task exists

The auth screens are where a server-side security property is easiest to undo. `T-032` went to some length to make
sign-in failures indistinguishable — one status, one body, constant-ish timing. A client that renders "we don't
recognise that email" for one case and "wrong password" for another **reintroduces the enumeration oracle in the
UI**, and the server test that asserts identical responses will still pass.

Order history is where [FR-10.1](../docs/business-requirements.md#710-order-history)'s consistent-status promise
pays off — including `AWAITING_MANAGER_QUOTE`, which `T-015` made first-class precisely so this screen has
something to show.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Sign in with email or phone; generic failure message | [FR-2.1](../docs/business-requirements.md#72-authentication), [FR-2.2](../docs/business-requirements.md#72-authentication) |
| Registration collects the minimum profile; email verified before checkout | [FR-2.3](../docs/business-requirements.md#72-authentication) |
| Password reset via a time-limited link | [FR-2.4](../docs/business-requirements.md#72-authentication) |
| Account switching without full re-login | [FR-2.5](../docs/business-requirements.md#72-authentication) |
| Signing out mid-checkout keeps the cart and offers resume-or-home | [FR-2.7](../docs/business-requirements.md#73-cart--checkout-entry) |
| Price inquiry from catalog, product page, or handoff; both parties notified | [FR-9.1](../docs/business-requirements.md#79-manager-mediated-inquiries) |
| Free-form message to staff | [FR-9.2](../docs/business-requirements.md#79-manager-mediated-inquiries) |
| History: number, date, item count, total, status, carrier and tracking once shipped; drill-down | [FR-10.1](../docs/business-requirements.md#710-order-history) |

## 3. Deliverables

| Path | What |
|---|---|
| `apps/web/src/routes/auth/SignInRoute.tsx` | FR-2.1, FR-2.2 |
| `.../auth/RegisterRoute.tsx`, `VerifyEmailRoute.tsx` | FR-2.3, FR-2.6 |
| `.../auth/ForgotPasswordRoute.tsx`, `ResetPasswordRoute.tsx` | FR-2.4 |
| `.../auth/AccountSwitcher.tsx` | FR-2.5 |
| `.../auth/SignOutPrompt.tsx` | FR-2.7's resume-or-home choice |
| `.../orders/OrderHistoryRoute.tsx`, `OrderDetailRoute.tsx` | FR-10.1 |
| `.../orders/OrderStatusBadge.tsx` | All eight statuses, from `T-038`'s label mapping |
| `.../inquiry/InquiryForm.tsx`, `InquiryRoute.tsx` | FR-9.1, FR-9.2 |
| `apps/web/src/features/auth/RequireAuth.tsx` | Route guard |
| Tests | Generic failure, reset flow, switching, sign-out-mid-checkout, every status badge, all three inquiry sources |

## 4. Specification

**One failure message on sign-in, always.** Render exactly one generic invalid-credentials message for every
failure — unknown identifier, wrong password, throttled. The form must not reveal which field was wrong, must not
validate the identifier's existence before submit, and must not offer "did you mean to register?" on failure.
Throttling (`T-032`) is the one case that may render differently, because the user needs to know to wait — but it
must not disclose whether the account exists. AC-2 asserts the messages are identical.

**No client-side existence checks anywhere.** An email-availability check on the registration form is an
enumeration oracle by another name. If registration with an existing email must be handled, do it server-side with
a response that does not confirm existence, and render that.

**Registration explains the verification gate.** After registering, tell the user an email is coming and that
checkout needs a verified address (FR-2.3). The verification route consumes `T-033`'s confirm endpoint and handles
expired, already-used and wrong-code identically — `T-033` returns one outcome for all three, and the UI must not
invent three messages.

**Password reset is symmetric by design.** Requesting a reset renders the same confirmation whether or not the
email exists, because `T-033` returns the same response. Do not add a "no account found" state; that would undo
the server's care.

**Account switching re-authenticates via the new token.** `T-033` mints a new token; store it, refresh the session,
and re-fetch scoped data. Switching must not leave the previous account's cart or history on screen — a stale view
of another account's data is a privacy failure even when the token is correct.

**FR-2.7's sign-out prompt is a real requirement, not a nicety.** Signing out mid-checkout must offer to resume
checkout or return home, and the cart must survive — `T-034` guarantees the server side; the client must not clear
local cart state on sign-out. Assert it.

**History renders every status, including the handoff.** Use `T-038`'s label mapping. A test iterating the eight
statuses guarantees a new one cannot render blank. Carrier and tracking appear only from `SHIPPED` onward — absent
means the fields are not rendered at all, not rendered empty.

**History totals come from the server** and are not recomputed. `T-038` AC-7 makes the total stable under later
price changes; a client that recomputes from current prices would reintroduce the BR-2 violation at the last
possible layer.

**Inquiries work for anonymous visitors.** FR-9.1 allows an inquiry from the catalog or product page without
sign-in, so the form collects an email and validates it. Three entry points — catalog, product, handoff — set the
source `T-038` records. Show the reference the server returns.

**Guarded routes must not flash protected content** while the token is being resolved, and an expired token routes
to sign-in preserving the intended destination.

## 5. Acceptance criteria

1. `pnpm -r run build`, `pnpm run typecheck`, `pnpm run lint` and `pnpm run test` pass from the repo root.
2. Unknown identifier and wrong password render **identical** messages — asserted by comparing rendered text.
3. No client-side email-existence or availability check exists — asserted by grep and by inspection of the
   registration form.
4. Reset request renders the same confirmation for a known and an unknown email.
5. Verification failures (expired, reused, wrong code) render one message.
6. Account switching replaces the token, re-fetches scoped data, and leaves no previous-account data on screen.
7. Signing out mid-checkout offers resume-or-home and the cart is still present afterwards.
8. All eight `OrderStatus` values render a label — asserted by iterating them.
9. Carrier and tracking are not rendered for a pre-`SHIPPED` order, and are rendered for a shipped one.
10. History totals are taken from the server payload, not recomputed — asserted with a payload whose total differs
    from a naive line sum.
11. An anonymous inquiry submits successfully and displays the returned reference; all three sources are covered.
12. A guarded route renders no protected content before the token resolves, and redirects preserving the
    destination.

## 6. Verification

```
cd <repo>
pnpm install --frozen-lockfile && pnpm -r run build && pnpm run typecheck && pnpm run lint && pnpm run test
grep -rniE 'email.*(exists|available|taken)|checkEmail' apps/web/src/routes/auth   # expect nothing
grep -rniE 'no account|not registered|unknown email|did you mean' apps/web/src/routes/auth   # expect nothing
grep -rn 'reduce' apps/web/src/routes/orders   # expect no client-side total
```

Expected: all green; no existence checks; no account-revealing copy; no recomputed totals.

## 7. Out of scope

Checkout itself (`T-054`). Staff screens (`T-056`). Inquiry replies or a message thread — no FR specifies them.
Profile editing beyond what registration collects. Social sign-in, MFA. Address book management.

## 8. Hazards

- **Distinguishing "unknown email" from "wrong password" in the UI** defeats FR-2.2 entirely while every server
  test keeps passing. This is the highest-value assertion in the task.
- An email-availability check on registration is the same oracle, and it is a standard, well-intentioned feature.
- "No account found" on password reset undoes `T-033`'s symmetric response.
- Leaving the previous account's cart or history rendered after switching shows one customer another's data.
- Clearing local cart state on sign-out breaks FR-2.7 even though the server kept the cart.
- Recomputing history totals from live prices is the last place BR-2 can be violated, and it is customer-visible.
- Flashing protected content before the guard resolves leaks data on every slow connection.
- Requiring sign-in for an inquiry blocks FR-9.1's catalog path.

## 9. On completion

Mark the T-055 row done in [`README.md`](README.md). Record how throttling is surfaced without disclosing account
existence — it is the one place FR-2.2 permits a different message and the reasoning should not be re-derived.
