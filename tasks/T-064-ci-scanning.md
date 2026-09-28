# T-064 — CI: dependency scanning, and extending the secret scan

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with `CLAUDE.md` or the parent issue, that document
> wins.** Update this task's row in [the ledger](README.md) in the same pull request.

**Parent issue:** [#4 — Phase 4 — CI/CD](https://github.com/rednavis/metal-desk/issues/4)

**This task:** [#46](https://github.com/rednavis/metal-desk/issues/46)

**Milestone:** M4 CI/CD · **Estimate:** 3 h

**Preconditions** — `T-061` and `T-062` merged, so there are both a JVM and an npm dependency graph to scan.

**Goal** — Scan dependencies and secrets on **every** pull request, extending the existing TruffleHog job rather than
replacing it, and complete the Dependabot coverage `T-002` deliberately deferred.

## 1. Why this task exists

The parent issue is unusually prescriptive here:

> Dependency and secret scanning on every PR (the docs-only `secrets-scan` job in `ci.yml` is the seed for this —
> **extend it, don't replace it**)

There is history behind that. Commit `5923005` replaced `gitleaks-action` with TruffleHog because gitleaks requires a
paid licence for organisation repositories, and `CLAUDE.md` records the outcome. Swapping the scanner again would
re-run a decision already made and paid for.

`T-002` also left an explicit gap: Dependabot covers GitHub Actions only, because no JVM or npm build graph existed.
Both now exist.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Dependency and secret scanning on every PR, from the start | [Plan Phase 4](../docs/modernization-plan.md), issue #4 |
| Extend the existing `secrets-scan` job, do not replace it | Issue #4 |
| Secret scanning stays TruffleHog | `CLAUDE.md`, commit `5923005` |
| `gradle` and `npm` Dependabot entries arrive with the build graph | `T-002`, issue #7 |
| Versions live only in `gradle/libs.versions.toml` | `CLAUDE.md` |
| `trufflehog@main` is a branch ref Dependabot cannot bump | `T-002` |
| No credentials in CI or the repository | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |
| Fix forward when unifying a version | [Lessons Learned](../docs/lessons-learned.md#prefer-fix-forward-over-pinning-back-when-unifying-versions) |

## 3. Deliverables

| Path | What |
|---|---|
| `.github/workflows/ci.yml` (modify) | Extend `secrets-scan`; add a `dependency-scan` job |
| `.github/dependabot.yml` (modify) | Add the `gradle` and `npm` ecosystem entries; repin the action refs |
| `.github/workflows/README.md` (modify) | What each scanner covers, and what it does not |
| `CONTRIBUTING.md` (modify) | Note what a contributor should expect a scan to reject |

## 4. Specification

**Keep `secrets-scan` unfiltered and keep TruffleHog.** It must run on every pull request with no path filter — a
secret can be committed in any file, and `T-060` already protected this. Extend it rather than replacing it: review
whether `--results=verified,unknown` is the right setting (it is the current one), ensure the full pull-request diff
range is scanned rather than a single commit, and make the job fail on a finding rather than only annotating.

**Repin `trufflesecurity/trufflehog@main` to a tag or a commit SHA.** `T-002` identified this and deferred it here.
A floating `main` means an upstream change runs in CI unreviewed, and Dependabot cannot bump a branch ref, so the
action is simultaneously unpinned and unwatched. Pin to a SHA (with the version in a comment) so Dependabot can
update it. Do the same for any other action still on a branch.

**Add the `gradle` and `npm` Dependabot entries**, with the constraint `T-002` recorded: a `gradle` bump must raise
`gradle/libs.versions.toml`, never a module build file, because the single-version policy has no exceptions.
Dependabot updates the catalog when versions are declared there, which they are. For npm, point at the pnpm
workspace; Dependabot reads `pnpm-lock.yaml`.

Group the updates so the volume is reviewable — patch-level updates grouped, majors separate. A flood of
single-dependency pull requests is how dependency automation gets switched off.

**Dependency *scanning* is not Dependabot.** Dependabot proposes upgrades; scanning fails a pull request that
introduces a known-vulnerable dependency. Add a job that does the latter for both ecosystems. Use tooling that needs
no paid licence and no credential — the gitleaks lesson applies to every scanner choice here. Enable GitHub's
dependency review on pull requests if it is available for this repository; if it is not, or requires a paid plan,
**say so explicitly rather than half-configuring it.**

**Decide and document the failure policy.** Fail on high and critical severity; report but do not block on lower.
A scanner that blocks on every advisory in a transitive test dependency will be bypassed within a week. Write the
threshold down so it is a decision rather than a default.

**Handle the unavoidable unfixable finding.** There will eventually be an advisory with no available upgrade. Provide
an explicit, auditable suppression mechanism with a required expiry date and a reason, and state that a suppression
without both is not acceptable. Do not add a suppression in this task unless a real finding requires it.

**No credentials.** Every scanner must work with the default `GITHUB_TOKEN` and minimal explicit `permissions:`. If
a scanner needs an account, choose a different scanner.

## 5. Acceptance criteria

1. `actionlint` reports no findings.
2. `secrets-scan` still uses TruffleHog, still runs on every pull request with no path filter, and fails the job on a
   finding — proven with a synthetic fake credential on a scratch branch, then removed.
3. The scan covers the whole pull-request diff range, not just the head commit.
4. No action in any workflow is pinned to a branch — every `uses:` is a tag or SHA.
5. `dependabot.yml` has `github-actions`, `gradle` and `npm` entries; the `gradle` entry targets the catalog.
6. Updates are grouped so patch bumps arrive together and majors separately.
7. A pull request adding a dependency with a known high-severity advisory fails the dependency-scan job — proven once.
8. A low-severity advisory reports without blocking, per the documented threshold.
9. The suppression mechanism requires a reason and an expiry date, and is documented; no suppression is added without
   a real finding.
10. Every scanner runs with the default token and explicit minimal `permissions:`; no credential or account is
    required.
11. `CONTRIBUTING.md` tells a contributor what the scans reject and how to respond.
12. If GitHub dependency review is unavailable for this repository, that is stated rather than silently skipped.

## 6. Verification

```
cd <repo>
docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint -color
grep -nE 'uses: .*@(main|master|v?[0-9]+)$' .github/workflows/*.yml   # inspect: expect SHAs or tags, no branches
python3 -c "import yaml;d=yaml.safe_load(open('.github/dependabot.yml'));print([u['package-ecosystem'] for u in d['updates']])"
grep -n 'permissions:' .github/workflows/ci.yml
grep -n 'trufflehog' .github/workflows/ci.yml
```

Expected: actionlint clean; no branch-pinned actions; three ecosystems listed; explicit permissions; TruffleHog still
in place.

## 7. Out of scope

Container image scanning — no image is built until `T-073`. SAST beyond what SpotBugs already provides. Licence
compliance scanning. Runtime vulnerability monitoring. Branch protection (`T-065`). Acting on findings — this task
establishes the gate.

## 8. Hazards

- **Replacing TruffleHog** re-opens a decision already made for a licensing reason, and `gitleaks-action` will fail
  again on an organisation repository.
- Path-filtering the secret scan makes it possible to commit a secret in a filtered path.
- Scanning only the head commit misses a secret added in an earlier commit of the same pull request and removed from
  the final diff — it is still in the history.
- Leaving actions pinned to `main` means unreviewed third-party code runs with repository permissions on every PR.
- A `gradle` Dependabot entry that edits module build files violates the single-version policy that `T-021` and the
  plan both enforce.
- Blocking on every severity gets the scanner disabled or bypassed, which is worse than a calibrated threshold.
- A suppression without an expiry becomes permanent and invisible.
- Choosing a scanner that needs a paid account repeats the gitleaks mistake exactly.

## 9. On completion

Mark the T-064 row done in [`README.md`](README.md). Record the severity threshold, the scanners chosen and why, the
SHA the TruffleHog action is now pinned to, and whether dependency review was available.
