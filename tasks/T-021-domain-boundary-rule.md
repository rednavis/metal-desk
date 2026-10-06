# T-021 — The `no domain type outside libs/share` rule, and the Phase 2 exit gate

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md`](../docs/architecture.md) or
> [`docs/lessons-learned.md`](../docs/lessons-learned.md), that document wins** — open an issue rather
> than implementing either version. Update this task's row in [the ledger](README.md) in the same pull
> request.

**Parent issue:** [#2 — Phase 2 — Domain model and libs](https://github.com/rednavis/metal-desk/issues/2)

**This task:** [#22](https://github.com/rednavis/metal-desk/issues/22)

**Milestone:** M1 Domain model and libs · **Estimate:** 2 h

**Preconditions** — `T-010` … `T-020` merged. The rule needs a domain package with something in it to
police, and the exit gate needs all of `libs/*` complete.

**This task closes Phase 2 and issue #2.**

**Goal** — Enforce the architectural boundary in the build, not in review comments, and prove Phase 2's exit
criterion: every downstream module compiles against `libs/*` with zero duplicated domain types.

## 1. Why this task exists

The parent issue and the plan both insist on the timing:

> wire the "no domain type outside `libs/share`" check into CI **in this phase, not later** — it only has
> value if it exists before there's anything to violate it.

[`docs/lessons-learned.md`](../docs/lessons-learned.md#the-shared-library-nobody-depends-on) is the
first-hand account of what happens without it. And there is a marker already in the tree: the last lines of
`build-logic/src/main/kotlin/metaldesk.quality-conventions.gradle.kts` read

> The "no domain type outside `libs/share`" rule from Architecture §8 is enforced in CI starting
> Modernization Plan Phase 2, once `libs/share` actually has a domain package to police. Not wired in here.

**This task removes that comment and replaces it with the rule.** Leaving the comment behind after wiring the
check is a documentation defect in its own right.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| "No type outside `libs/share` may declare a class in the shared domain package" | [Architecture §8](../docs/architecture.md#8-build-graph) |
| The rule exists because of a real prior failure | [Lessons Learned](../docs/lessons-learned.md#the-shared-library-nobody-depends-on) |
| Wire it in Phase 2, not later | [Modernization Plan, Phase 2](../docs/modernization-plan.md), issue #2 |
| Exit criterion: downstream compiles against `libs/*`, zero duplicated domain types | [Modernization Plan, Phase 2](../docs/modernization-plan.md), issue #2 |
| Shared build behaviour belongs in `build-logic`, not per module | `CLAUDE.md`, [Modernization Plan, Phase 1](../docs/modernization-plan.md) |
| Versions only in `gradle/libs.versions.toml` | `CLAUDE.md`, [Modernization Plan](../docs/modernization-plan.md) |

## 3. Deliverables

| Path | What |
|---|---|
| `gradle/libs.versions.toml` (modify) | Add the ArchUnit version and library aliases — the catalog has neither today |
| `build-logic/src/main/kotlin/metaldesk.quality-conventions.gradle.kts` (modify) | Wire the architecture test into `check`; **delete** the deferral comment at the end of the file |
| `libs/share/src/test/java/com/rednavis/metaldesk/share/architecture/DomainBoundaryTest.java` | The ArchUnit rules — see §4 for why it lives here |
| `libs/share/src/test/java/com/rednavis/metaldesk/share/architecture/DomainBoundaryViolationTest.java` | The negative proof: a deliberately violating fixture is rejected |
| `services/api/build.gradle.kts`, `services/pricing-bridge/build.gradle.kts`, `apps/admin/build.gradle.kts` (modify, if needed) | Declare the `libs/*` dependencies the exit criterion requires |
| `docs/architecture.md` (modify) | §8's rule sentence gains a pointer to where it is now enforced |

## 4. Specification

**Use ArchUnit, and put the version in the catalog.** `gradle/libs.versions.toml` has no ArchUnit entry
today; add a version and the `archunit-junit5` library alias there. Do **not** pin it in a module build file
— the single-version policy has no exception for test tooling.

**The rules to encode.** At minimum:

1. **No class outside `libs/share` may be declared in `com.rednavis.metaldesk.share.domain..`.** This is the
   Architecture §8 rule literally. Scope the ArchUnit import to all six JVM modules' classes, not just
   `libs/share`'s, or the rule is vacuous.
2. **`libs/share`'s domain packages must not depend on Spring, on any service module, or on
   `libs/payments`/`libs/mail`.** The domain is the leaf of the graph. This catches the inverted dependency
   before it is convenient.
3. **No duplicated aggregate names.** No class named `Order`, `Customer`, `Product`, `OrderLine`,
   `PaymentRecord`, `FulfillmentTier` or `DeliveryQuote` may exist outside `libs/share` — this is the
   mechanical form of "zero duplicated domain types" and the specific shape the Lessons Learned failure took.

**Where the test lives, and the honest trade-off.** ArchUnit needs the classes it inspects on its classpath.
`libs/share` cannot see `services/api`'s classes — the dependency runs the other way. So rule 1 and rule 3, to
be non-vacuous, must run from a module that sees everything, or once per module.

Pick one and document it:
- **(a)** a test in each downstream module, applied from the convention plugin so no module can opt out; or
- **(b)** a dedicated verification source set / module that depends on all six.

**(a) is the smaller change and it cannot be silently skipped**, because the convention plugin applies to
every module. Whichever you choose, state in the Javadoc and in the ledger why, and make sure a *new* module
added later inherits the rule automatically rather than needing a manual opt-in — a rule the next module
forgets is worse than no rule.

**Prove the rule fails.** `DomainBoundaryViolationTest` constructs the violation — a class declared in the
shared domain package from outside `libs/share`, produced as a test fixture or by an in-test bytecode/source
fixture — and asserts the rule rejects it. A rule nobody has seen fail is not known to work; this is the same
standard `T-018` applies to the timeout path.

**Wire it into `check`, not into a separate task.** `metaldesk.quality-conventions.gradle.kts` already wires
`spotlessCheck`, `checkstyleMain`, `spotbugsMain` and `jacocoTestReport` into `check`. The architecture test
runs as part of `test`, which `build` already depends on — so the wiring may be nothing more than the test
dependency plus removing the deferral comment. **Confirm empirically that `./gradlew build` actually executes
it**; do not assume.

**The exit gate.** Make the three downstream modules declare and use their `libs/*` dependencies, then prove
the criterion:

- `services/api` depends on `libs:share`, `libs:payments`, `libs:mail` (its build file already does);
- `services/pricing-bridge` on `libs:share`;
- `apps/admin` on `libs:share`.

Each must **compile against a real type from each lib**, not merely declare the dependency — an unused
`implementation` line proves nothing. A single reference in an existing class is enough.

**Update `docs/architecture.md` §8 in this pull request**, per `CLAUDE.md`'s rule that the architecture doc
stays in sync with the change that describes it.

## 5. Acceptance criteria

1. `./gradlew clean build` is `BUILD SUCCESSFUL` for all six JVM modules.
2. The ArchUnit version and library live in `gradle/libs.versions.toml`; `grep` finds no version literal in
   any module's `build.gradle.kts`.
3. The deferral comment at the end of `metaldesk.quality-conventions.gradle.kts` is **deleted**.
4. Rule 1 is non-vacuous: a test proves it inspects classes from a module other than `libs/share`.
5. `DomainBoundaryViolationTest` demonstrates the rule failing on a deliberate violation, and passing once it
   is removed.
6. Rule 2 fails if a Spring import is added to a `libs/share` domain class — verified by adding one
   temporarily and observing the failure, then reverting.
7. Rule 3 fails if a second class named `Order` is added outside `libs/share` — verified the same way.
8. A newly added JVM module inherits the rule without editing its own build file — argued in the ledger, and
   demonstrated if cheap.
9. `services/api`, `services/pricing-bridge` and `apps/admin` each compile against at least one real type
   from each `libs/*` module they declare.
10. `docs/architecture.md` §8 names where the rule is enforced.
11. `./gradlew build` executes the architecture test — proven from the build scan or `--info` output, not
    assumed.

## 6. Verification

```
cd <repo>
./gradlew clean build
./gradlew build --info | grep -i 'DomainBoundaryTest'
grep -rnE '[0-9]+\.[0-9]+\.[0-9]+' --include=build.gradle.kts libs apps services   # expect nothing
grep -n 'Not wired in here' build-logic/src/main/kotlin/metaldesk.quality-conventions.gradle.kts   # expect nothing
grep -rn 'archunit' gradle/libs.versions.toml
for m in services:api services:pricing-bridge apps:admin; do ./gradlew :$m:dependencies --configuration compileClasspath | grep -c metal- ; done
```

Expected: `BUILD SUCCESSFUL`; the architecture test named in the build output; no version literals; the
deferral comment gone; ArchUnit in the catalog; each downstream module resolving its `metal-*` libs.

## 7. Out of scope

Any further architectural rule — layering inside a service, naming conventions, cycle detection. Those are
worth having and are not this task's scope; propose them as follow-ups. The Phase 4 CI workflow that runs the
build on a PR (`T-060`…`T-065`) — this task makes the rule part of `./gradlew build`, which is what the plan
asks for in Phase 2. Any service logic.

## 8. Hazards

- **A vacuous rule is the default outcome.** ArchUnit importing only `libs/share`'s own classes will pass
  forever and catch nothing, and it looks identical in CI to a working rule. AC-4 and AC-5 exist because of
  this.
- Leaving the deferral comment in place after wiring the rule means the next reader believes the rule does not
  exist and may add a second one.
- Pinning ArchUnit in a module build file breaks the single-version policy in the same pull request that
  enforces an architectural policy.
- Making the rule opt-in per module means the next module silently escapes it — which is exactly the drift
  [Lessons Learned](../docs/lessons-learned.md#configuration-and-tooling-drift-compounds-silently) describes.
- Declaring `libs/*` dependencies without using them satisfies a `dependencies` grep and not the exit
  criterion.
- `maxWarnings = 0` and `MissingJavadocType` apply to the new test classes too.

## 9. On completion

Mark the T-021 row done in [`README.md`](README.md) and record **Phase 2 closed** there, with the exit-criterion
evidence: the `clean build` result and the per-module compile proof. Record which placement option (a or b) was
chosen for the ArchUnit tests and why. Then comment the same evidence on
[issue #2](https://github.com/rednavis/metal-desk/issues/2) and close it.
