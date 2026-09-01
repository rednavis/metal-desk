# T-050 — Frontend foundation: routing, API client and the test baseline

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md) or `CLAUDE.md`, that document wins** — open
> an issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#35](https://github.com/rednavis/metal-desk/issues/35)

**Milestone:** M3 Frontends · **Estimate:** 4 h

**Preconditions** — `T-031` merged, so there is a real API to type against. Both apps currently contain only
`src/App.tsx` and `src/main.tsx`.

**This task blocks every other M3 task.**

**Goal** — Turn two Vite scaffolds into two real applications: routing, a typed API client, shared UI primitives,
and a test runner — **without** adding a per-app ESLint config or pulling either app into the Gradle graph.

## 1. Why this task exists

`apps/web` and `apps/admin-web` are scaffolds: four `.tsx` files, no router, no test runner, no state management,
no API client. Six M3 tasks are about to build screens against them, and each would otherwise make its own choice
about data fetching and routing.

There is also a hard constraint that is easy to violate accidentally. `CLAUDE.md`:

> ESLint config is a single root `eslint.config.mjs` covering both `apps/web/src` and `apps/admin-web/src` —
> don't add a per-app config, and don't run `eslint` from inside an app directory (flat config resolution is
> root-scoped here; always run from repo root).

And the apps are **intentionally excluded** from the Gradle build graph per
[ADR-0005](../docs/adr/0005-consolidated-react-frontend.md). Never add a Gradle module for them.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| One React stack for both surfaces; `apps/admin-web` is `apps/admin`'s sole client | [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md), `CLAUDE.md` |
| Both apps stay out of the Gradle graph | [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md), `CLAUDE.md` |
| Single root `eslint.config.mjs`; run ESLint from the repo root only | `CLAUDE.md` |
| Prettier owns TS/TSX formatting; it does **not** touch `*.md` or `docs/` | `.prettierrc.json`, `.prettierignore` |
| Build is `tsc -b && vite build`; typecheck is `tsc -b --noEmit` | `apps/*/package.json` |
| `pnpm install` then `pnpm -r run build` | `CLAUDE.md` |
| `api` is on 8082, `admin` on 8081 | `CLAUDE.md` |
| Theme, language and currency are user-switchable | [FR-1.6](../docs/business-requirements.md#71-home--market-data), [FR-1.7](../docs/business-requirements.md#71-home--market-data), [FR-1.8](../docs/business-requirements.md#71-home--market-data) |

## 3. Deliverables

| Path | What |
|---|---|
| `apps/web/package.json`, `apps/admin-web/package.json` (modify) | Router, test runner, and a `test` script in each |
| `apps/web/src/routes/`, `apps/admin-web/src/routes/` | Route definitions and the route shells |
| `apps/*/src/api/client.ts` | The fetch wrapper: base URL, auth header, envelope handling |
| `apps/*/src/api/types.ts` | Types mirroring `T-031`'s DTOs — see §4 on how they are produced |
| `apps/*/src/api/errors.ts` | `T-031`'s error envelope as a typed error, with the correlation id |
| `apps/*/src/ui/` | Shared primitives: layout, button, field, empty state, error state, spinner |
| `apps/*/vite.config.ts` (modify) | A dev-server proxy to the right backend port |
| `apps/*/src/test/setup.ts`, `apps/*/src/**/*.test.tsx` | Test baseline and one real test per app |
| `apps/web/.env.example`, `apps/admin-web/.env.example` | The documented environment variables, with no secrets |
| `package.json` (root, modify) | A root `test` script running both apps' suites |

## 4. Specification

**Add a router and a test runner; decide the data layer deliberately.** Nothing is installed today. Choose and
record: a router (React Router or equivalent), Vitest plus Testing Library for tests, and either a data-fetching
library or plain `fetch` with a small hook. **Justify the data layer in the ledger** — every M3 task consumes it,
and a server-state library brings caching, retry and invalidation semantics that FR-1.1's short refresh interval
(`T-052`) will otherwise need by hand.

Add `test` and `test:run` scripts to each app and a root `test` script, so that the project has a frontend test
command at all — it currently has none.

**API types are generated or asserted, not hand-copied.** Hand-written TypeScript mirrors of `T-031`'s DTOs drift
silently and are only caught by a user. Either generate them from an OpenAPI document the services expose, or
write them by hand **and** add a contract test that fails when the shapes diverge. State which you did. Copying
without either is not acceptable — it is the frontend version of the duplicated-domain-type problem `T-021` exists
to prevent.

**The API client handles `T-031`'s envelope centrally.** One place parses the error body, surfaces the
machine-readable code and the correlation id, and turns them into a typed error. Screens then render an actionable
message with the correlation id, which is how FR-8.1's "support-actionable" reaches the user. No screen parses an
error body itself.

**Auth token handling is in the client, and the storage choice is documented.** `T-032` issues a bearer JWT.
Decide where it lives — memory, `sessionStorage`, `localStorage` — and write down the trade-off: `localStorage`
survives a reload and is readable by any XSS, memory is safer and loses the session on refresh. Handle 401 by
clearing the token and routing to sign-in. `apps/admin-web` does **not** use this token at all: `apps/admin` sits
behind Identity-Aware Proxy (`T-040`, `T-076`), so its client sends no `Authorization` header and must not
implement a login screen.

**Dev-server proxy, not CORS-by-hardcoded-URL.** Point `apps/web`'s Vite proxy at 8082 and `apps/admin-web`'s at
8081. The base URL is an environment variable with the proxy path as its default, so no port is compiled into a
bundle.

**Shared primitives, duplicated deliberately.** ADR-0005 mandates one *stack*, not one codebase. Rather than
inventing a shared package now, keep the `ui/` primitives in each app and record the duplication as a known
trade-off with the threshold at which a shared package becomes worth it. Do **not** add a third pnpm workspace
package in this task without saying why.

Include an **empty state** and an **error state** primitive from the start: FR-3.4 requires an explicit empty cart
and FR-8.1 an actionable error, and both are forgotten when the primitives do not exist.

**No per-app ESLint config, ever.** Verify with `pnpm run lint` from the repo root after adding files. If lint
fails only when run from inside an app directory, that is expected — `CLAUDE.md` documents it.

**Nothing secret in `.env.example`.** Base URLs and feature flags only.

## 5. Acceptance criteria

1. `pnpm install && pnpm -r run build` succeeds for both apps.
2. `pnpm run typecheck`, `pnpm run lint` and `pnpm run format:check` all pass from the repo root.
3. `pnpm run test` runs a real test in each app — at minimum a route renders and the API client maps an error
   envelope to a typed error.
4. No `eslint.config.*` file exists inside either app directory.
5. Neither app appears in `./gradlew projects`, and no Gradle file references them.
6. The API client is the only place `fetch` is called — asserted by grep over `src`, excluding the client itself.
7. A 401 response clears the stored token and routes to sign-in — asserted in a test.
8. `apps/admin-web` sends no `Authorization` header and has no sign-in route — asserted by grep and by a client
   test.
9. No port number or backend hostname is hard-coded outside `vite.config.ts` and `.env.example`.
10. API types are either generated, or accompanied by a contract test that fails on divergence.
11. `.env.example` files contain no credential.

## 6. Verification

```
cd <repo>
pnpm install --frozen-lockfile && pnpm -r run build && pnpm run typecheck && pnpm run lint && pnpm run format:check
pnpm run test
find apps -name 'eslint.config.*'            # expect nothing
./gradlew projects | grep -iE 'web'          # expect nothing
grep -rn 'fetch(' apps/*/src --include=*.ts --include=*.tsx | grep -v 'api/client.ts'   # expect nothing
grep -rn 'Authorization' apps/admin-web/src  # expect nothing
grep -rnE 'localhost:80[0-9]{2}' apps/*/src  # expect nothing
```

Expected: all builds and checks green; tests run; no per-app ESLint config; no Gradle project; `fetch` confined to
the client; no auth header in admin-web; no hard-coded ports in sources.

## 7. Out of scope

Any screen — market data (`T-052`), catalog and cart (`T-053`), checkout (`T-054`), auth and history (`T-055`),
admin (`T-056`). i18n, currency and theme switching (`T-051`) — this task only leaves room for them. Visual
design. A shared component package. SSR — both apps are static SPAs per Architecture §7.

## 8. Hazards

- **Adding a per-app `eslint.config.mjs`** because lint "does not work in this directory" is the documented
  failure mode in `CLAUDE.md`. Run it from the root.
- Adding a Gradle module for either app contradicts ADR-0005 and Phase 1, and will be reverted.
- Hand-copied API types drift the moment a DTO changes, and the first symptom is a blank field in production.
- Calling `fetch` directly from a screen bypasses envelope handling, so that screen loses the correlation id and
  FR-8.1's actionable error.
- Putting the JWT in `localStorage` without recording the trade-off means nobody revisits it; putting it in memory
  without recording it means the next person "fixes" the reload behaviour.
- Building a sign-in screen for `apps/admin-web` duplicates an authentication path that IAP owns, and it will
  appear to work locally.
- Compiling a port into the bundle breaks every deployed environment (`T-072`, `T-074`).

## 9. On completion

Mark the T-050 row done in [`README.md`](README.md). Record the router, test runner and data-layer choices, the
token-storage decision and its trade-off, and how API types are kept in sync — all six remaining M3 tasks build on
these.
