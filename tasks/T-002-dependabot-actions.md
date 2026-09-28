# T-002 — Dependabot for GitHub Actions

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with the actual contents of
> `.github/workflows/ci.yml`, the workflow wins** — report the mismatch rather than writing a
> Dependabot entry for an action that is not there. Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#7 — Add Dependabot config for GitHub Actions](https://github.com/rednavis/metal-desk/issues/7)

**This task:** [#10](https://github.com/rednavis/metal-desk/issues/10)

**Milestone:** M0 Repo hygiene · **Estimate:** 30 min

**Preconditions** — none. Independent of `T-001` and of every phase.

**Goal** — Add `.github/dependabot.yml` with a single `github-actions` entry, so the actions pinned in
`.github/workflows/ci.yml` get automatic version-bump pull requests.

## 1. Why this task exists

`ci.yml` pins three third-party actions by tag or branch. Nothing currently tells anyone when one of
them ships a fix, and an action pinned to a floating ref is a supply-chain surface the repository does
not otherwise accept — [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) forbids committed
credentials precisely because CI is treated as an attack surface, and an unreviewed action upgrade is
the same class of risk arriving by a different door.

Adding this in M0 rather than with the rest of CI (`T-060`…`T-065`) means the bump stream starts
before there is a JVM or npm build to break, so the first few Dependabot pull requests are trivially
reviewable.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Which actions exist and at which refs | `.github/workflows/ci.yml` — read it, do not trust this spec or the parent issue |
| No `gradle` or `npm` ecosystem entry yet | [Parent issue #7](https://github.com/rednavis/metal-desk/issues/7); the build graph they would scan arrives in `T-060`…`T-065` |
| Secret scanning stays TruffleHog; do not swap it | `CLAUDE.md`, and commit `5923005` |

**Precedence:** the workflow file wins over both this spec and the parent issue.

## 3. Deliverables

| Path | What |
|---|---|
| `.github/dependabot.yml` | The only file this task creates |

## 4. Specification

**First, read `.github/workflows/ci.yml` and list its `uses:` lines.** At the time of writing they are
`actions/checkout@v4`, `actions/jekyll-build-pages@v1` and `trufflesecurity/trufflehog@main`.

> **The parent issue is stale on this point.** Issue #7 names `gitleaks/gitleaks-action`. That action
> was removed in commit `5923005` ("gitleaks-action requires a paid license for org repos") and
> replaced by TruffleHog. Write the entry for what the workflow actually uses, and say so in your pull
> request body — do not re-add gitleaks, and do not replace TruffleHog.

The file declares `version: 2` and exactly one `updates` entry:

- `package-ecosystem: "github-actions"`
- `directory: "/"` — for this ecosystem Dependabot always scans `.github/workflows/`, regardless of
  what `directory` says; `/` is the conventional value and the only one that works.
- `schedule: { interval: "weekly" }`
- `open-pull-requests-limit`: a small number (2–5). The default of 5 is acceptable; state a value
  rather than leaving it implicit.
- `commit-message: { prefix: "ci" }` so the bumps match
  [`CONTRIBUTING.md`](../CONTRIBUTING.md#commit-and-pr-conventions).

Add a comment block naming the two ecosystems deliberately left out and what unblocks each:

```
# gradle  — added by T-060..T-065, once a JVM build runs in CI.
#           gradle/libs.versions.toml is the single version source (ADR/Plan Phase 1),
#           so a gradle entry must raise the catalog, never a module build file.
# npm     — added by T-060..T-065. pnpm workspace; Dependabot reads pnpm-lock.yaml.
```

`trufflesecurity/trufflehog@main` is a **branch** ref. Dependabot cannot bump a branch — it only
updates tags, semver ranges and commit SHAs. Note this in a comment beside the entry; whether to
repin TruffleHog to a tag or a SHA is a decision for `T-064`, not this task.

## 5. Acceptance criteria

1. `.github/dependabot.yml` exists, parses as YAML, and declares `version: 2`.
2. It contains exactly **one** `updates` entry, and its `package-ecosystem` is `github-actions`.
3. No `gradle` and no `npm` entry is present; both are named in a comment with what unblocks them.
4. A comment records that the `trufflehog@main` branch ref is outside Dependabot's reach.
5. `.github/workflows/ci.yml` is **unmodified** — this task adds a config file and changes no workflow.
6. Every action named in the file is one that `grep 'uses:' .github/workflows/ci.yml` actually returns.
7. The pull request body states that issue #7's reference to `gitleaks-action` is stale.

## 6. Verification

```
cd <repo>
python3 -c "import yaml,sys; d=yaml.safe_load(open('.github/dependabot.yml')); print(d['version'], len(d['updates']), d['updates'][0]['package-ecosystem'])"
grep -n 'uses:' .github/workflows/ci.yml
git diff --stat .github/workflows/ci.yml     # expect empty
grep -nE 'gradle|npm|trufflehog' .github/dependabot.yml
```

Expected: `2 1 github-actions`; three `uses:` lines, none of them gitleaks; no diff in `ci.yml`; the
three greps each hit a comment line.

## 7. Out of scope

Any `gradle` or `npm` ecosystem entry (`T-060`…`T-065`). Repinning `trufflehog@main` to a tag or SHA
(`T-064`). Auto-merge rules, Dependabot groups, or a `dependabot` label. Touching `ci.yml` at all.

## 8. Hazards

- **Writing the entry from the parent issue instead of the workflow** produces a config that watches an
  action the repository does not use. Read `ci.yml`.
- Dependabot validates `dependabot.yml` on push and reports errors on the repository's Insights →
  Dependency graph → Dependabot tab, **not** as a failing check. A malformed file fails silently; the
  §6 YAML parse is the only local proof you get.
- Adding a `gradle` entry "since the build now exists" pins versions in module files, which the
  single-version policy forbids — the catalog is the only place a version is written. Leave it out.
- `directory` is not a filter for this ecosystem. Setting it to `/.github/workflows` is a common and
  silent mistake: the entry still works, which makes it look correct, and then reads as precedent.

## 9. On completion

Mark the T-002 row done in [`README.md`](README.md). Record in its Notes column which actions were
actually found in `ci.yml`, so the next person does not re-derive it, and flag the stale
`gitleaks-action` reference in issue #7 so the issue can be corrected or closed with a note.
