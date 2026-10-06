# T-017 — `libs/payments`: the `PaymentProvider` SPI

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or an [ADR](../docs/adr/), that document wins** —
> open an issue rather than implementing either version. Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#18](https://github.com/rednavis/metal-desk/issues/18)

**Milestone:** M1 Domain model and libs · **Estimate:** 90 min

**Preconditions** — `T-010` and `T-016` merged. `Money`, the exception taxonomy, `PaymentMethod`,
`PaymentStatus` and `ProviderReference` must exist.

**This task blocks `T-018` and `T-019`.**

**Goal** — Define the single interface that
[Architecture §4](../docs/architecture.md#4-payments-as-a-provider-abstraction) puts between checkout
orchestration and any payment vendor, plus the request/result types it exchanges — so that swapping
WireMock for a real provider is configuration, not code.

## 1. Why this task exists

Architecture §4 states the property this interface has to deliver:

> `api` never calls a specific payment vendor's SDK directly from checkout orchestration code — it calls
> `PaymentProvider`, and a provider-specific adapter implements it. […] Swapping WireMock for a real
> provider in a real deployment is purely a configuration change, not a code change, which is the point of
> the abstraction.

That property is only true if the interface leaks nothing vendor-shaped: no HTTP status codes, no
provider-specific error strings, no redirect-URL assumptions particular to one gateway. Getting the
signature right now is cheaper than unpicking three adapters later.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| One `PaymentProvider` interface; three implementation families | [Architecture §4](../docs/architecture.md#4-payments-as-a-provider-abstraction) |
| Card, bank debit, bank redirect, bank transfer, wallet, invoice all route through it | [FR-6.1](../docs/business-requirements.md#76-checkout--step-2-payment-method) |
| Provider flow is redirect, embedded element, or invoice generation | [FR-7.2](../docs/business-requirements.md#77-checkout--steps-34-overview--payment) |
| Failure returns to payment selection with nothing lost | [FR-6.3](../docs/business-requirements.md#76-checkout--step-2-payment-method) |
| Every outbound call is mocked at the boundary | [ADR-0002](../docs/adr/0002-mocked-external-dependencies.md) |
| Never store raw instrument data | [Architecture §3](../docs/architecture.md#3-domain-model) |
| Reactive, non-blocking throughout | [Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack) |

## 3. Deliverables

All under `libs/payments/src/main/java/com/rednavis/metaldesk/payments/`:

| Path | What |
|---|---|
| `provider/PaymentProvider.java` | The interface (the existing `provider/package-info.java` documents this package — extend it) |
| `provider/PaymentIntent.java` | What checkout asks for: order reference, amount, method, return targets |
| `provider/PaymentOutcome.java` | Sealed result: `Captured`, `RedirectRequired`, `ElementRequired`, `DocumentIssued`, `Declined`, `Failed` |
| `provider/PaymentProviderException.java` | Transport/infrastructure failure, distinct from a decline |
| `provider/DeclineReason.java` | The vendor-neutral decline taxonomy |
| `provider/ProviderCapability.java` | Which `PaymentMethod`s an adapter serves |
| `libs/payments/build.gradle.kts` (modify) | Add the `libs:share` and reactive dependencies — see §4 |

## 4. Specification

**`PaymentProvider` is reactive.** Architecture §5 commits the whole stack to non-blocking types, and a
payment call is exactly the long-latency I/O that makes it worth it. Return `Mono<PaymentOutcome>` (or the
project's chosen reactive type) from `authorise(PaymentIntent)`. Also declare
`ProviderCapability capability()` and `boolean supports(PaymentMethod)`.

Keep the method count small: authorise, and a `confirm(ProviderReference)` for the redirect/element flows
that complete out of band. Do **not** add refund, capture-later or void in this task — no FR in scope
needs them, and each one is a second state machine.

**A decline is not an exception.** FR-6.3 makes payment failure an ordinary, recoverable checkout outcome.
`Declined` is a `PaymentOutcome` variant carrying a `DeclineReason`; `PaymentProviderException` is reserved
for the transport failing — timeout, connection refused, malformed response. A caller must be able to tell
"the bank said no" from "we could not reach the bank" **by type alone**, because the first returns the user
to payment selection and the second is an incident.

**`PaymentOutcome` is sealed, and its variants are the flows FR-7.2 names.** `RedirectRequired` carries a
URI the caller hands to the browser. `ElementRequired` carries an opaque client secret for an embedded
element. `DocumentIssued` carries the invoice `ProviderReference` (FR-6.2). Sealing it means `T-037` gets a
compile error when a new flow is added rather than a runtime fallthrough.

**`DeclineReason` is vendor-neutral.** Insufficient funds, instrument rejected, authentication failed,
risk-blocked, expired, and an `OTHER` carrying a safe message. An adapter maps its vendor's codes into this
enum; the enum must contain no vendor code and no HTTP status.

**`PaymentIntent` carries no instrument.** Order reference, `Money` amount, `PaymentMethod`, customer
reference, a return URI and a cancel URI. **No** card fields, no map, no `Map<String,Object> extra` — the
same prohibition as `PaymentRecord` in `T-016`, for the same reason, asserted in §5.

**`libs/payments` depends on `libs/share`, and on nothing vendor-specific.** Add
`api(project(":libs:share"))` and the reactive dependency from `gradle/libs.versions.toml`. If a reactive
alias is missing from the catalog, add it there — never pin a version in this module's build file.

## 5. Acceptance criteria

1. `./gradlew :libs:payments:build` is `BUILD SUCCESSFUL`.
2. `PaymentProvider` declares no method whose signature mentions an HTTP type, a URL string, or any vendor
   class name.
3. `PaymentOutcome` is `sealed` and permits exactly the six variants in §3.
4. `Declined` is a `PaymentOutcome`, not a `Throwable`; `PaymentProviderException` is a `Throwable` and is
   not used for declines — asserted by a test that distinguishes the two paths by type.
5. `PaymentIntent` has no component of type `Map`, and none whose name matches
   `(?i)card|pan|cvv|iban|expiry|token` — asserted reflectively.
6. `DeclineReason` contains no constant named for a vendor and no numeric HTTP code.
7. `authorise` returns a reactive type; nothing in the package blocks.
8. `./gradlew :libs:payments:dependencies --configuration compileClasspath` shows `libs:share` and no
   payment-vendor artifact.
9. No version literal appears in `libs/payments/build.gradle.kts`.

## 6. Verification

```
cd <repo>
./gradlew :libs:payments:build
grep -rniE 'card|cvv|iban|expiry' libs/payments/src/main/java/com/rednavis/metaldesk/payments/provider/PaymentIntent.java   # expect nothing
grep -rn 'sealed' libs/payments/src/main/java/com/rednavis/metaldesk/payments/provider/PaymentOutcome.java
grep -rnE '[0-9]+\.[0-9]+\.[0-9]+' libs/payments/build.gradle.kts   # expect nothing
./gradlew :libs:payments:dependencies --configuration compileClasspath
```

Expected: `BUILD SUCCESSFUL`; no instrument fields; a sealed outcome; no version literal; `libs:share` on
the classpath and nothing vendor-specific.

## 7. Out of scope

Every adapter — gateway (`T-018`), wallet and invoice (`T-019`). WireMock stubs and fixtures (`T-018`).
Refunds, partial capture, chargebacks. BR-9's value ceiling (`T-037`). Choosing a real vendor.

## 8. Hazards

- **Modelling a decline as an exception** is the single most common shape here, and it makes FR-6.3's
  "nothing already entered is lost" require a `catch` in the orchestration path for an expected outcome.
- A `Map<String,Object>` on `PaymentIntent` re-opens the instrument-data hole `T-016` closed.
- Returning a non-sealed `PaymentOutcome` means `T-019`'s invoice flow can be added without `T-037`
  noticing it has a new case to handle.
- Letting a redirect URI be a `String` rather than a `URI` invites string concatenation at the call site.
- Adding refund/capture "for completeness" doubles the adapter surface for no requirement in scope.

## 9. On completion

Mark the T-017 row done in [`README.md`](README.md). Record the reactive type chosen and any catalog alias
added, since `T-018`, `T-019` and `T-037` all build directly on this interface.
