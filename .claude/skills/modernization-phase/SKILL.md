---
name: modernization-phase
description: Verify a GitHub issue against its Modernization Plan phase's stated exit criteria for metal-desk, empirically (not just by reading docs), and report results. Use when asked to check whether a phase/issue's conditions are met, or before opening a PR for a modernization-plan phase.
disable-model-invocation: true
---

The user will name a GitHub issue (e.g. `#1`) or a phase number from `docs/modernization-plan.md`.
$ARGUMENTS

1. Fetch the issue with `gh issue view <number>` (repo is `rednavis/metal-desk`) to get its stated
   scope and any exit criteria in the issue body/comments.
2. Read `docs/modernization-plan.md` and find the matching phase — each phase has an explicit "Exit"
   condition. Cross-reference the issue against it; the issue is the source of truth for scope, the
   plan is the source of truth for the phase's exit bar.
3. For each condition, verify it **empirically**, not by inspection alone — e.g. actually run
   `./gradlew projects`, make a trivial one-module change and confirm no other module's build file is
   touched, run `./gradlew build` / `pnpm -r run build`, check CI config triggers path-filtering by
   testing which jobs a scoped diff would trigger, etc. Prefer the `verify` skill for the build/run
   portions.
4. Write results to an **untracked** `OUTPUT.md` at the repo root (do not `git add` it unless asked) —
   a table of each condition, pass/fail, and the empirical evidence (command + output) for each. Note
   any doc/code inconsistencies found along the way as open items, without fixing them unless asked.
5. Do not post the results as a GitHub issue comment unless explicitly asked to.
