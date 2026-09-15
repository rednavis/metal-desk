# Contributing

Thanks for taking a look. This repository is documentation-first right now (see the
[README](README.md#status) and the [Modernization Plan](docs/modernization-plan.md)), so early
contributions are as likely to be to the docs as to code — both are equally welcome.

## Getting started

1. Look through [open issues](https://github.com/rednavis/metal-desk/issues), especially ones
   labeled [`good first issue`](https://github.com/rednavis/metal-desk/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22).
   The larger issues are one per [Modernization Plan](docs/modernization-plan.md) phase — comment
   on one before starting so two people don't build the same phase in parallel.
2. Fork, branch, and open a PR against `master` when ready for review — draft PRs are welcome
   earlier than that if you want direction before finishing.
3. One reviewer approval and a green CI run are required before merge.

## Ground rules

- **No real external credentials, ever** — including sandbox/test keys. Every external dependency
  is mocked (WireMock or an in-process fake); see
  [ADR-0002](docs/adr/0002-mocked-external-dependencies.md). If a change seems to need a real
  credential to work, that's a signal the mocking boundary is in the wrong place, not a reason to
  add a secret. CI runs a secret scan on every PR.
- **No production-scale or real-company data.** Fixtures and seed data are synthetic and small.
- **A dependency version is declared once** — `gradle/libs.versions.toml` for the JVM modules, the
  `pnpm` workspace root for the frontend apps — once the build graph exists (Modernization Plan,
  Phase 1). Never pinned per-module.
- **Fix forward, not backward**, when a change would otherwise require pinning a module to an older
  version of something the rest of the system has moved past — see
  [Lessons Learned](docs/lessons-learned.md#prefer-fix-forward-over-pinning-back-when-unifying-versions).

## Code style

Not yet enforced by tooling — no code exists yet, and wiring this in is part of Modernization Plan
Phase 1 — but stated here so early code lands consistent from the first PR:

- **Java** — Google Java Style, applied via Spotless; Lombok limited to
  `@RequiredArgsConstructor` and `@Slf4j` if used at all — prefer records and constructor injection
  first. Target runtime is Java 25 / Spring Boot 4 — see
  [ADR-0004](docs/adr/0004-java25-spring-boot4-runtime.md).
- **TypeScript/React** — ESLint + Prettier, strict `tsconfig`. Function components and hooks only;
  no class components. One frontend stack for both `apps/web` and `apps/admin-web` — see
  [ADR-0005](docs/adr/0005-consolidated-react-frontend.md).

## Commit and PR conventions

- One logical change per commit; write the commit message for someone reading `git log`, not for
  yourself right now.
- Reference the [Modernization Plan](docs/modernization-plan.md) phase or the
  [ADR](docs/adr/) a change implements, where applicable.
- Keep documentation and code in sync in the same PR — a code change that makes
  [Architecture](docs/architecture.md) inaccurate is incomplete until the doc is updated too.

## Docs site

The `docs/` directory is a Jekyll site (via `remote_theme: just-the-docs`) published to GitHub
Pages. New pages need front matter (`title`, `nav_order`, and `parent` if nested under ADRs) to
appear in navigation — see any existing page for the pattern. CI builds the site on every PR that
touches `docs/` to catch config/front-matter errors before merge.

## Questions

Open an issue.
