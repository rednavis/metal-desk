---
title: "ADR-0005: One React frontend stack for both customer and staff surfaces"
parent: ADRs
nav_order: 5
---

# ADR-0005: One React frontend stack for both customer and staff surfaces

**Status:** Accepted

## Context

A 2022-era version of this kind of system typically ends up with two different UI paradigms for
its two audiences: a JavaScript SPA framework for the customer-facing storefront, because that's
what a public shopping experience needs, and a server-side Java UI framework for the internal staff
back office, because it ships fast for a small internal tool and reuses backend developers who are
already there. The result is two component models, two build toolchains, two testing approaches,
and two skill sets — for one product with one design language.

[Architecture §2](../architecture.md#2-c4-level-2--containers) needs a staff-facing surface for
`admin` (fulfillment-tier configuration, quote handling, order management). The question this ADR
answers is whether that surface repeats the two-stack pattern or not.

## Decision

**One frontend stack, for both audiences: React 19, TypeScript, and Vite, in a shared `pnpm`
workspace** ([Modernization Plan, Phase 1](../modernization-plan.md#phase-1--build-graph-and-conventions)).
`apps/web` (customer) and the new `apps/admin-web` (staff) are still two separate deployables —
they ship, scale, and deploy independently ([Architecture §7](../architecture.md#7-reference-deployment-gcp))
— but one component library, one design-token set, one lint/format config, and one dependency
policy cover both, the same way [ADR-0001](0001-monorepo-from-day-one.md) puts every JVM module on
one build graph so nothing has a reason to fork.

`apps/admin` stays a pure API — see the updated row in
[Architecture §2](../architecture.md#2-c4-level-2--containers) — with `apps/admin-web` as its only
client, the same `api`/`web` split the customer side already has.

## Consequences

**Positive:**
- One hiring profile and one onboarding path for frontend contributors, instead of needing both a
  SPA developer and a server-side-Java-UI developer to touch the whole product.
- Design-system components (a button, a data table, a form field) are written once and shared
  between `web` and `admin-web` via the workspace, instead of existing twice in two incompatible
  frameworks — the same "shared library nobody depends on" risk from
  [Lessons Learned](../lessons-learned.md#the-shared-library-nobody-depends-on), avoided by not
  having two frameworks for it to fork across in the first place.
- `admin-web` deploys exactly like `web` — static assets on Cloud Storage + Cloud CDN
  ([ADR-0003](0003-gcp-target-architecture.md)), just behind Identity-Aware Proxy — so
  [Architecture §7](../architecture.md#7-reference-deployment-gcp) doesn't need a second deployment
  shape for a second UI runtime.

**Costs, accepted deliberately:**
- A server-side admin framework typically provides data grids, form binding, and validation UI out
  of the box; `apps/admin-web` has to select and maintain its own equivalents (a table/query
  library, a form library) as real dependencies, not get them for free from the framework. This is
  genuine, ongoing work, not a one-time setup cost.
- Two deployables now share a frontend dependency surface, which is more coupling than two fully
  independent stacks would have — mitigated, not eliminated, by the same monorepo/single-version
  reasoning as [ADR-0001](0001-monorepo-from-day-one.md).

**Explicitly not decided here:** a meta-framework for `web` (e.g. Next.js, for SSR/SEO on the
storefront) is a separate, later decision — nothing about the admin-surface question this ADR
answers requires or precludes one.
