# T-033 — `services/api`: registration, email verification, password reset and account switching

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#26](https://github.com/rednavis/metal-desk/issues/26)

**Milestone:** M2 Services and admin API · **Estimate:** 3 h

**Preconditions** — `T-032` merged (the auth surface and token). `T-020` merged (`libs/mail`).

**Goal** — Complete [BRD §7.2](../docs/business-requirements.md#72-authentication): registration with a
verified email, the **reusable** verification primitive FR-2.6 requires, time-limited password reset, and
multi-account switching.

## 1. Why this task exists

[FR-2.6](../docs/business-requirements.md#72-authentication) is unusually prescriptive about reuse:

> Email verification is a reusable primitive (issue code → bind to user → confirm) used by both registration
> and quick-registration-at-checkout (§7.4).

That matters because `T-035` implements FR-4.2's "remember me" conversion at checkout, which must reuse this
primitive rather than growing a second verification flow. If this task builds verification *inside*
registration, `T-035` will copy it.

FR-2.5's account switching is the other structural piece: one principal, several accounts. `T-011` already
separated `AuthCredential` from `Customer` for it; this task makes the switch work without a full re-login.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Registration collects the minimum viable profile, validates it, sends a verification code | [FR-2.3](../docs/business-requirements.md#72-authentication) |
| Verification is a reusable three-step primitive | [FR-2.6](../docs/business-requirements.md#72-authentication) |
| Account is not usable for checkout until verified | [FR-2.3](../docs/business-requirements.md#72-authentication) |
| Password reset via a **time-limited** link to the verified email | [FR-2.4](../docs/business-requirements.md#72-authentication) |
| Switch accounts without a full re-login | [FR-2.5](../docs/business-requirements.md#72-authentication) |
| Signing out mid-checkout keeps the cart | [FR-2.7](../docs/business-requirements.md#73-cart--checkout-entry) |
| Mail goes through the `libs/mail` abstraction and its fake in dev/CI | `T-020`, [ADR-0002](../docs/adr/0002-mocked-external-dependencies.md) |
| Non-blocking; crypto off the event loop | [Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack) |

## 3. Deliverables

Under `services/api/src/main/java/com/rednavis/metaldesk/api/account/`:

| Path | What |
|---|---|
| `verification/VerificationService.java` | The FR-2.6 primitive: `issue`, `bind`, `confirm` |
| `verification/VerificationChallenge.java` | Code hash, purpose, subject, expiry, attempt count |
| `verification/VerificationPurpose.java` | `REGISTRATION`, `CHECKOUT_QUICK_REGISTRATION`, `PASSWORD_RESET` |
| `RegistrationController.java`, `RegistrationService.java` | FR-2.3 |
| `PasswordResetController.java`, `PasswordResetService.java` | FR-2.4 |
| `AccountSwitchController.java`, `AccountAccessService.java` | FR-2.5 |
| `dto/*.java` | Request and response views |
| Tests | One per flow, plus `VerificationServiceTest` covering expiry, reuse and attempt limits |

## 4. Specification

**Build `VerificationService` first, and make it purpose-agnostic.** Its API is exactly FR-2.6's three steps:
`issue(purpose, subject, EmailAddress)` returns a challenge reference and sends the mail; `bind` associates the
challenge with a user when that is a separate step; `confirm(reference, code)` validates. `T-035` will call it
with `CHECKOUT_QUICK_REGISTRATION` and must need no change here.

**Store a hash of the code, never the code.** A verification code in the database is a credential. Hash it with
the same adapter `T-032` uses, off the event loop.

**Challenges expire, are single-use, and limit attempts.** Short configurable TTL. `confirm` on an expired,
already-confirmed or attempt-exhausted challenge fails with the **same** generic outcome as a wrong code —
FR-2.2's reasoning applies here too. Log the real reason server-side; return one outcome.

**Reset links are time-limited, per FR-2.4.** Issue a `PASSWORD_RESET` challenge and mail a link containing the
challenge reference and code. Requesting a reset for an unknown or unverified email returns the **same**
success-shaped response as a known one — an enumeration oracle here undoes FR-2.2's work in the sign-in path.
Invalidate every outstanding reset challenge and, if feasible, existing tokens for that customer once the
password changes.

**Registration does not sign the user in.** FR-2.3 makes the account unusable for checkout until verified.
Create the `Customer` with `VerificationState.UNVERIFIED`, issue the challenge, and return a response that tells
the client to expect an email. Whether a token is issued at this point is your call — if it is, its
verification claim must be `UNVERIFIED` so `T-035`'s checkout gate rejects it.

**Account switching re-issues the token, it does not mutate it.** `AccountAccessService` answers "which
customers may this principal act as". Switching validates the current token, checks the target is in that set,
and mints a **new** token for the target — no session, nothing server-side, consistent with Architecture §5.
Rejecting a target outside the set is a `ConflictException` or 403, and it must be tested.

**FR-2.7's cart survival is a constraint on sign-out, not a feature here.** Sign-out must not delete the cart.
`T-034` owns cart ownership and anonymous-to-authenticated merging; this task's only obligation is to avoid
breaking it, and to state the contract it relies on.

**All mail goes through `MailSender`**, using `T-020`'s `EMAIL_VERIFICATION` and `PASSWORD_RESET` templates in
the customer's locale. Tests assert against `InProcessMailSender`, which is how the code actually gets into a
test — never log it.

## 5. Acceptance criteria

1. `./gradlew :services:api:build` is `BUILD SUCCESSFUL`.
2. `VerificationService` is callable with all three `VerificationPurpose` values and contains no
   registration-specific logic — asserted by using it for a password reset and a registration in the same test
   class.
3. Registration creates an `UNVERIFIED` customer and sends exactly one `EMAIL_VERIFICATION` mail, asserted via
   `InProcessMailSender`.
4. `confirm` with the correct code moves the customer to `VERIFIED`; a second `confirm` with the same code
   fails.
5. An expired challenge, a wrong code, and an attempt-exhausted challenge produce **identical** responses.
6. The stored challenge contains no plaintext code — asserted by reading the persisted document.
7. A reset request for an unknown email returns the same response as for a known one, and sends no mail to a
   non-existent address.
8. Completing a reset invalidates other outstanding reset challenges for that customer.
9. Switching to a permitted account returns a new token whose subject is the target; switching to a
   non-permitted account is rejected.
10. No verification code or reset code appears in any log output — asserted by capturing logs in a test.

## 6. Verification

```
cd <repo>
./gradlew :services:api:build :services:api:test
grep -rniE 'log.*\b(code|token)\b.*\{\}' services/api/src/main/java/com/rednavis/metaldesk/api/account/   # inspect each hit by hand
grep -rn 'VerificationPurpose' services/api/src/main | sort -u
grep -rn 'InProcessMailSender' services/api/src/test | head
```

Expected: `BUILD SUCCESSFUL`; no code or token interpolated into a log statement; the purpose enum used across
all three flows; mail assertions against the fake.

## 7. Out of scope

Cart ownership and anonymous-to-authenticated merge (`T-034`). FR-4.2's quick registration at checkout
(`T-035`) — it *reuses* this primitive; do not implement the checkout path here. Social or SSO sign-in. MFA.
Admin staff authentication, which is Identity-Aware Proxy (`T-076`).

## 8. Hazards

- **Verification built inside registration** guarantees `T-035` duplicates it, which is precisely what FR-2.6
  legislates against.
- Storing the plaintext code makes the database a credential store; it is also the kind of thing that survives
  into a support tool.
- A different response for "unknown email" on password reset is an account-enumeration oracle, and it sits
  right next to the sign-in path that carefully avoids one.
- Issuing a `VERIFIED`-claim token at registration lets an unverified account reach checkout, contradicting
  FR-2.3, and the failure surfaces in `T-041` rather than here.
- Logging the verification code "for local debugging" puts a live credential in Cloud Logging.
- Mutating the existing token's subject on account switch requires server-side state and breaks Architecture
  §5's stateless commitment.

## 9. On completion

Mark the T-033 row done in [`README.md`](README.md). Record the challenge TTL, the attempt limit and whether a
token is issued at registration — `T-035` reuses the primitive and `T-050` consumes these endpoints.
