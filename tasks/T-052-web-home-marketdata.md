# T-052 — `apps/web`: home and the live market-data panel

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with the
> [business requirements](../docs/business-requirements.md), that document wins** — open an issue rather
> than implementing either version. Update this task's row in [the ledger](README.md) in the same pull
> request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#37](https://github.com/rednavis/metal-desk/issues/37)

**Milestone:** M3 Frontends · **Estimate:** 2 h

**Preconditions** — `T-050` and `T-051` merged. `T-031`'s market-data endpoint and `T-039`'s feed available.

**Goal** — Build the home screen and the reference-price panel
[FR-1.1](../docs/business-requirements.md#71-home--market-data) specifies: live prices per metal on a short
refresh interval, with the direction and magnitude of the most recent change indicated visually.

## 1. Why this task exists

FR-1.1 is the only screen in the system with a live refresh, and it is the first consumer of `T-031`'s
`ReferencePriceCache` and therefore of `T-039`'s stream. Three details make it more than a table:

- the refresh interval is short (illustrative: 20 seconds), so polling behaviour, error handling and tab-visibility
  all matter;
- **direction and magnitude of the most recent change** must be shown, which requires the previous observation —
  `T-031` returns it for exactly this reason;
- `T-031` returns an **explicitly absent** delta on a cold start, and rendering that as "0.00 unchanged" would tell
  the customer the price is flat when it is unknown.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Live reference prices per metal, short polling interval | [FR-1.1](../docs/business-requirements.md#71-home--market-data) |
| Direction **and** magnitude of the most recent change, visually indicated | [FR-1.1](../docs/business-requirements.md#71-home--market-data) |
| Absent delta on cold start is not zero | `T-031` |
| Prices format through the active locale and currency | [FR-1.7](../docs/business-requirements.md#71-home--market-data), [FR-1.8](../docs/business-requirements.md#71-home--market-data), `T-051` |
| Catalog reads are public — no sign-in required | [FR-1.2](../docs/business-requirements.md#71-home--market-data), `T-032` |
| All strings from the message catalogue | `T-051` |

## 3. Deliverables

| Path | What |
|---|---|
| `apps/web/src/routes/home/HomeRoute.tsx` | The landing screen |
| `apps/web/src/features/marketdata/MarketDataPanel.tsx` | The price panel |
| `apps/web/src/features/marketdata/PriceTicker.tsx` | One metal's price, delta and direction |
| `apps/web/src/features/marketdata/useReferencePrices.ts` | The polling hook |
| `apps/web/src/features/marketdata/staleness.ts` | Stale-data detection and presentation |
| Tests | Rendering, delta direction, absent delta, stale state, polling pause |

## 4. Specification

**Polling interval is configuration with FR-1.1's illustrative 20 seconds as the default.** Do not hard-code it in
a component.

**Pause polling when the tab is hidden, and resume on focus with an immediate fetch.** A background tab polling
every 20 seconds indefinitely is a real cost on a mobile connection and produces no value. Resuming must refetch at
once rather than waiting out the interval, so a returning user never sees a minute-old price.

**Direction has three states, not two.** Up, down, and unchanged — plus a fourth, *unknown*, when `T-031` reports
no previous observation. Render unknown distinctly: no arrow, no colour, no zero. Asserting this is AC-4.

**Direction must not be conveyed by colour alone.** Green-up / red-down is invisible to a colour-blind user and to
a screen reader. Pair the colour with a glyph or arrow **and** an accessible text label that names the direction
and magnitude. This is the one accessibility requirement in this task that is cheap now and expensive to retrofit.

**Prices format through `T-051`'s formatter.** No direct `Intl` use — `T-051` AC-6 already forbids it repo-wide.

**Stale data is shown as stale.** If the last successful fetch is older than a configured threshold (a small
multiple of the interval), mark the panel stale rather than continuing to present the last value as live. `T-039`
exposes feed staleness server-side; this is the client-side counterpart, and it matters because a customer may
trade against a frozen price.

**A failed fetch keeps the last known value and shows an error affordance.** Do not blank the panel — a momentary
network error should not erase the page. Retry with backoff rather than at the polling interval.

**No sign-in required.** The panel must render for an anonymous visitor; a 401 here would mean `T-032`'s public
allowlist is wrong, and the test should make that obvious.

## 5. Acceptance criteria

1. `pnpm -r run build`, `pnpm run typecheck`, `pnpm run lint` and `pnpm run test` pass from the repo root.
2. The panel renders one row per metal with a formatted price in the active currency.
3. A rising price renders an up direction with its magnitude; a falling price a down direction — both asserted.
4. An absent delta renders as unknown: no arrow, no zero magnitude — asserted explicitly.
5. Direction is conveyed by a non-colour cue and an accessible label naming direction and magnitude.
6. Polling stops when the document is hidden and refetches immediately on becoming visible — asserted with a
   simulated visibility change.
7. A fetch failure keeps the previous values visible and shows an error affordance; retries back off.
8. Data older than the staleness threshold renders a stale indication.
9. The panel renders with no auth token present.
10. No direct `Intl` call and no hard-coded user-facing string.

## 6. Verification

```
cd <repo>
pnpm install --frozen-lockfile && pnpm -r run build && pnpm run typecheck && pnpm run lint && pnpm run test
grep -rnE 'Intl\.|toLocaleString' apps/web/src/features/marketdata   # expect nothing
grep -rn 'visibilitychange\|document.hidden' apps/web/src/features/marketdata
grep -rnE '20000|20 \* 1000' apps/web/src/features/marketdata   # expect nothing: interval is configured
```

Expected: all green; no direct formatting; visibility handling present; no hard-coded interval.

## 7. Out of scope

Price history charts — FR-1.1 asks for the latest change only, and `T-039` keeps just two observations. WebSocket
or SSE streaming to the browser; polling is what FR-1.1 describes. The catalog grid (`T-053`). Currency conversion
mechanics (`T-051`).

## 8. Hazards

- **Rendering an absent delta as 0.00 unchanged** tells the customer the price is flat when it is unknown. It is a
  one-line default and it is wrong.
- Colour-only direction fails accessibility and is invisible in a screen reader — and on a price display that is
  the whole information content.
- Polling a hidden tab forever wastes the customer's battery and the service's quota.
- Blanking the panel on a transient error makes a working page look broken.
- Continuing to present a frozen price as live is the failure mode with actual financial consequence; the staleness
  indication is not cosmetic.
- Retrying a failed fetch at the polling interval turns a backend outage into a steady load at full rate.

## 9. On completion

Mark the T-052 row done in [`README.md`](README.md). Record the polling interval and staleness threshold defaults,
so `T-039`'s server-side threshold and this one can be reconciled.
