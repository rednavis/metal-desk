# T-076 — Identity-Aware Proxy for `admin` and `admin-web`

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md` §7](../docs/architecture.md#7-reference-deployment-gcp), that document wins.** Update
> this task's row in [the ledger](README.md) in the same pull request.

**Parent issue:** [#5 — Phase 5 — GCP infrastructure](https://github.com/rednavis/metal-desk/issues/5)

**This task:** [#54](https://github.com/rednavis/metal-desk/issues/54)

**Milestone:** M5 GCP infrastructure · **Estimate:** 3 h

**Preconditions** — `T-072` and `T-074` merged. **Read what `T-074` recorded about IAP in front of a backend bucket
before starting** — it may change this task's shape.

> [!WARNING]
> **Write-and-validate only.** IAP itself is not separately billed, but it requires a load balancer, which is.

**Goal** — Put both staff surfaces behind Identity-Aware Proxy, so `apps/admin`'s "no application login" design
(`T-040`) has the authentication layer it depends on, and verify the identity assertion the application reads.

## 1. Why this task exists

Architecture §7 uses IAP to replace an entire feature:

> `admin` — Cloud Run, **behind Identity-Aware Proxy**. Internal-only; **IAP replaces a hand-rolled staff auth flow.**

`T-040` built `apps/admin` with no login, reading staff identity from an IAP-supplied header, with a development
override that defaults to off. `T-050` and `T-056` built `apps/admin-web` with no sign-in route. Both decisions are
correct **only if this task lands** — until then the staff surface has no authentication beyond ingress restrictions,
and the development override is the only way in.

That makes this a genuine security dependency, not a deployment nicety. It is also why `T-040` AC-12 asserted the
override is disabled by default.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| `admin` behind IAP; internal-only; IAP replaces staff auth | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `admin-web` behind the **same** IAP as `admin` | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `apps/admin` implements no login and rejects the customer JWT | `T-040` |
| `apps/admin-web` has no sign-in route and sends no `Authorization` header | `T-050`, `T-056` |
| The development identity override is off by default | `T-040` |
| No long-lived credentials | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |

## 3. Deliverables

| Path | What |
|---|---|
| `infra/terraform/modules/iap/` | The OAuth brand/client wiring, IAP settings and access bindings |
| `infra/terraform/envs/dev/iap.tf` | IAP over the `admin` backend and the `admin-web` site |
| `apps/admin/.../security/StaffPrincipalResolver.java` (modify) | Verify the IAP JWT assertion — see §4 |
| `apps/admin/src/test/.../IapAssertionTest.java` | Assertion verification tests, including the forgery case |
| `infra/README.md` (modify) | Who has access, how it is granted, and the local-development story |
| `docs/architecture.md` (modify) | §7 gains a pointer to how IAP is configured |

## 4. Specification

**Verify the signed assertion, not the plain header.** IAP forwards `X-Goog-Authenticated-User-Email`, which is
trivially spoofable by anything that can reach the service directly. It also forwards
`X-Goog-IAP-JWT-Assertion`, a signed JWT whose signature, issuer and **audience** can be verified against Google's
public keys. `T-040` read "the IAP-supplied header"; this task must make it the **verified assertion**.

Verify all of: signature, issuer, expiry, and audience — the audience encodes the specific backend, so an assertion
minted for another service in the same project must be rejected. This is the same lesson `T-032` applied to the
customer JWT, and it matters more here because the token grants staff access.

**Defence in depth: IAP is not the only gate.** Cloud Run ingress must remain restricted (`T-072` made `admin`
internal-only) **and** the service must reject a request carrying no valid assertion. Either alone is a single point
of failure — if ingress is ever relaxed, header-only trust becomes a full bypass.

**The development override must become impossible, not merely default-off.** Now that a real mechanism exists, gate the
override on a Spring profile that deployment never sets, and fail fast at startup if the override is enabled while a
deployed profile is active. A configuration flag that *can* be set in production eventually will be.

**Access is granted to a group, not to individuals.** Bind `roles/iap.httpsResourceAccessor` to a Google group for
staff. Individual bindings become a list nobody prunes; a group makes onboarding and offboarding one action. Name the
group in `infra/README.md`.

**`admin-web` and `admin` share the IAP boundary — resolve `T-074`'s finding here.** If IAP in front of a backend
bucket is not supported as Architecture §7 assumes, implement the alternative `T-074` proposed and **update
`docs/architecture.md` §7 to describe what was actually built.** Leaving the architecture document describing an
unimplementable arrangement is worse than the workaround itself.

Whatever the shape: `admin-web`'s bundle must not be fetchable outside the IAP boundary. `T-074` AC-3 kept the bucket
objects non-public; re-verify it here, because this is the task where that guarantee becomes load-bearing.

**The SPA must handle IAP's redirect.** IAP answers an unauthenticated browser request with a redirect to Google
sign-in. An XHR from an already-loaded SPA whose session has expired gets that redirect too, which fails opaquely as a
CORS error rather than a 401. Detect the condition and reload the page so IAP can redirect properly; note any change
needed in `T-056`'s app.

**OAuth brand and client creation has manual prerequisites.** An IAP OAuth brand is often a one-time, per-project,
manually created object that cannot be recreated or deleted through Terraform. Document what must be done by hand, and
do not fabricate a client id.

## 5. Acceptance criteria

1. `terraform fmt -check -recursive`, `init -backend=false`, `validate` and the linter pass.
2. `StaffPrincipalResolver` verifies the IAP **JWT assertion** — signature, issuer, expiry and audience — with a test
   for each of the four failure modes.
3. A forged `X-Goog-Authenticated-User-Email` header with **no** valid assertion is rejected — the key assertion of
   this task.
4. An assertion with a valid signature but the wrong audience is rejected.
5. A request with no assertion is rejected even when it reaches the service directly.
6. The development override cannot be enabled under a deployed profile: startup fails fast if it is.
7. `admin` Cloud Run ingress remains internal-only; IAP is an additional layer, not a replacement.
8. Access is bound to a staff group, not to individual accounts; the group is named in `infra/README.md`.
9. `admin-web` is reachable only through the IAP boundary, and its bucket objects remain non-public — re-verified.
10. If `T-074`'s finding required a different arrangement, it is implemented and `docs/architecture.md` §7 is updated
    to match.
11. The SPA handles an expired IAP session without presenting an opaque CORS failure.
12. Manual OAuth brand prerequisites are documented; no fabricated client id exists in the tree.
13. Nothing was applied; `git status` is clean; `./gradlew :apps:admin:build` passes.

## 6. Verification

```
cd <repo>
./gradlew :apps:admin:build :apps:admin:test
grep -rn 'X-Goog-IAP-JWT-Assertion' apps/admin/src/main
grep -rn 'X-Goog-Authenticated-User-Email' apps/admin/src/main   # if present, must not be the sole trust source
grep -rn 'iap.httpsResourceAccessor' infra/terraform
grep -rniE 'client_id *= *"[0-9]' infra/terraform   # expect nothing fabricated
cd infra/terraform && terraform fmt -check -recursive && (cd envs/dev && terraform init -backend=false && terraform validate)
```

Expected: the module builds and tests pass; the signed assertion is verified; the group binding present; no fabricated
client id; Terraform checks green.

## 7. Out of scope

Staff roles and fine-grained permissions inside `apps/admin` — IAP answers "is this a staff member", not "may they
decline a quote". Customer authentication (`T-032`). Provisioning the Google group. Context-aware access policies. Any
`apply`.

## 8. Hazards

- **Trusting `X-Goog-Authenticated-User-Email` without verifying the assertion** means anything that can reach the
  service can impersonate any staff member. It works in every test, and it is a complete authentication bypass.
- Skipping audience verification accepts an assertion minted for a different backend in the same project.
- Relying on IAP alone while relaxing Cloud Run ingress turns a defence-in-depth design into a single check.
- A development override that is merely default-off will be enabled in a deployed environment eventually; make it
  impossible instead.
- Individual IAP bindings accumulate former staff.
- Publicly readable `admin-web` objects bypass IAP entirely — the bundle, and the admin API surface it documents,
  becomes public.
- An expired IAP session on an XHR surfaces as a CORS error, and the staff user sees a broken page with no explanation.
- Leaving `docs/architecture.md` describing an IAP arrangement that was not built strands the next reader.

## 9. On completion

Mark the T-076 row done in [`README.md`](README.md). Record the assertion-verification approach, the staff group, the
manual OAuth prerequisites, and any change made to Architecture §7 — `T-078` stands this up for `staging` and `prod`.
