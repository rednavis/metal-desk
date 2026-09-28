# T-039 — `services/pricing-bridge`: the fake feed and the spot→sellable subscription

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or an [ADR](../docs/adr/), that document wins** —
> open an issue rather than implementing either version. Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#32](https://github.com/rednavis/metal-desk/issues/32)

**Milestone:** M2 Services and admin API · **Estimate:** 3 h

**Preconditions** — `T-013` merged (`PriceDerivation`, `ReferencePrice`). `T-030` merged if prices are persisted.

**Goal** — Give `services/pricing-bridge` (port 8083) its reason to exist: a long-lived market-data subscription
with a fake in-process feed for local dev and CI, and the spot→sellable computation that
[BR-3](../docs/business-requirements.md#8-business-rules) defines — reusing `T-013`, never reimplementing it.

## 1. Why this task exists

[Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack) names this service as the reason
the whole platform is reactive:

> `pricing-bridge`'s market-data connection is a long-lived streaming subscription, and a thread-per-request
> blocking model wastes a thread per idle subscriber — the container that most needs backpressure-aware I/O gets
> it for free.

So this is the one service where the reactive choice must be visibly justified: a genuine stream with
backpressure, not a scheduled poll wrapped in `Mono.just`.

[ADR-0002](../docs/adr/0002-mocked-external-dependencies.md) and the
[plan](../docs/modernization-plan.md) both require the fake: "a fake market-data source for local dev/CI (no
external feed dependency to build or test)".

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Long-lived streaming subscription; backpressure-aware | [Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack) |
| Spot → sellable price computation | [Plan Phase 3](../docs/modernization-plan.md), [BR-3](../docs/business-requirements.md#8-business-rules) |
| Fake market-data source for dev and CI | [ADR-0002](../docs/adr/0002-mocked-external-dependencies.md), [Plan Phase 3](../docs/modernization-plan.md) |
| Reference prices refresh on a short interval; direction and magnitude of last change | [FR-1.1](../docs/business-requirements.md#71-home--market-data) |
| Deployment may be always-on Cloud Run **or** a scheduled job, depending on the feed | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Port 8083 | `CLAUDE.md` |
| No real credentials; no external account needed to build or test | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |

## 3. Deliverables

Under `services/pricing-bridge/src/main/java/com/rednavis/metaldesk/pricingbridge/`:

| Path | What |
|---|---|
| `feed/MarketDataFeed.java` | The port: `Flux<ReferencePriceTick>` |
| `feed/ReferencePriceTick.java` | Metal, price, observation instant, source |
| `feed/fake/FakeMarketDataFeed.java` | The in-process generator — deterministic when seeded |
| `feed/fake/FakeFeedProperties.java` | Interval, metals, starting prices, volatility, seed |
| `subscription/SubscriptionManager.java` | Owns the long-lived subscription, retry and backpressure policy |
| `pricing/SellablePriceService.java` | Applies `T-013`'s `PriceDerivation` per tick |
| `web/ReferencePriceController.java` | The read surface `services/api` (`T-031`) consumes |
| `web/SellablePriceController.java` | Derived prices per product or category |
| Tests | Determinism, backpressure, reconnect, and the "same formula as `T-013`" assertion |

## 4. Specification

**`MarketDataFeed` returns a `Flux`, and the fake is a real stream.** `FakeMarketDataFeed` emits on an interval
with a bounded random walk from configured starting prices. **Seed it**: given a fixed seed, the sequence must be
reproducible, or every test touching prices becomes flaky. Expose the seed as configuration and default it to a
fixed value in tests.

**Backpressure is handled explicitly, and the strategy is stated.** A market-data stream is inherently faster
than any consumer wants. Choose `onBackpressureLatest` — for prices, the newest value is the only interesting one
— or justify something else. **Do not use an unbounded buffer**: it turns a slow consumer into an out-of-memory
kill in Cloud Run. Write the choice and its reasoning into the Javadoc; this is the single most important
sentence in the service.

**The subscription survives failure.** `SubscriptionManager` subscribes at startup and resubscribes with bounded
exponential backoff and jitter on error or completion. It must never resubscribe in a tight loop, and it must log
each reconnect at warn with `@Slf4j`. A feed that silently stops and never reconnects leaves the storefront
showing a frozen price, which is worse than showing none — so expose the last-tick age through Actuator so
`T-079` can alert on staleness.

**Reuse `T-013`'s `PriceDerivation`. Do not reimplement BR-3.** `SellablePriceService` calls the shared function
with the tick's `ReferencePrice`, the product's specification and its `PriceRule`. `T-013` §9 flagged the
fine-weight basis as a BRD gap precisely so the two call sites cannot diverge. Assert equality against a direct
`PriceDerivation` call in a test — that assertion is the guard against a second formula appearing here.

**The read surface serves `T-031`.** `ReferencePriceController` exposes the latest price per metal plus the
previous observation, because FR-1.1 needs direction and magnitude and `T-031`'s cache is a client of this.
Agree the shape with `T-031` rather than inventing a second one.

**Deployment shape is a decision to record, not to resolve.** Architecture §7 leaves always-on Cloud Run versus
Cloud Run Job + Scheduler open, "depending on its actual market-data source". Build the always-on streaming
shape here — it is what the fake models and what Architecture §5's justification assumes — and record in the
ledger that `T-072` must not assume the job shape.

**No external feed, no credentials, no hostnames.** There is no real provider in this build. If a real-feed
adapter skeleton is added, it must be unreachable by default and contain no endpoint or key.

## 5. Acceptance criteria

1. `./gradlew :services:pricing-bridge:build` is `BUILD SUCCESSFUL`.
2. `FakeMarketDataFeed` with a fixed seed produces an identical tick sequence across two runs — asserted.
3. The feed emits on the configured interval and the service starts with no external dependency available.
4. A deliberately slow consumer does **not** grow an unbounded buffer: asserted with a backpressure test that
   observes dropped or latest-only values rather than accumulation.
5. On a feed error the subscription resubscribes with a delay that increases, and the delays are asserted to be
   non-zero and bounded.
6. Last-tick age is exposed through an Actuator endpoint or a custom health indicator, and reports stale after a
   configured threshold.
7. `SellablePriceService`'s output equals a direct `PriceDerivation.derive(...)` call for the same inputs —
   asserted, and no arithmetic on price exists in this service outside that call.
8. The reference-price endpoint returns the latest and previous observation per metal.
9. `grep` finds no hostname, API key or credential anywhere under `services/pricing-bridge/`.
10. `./gradlew :services:pricing-bridge:bootRun` serves `/actuator/health` on port 8083 with no external
    dependency.

## 6. Verification

```
cd <repo>
./gradlew :services:pricing-bridge:build :services:pricing-bridge:test
grep -rniE 'api[_-]?key|https?://[a-z]|secret' services/pricing-bridge/src/main   # expect nothing
grep -rn 'multiply\|BigDecimal' services/pricing-bridge/src/main/java/com/rednavis/metaldesk/pricingbridge/pricing/   # expect only the PriceDerivation call
grep -rn 'onBackpressure' services/pricing-bridge/src/main
./gradlew :services:pricing-bridge:bootRun &   # then: curl -s localhost:8083/actuator/health
```

Expected: `BUILD SUCCESSFUL`; no credentials; no local price arithmetic; an explicit backpressure operator; a
healthy service on 8083.

## 7. Out of scope

A real market-data provider adapter. Persisting a price history for charting — only the latest and previous
observation are required by FR-1.1. Currency conversion for FR-1.8. The storefront panel (`T-052`). Alerting
policy on staleness (`T-079`) — this task only exposes the signal. Choosing the Cloud Run deployment shape
(`T-072`).

## 8. Hazards

- **An unseeded random walk** makes every downstream price test flaky, and the flakiness will be blamed on the
  reactive code.
- An unbounded buffer on a market-data stream is a latent OOM that passes every functional test and fails under
  load in the container with the smallest memory limit.
- Reimplementing BR-3 here "because the tick is right there" creates the divergence `T-013` was written to
  prevent. AC-7 is the guard.
- A subscription that stops without reconnecting leaves a frozen price on the storefront, which customers will
  trade against. The staleness signal matters as much as the reconnect.
- Resubscribing with no backoff hammers a real feed and will get the account throttled the first time it is
  real.
- Wrapping a scheduled poll in a `Flux` satisfies the type signature and forfeits the justification Architecture
  §5 gives for the whole reactive stack.

## 9. On completion

Mark the T-039 row done in [`README.md`](README.md). Record the backpressure strategy, the reconnect policy and
the staleness threshold — `T-031` consumes the read surface, `T-072` deploys it and `T-079` alerts on it.
