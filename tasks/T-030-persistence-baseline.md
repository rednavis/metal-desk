# T-030 — Reactive MongoDB persistence baseline and the Testcontainers harness

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or an [ADR](../docs/adr/), that document wins** —
> open an issue rather than implementing either version. Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#3 — Phase 3 — Services and apps](https://github.com/rednavis/metal-desk/issues/3)

**This task:** [#23](https://github.com/rednavis/metal-desk/issues/23)

**Milestone:** M2 Services and admin API · **Estimate:** 3 h

**Preconditions** — Phase 2 closed (`T-021`). Every `libs/*` type this task maps must exist.

**This task blocks every other M2 task.** Nothing in `services/api`, `services/pricing-bridge` or
`apps/admin` can be tested against real storage without it.

**Goal** — Establish how domain aggregates reach the document store and back — reactive repositories, the
mapping boundary, the order-number sequence, and a Testcontainers harness — once, so that eleven downstream
tasks do not each invent it.

## 1. Why this task exists

[Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack) commits to
`controller → service → repository`, "fully non-blocking end-to-end … backed by a document store", and
[§7](../docs/architecture.md#7-reference-deployment-gcp) names MongoDB Atlas. Two decisions have to be made
before any service writes a repository, and both are expensive to reverse:

**Does a domain aggregate get persistence annotations, or is there a separate document type?** Annotating
`Order` in `libs/share` would put Spring Data on the compile classpath of every module and make
`T-021`'s rule 2 fail. So there must be a mapping boundary — and where it lives, and who owns the mapping
code, is this task's call.

**The order-number daily sequence.** `T-014` deliberately left allocation out of `OrderNumber`, because
[BR-6](../docs/business-requirements.md#8-business-rules)'s `<date><daily-sequence>` needs an atomic counter.
That counter is storage, and getting it wrong produces duplicate order numbers under concurrency.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| `controller → service → repository`, non-blocking end to end | [Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack) |
| Document store; MongoDB Atlas in the reference deployment | [Architecture §5](../docs/architecture.md#5-request-flow--the-reactive-stack), [§7](../docs/architecture.md#7-reference-deployment-gcp) |
| `libs/share` domain types must not depend on Spring | [Architecture §8](../docs/architecture.md#8-build-graph), enforced by `T-021` |
| Order number is `<date><daily-sequence>` | [BR-6](../docs/business-requirements.md#8-business-rules) |
| No real credentials; everything mocked or local | [ADR-0002](../docs/adr/0002-mocked-external-dependencies.md), [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |
| Fixtures synthetic and small | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |
| Versions only in the catalog | `CLAUDE.md` |

## 3. Deliverables

| Path | What |
|---|---|
| `gradle/libs.versions.toml` (modify) | Add the Testcontainers version, BOM and `mongodb` module aliases — none exist today |
| `build-logic/src/main/kotlin/metaldesk.spring-boot-conventions.gradle.kts` (modify, if needed) | Common reactive-Mongo test wiring, if it belongs to every service |
| `services/api/.../persistence/document/*Document.java` | The document types: order, customer, product, category, fulfillment tier |
| `services/api/.../persistence/mapper/*Mapper.java` | Document ↔ domain mapping, one per aggregate |
| `services/api/.../persistence/repository/*Repository.java` | The reactive repositories |
| `services/api/.../persistence/OrderNumberSequence.java` | The atomic daily-sequence allocator |
| `services/api/src/test/.../persistence/MongoTestSupport.java` | The shared Testcontainers base: one container per suite, dynamic properties |
| `services/api/src/test/.../persistence/OrderNumberSequenceTest.java` | The concurrency proof — see §4 |
| `services/api/src/main/resources/application.yml` (modify) | Reactive Mongo connection, with no credential committed |

## 4. Specification

**The mapping boundary is explicit and one-directional.** Document types live in the service, carry the Spring
Data annotations, and know how to become domain types. Domain types know nothing about documents. Each mapper
is a plain class with `toDomain(XDocument)` and `toDocument(X)`; no reflection-based auto-mapping library, so
that a field added to an aggregate produces a compile error rather than a silently dropped column.

**Decide where the persistence layer lives, and record it.** This task puts it in `services/api` because that
is the only module in M2 that owns all the aggregates. `apps/admin` (`T-040`) also needs tiers and orders.
Three options: duplicate the documents (no), extract a `libs/persistence` module (a new Gradle module, which
`CLAUDE.md` says to think twice about), or have `apps/admin` call `services/api`. **Choose, justify it in the
ledger, and do not leave it implicit** — `T-040` is blocked on the answer.

**The order-number sequence must be atomic, and proven so.** Use a single findAndModify-style atomic increment
on a per-date counter document — never read-then-write. `OrderNumberSequenceTest` allocates N numbers from C
concurrent subscribers and asserts **N distinct values** with no gaps beyond the documented policy. A test
that allocates sequentially proves nothing; this must be concurrent.

**Testcontainers, and the container is shared per suite.** Add the Testcontainers BOM and its `mongodb` module
to the catalog. `MongoTestSupport` starts one container for the whole suite (static, or via the singleton
pattern) and injects the connection string with `@DynamicPropertySource`. Starting a container per test class
will make M2's twelve tasks unbearably slow and will be blamed on the wrong thing.

**Nothing blocks.** Repositories return `Mono`/`Flux`. No `.block()`, no `subscribeOn` compensating for a
blocking driver call, and the blocking MongoDB driver must not be on the classpath — the catalog's
`mongodb-driver-reactivestreams` and `spring-boot-starter-data-mongodb-reactive` aliases exist for this and are
currently unused.

**Indexes are declared, not discovered.** Every field a downstream query filters on gets an index declared with
the document. At minimum: product category, product name for search (`T-031`), order customer id, order
number (unique), and fulfillment tier region. A unique index on order number is the second half of BR-6's
guarantee.

**No credential in `application.yml`.** Use a property placeholder with a local default that points at a local
instance. TruffleHog scans every PR.

## 5. Acceptance criteria

1. `./gradlew :services:api:build` is `BUILD SUCCESSFUL`.
2. Testcontainers and its Mongo module resolve from `gradle/libs.versions.toml`; no version literal in any
   module build file.
3. No domain type in `libs/share` carries a Spring Data annotation — re-asserted by `T-021`'s rule 2 still
   passing.
4. Every mapper round-trips: `toDomain(toDocument(x))` equals `x` for a fixture of each aggregate, including
   the `Money` scale and the `OrderLine` snapshot fields.
5. `OrderNumberSequenceTest` allocates at least 200 numbers across at least 8 concurrent subscribers and
   asserts all are distinct.
6. A second allocation on a different date restarts the daily sequence.
7. A unique index exists on order number, and inserting a duplicate fails.
8. One Mongo container serves the whole test suite — asserted by logging or by container-id identity across
   two test classes.
9. `grep` finds no `.block()` in `services/api/src/main`, and the blocking `mongodb-driver-sync` is absent from
   the runtime classpath.
10. No credential or connection URI with embedded password in any committed file.

## 6. Verification

```
cd <repo>
./gradlew :services:api:build :services:api:test
./gradlew :services:api:dependencies --configuration runtimeClasspath | grep -i mongodb
grep -rn '\.block()' services/api/src/main   # expect nothing
grep -rniE 'mongodb\+srv://[^$]|password:' services/api/src/main/resources/application.yml   # expect nothing
grep -rn 'testcontainers' gradle/libs.versions.toml
```

Expected: `BUILD SUCCESSFUL`; only the reactive-streams driver on the runtime classpath; no blocking calls; no
credential in `application.yml`; Testcontainers in the catalog.

## 7. Out of scope

Any business query or endpoint (`T-031` onward). Migrations or schema versioning — a document store with no
production data needs none yet; say so rather than building one. Atlas-specific configuration and Private
Service Connect (`T-070`…`T-078`). Caching. Read models and projections beyond what the round-trip test needs.

## 8. Hazards

- **Annotating the `libs/share` aggregates** is the fastest route to a working repository and it breaks
  `T-021`'s rule 2 and Architecture §8 at once. The mapper boilerplate is the price of the boundary.
- A read-then-write sequence allocator passes every sequential test and issues duplicate order numbers the
  first time two checkouts land together. AC-5 is the guard.
- A container per test class turns a 2-minute suite into 20 and will be attributed to Mongo rather than to the
  harness.
- Pulling in `spring-boot-starter-data-mongodb` (blocking) instead of the reactive starter compiles, runs, and
  silently blocks the event loop — exactly what Architecture §5 exists to avoid.
- Auto-mapping documents to domain records by reflection hides a dropped field until an order loses its tax
  snapshot.
- Leaving the "where does persistence live" question implicit blocks `T-040` and invites a duplicate document
  layer.

## 9. On completion

Mark the T-030 row done in [`README.md`](README.md). Record the persistence-placement decision, the sequence
policy (gaps allowed or not), and the declared indexes — `T-031`, `T-036`, `T-040` and `T-041` all build on
them.
