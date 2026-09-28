# T-032 — `services/api`: stateless JWT auth and sign-in throttling

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or the
> [business rules](../docs/business-requirements.md#8-business-rules), that document wins** — open an
> issue rather than implementing either version. Update this task's row in [the ledger](README.md) in
> the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#25](https://github.com/rednavis/metal-desk/issues/25)

**Milestone:** M2 Services and admin API · **Estimate:** 3 h

**Preconditions** — `T-030` and `T-031` merged. `T-011` merged (`AuthCredential`, `AuthIdentifier`).

**Goal** — Implement sign-in and per-request bearer-token validation exactly as
[Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack) specifies — stateless JWT, no
server-side session — with the generic-failure and throttling behaviour
[FR-2.2](../docs/business-requirements.md#72-authentication) requires.

## 1. Why this task exists

Architecture §5 makes two commitments that are easy to break and hard to undo:

> **Auth** is stateless JWT, not server-side sessions — deliberately, so `api` can scale horizontally with no
> shared session store. A bearer token is validated per request; CPU-bound crypto work is explicitly scheduled
> off the reactive event loop rather than blocking it.

The second half is the one that gets skipped. Password hashing with a modern KDF costs tens of milliseconds of
CPU; running it on the event loop stalls every concurrent request on that thread. It works fine in a test with
one user and degrades non-obviously under load.

FR-2.2 is the other trap: the generic message is a security property, and two different error responses for
"no such user" and "wrong password" defeat it no matter what the message says.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Stateless JWT, no server-side session store | [Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack) |
| Crypto work scheduled off the event loop | [Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack) |
| Sign in with email **or** phone plus password | [FR-2.1](../docs/business-requirements.md#72-authentication) |
| Generic invalid-credentials message; throttle after repeated failures | [FR-2.2](../docs/business-requirements.md#72-authentication) |
| Illustrative policy: lock the source IP for a cool-down after 3 consecutive failures | [FR-2.2](../docs/business-requirements.md#72-authentication) |
| Email must be verified before checkout | [FR-2.3](../docs/business-requirements.md#72-authentication), `T-011`'s `VerificationState` |
| No committed secrets, ever | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |
| Signing keys come from Secret Manager in deployment | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |

## 3. Deliverables

Under `services/api/src/main/java/com/rednavis/metaldesk/api/auth/`:

| Path | What |
|---|---|
| `AuthController.java` | Sign-in and sign-out endpoints |
| `AuthenticationService.java` | Credential lookup, verification, token issue |
| `PasswordEncoderAdapter.java` | The KDF, invoked off the event loop |
| `JwtIssuer.java`, `JwtValidator.java` | Token mint and per-request validation |
| `JwtProperties.java` | Issuer, audience, TTL, key reference — externally configured |
| `SecurityConfiguration.java` | The reactive security filter chain; public vs authenticated routes |
| `SignInThrottle.java` | The FR-2.2 failure counter and cool-down |
| `AuthenticatedCustomer.java` | The request-scoped principal |
| Tests | `AuthenticationServiceTest`, `SignInThrottleTest`, `JwtValidatorTest`, a controller test proving response-identity |

## 4. Specification

**One failure path, one response.** A missing identifier, an unknown identifier and a wrong password must all
produce the **same** HTTP status, the same body and the same error code. Implement it by resolving to a single
outcome type before building the response, not by three handlers that happen to return the same literal. The
test in AC-3 compares the full serialised responses byte for byte.

**Constant-ish timing.** If the identifier is unknown, still perform a password verification against a dummy
hash so the response time does not reveal account existence. Document it — otherwise it looks like dead code
and someone will delete it.

**Crypto goes off the event loop.** Wrap the KDF call so it runs on a bounded elastic scheduler, not the
event-loop thread. This is an explicit Architecture §5 requirement, so make it visible and comment it. Do not
scatter the scheduling decision across call sites — it belongs in `PasswordEncoderAdapter`.

**`SignInThrottle` implements the FR-2.2 policy, with the numbers as configuration.** Three consecutive
failures, then a cool-down for that source — both values externally configured with the FR's illustrative
figures as defaults. Keyed on the source IP as FR-2.2 says, and **document the reverse-proxy caveat**: behind
Cloud Run and a load balancer the immediate peer is not the client, so the key must come from the forwarded
header, and trusting that header blindly lets an attacker rotate it. State the decision you made.

Reset the counter on a successful sign-in. Because the service is stateless and horizontally scaled
(Architecture §5), an in-memory counter is per-instance and therefore weaker than it appears — implement it
in-memory for this phase, **and say so in the Javadoc and the ledger**, noting that a shared store is needed
before this is a real control. Do not silently present a per-instance counter as the FR-2.2 guarantee.

**The JWT carries the customer id, the verification state and nothing sensitive.** No email, no phone, no
password hash. Short TTL, explicit issuer and audience, and validation that checks signature, expiry, issuer
and audience — all four. A validator that only checks the signature accepts a token minted for another
environment.

**No key material in the repository.** The signing key is a configured reference with a local development
default that is obviously a development value, generated at startup if absent. No key, no PEM, no secret in
any committed file.

**Catalog routes stay public.** FR-1.x is anonymous browsing; `SecurityConfiguration` must permit the `T-031`
catalog and market-data routes and `/actuator/health` without a token, and require one everywhere else by
default. Default-deny, with an explicit allowlist.

**`VerificationState` gates checkout, not sign-in.** FR-2.3 makes an unverified account unusable *for
checkout*; an unverified user may sign in and browse. Put the check where checkout reads it (`T-035`), and
carry the state in the token.

## 5. Acceptance criteria

1. `./gradlew :services:api:build` is `BUILD SUCCESSFUL`.
2. Sign-in succeeds with an email identifier and with a phone identifier for the same customer.
3. Unknown identifier, wrong password, and missing identifier produce **byte-identical** response bodies and
   the same status — asserted by comparing serialised responses.
4. The unknown-identifier path still invokes the KDF — asserted with a spy or counter.
5. The KDF does not execute on an event-loop thread — asserted by capturing the thread name inside the encoder
   and asserting it is not a reactor event-loop thread.
6. After three consecutive failures from one source, the fourth attempt is throttled; a successful sign-in
   resets the counter; the cool-down expires.
7. A token missing, expired, wrongly signed, or carrying the wrong issuer or audience is each rejected — four
   separate assertions.
8. The token payload contains no email, phone or hash — asserted by decoding it.
9. Catalog, market-data and `/actuator/health` are reachable with no token; any other route returns 401.
10. `grep` finds no key material, no `-----BEGIN`, and no hard-coded secret under `services/api/`.

## 6. Verification

```
cd <repo>
./gradlew :services:api:build :services:api:test
grep -rniE '\-\-\-\-\-BEGIN|secret *[:=] *"[^$]|jwt.*key *[:=] *"[^$]' services/api/src services/api/src/main/resources   # expect nothing
grep -rn 'boundedElastic\|Schedulers' services/api/src/main/java/com/rednavis/metaldesk/api/auth/
grep -rn 'permitAll\|pathMatchers' services/api/src/main/java/com/rednavis/metaldesk/api/auth/SecurityConfiguration.java
```

Expected: `BUILD SUCCESSFUL`; no key material; explicit scheduler use in the auth package; an explicit
public-route allowlist.

## 7. Out of scope

Registration, email verification, password reset and account switching (`T-033`). Refresh tokens and token
revocation — note the absence rather than improvising. Identity-Aware Proxy for `apps/admin` (`T-076`) —
`apps/admin` is internal-only and does **not** use this JWT flow. Rate limiting beyond FR-2.2's sign-in
throttle. A shared throttle store.

## 8. Hazards

- **Two distinguishable failure responses** breaks FR-2.2 even when both messages read "invalid credentials" —
  a different status, a different field order, or a different latency is enough. AC-3 and AC-4 exist for this.
- Running the KDF on the event loop is the defect Architecture §5 names explicitly. It passes every functional
  test.
- Validating only the signature accepts tokens from another environment signed with a shared key.
- Keying the throttle on the immediate peer address throttles the load balancer, not the attacker; trusting a
  forwarded header without a trusted-proxy configuration lets the attacker bypass it. Neither is safe by
  default — decide and document.
- Presenting an in-memory per-instance counter as satisfying FR-2.2 overstates the control. Record the
  limitation.
- Putting the verification check on sign-in locks unverified users out of browsing, which FR-2.3 does not ask
  for.

## 9. On completion

Mark the T-032 row done in [`README.md`](README.md). Record the throttle key decision, the in-memory-counter
limitation, and the token claim set — `T-033`, `T-035` and `T-050` all depend on them.
