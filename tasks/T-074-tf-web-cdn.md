# T-074 — Terraform module: the web CDN stack

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md` §7](../docs/architecture.md#7-reference-deployment-gcp) or
> [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md), that document wins.** Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#5 — Phase 5 — GCP infrastructure](https://github.com/rednavis/metal-desk/issues/5)

**This task:** [#52](https://github.com/rednavis/metal-desk/issues/52)

**Milestone:** M5 GCP infrastructure · **Estimate:** 4 h

**Preconditions** — `T-070` and `T-071` merged. `T-050` merged, so both SPAs produce a real bundle.

> [!WARNING]
> **Write-and-validate only.** A global external load balancer bills a standing hourly charge per forwarding rule, plus
> egress. This is the second-largest idle cost after `api`'s min-instance. Do not apply from this task.

**Goal** — Build the static-hosting stack Architecture §7 specifies for both SPAs: Cloud Storage behind an external
HTTPS load balancer with Cloud CDN — one reusable module, two invocations, with `admin-web` restricted to the same IAP
as `admin`.

## 1. Why this task exists

Architecture §7 gives the two SPAs different exposure from one shape:

| Component | Requirement |
|---|---|
| `web` | Cloud Storage + external HTTPS LB + Cloud CDN. Static SPA; **no application server needed** |
| `admin-web` | The same, **behind the same Identity-Aware Proxy as `admin`** — static SPA, internal-only |

Two things about this stack bite specifically on SPAs, and both are invisible until someone reloads a deep link:

- a bucket-backed load balancer returns 404 for `/orders/123` because no such object exists, so client-side routing
  breaks on refresh;
- an aggressively cached `index.html` pins users to an old bundle after a deploy.

Both are configuration, and both are the kind of thing that gets discovered by a customer.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Both SPAs on GCS + external HTTPS LB + Cloud CDN | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `admin-web` behind the same IAP as `admin` | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `admin-web` is the sole client of `apps/admin` | `CLAUDE.md`, [ADR-0005](../docs/adr/0005-consolidated-react-frontend.md) |
| No application server for the frontend | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Both apps are Vite SPAs built with `pnpm -r run build` | `CLAUDE.md` |
| No port or backend hostname compiled into a bundle | `T-050` |
| No secrets committed | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |

## 3. Deliverables

| Path | What |
|---|---|
| `infra/terraform/modules/static-site/main.tf` | Bucket, backend bucket, URL map, HTTPS proxy, forwarding rule, certificate |
| `.../static-site/cdn.tf` | Cache policy and invalidation notes |
| `.../static-site/variables.tf`, `outputs.tf`, `README.md` | The module contract |
| `infra/terraform/envs/dev/sites.tf` | Two invocations: `web` public, `admin-web` IAP-restricted |
| `.github/workflows/frontend-deploy.yml` | Build and sync the bundles — conditional, as in `T-073` |
| `infra/README.md` (modify) | The cache policy, the SPA-rewrite behaviour, and the standing LB cost |

## 4. Specification

**Solve SPA deep-link routing explicitly and say which mechanism you used.** A bucket-backed backend has no rewrite
engine of its own. The options are the bucket's own `not_found_page` set to `index.html` (website configuration), or a
URL-map rewrite. **Website configuration on the bucket is the simplest thing that works for a bucket backend** — verify
it actually applies through the load balancer rather than only on direct bucket access, because the two paths behave
differently. Record what you verified.

Note the consequence: with `index.html` served for unknown paths, a genuinely missing asset returns HTML with a 200 or
404 depending on configuration. Make sure a missing `.js` chunk does not silently return the HTML shell — that produces
a blank page and a console error that is very hard to attribute.

**Two cache policies, not one.** Vite emits content-hashed assets and a non-hashed `index.html`.

- hashed assets under `/assets/` — cache for a long time, immutable;
- `index.html` — `no-cache` or a very short TTL, so a deploy takes effect.

A single long TTL on everything pins users to the previous bundle until the cache expires, and a cache invalidation on
every deploy is both slow and rate-limited. Getting the split right removes the need to invalidate at all.

**`admin-web` is IAP-restricted, and the module must support it without a second module.** Parameterise IAP on the
backend bucket. Note that IAP in front of a backend **bucket** has different support characteristics from IAP in front
of a backend service — **verify that the combination Architecture §7 describes is actually supported**, and if it is
not, say so plainly and propose the alternative (for example serving `admin-web` through an IAP-protected backend
service) rather than quietly leaving `admin-web` public. This is the one place in M5 where the architecture document
may be describing something the platform does not directly offer, and discovering that here is the point of doing
write-and-validate first.

**Managed certificates need a domain, and there may not be one.** A Google-managed certificate requires a domain you
control and DNS pointing at the load balancer. If no domain exists for this build, make the domain a required variable
with no default and document that an `apply` needs one — do not invent a domain, and do not fall back to an unencrypted
listener.

**The buckets are not public in the ordinary sense.** Grant the load balancer's service agent read access rather than
making objects publicly readable, so the only path to content is through the load balancer — otherwise `admin-web`'s
bundle is fetchable directly from the bucket, bypassing IAP entirely. **That bypass is the most serious failure
available in this task.**

**Deploy is a sync, and `index.html` goes last.** Upload hashed assets first, then `index.html`, so no user can load a
shell that references assets not yet present. Use `pnpm -r run build`'s output; set cache headers at upload time to
match the policy above.

**The bundle carries no secret and no compiled hostname.** `T-050` made the API base URL an environment variable; the
deploy supplies the right value per environment at build time. A bundle is public by definition — anything in it is
published.

## 5. Acceptance criteria

1. `terraform fmt -check -recursive`, `init -backend=false`, `validate` and the linter pass.
2. One `static-site` module, invoked twice in `envs/dev/sites.tf`; no duplicated bucket or load-balancer resource.
3. Objects are **not** publicly readable; access is granted to the load balancer's service agent only — asserted by
   reading the IAM in the plan.
4. `admin-web` is IAP-restricted, **or** the unsupported combination is documented with a concrete proposed
   alternative and `admin-web` is not left publicly reachable.
5. SPA deep links resolve to `index.html`, verified through the load-balancer path — and the mechanism used is
   recorded.
6. A missing hashed asset does not return the HTML shell as if it were the asset.
7. Two distinct cache policies exist: long-lived immutable for `/assets/`, no-cache or short for `index.html`.
8. The certificate domain is a required variable with no default, and the DNS prerequisite is documented.
9. The deploy workflow uploads assets before `index.html` and sets cache headers at upload time.
10. No secret and no compiled backend hostname in either bundle — asserted by grepping the built output.
11. The standing load-balancer cost is recorded in `infra/README.md`.
12. Nothing was applied; `git status` is clean.

## 6. Verification

```
cd <repo>
pnpm install --frozen-lockfile && pnpm -r run build
grep -rniE 'localhost:80[0-9]{2}|api[_-]?key|secret' apps/web/dist apps/admin-web/dist   # expect nothing
cd infra/terraform && terraform fmt -check -recursive
(cd envs/dev && terraform init -backend=false && terraform validate)
tflint --recursive
grep -rn 'allUsers\|allAuthenticatedUsers' modules/static-site   # expect nothing
cd <repo> && git status --short   # expect clean (dist/ ignored)
```

Expected: bundles build with no secrets or hard-coded hosts; Terraform checks green; no public IAM binding; a clean
tree.

## 7. Out of scope

Cloud Run services (`T-072`). IAP provisioning itself (`T-076`) — this task parameterises it. CI authentication
(`T-077`). DNS zone management, unless the domain is owned here. A custom 404 page design. Any `apply`.

## 8. Hazards

- **Public bucket objects behind an IAP-protected load balancer** means `admin-web`'s bundle is downloadable directly
  from the bucket, and IAP protects nothing. This is the worst mistake in the task and it passes every functional
  check.
- One long cache TTL on `index.html` leaves users on the old bundle after every deploy, and the symptom is
  intermittent, per-user and unreproducible.
- Uploading `index.html` before the assets it references gives every user loading during the deploy a broken page.
- Serving the HTML shell for a missing `.js` chunk produces a blank screen with a MIME-type console error and no
  obvious cause.
- Assuming IAP works in front of a backend bucket without verifying it can leave `admin-web` open; check rather than
  assume, and report what you find.
- Inventing a domain for the managed certificate produces a configuration that fails at `apply` after provisioning
  time has been spent.
- Forgetting the standing per-forwarding-rule charge means two load balancers bill continuously from the first apply.
- Compiling the API hostname into the bundle makes one build undeployable to a second environment.

## 9. On completion

Mark the T-074 row done in [`README.md`](README.md). Record the SPA-rewrite mechanism, the two cache policies, and —
most importantly — what you found out about IAP in front of a backend bucket, since `T-076` and `T-078` both depend on
that answer.
