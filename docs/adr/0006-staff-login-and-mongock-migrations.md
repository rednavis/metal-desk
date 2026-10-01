---
title: "ADR-0006: Staff sign-in with their own users, and Mongock for migrations"
parent: ADRs
nav_order: 6
---

# ADR-0006: Staff sign-in with their own users, and Mongock for migrations

**Status:** Accepted. Supersedes the "behind Identity-Aware Proxy, no application login" parts of
[ADR-0003](0003-gcp-target-architecture.md) and [ADR-0005](0005-consolidated-react-frontend.md).

## Context

`apps/admin` was built to trust an identity header set by Identity-Aware Proxy, with no login of its
own. IAP is a deployment-time piece (Terraform, a load balancer, an OAuth brand) that does not exist
yet, so `apps/admin-web` could not be signed in to at all outside a development override. Staff also
need roles, which IAP's "is this a staff member" does not answer. Separately, the first piece of
reference data (the first users) needs a migration mechanism, which the platform did not have.

## Decision

**Users are a separate collection and a separate trust domain.** A `users` collection holds the
people who work in `apps/admin-web` (login, email, BCrypt hash, one role, enabled). Roles are the
`UserRole` enum in `libs/share` (`ADMIN`, `MANAGER`); a new role is a new constant. Customers and users
never share a credential, a sign-in route or a token:

- `services/api` signs in **customers only**, from `customers`/`credentials`. It never reads `users`.
- `apps/admin` signs in **users only**, at `POST /api/admin/auth/sign-in`, and every other route needs
  the bearer token that call returns. It never reads `customers` for authentication.
- The two tokens differ in issuer (`metal-desk-api` / `metal-desk-admin`), audience
  (`metal-desk-storefront` / `metal-desk-staff`), claims, and signing key (`JWT_SIGNING_KEY` /
  `ADMIN_JWT_SIGNING_KEY`). Each service checks signature, expiry, issuer and audience, so a token of
  the other kind is refused even if the two services were configured with the same secret. Both
  directions are tested.

The staff token is HS256, 30 minutes, with no refresh and no revocation (the same trade-off as the
customer token): its claims are the user id, login and role. `/api/admin/me` reads the user from the
database, so a user removed or disabled since signing in is told so on the next screen. Sign-in has
one failure response whatever went wrong, checks a password hash even for an unknown login so timing
does not reveal which logins exist, and locks a login-and-address pair out after repeated failures.
`admin-web` keeps the token in memory only, so a reload signs out.

**Role enforcement is not built yet.** Both roles can call every staff endpoint; the role is in the
token and the principal so that restricting an endpoint is one rule, added when there is a reason.

**Migrations use Mongock, not Flyway.** Flyway's MongoDB support runs migrations through an external
`mongosh` executable, which would have to be installed on every developer machine, CI runner and
container image. Mongock is native to the JVM and MongoDB, records what it ran in the database and
holds a lock, which matters because `services/api` and `apps/admin` share one database and both start
migrations. Migrations live in one module, `libs/migrations`, and both applications import its
`MigrationsConfiguration`: the run happens before the web server accepts requests, and a failed
migration stops the application from starting. Change units are Java classes listed explicitly in
`MongoMigrations`; one that has run anywhere is never edited, a correction is the next change unit.
Mongock runs over the synchronous driver on a connection of its own, opened for the run and closed
after it, so the reactive service does not gain a blocking driver in its request path.

**Indexes of `users` belong to the migration**, not to `@Indexed` on the document: `services/api`
creates indexes from annotations at startup and would race the migration.

## Consequences

- The first migration creates `admin/admin` (ADMIN) and `manager/manager` (MANAGER) **in every
  environment it runs in**. They exist so a fresh database can be signed in to; a real deployment
  must change or remove them before it is reachable. Nothing forces a password change yet.
- The IAP work in `T-076` is no longer what authenticates staff. If IAP is still put in front of
  `admin`, it becomes an outer network gate, and the bearer token is still required.
- A token cannot be revoked before it expires, and the sign-in lockout is per instance and in memory.
- `services/api` now has the synchronous MongoDB driver on its classpath, used only by the startup
  migration.
