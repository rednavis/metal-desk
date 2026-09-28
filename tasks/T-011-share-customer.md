# T-011 — `libs/share`: Customer, Address and AuthCredential

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#12](https://github.com/rednavis/metal-desk/issues/12)

**Milestone:** M1 Domain model and libs · **Estimate:** 75 min

**Preconditions** — `T-010` merged. The typed identifiers and the exception taxonomy must exist.

**Goal** — Model the customer side of [Architecture §3](../docs/architecture.md#3-domain-model):
`Customer` with its delivery and billing addresses and its authentication credential, shaped so that
`services/api`'s auth and checkout flows have nothing left to invent.

## 1. Why this task exists

`Customer` is referenced by `Order`, by the auth flows (FR-2.x) and by checkout step 1 (FR-4.1). Every
one of those would otherwise grow its own notion of "the customer's address", and the checkout form
would end up the de-facto schema — which is how a delivery address acquires a free-text country field
and tier lookup (FR-5.1) stops working.

`AuthCredential` is separated from `Customer` deliberately: FR-2.5 lets one authenticated principal
reach several accounts, so credential and customer are not one-to-one.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| `Customer ──┬── Address (delivery / billing) └── AuthCredential` | [Architecture §3](../docs/architecture.md#3-domain-model) |
| Sign-in identifier is email **or** phone | [FR-2.1](../docs/business-requirements.md#72-authentication) |
| Registration collects "the minimum viable profile", email-verified before checkout | [FR-2.3](../docs/business-requirements.md#72-authentication) |
| One principal, several accessible accounts | [FR-2.5](../docs/business-requirements.md#72-authentication) |
| Mandatory address fields: street, city, country, postal code; optional company name/address | [FR-4.1](../docs/business-requirements.md#74-checkout--step-1-customer--delivery-data) |
| Address country must resolve to a delivery `Region` | [FR-5.1](../docs/business-requirements.md#75-delivery-tiering--the-manager-handoff-rule) |
| Platform never stores raw payment instrument data | [Architecture §3](../docs/architecture.md#3-domain-model), NFR section of the BRD |

## 3. Deliverables

All under `libs/share/src/main/java/com/rednavis/metaldesk/share/domain/customer/`:

| Path | What |
|---|---|
| `Customer.java` | Aggregate root: id, name, email, phone, addresses, verification state |
| `Address.java` | Street, city, country, postal code, optional company name and company address |
| `AddressKind.java` | `DELIVERY` / `BILLING` |
| `AuthCredential.java` | Identifier + password hash reference + credential state |
| `AuthIdentifier.java` | The email-or-phone sign-in identifier (FR-2.1) |
| `EmailAddress.java`, `PhoneNumber.java` | Validated value types |
| `VerificationState.java` | `UNVERIFIED` / `VERIFIED`, with the rule that checkout requires `VERIFIED` |
| `package-info.java` | Package Javadoc, including the FR-2.5 one-to-many note |

## 4. Specification

**`Customer` is a record with a validating canonical constructor.** Mandatory: `CustomerId`, name,
`EmailAddress`. Optional: `PhoneNumber`. Addresses are held as an immutable `List<Address>` plus an
`AddressKind` on each, **not** as two fields — a customer with three saved delivery addresses is
ordinary, and FR-3.5 pre-fills "their saved delivery profile". Expose
`primaryAddress(AddressKind)` returning `Optional`.

**`Address.country` is not free text.** It is whatever type `T-010` chose for `Region`, or a country
code that resolves to one. Tier lookup binds on region (FR-5.1); an address whose country cannot resolve
is a `ValidationException` at construction, not a checkout-time surprise.

**`AuthCredential` stores a hash reference, never a password.** Fields: `AuthIdentifier`, an opaque
`passwordHash` string, and a state. Give it **no** `verify(String plaintext)` method — password
verification needs an encoder, which is a Spring concern and `libs/share` has no Spring dependency
(`T-010` §5.7). Document that the encoder lives in `services/api` (`T-032`).

**`AuthIdentifier` normalises.** FR-2.1 accepts email or phone; FR-2.2 requires a *generic*
invalid-credentials message that never reveals which field was wrong, so the two cases must be
indistinguishable downstream. Model it as a sealed interface with `Email` and `Phone` implementations,
or as a record carrying a kind — either way, expose a single `normalised()` used as the lookup key, and
document that a failed lookup and a failed password check must produce the same outcome.

**`VerificationState`** exists because FR-2.3 makes the account unusable for checkout until the email is
verified. Put that rule in the type as a `canCheckout()` predicate with the FR cited in its Javadoc, so
`T-035` does not re-derive it.

**No Spring, no Lombok, no persistence annotations.** Same constraint as `T-010`.

## 5. Acceptance criteria

1. `./gradlew :libs:share:build` is `BUILD SUCCESSFUL`.
2. `Customer` rejects a null id, a blank name and a null email; accepts a null phone.
3. `Address` rejects a blank street, city or postal code, and rejects a country that does not resolve to
   a `Region`.
4. `AuthCredential` exposes no method taking a plaintext password — asserted reflectively.
5. `AuthIdentifier.normalised()` is case-insensitive for email and strips formatting for phone; an
   `Email` and a `Phone` that normalise to different keys are not `equals`.
6. `VerificationState.UNVERIFIED.canCheckout()` is false and `VERIFIED.canCheckout()` is true.
7. `primaryAddress(DELIVERY)` on a customer with no delivery address returns `Optional.empty()`, not null.
8. No Spring artifact on `:libs:share`'s compile classpath.

## 6. Verification

```
cd <repo>
./gradlew :libs:share:build
grep -rn 'password' libs/share/src/main/java --include=*.java -i   # expect only hash-reference mentions
./gradlew :libs:share:dependencies --configuration compileClasspath | grep -i spring   # expect nothing
grep -rn 'String country' libs/share/src/main/java   # expect nothing
```

Expected: `BUILD SUCCESSFUL`; no plaintext-password API; no Spring; country is a typed field.

## 7. Out of scope

Password hashing, JWT issuance and throttling (`T-032`). The email-verification code lifecycle
(`T-033`) — this task only models the resulting state. Account switching mechanics (`T-033`). Mongo
documents and repositories (`T-030`).

## 8. Hazards

- **Two fixed address fields** (`deliveryAddress`, `billingAddress`) look simpler and then block FR-3.5's
  saved profile the moment a customer has two delivery addresses. The list plus kind is the shape.
- Putting `verify(plaintext)` on `AuthCredential` drags Spring Security into `libs/share` and every
  module that depends on it. Resist it.
- Revealing which of identifier or password failed — even through two different exception types — breaks
  FR-2.2. One outcome, one message.
- A free-text country field will pass every test in this task and fail in `T-016` when tier lookup needs
  a region. Type it now.

## 9. On completion

Mark the T-011 row done in [`README.md`](README.md). Note in the Notes column how `AuthIdentifier` was
modelled, since `T-032` and `T-033` both consume it.
