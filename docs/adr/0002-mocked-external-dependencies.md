---
title: "ADR-0002: Mocked external dependencies"
parent: ADRs
nav_order: 2
---

# ADR-0002: Mock every external dependency at the boundary

**Status:** Accepted

## Context

The system in [Architecture](../architecture.md) integrates with three categories of external
system: payment providers, transactional mail, and a market-data feed. This is a public reference
repository under an open-source license — it must build, start, and pass its test suite for anyone
who clones it, with no shared credentials, no sandbox accounts to provision, and no risk of a
contributor's test run hitting a real (even sandboxed) third-party API.

## Decision

Every external HTTP dependency is intercepted by **WireMock** with canned responses covering both
success and failure paths (decline, timeout, malformed response). Anything without a wire protocol
to intercept (e.g., an in-process abstraction like mail sending) gets a fake in-process
implementation instead. This applies from the first commit of each service, not as a
later "add tests" pass — see
[Modernization Plan, Phase 3](../modernization-plan.md#phase-3--services-and-apps).

Concretely: `services/api` and `services/pricing-bridge` never call a payment or market-data
vendor's real SDK/endpoint in any test or local-dev profile; `libs/payments`'s adapters are the only
code aware that a specific vendor exists, and even that code talks to a WireMock-stubbed endpoint
in every non-production profile.

## Consequences

**Positive:**
- Zero external accounts or secrets are needed to clone, build, run, or test this repository — which
  also structurally guarantees no real credential can ever be committed by accident, because none
  exist in any development or CI profile.
- Failure-path behavior (a declined payment, a timed-out mail send) is exercised by an explicit,
  version-controlled stub, not left to whatever a real sandbox happens to do on a given day —
  making these tests both deterministic and fast.
- Swapping a mocked provider for a real one in an actual deployment is a configuration change (which
  base URL and credential the adapter points at), not a code change — the `PaymentProvider`
  interface boundary from [Architecture §4](../architecture.md#4-payments-as-a-provider-abstraction)
  is what makes that true.

**Costs, accepted deliberately:**
- Mocked responses can drift from a real provider's actual current behavior over time; this is a
  reference architecture, not an integration-certified client for any specific vendor, so that
  trade-off is acceptable here. A real deployment would still need contract or staging-environment
  tests against the real provider before going live.
