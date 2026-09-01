# T-051 — `apps/web`: language, currency and theme switching

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with the
> [business requirements](../docs/business-requirements.md), that document wins** — open an issue rather
> than implementing either version. Update this task's row in [the ledger](README.md) in the same pull
> request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#36](https://github.com/rednavis/metal-desk/issues/36)

**Milestone:** M3 Frontends · **Estimate:** 3 h

**Preconditions** — `T-050` merged. `T-032`/`T-033` merged if the persisted preference is stored server-side.

**Goal** — Implement [FR-1.6](../docs/business-requirements.md#71-home--market-data),
[FR-1.7](../docs/business-requirements.md#71-home--market-data) and
[FR-1.8](../docs/business-requirements.md#71-home--market-data): theme switching that persists for a signed-in
user, language switching that re-renders all content, and currency switching that recomputes prices, taxes and
totals.

## 1. Why this task exists

These three are grouped because they are one mechanism — a user preference that affects every screen — and because
two of them have consequences deeper than the UI.

**FR-1.6 persists across sessions for a signed-in user.** That means a server-side preference, not just
`localStorage`, and therefore an endpoint. Building it after the screens exist means retrofitting a provider
through every component.

**FR-1.8 is not a formatting change.** "prices, taxes, and totals **recompute** in the selected currency" — which
needs a rate, and `T-013` deliberately declined to implement conversion, documenting that "switching display
currency needs a rate from outside the domain". This task has to confront that: either the server converts, or the
client does with a server-supplied rate. **It is a real gap in the specification and must be raised, not papered
over.**

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Theme switch; persists across sessions for a signed-in user | [FR-1.6](../docs/business-requirements.md#71-home--market-data) |
| Language switch; all catalog, cart and transactional content re-renders | [FR-1.7](../docs/business-requirements.md#71-home--market-data) |
| Currency switch; prices, taxes and totals recompute | [FR-1.8](../docs/business-requirements.md#71-home--market-data) |
| Transactional mail is rendered in the customer's locale | [FR-6.2](../docs/business-requirements.md#76-checkout--step-2-payment-method), `T-020` |
| `Money` carries a currency; conversion is out of the domain | `T-010`, `T-013` |
| Prices derive through `T-013` on the server | [BR-3](../docs/business-requirements.md#8-business-rules) |

## 3. Deliverables

| Path | What |
|---|---|
| `apps/web/src/preferences/PreferencesProvider.tsx` | The single context holding theme, locale and currency |
| `apps/web/src/preferences/usePreferences.ts` | The hook every screen uses |
| `apps/web/src/preferences/persistence.ts` | Local persistence plus the signed-in sync |
| `apps/web/src/i18n/` | The i18n setup and message catalogues for at least two locales |
| `apps/web/src/i18n/format.ts` | Money, date and number formatting bound to the active locale and currency |
| `apps/web/src/theme/` | Theme tokens and the light/dark application mechanism |
| `apps/web/src/ui/PreferenceSwitcher.tsx` | The three controls |
| `services/api/.../account/PreferencesController.java` | The server-side preference endpoint — see §4 |
| Tests | Persistence for signed-in and anonymous users, re-render on switch, currency recomputation |

## 4. Specification

**One provider, three preferences, no component-local copies.** A single context. Every price, date and number in
the app formats through `i18n/format.ts` bound to that context — a component that calls
`Intl.NumberFormat` directly will not update on a switch and will be missed in review. Assert this with a grep in
§6.

**FR-1.6's persistence needs both halves.** Anonymous: persist locally so the choice survives a reload. Signed in:
persist server-side so it survives across devices and sessions — that is what "persists across sessions" means for
an account. Add the endpoint to `services/api` under the account package (it is small, and it belongs with the
customer, not in a new module). On sign-in, the server preference wins over the local one; document that rule,
because the opposite choice is equally defensible and the two must not fight.

**FR-1.7 must cover transactional content, not just chrome.** The requirement says "all catalog, cart, and
transactional content". Catalog product names come from the server, so decide and record: are product names
localized server-side, or is the catalogue single-language with only UI chrome translated? The BRD does not say.
**Implement UI chrome plus any server-supplied localized field that exists, state the limitation explicitly, and
raise it on issue #3** — claiming full content localization without server-side translated catalogue data would be
false.

Emails are already localized server-side by `T-020`. Make sure the locale the customer selects here is the locale
sent with requests that trigger mail, so FR-6.2 and FR-8.1 actually use it. That wiring is the easiest part to
forget and the most visible when missed.

**FR-1.8 is the hard one. Resolve it deliberately.** `T-013` produces a `SellablePrice` in one currency. To show
another, something must convert. Options:

- **(a)** the server converts and returns prices in the requested currency — the client stays a formatter, totals
  always agree with what will be charged;
- **(b)** the client converts using a server-supplied rate — cheaper to build, and it risks showing a total that
  differs from the charged amount.

**(a) is strongly preferred**, because `T-037` AC-9 requires the overview total to equal the charged amount, and a
client-side conversion makes that assertion meaningless. Whichever you choose: the *settlement* currency must be
unambiguous on the checkout overview and the confirmation, so a customer browsing in one currency and paying in
another is never surprised. If no rate source exists in this build, implement a fake rate provider consistent with
[ADR-0002](../docs/adr/0002-mocked-external-dependencies.md) and say so plainly in the UI copy and the ledger —
**do not invent plausible-looking rates and present them as real.**

**Theme is tokens, not a class sprinkled per component.** Define tokens once and switch at the root. Respect the
OS preference as the initial default before any stored choice.

## 5. Acceptance criteria

1. `pnpm -r run build`, `pnpm run typecheck`, `pnpm run lint` and `pnpm run test` all pass from the repo root.
2. Switching theme, language or currency re-renders affected content without a page reload — one test each.
3. An anonymous user's choices survive a reload.
4. A signed-in user's theme persists across a simulated new session, via the server endpoint.
5. On sign-in with a differing local and server preference, the documented winner applies — asserted.
6. No component calls `Intl.NumberFormat`, `Intl.DateTimeFormat` or `toLocaleString` directly outside
   `i18n/format.ts` — asserted by grep.
7. Switching currency changes displayed prices, taxes **and** totals — all three asserted, not just unit prices.
8. The selected locale is sent on requests that trigger transactional mail — asserted at the client layer.
9. The initial theme follows the OS preference when nothing is stored.
10. Every user-facing string in the app comes from a message catalogue — asserted by a lint rule or a test that
    fails on a hard-coded literal in a rendered position.
11. If conversion rates are faked, the UI says so and the ledger records it.

## 6. Verification

```
cd <repo>
pnpm install --frozen-lockfile && pnpm -r run build && pnpm run typecheck && pnpm run lint && pnpm run test
grep -rnE 'Intl\.(NumberFormat|DateTimeFormat)|toLocaleString' apps/web/src | grep -v 'i18n/format.ts'   # expect nothing
ls apps/web/src/i18n
grep -rn 'PreferencesProvider' apps/web/src/main.tsx
```

Expected: all green; no direct `Intl` use outside the formatter; at least two message catalogues; the provider
mounted at the root.

## 7. Out of scope

`apps/admin-web` preferences — staff tooling has no FR requiring them; note the omission. Right-to-left layout.
Translating `docs/`. Server-side rendering. Real foreign-exchange rates and a rate provider contract. Localized
product catalogue **data** (raise it, do not build it).

## 8. Hazards

- **Client-side currency conversion** can show a total that differs from the charged amount, which is the one
  discrepancy customers escalate. Option (a) avoids it structurally.
- Presenting invented conversion rates as real is a correctness and honesty failure, not a placeholder.
- A component formatting money directly will silently ignore a currency switch and will pass review because it
  looks right in the default currency.
- `localStorage`-only persistence fails FR-1.6's "across sessions" for a signed-in user on a second device.
- Letting the local preference overwrite the server preference on every sign-in makes the server value unreachable.
- Forgetting to send the locale with mail-triggering requests leaves FR-6.2's localized invoice on the default
  locale, and it will be found by a customer.
- Claiming FR-1.7 is met when only chrome is translated overstates the coverage.

## 9. On completion

Mark the T-051 row done in [`README.md`](README.md). Record the conversion approach, the sign-in precedence rule,
and the catalogue-localization limitation, and raise both BRD gaps on
[issue #3](https://github.com/rednavis/metal-desk/issues/3).
