# T-018 — `libs/payments`: the gateway provider on WireMock

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or an [ADR](../docs/adr/), that document wins** —
> open an issue rather than implementing either version. Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#19](https://github.com/rednavis/metal-desk/issues/19)

**Milestone:** M1 Domain model and libs · **Estimate:** 2 h

**Preconditions** — `T-017` merged. The SPI, `PaymentOutcome` and `DeclineReason` must exist.

**Goal** — Implement `GatewayProvider` — the adapter covering card, bank debit, bank redirect, bank
transfer and saved wallet — with every outbound HTTP call intercepted by WireMock, and canned responses for
the success, decline and timeout paths.

## 1. Why this task exists

[ADR-0002](../docs/adr/0002-mocked-external-dependencies.md) makes this non-negotiable, and
[Architecture §4](../docs/architecture.md#4-payments-as-a-provider-abstraction) says what it buys:

> every adapter's outbound HTTP call is intercepted by WireMock with canned responses for the success,
> decline, and timeout paths, so the full checkout flow — including failure handling (FR-6.3) — is
> exercised in CI without a real payment account.

The timeout path is the one that matters most and is usually skipped. FR-6.3's guarantee that nothing is
lost on failure is only proved if a test actually makes the provider time out, and that is impossible
against a real sandbox and trivial against WireMock.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Gateway group = card, bank debit, bank redirect, bank transfer, saved wallet | [FR-6.1](../docs/business-requirements.md#76-checkout--step-2-payment-method), [Architecture §4](../docs/architecture.md#4-payments-as-a-provider-abstraction) |
| Success, decline **and timeout** are all stubbed | [Architecture §4](../docs/architecture.md#4-payments-as-a-provider-abstraction) |
| Every external dependency mocked at the boundary | [ADR-0002](../docs/adr/0002-mocked-external-dependencies.md) |
| Failure returns the user to payment selection, nothing lost | [FR-6.3](../docs/business-requirements.md#76-checkout--step-2-payment-method) |
| No real credentials or sandbox keys, ever | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |
| Fixtures are synthetic and small | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules), [Modernization Plan](../docs/modernization-plan.md) |
| Non-blocking client | [Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack) |

## 3. Deliverables

Under `libs/payments/src/main/java/com/rednavis/metaldesk/payments/gateway/`:

| Path | What |
|---|---|
| `GatewayProvider.java` | The `PaymentProvider` implementation |
| `GatewayClient.java` | The thin non-blocking HTTP client, base URL injected |
| `GatewayConfiguration.java` | Base URL, timeout, retry budget — all externally configured |
| `GatewayRequest.java`, `GatewayResponse.java` | The wire DTOs, package-private if possible |
| `GatewayDeclineMapper.java` | Vendor decline code → `DeclineReason` |

Under `libs/payments/src/test/java/com/rednavis/metaldesk/payments/gateway/` and
`src/test/resources/wiremock/gateway/`:

| Path | What |
|---|---|
| `GatewayProviderTest.java` | The three-path test: captured, declined, timeout |
| `GatewayDeclineMapperTest.java` | Every mapped code, plus the unmapped fallback |
| `__files/` and `mappings/` | WireMock stubs: success, each decline code, a fixed delay for timeout |
| `gradle/libs.versions.toml` (modify, if needed) | The `wiremock-standalone` alias already exists — wire it into this module's test dependencies |

## 4. Specification

**Use the existing catalog alias.** `wiremock-standalone` is already declared in
`gradle/libs.versions.toml` and consumed by nothing. Add it as a `testImplementation` in
`libs/payments/build.gradle.kts`; do not add a second WireMock coordinate and do not pin a version.

**The base URL is configuration, never a constant.** `GatewayConfiguration` takes it as a value, and the
test supplies WireMock's port. Nothing in `src/main` may contain a hostname, and nothing anywhere may
contain a credential — the CI TruffleHog job scans every PR, and `CONTRIBUTING.md` forbids even a sandbox
key.

**Three stubbed paths, and the timeout is a real delay.** The success stub returns a capture confirmation
with a synthetic `ProviderReference`. The decline stubs return each vendor code the mapper handles. The
timeout stub uses WireMock's fixed delay set **above** `GatewayConfiguration`'s configured timeout, so the
client's own timeout fires. A stub that returns HTTP 504 tests error mapping, not timeout handling — both
are worth having, but only the delay proves the timeout path.

**Map the three paths onto `T-017`'s types, and get the distinction right:**

| Wire situation | `PaymentProvider` result |
|---|---|
| Capture confirmed | `PaymentOutcome.Captured` with the provider reference |
| Redirect needed (bank redirect) | `PaymentOutcome.RedirectRequired` |
| Vendor declined | `PaymentOutcome.Declined` with a mapped `DeclineReason` |
| Timeout, connection refused, unparseable body | `PaymentProviderException` |

A timeout must **not** become `Declined` — that would tell the customer their card was refused when it was
not, and it breaks FR-6.3's recovery semantics.

**`GatewayDeclineMapper` has an explicit fallback.** An unrecognised vendor code maps to
`DeclineReason.OTHER` with a safe message, and logs at warn with `@Slf4j`. It must not throw: an unknown
decline code is still a decline.

**Retries are bounded and idempotent-only.** If you implement a retry, retry only on transport failure,
never on a decline, with a small fixed budget, and document it. Do not retry an authorisation that may have
succeeded — a duplicate charge is worse than a failed checkout.

**Fixtures are synthetic and small.** Amounts and references are obviously fake. No real card test numbers,
no copied vendor response payload.

## 5. Acceptance criteria

1. `./gradlew :libs:payments:build` is `BUILD SUCCESSFUL`.
2. `GatewayProvider.supports()` returns true for exactly the five gateway methods and false for the wallet
   and invoice methods.
3. The success stub yields `Captured` carrying the stub's `ProviderReference`.
4. Each decline stub yields `Declined` with the expected `DeclineReason`; an unmapped code yields `OTHER`
   and does not throw.
5. The delay stub yields `PaymentProviderException` — **not** `Declined` — and the test asserts the type.
6. `grep` finds no hostname, API key, bearer token or `-----BEGIN` anywhere under `libs/payments/`.
7. No version literal in `libs/payments/build.gradle.kts`; WireMock comes from the catalog alias.
8. Nothing in the adapter blocks the calling thread — no `.block()`, no `Thread.sleep` outside tests.
9. Test fixtures contain no real card number and no copied vendor payload.

## 6. Verification

```
cd <repo>
./gradlew :libs:payments:build :libs:payments:test
grep -rniE 'api[_-]?key|secret|bearer |-----BEGIN|https?://[a-z]' libs/payments/src/main   # expect nothing
grep -rn '\.block()' libs/payments/src/main   # expect nothing
grep -rn 'wiremock' libs/payments/build.gradle.kts
grep -rnE '[0-9]{13,19}' libs/payments/src/test/resources   # expect nothing card-shaped
```

Expected: `BUILD SUCCESSFUL`; no secrets or hostnames in `src/main`; no blocking call; WireMock via the
catalog alias; no card-length digit strings in fixtures.

## 7. Out of scope

The wallet and invoice providers (`T-019`). Choosing between providers at checkout (`T-037`). BR-9's
high-value restriction (`T-037`). Webhooks and out-of-band confirmation callbacks — `confirm()` is
implemented, the HTTP endpoint that receives the callback is `T-037`. Running WireMock as a standalone
process for local dev (`T-041`).

## 8. Hazards

- **Mapping a timeout to `Declined`** is the defect this task exists to prevent. It is a one-line mistake
  in an exception handler and it lies to the customer.
- Retrying an authorisation on timeout can double-charge. If in doubt, do not retry.
- A `504` stub looks like a timeout test and is not. Use WireMock's delay.
- Committing a vendor's documented test card number is still committing an external artifact and will trip
  a reviewer if not the secret scanner. Generate synthetic values.
- Hard-coding WireMock's port in `src/main` couples production code to a test fixture.
- A mapper that throws on an unknown code converts a recoverable decline into a 500.

## 9. On completion

Mark the T-018 row done in [`README.md`](README.md). Record the retry decision and the configured timeout in
its Notes column — `T-037` and `T-041` both depend on the timeout being reachable in a test.
