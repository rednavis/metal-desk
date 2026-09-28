# T-035 — `services/api`: checkout step 1 — customer and delivery data

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#28](https://github.com/rednavis/metal-desk/issues/28)

**Milestone:** M2 Services and admin API · **Estimate:** 3 h

**Preconditions** — `T-033` merged (the verification primitive) and `T-034` merged (`CheckoutBasket`).

**Goal** — Implement [BRD §7.4](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data):
collect and validate the customer and delivery data, support the guest-to-account conversion FR-4.2 describes
by **reusing** FR-2.6's primitive, and require explicit privacy-policy acceptance.

## 1. Why this task exists

[FR-4.2](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data) is the requirement that
makes or breaks `T-033`'s design:

> A guest may check **"remember me"** to convert the checkout into a lightweight registration: the system
> prompts for a password, verifies the email belongs to them (reusing FR-2.6), and creates the account without
> leaving the checkout flow.

"Reusing FR-2.6" is explicit. If this task grows its own verification, the system has two code paths issuing
credentials to email addresses, and they will diverge.

[FR-4.3](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data) is equally specific and
easy to under-implement: privacy acceptance is "a distinct checkbox, not bundled into an unrelated 'I agree'
toggle". That is a server-side invariant too — the API must not accept a single combined consent flag.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Mandatory: name, email, phone, structured address (street, city, country, postal code) | [FR-4.1](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data) |
| Optional: company name/address, order note | [FR-4.1](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data) |
| Every mandatory field presence- **and** format-validated before proceeding | [FR-4.1](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data) |
| "Remember me" converts to a registration, reusing FR-2.6 | [FR-4.2](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data) |
| Privacy acceptance is a distinct, affirmative checkbox | [FR-4.3](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data) |
| Saved delivery profile pre-filled but editable | [FR-3.5](../docs/business-requirements.md#73-cart--checkout-entry) |
| Unverified accounts cannot check out | [FR-2.3](../docs/business-requirements.md#72-authentication) |
| Address country must resolve to a `Region` | `T-011`, [FR-5.1](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |

## 3. Deliverables

Under `services/api/src/main/java/com/rednavis/metaldesk/api/checkout/`:

| Path | What |
|---|---|
| `CheckoutController.java` | The step-1 endpoint (extended by `T-036`…`T-038`) |
| `CheckoutSession.java` | The server-side checkout state: basket, customer data, quote, chosen method |
| `CheckoutSessionService.java` | Create, read, advance, and the step-1 validation |
| `step1/CustomerDetails.java` | The validated FR-4.1 field set |
| `step1/ConsentRecord.java` | Privacy acceptance: accepted flag, policy version, instant |
| `step1/GuestConversionService.java` | FR-4.2, delegating to `T-033`'s `VerificationService` |
| `dto/Step1Request.java`, `dto/Step1Response.java` | Wire types |
| Tests | Validation matrix, guest conversion, consent rejection, verified-account gate |

## 4. Specification

**`CheckoutSession` is server-side state, and that is not a contradiction of Architecture §5.** Architecture
§5's statelessness applies to *authentication* — "no shared session store" for auth. A multi-step checkout has
genuine intermediate state (FR-7.1 requires going back and editing any prior step), and that state is
persisted per `T-030`, keyed by a checkout id the client holds. **State this distinction in the Javadoc**,
because it otherwise reads as a violation of the ADR.

**Validate presence and format separately, and report both.** FR-4.1 requires both. A missing city and a
malformed postal code are different messages; return a field-keyed list of violations in `T-031`'s envelope, not
the first failure. This is the endpoint a real form binds to, and a single-error response makes the form
unusable.

**The country must resolve to a `Region`.** `T-011` made this a construction-time check on `Address`. Surface it
as a field-level validation error, not a 500, and be explicit in the message that delivery is unavailable for
that country — `T-036` cannot price a region that does not exist.

**Consent is its own field with its own type.** `ConsentRecord` carries an explicit `privacyPolicyAccepted`
boolean, the policy version accepted, and the instant. The request DTO must have **no** generic `termsAccepted`
or `agreed` field that stands for several consents — AC-5 asserts this reflectively. Reject a request whose
privacy acceptance is absent or false with a 400, and never default it to true.

**Guest conversion reuses `T-033` without modifying it.** `GuestConversionService` calls
`VerificationService.issue(CHECKOUT_QUICK_REGISTRATION, ...)`, and the flow continues in checkout while the
account is created `UNVERIFIED`. The checkout must **not** block on confirmation — FR-4.2 says the account is
created "without leaving the checkout flow", and FR-2.3's gate is on the account being usable for checkout
*later*, not on this order. Document that reading explicitly; it is the one place the two requirements touch
and could be read as contradictory.

If `VerificationService` needs a new `VerificationPurpose` constant, that constant already exists from `T-033`.
If it needs anything more, that is a signal `T-033`'s primitive was not general enough — raise it rather than
patching around it.

**Signed-in unverified customers are rejected at this step.** Read the verification claim from the token
(`T-032`) and reject with a `ConflictException` → 409 carrying a code the client can act on. This is where
FR-2.3's gate lives; `T-032` deliberately did not put it on sign-in.

**Pre-fill is a separate read, not a write.** Expose the saved `primaryAddress(DELIVERY)` for FR-3.5 through a
GET; the step-1 POST always takes the full explicit field set, so an edited value cannot be silently replaced by
the saved one.

## 5. Acceptance criteria

1. `./gradlew :services:api:build` is `BUILD SUCCESSFUL`.
2. Every mandatory FR-4.1 field, omitted individually, produces a 400 naming that field — one assertion per
   field.
3. A present-but-malformed email, phone and postal code each produce a format violation distinct from a
   presence violation.
4. A single request with two violations returns both, not the first.
5. `Step1Request` has no field named `termsAccepted`, `agreed`, `accepted` or `consent` standing for more than
   one consent — asserted reflectively; the privacy field is its own boolean.
6. Privacy acceptance absent or `false` returns 400; the session does not advance.
7. A country that does not resolve to a `Region` returns a field-level 400, not 500.
8. "Remember me" creates an `UNVERIFIED` customer, issues a `CHECKOUT_QUICK_REGISTRATION` challenge via
   `T-033`'s service, sends exactly one mail, and **does not** block checkout progress.
9. A signed-in `UNVERIFIED` customer is rejected with 409 and an actionable code.
10. `GuestConversionService` contains no code-generation, hashing or mail-sending of its own — asserted by
    grep and by a test that stubs `VerificationService` and observes the delegation.

## 6. Verification

```
cd <repo>
./gradlew :services:api:build :services:api:test
grep -rniE 'termsAccepted|agreed|allConsents' services/api/src/main/java/com/rednavis/metaldesk/api/checkout/   # expect nothing
grep -rn 'VerificationService' services/api/src/main/java/com/rednavis/metaldesk/api/checkout/step1/GuestConversionService.java
grep -rniE 'randomCode|generateCode|MessageDigest|MailSender' services/api/src/main/java/com/rednavis/metaldesk/api/checkout/   # expect nothing
```

Expected: `BUILD SUCCESSFUL`; no bundled-consent field; explicit delegation to the shared verification service;
no local code generation, hashing or mail sending in the checkout package.

## 7. Out of scope

Delivery tiering and the manager handoff (`T-036`). Payment selection and execution (`T-037`). Confirmation and
notifications (`T-038`). The checkout UI and its five steps (`T-054`). Storing the privacy policy text or
versioning it beyond recording the accepted version string.

## 8. Hazards

- **Reimplementing verification inside checkout** is the specific failure FR-4.2's "reusing FR-2.6" wording
  exists to prevent. AC-10 is the guard.
- A single combined consent flag satisfies a form and violates FR-4.3, and it is legally meaningful rather than
  cosmetic.
- Returning only the first validation error makes a multi-field form require one round trip per mistake.
- Blocking checkout until the guest confirms the verification email contradicts FR-4.2's "without leaving the
  checkout flow" and will strand orders.
- Letting the saved delivery address override an edited field breaks FR-3.5's "editable".
- Treating `CheckoutSession` as forbidden by Architecture §5 leads to smuggling checkout state into the JWT,
  which is both a size problem and an integrity problem.

## 9. On completion

Mark the T-035 row done in [`README.md`](README.md). Record the FR-4.2/FR-2.3 reading — that a quick-registered
guest completes *this* order while the account stays `UNVERIFIED` — since `T-041`'s end-to-end test asserts it.
