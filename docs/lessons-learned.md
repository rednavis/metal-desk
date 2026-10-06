---
title: Lessons Learned
nav_order: 5
---

# Lessons Learned
{: .no_toc }

<details open markdown="block">
  <summary>Table of contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

This page exists because a reference architecture that only shows the happy path teaches less than
one that also shows the failure mode it was built to avoid. Everything below is a **generalized
engineering pattern**, not a case study of any specific codebase — see the
[provenance note](../README.md#provenance) for what this repository is and isn't derived from.

## The shared library nobody depends on

This is the single most influential failure mode behind this reference architecture's module
boundaries (see [Architecture §8](architecture.md#8-build-graph)), and it is common enough in
multi-service Java/Spring systems to be worth naming precisely.

**The pattern:** a team extracts a shared domain library early — documents, DTOs, enums,
exceptions — with good intentions. But in a multi-repo setup, actually consuming that library means
adding a real dependency, publishing or resolving an artifact, and accepting its release cadence.
Under delivery pressure, it is always faster to copy the handful of classes a new service actually
needs into that service's own tree and move on. The library ships; nothing depends on it; its
classes get copy-pasted into every consumer instead.

**Why it's worse than it sounds.** A copy is not a snapshot — it is a fork. Each consumer's copy
evolves independently: a validation rule tightens in one place and not another, a field gets added
to support one service's need, a bug gets fixed in one copy and reproduced identically in the
others because nobody remembered the class had siblings. Months later, the "shared" library is
fiction, several near-identical classes model the same real-world entity slightly differently across
services, and — the genuinely dangerous version of this — two of those classes can be different
shapes of "the same" record in the same underlying datastore, which is a production data-integrity
risk hiding behind what looks like a code-cleanliness problem.

**Why it happens even to competent teams.** It is never one bad decision — it's death by a thousand
reasonable ones, each locally correct under the deadline that produced it. The actual fix is not "be
more disciplined"; discipline doesn't survive contact with a delivery deadline. The fix is
structural:

1. **One build graph.** If the shared library and every consumer live in the same repository with
   the same build graph, "add a real dependency" and "copy the class" cost the same number of
   keystrokes — so there's no delivery-pressure reason to pick the copy.
2. **Enforce it in CI, not in review.** A lint/ArchUnit rule that fails the build if a domain type is
   declared outside the shared module turns "please don't copy this class" from a code-review
   opinion into a fact about whether the build is green. See
   [Architecture §8](architecture.md#8-build-graph).
3. **Toolchain parity is a precondition, not a nice-to-have.** If one consumer is pinned to an older
   language/runtime version than the shared library targets, that consumer *cannot* adopt the
   library's newer idioms even when it wants to — which quietly reintroduces the incentive to fork.
   Unify toolchain versions before attempting to unify the domain model, not after.

## Configuration and tooling drift compounds silently

A related, smaller version of the same story shows up in static-analysis configuration, dependency
version pins, and CI definitions: when every service repository carries its own copy of
`checkstyle.xml`, its own pinned linter/plugin versions, its own CI workflow file, those copies drift
the same way the domain classes do — not from any single decision, but from N independent "just
bump this one thing to unblock my PR" moments. A single version catalog and a single set of
convention plugins, applied from one place to every module, removes the *opportunity* for that drift
rather than relying on someone noticing it after the fact.

## Prefer "fix forward" over pinning back when unifying versions

When consolidating several previously-independent services onto one version policy, at least one
service is usually behind (a different minor version of the same framework, say). The tempting
shortcut is to pin the newer services back to match the oldest one, because that guarantees nothing
breaks today. That trades a small, contained migration now for a larger, less-contained one later —
and the version gap tends to grow, not shrink, if nothing forces it closed. The better default is to
bring the lagging service forward and treat whatever breaks as scoped, attributable work, rather than
deferring the whole fleet's upgrade indefinitely to protect one service's test suite.

## A monorepo doesn't fix anything by itself

Worth stating plainly, because it's the one lesson easiest to get backwards: moving several
repositories into one directory tree does not, on its own, stop domain models from forking or
configuration from drifting — it only makes both problems *visible in one place* and removes the
cross-repository friction that made the disciplined choice more expensive than the shortcut. The
actual fix is still the structural enforcement in the previous sections; the monorepo is what makes
that enforcement possible to write in the first place (one CI job can see the whole build graph),
not a substitute for writing it.

---

See also: [Architecture](architecture.md) · [Modernization Plan](modernization-plan.md) ·
[Business Requirements](business-requirements.md) · [ADRs](adr/)
