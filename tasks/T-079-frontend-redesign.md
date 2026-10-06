# T-079 — Frontend redesign: a 2026 visual language for `web` and `admin-web`

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then work on a branch. **If anything below
> disagrees with [`docs/architecture.md`](../docs/architecture.md), the document wins.** Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#3 — Phase 3 — Frontends](https://github.com/rednavis/metal-desk/issues/3)

**This task:** no child issue was opened; the work is tracked by pull request
[#107](https://github.com/rednavis/metal-desk/pull/107).

**Milestone:** M3 Frontends · **Id:** taken from the reserved `T-079`…`T-089` range, not a split of an existing task.

**Preconditions** — `T-051` … `T-056` merged (both apps exist and are tested).

**Goal** — Give both SPAs one modern, consistent look, modelled on the metals-api.com
[login](https://metals-api.com/login) and [symbols](https://metals-api.com/symbols) pages, **without changing
behaviour, routes, API calls or the accessible names the tests rely on.**

## 1. Why this task exists

`T-051`…`T-056` delivered working screens on a bare stylesheet: system fonts, hairline borders, a single blue, a
header that was a row of links. Customers and staff saw two apps that looked unrelated and a storefront that did not
read as a precious-metals desk. The BRD asks for light and dark themes (FR-1.8) and an accessible, localised
storefront; this task makes the visuals match that bar.

It also pays down two kinds of debt found on the way: a table style copied under a second name, and CSS classes that
components used but no stylesheet defined.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Colour is never the only carrier of meaning (direction of a price change, stock, status) | `T-052`, FR-8 |
| Theme is chosen on `<html data-theme>`; components read tokens and never name a theme | `apps/web/src/theme/` |
| Every user-facing string is an i18n key in `en` **and** `de` (`react/jsx-no-literals`) | `T-051` |
| `admin-web` has no theme switch; it follows the device | this task |
| The pnpm apps are separate packages; no shared UI package yet | `.github/scripts/affected.mjs` |
| No legacy code is copied; the reference sites are looked at, not reused | `CONTRIBUTING.md` |

## 3. Deliverables

| Path | What |
|---|---|
| `apps/web/src/theme/tokens.css` | Palette, surfaces, shadows, metal colours (`--metal-*`, `[data-metal]`), base typography; light and dark |
| `apps/web/src/ui/` | `Layout` (utility bar, sticky header, footer, skip link), `BrandMark`, restyled kit, `hideLabel` on `Field` |
| `apps/web/src/routes/` | `AppShell`, `home/HomeRoute` + `home.css` (hero), `auth/AuthFrame` (centred card for sign-in, registration, reset) |
| `apps/web/src/features/` | Restyled catalog, cart, market-data and checkout stylesheets |
| `apps/web/src/i18n/messages/{en,de}.ts` | `a11y.skipToContent`, `footer.*`, `home.hero.*`, `home.feature.*` |
| `apps/admin-web/src/ui/` | `tokens.css` (dark theme via `prefers-color-scheme`), sidebar `Layout`, `BrandMark`, `icons`, restyled kit |
| `apps/admin-web/src/routes/` | `AppShell` (sidebar + top bar), split-screen `LoginRoute`, overview tiles, status pills |
| `apps/*/public/favicon.svg`, `index.html` | Favicon, `theme-color`, `color-scheme` |

## 4. Specification

**Design language** (same in both apps):

- Indigo accent `#4f46e5` (`#818cf8` in dark) for everything interactive; slate neutrals for surfaces and text.
- The "metal" gradient (deep indigo → gold) and the rainbow header rule are **brand moments only**: the header rule,
  the home hero, the admin login. Never behind data.
- Cards: 1 rem radius, soft shadow. Tables live in cards with uppercase column headers.
- Status is a coloured pill **with a dot and a text label**; price direction keeps its glyph and screen-reader text.
- Motion is decoration: everything animated respects `prefers-reduced-motion`.
- Fonts: Inter if installed, else `system-ui`. **No web font is downloaded** (no third-party request at load).

**Behaviour that must not change:** routes, query keys, API contracts, the `data-testid`s, button and link names, the
`md-sr-only` and `md-change--*` classes the tests query.

**Refactoring in scope:** `md-cart-table` is replaced by `md-table`; duplicated dialog/status rules leave `cart.css`;
`md-login`, `md-acknowledge` and `md-visually-hidden` are defined or replaced; the stray `md-sr-only` copy in the
reduced-motion block goes; the duplicate `react-router` import in `admin-web`'s shell is merged.

**Out of scope:** a shared `ui` package (needs `affected.mjs` to learn about non-app workspaces), a theme switch in
`admin-web`, new screens, any backend change.

## 5. Acceptance criteria

- [x] `pnpm run test` — every existing test passes unchanged.
- [x] `pnpm run lint`, `typecheck`, `format:check` clean; `pnpm -r run build` succeeds.
- [ ] Home, catalog, sign-in, checkout and order screens render in light and dark, and at 420 px width without
      horizontal scroll.
- [ ] `admin-web` login, overview, tiers, quotes and orders render in light and dark.
- [x] Every new string exists in `en` and `de`.
- [x] No request to a third-party host at page load.

## 6. Verification

```bash
pnpm install --frozen-lockfile
pnpm run format:check && pnpm run lint && pnpm run typecheck && pnpm run test
pnpm -r run build
# with the backend up (docker compose up -d; the two bootRun tasks) and both dev servers:
pnpm --filter web run dev           # http://localhost:5173
pnpm --filter admin-web run dev     # http://localhost:5174  (admin / admin)
```

## 7. Notes for whoever picks up the follow-ups

- The brand mark and token files are **copies** in the two apps. Extracting `packages/ui` means adding it to
  `pnpm-workspace.yaml` *and* teaching `.github/scripts/affected.mjs` that a non-app package affects every app.
- `admin-web` keeps its token in memory, so a reload returns to the login screen (unchanged by this task).
