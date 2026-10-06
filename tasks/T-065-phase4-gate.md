# T-065 — Phase 4 exit gate: the two filtering proofs and required checks

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/modernization-plan.md`](../docs/modernization-plan.md), the plan wins** — it defines the criterion
> this task proves. Update this task's row in [the ledger](README.md) in the same pull request.

**Parent issue:** [#4 — Phase 4 — CI/CD](https://github.com/rednavis/metal-desk/issues/4)

**This task:** [#47](https://github.com/rednavis/metal-desk/issues/47)

**Milestone:** M4 CI/CD · **Estimate:** 2 h

**Preconditions** — `T-060` … `T-064` merged.

**This task closes Phase 4 and issue #4.**

**Goal** — Demonstrate the exit criterion with real pull requests, and make the build a **required** status check so
a red build actually blocks a merge.

## 1. Why this task exists

The exit criterion is a pair of observations, not a configuration:

> **Exit criteria:** a PR touching only `apps/web` does not trigger a JVM build; a PR touching `libs/share` triggers
> every downstream module's build.

Both must be demonstrated on real pull requests, because path filtering is exactly the kind of thing that works in
review and not in practice.

There is also a gap worth closing in the same breath. The pull-request template asks a contributor to certify "CI
green", and until now that certified a docs build and a secret scan. Making the build a required check is what turns
`T-061` and `T-062` from advisory into a gate — and it needs repository admin rights, so it cannot be done by the
workflow alone.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| The exact exit criterion | [Plan Phase 4](../docs/modernization-plan.md), issue #4 |
| `CI green` is a pull-request checklist item | `.github/pull_request_template.md` |
| CI previously did not build the code | `CLAUDE.md` — this task makes that note obsolete |
| Empty-matrix behaviour of the JVM job | `T-061` |
| Root-scoped frontend checks are not per-app filtered | `T-062` |
| Keep `docs/architecture.md` in sync in the same PR | `CLAUDE.md` |

## 3. Deliverables

| Path | What |
|---|---|
| `tasks/README.md` (modify) | Phase 4 closed, with the two proofs recorded |
| `CLAUDE.md` (modify) | Replace the "CI does not build the code" note with what CI now does |
| `docs/modernization-plan.md` (modify) | Record Phase 4 as met, and correct the README's "path-filtered affected-target builds" claim |
| `.github/workflows/README.md` (modify) | The required-checks list and the skipped-job caveat |
| `CONTRIBUTING.md` (modify) | What "CI green" now means |
| Two demonstration pull requests | Referenced from the ledger by number; not merged unless trivial |

## 4. Specification

**Prove the criterion with two throwaway pull requests, and record their numbers.**

1. A pull request touching **only** `apps/web/src` — for example a comment in a component. Record which jobs ran.
   The JVM job must not build any module.
2. A pull request touching **only** `libs/share` — for example a Javadoc line. Record which jobs ran. All five
   downstream modules must build.

Take the evidence from the Actions run, not from reasoning about the filter file. Link both runs in the ledger. Close
the demonstration pull requests without merging, or merge them if the change is genuinely harmless.

Add a third observation worth having: a pull request touching **only** `docs/` should trigger neither build.

**Make the checks required, and handle the skipped-job problem explicitly.** Branch protection on `master` should
require the JVM build, the frontend build, the secret scan and the dependency scan. But `T-061` and `T-062` are
conditional: on a frontend-only pull request the JVM job does not run. **A required check that never reports blocks
the pull request forever.**

Resolve it one of the two standard ways: either the job always runs and exits early as a success (so it always
reports), or a small aggregating job that depends on the conditional ones and succeeds when they are successful or
skipped becomes the single required check. **Recommend the aggregating job** — it keeps one required check to
configure and makes the skip semantics explicit in the workflow rather than in branch-protection settings.

**Branch protection needs admin rights, so state clearly what a maintainer must do.** The workflow cannot configure
it. Write the exact list of required checks in the workflow README and in the ledger, and if you do not have the
rights, record it as outstanding rather than implying it is done — an unenforced required check is the difference
between a gate and a suggestion.

**Correct the documentation that is now wrong or now right.** `CLAUDE.md`'s "CI does not build the code" becomes
false with this phase — update it, because it is loaded into every session and a stale warning there is worse than
none. `README.md` already advertises "path-filtered affected-target builds" under CI/CD, which was aspirational; it
becomes true here, so verify the wording matches what was built.

**Do not weaken a gate to make the proof pass.** If the `libs/share` pull request reveals that a downstream module is
not built, fix the filter — do not adjust the criterion.

## 5. Acceptance criteria

1. A pull request touching only `apps/web/src` runs the frontend job and builds **no** JVM module — evidenced by a
   linked Actions run.
2. A pull request touching only `libs/share` builds `libs:share` and all five downstream modules — evidenced by a
   linked Actions run.
3. A pull request touching only `docs/` triggers neither build.
4. Every required check reports a conclusion on all three pull requests — none is left permanently pending.
5. The aggregating job (or the always-run-and-exit-early shape) is implemented and documented.
6. The required-checks list is written down in the workflow README and the ledger.
7. Branch protection is configured, or the outstanding admin action is recorded explicitly as not done.
8. A deliberately failing build on a scratch pull request blocks merge — proven if branch protection is in place, or
   recorded as unverifiable if it is not.
9. `CLAUDE.md` no longer says CI does not build the code, and describes what it now does.
10. `docs/modernization-plan.md` records Phase 4 as met, and `README.md`'s CI/CD wording matches reality.
11. No quality gate was weakened or skipped to make any proof pass.
12. `actionlint` is clean and all four jobs pass on `master`.

## 6. Verification

```
cd <repo>
docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint -color
gh pr list --state all --limit 10
gh run list --limit 10
grep -n 'CI does not build' CLAUDE.md          # expect nothing
grep -ni 'path-filtered' README.md docs/modernization-plan.md
```

Expected: actionlint clean; the demonstration pull requests and their runs listed; the stale `CLAUDE.md` note gone;
the path-filtering claims matching what exists.

## 7. Out of scope

Deployment and Cloud Run (`T-070`…`T-078`). Container image builds (`T-073`). Release tagging or versioning.
Performance tuning beyond `T-063`. Adding new quality gates.

## 8. Hazards

- **A required check that is skipped on some pull requests** leaves them permanently unmergeable, and the usual
  reaction is to remove the requirement — losing the gate entirely.
- Proving the criterion by reading the filter file rather than running a pull request is exactly the mistake the
  criterion is written to prevent.
- Adjusting the criterion when the second proof fails converts a genuine finding into a documentation change.
- Leaving `CLAUDE.md`'s "CI does not build the code" in place misleads every future session, and it is loaded every
  time.
- Recording branch protection as configured when nobody with admin rights did it means a red build still merges.
- Merging the demonstration pull requests without checking their content adds noise to `master`.

## 9. On completion

Mark the T-065 row done in [`README.md`](README.md), record **Phase 4 closed** with both Actions run links, the
required-checks list, and whether branch protection was actually applied. Comment the same evidence on
[issue #4](https://github.com/rednavis/metal-desk/issues/4) and close it.
