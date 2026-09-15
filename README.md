# MetalDesk

[![CI](https://github.com/rednavis/metal-desk/actions/workflows/ci.yml/badge.svg)](https://github.com/rednavis/metal-desk/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![good first issues](https://img.shields.io/github/issues/rednavis/metal-desk/good%20first%20issue)](https://github.com/rednavis/metal-desk/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)

A reference architecture and requirements set for a precious-metals e-commerce and trading
platform — live spot pricing, a shopping cart with an automatic handoff to a human for large
orders, and multi-provider payments — designed as a monorepo from day one, with every external
dependency mocked at the boundary, targeting GCP.

**Status: documentation-first.** The [full requirements](docs/business-requirements.md),
[architecture](docs/architecture.md), and [build plan](docs/modernization-plan.md) are written;
application code has not been started yet. See the plan for the build order.

📖 **[Read the docs](https://rednavis.github.io/metal-desk/)**

## Provenance

The requirement categories and architectural patterns here are informed by a precious-metals
e-commerce delivery engagement the maintainer led in 2022, as a vendor building a platform on
behalf of a client. **This repository is not that platform.** It is an independent, from-scratch
reference implementation, written in 2026, that:

- uses a different, illustrative business framing (see the "About this document" note at the top
  of the [Business Requirements](docs/business-requirements.md)) rather than any real company's
  actual specification or configuration,
- mocks every external integration (payments, mail, market data) rather than integrating with any
  real provider or account,
- targets a different cloud (GCP, via fresh Terraform) rather than reusing any prior deployment,
  and
- contains no source code, data, secrets, or business configuration belonging to any former client.

It exists to document engineering judgment and a specific piece of design reasoning — see
[Lessons Learned](docs/lessons-learned.md) — not to reproduce, or serve as a substitute for, any
prior commercial system. See [ADR-0002](docs/adr/0002-mocked-external-dependencies.md) and
[ADR-0003](docs/adr/0003-gcp-target-architecture.md) for how the mocking and cloud-target
decisions were made.

## Documentation

| | |
|---|---|
| [Business Requirements](docs/business-requirements.md) | Functional requirements and business rules — catalog, checkout, tiered fulfillment, payments. |
| [Architecture](docs/architecture.md) | C4 context/containers, domain model, payment-provider abstraction, GCP deployment target. |
| [Modernization Plan](docs/modernization-plan.md) | Build order from an empty repo to a deployed system, and the standards held constant throughout. |
| [Lessons Learned](docs/lessons-learned.md) | The engineering failure modes this design is built to prevent. |
| [ADRs](docs/adr/) | Decisions actually weighed against an alternative, costs included. |

The same pages are published as a browsable site at
**[rednavis.github.io/metal-desk](https://rednavis.github.io/metal-desk/)**.

## Planned tech stack

| Layer | Choice |
|---|---|
| Backend | Java 25 (LTS), Spring Boot 4, WebFlux (reactive end-to-end) — see [ADR-0004](docs/adr/0004-java25-spring-boot4-runtime.md) |
| Frontend | React 19, TypeScript, Vite, pnpm workspace — one stack for both customer and staff surfaces, see [ADR-0005](docs/adr/0005-consolidated-react-frontend.md) |
| Build | Gradle multi-project, one root `settings.gradle`, `gradle/libs.versions.toml` as the single version source, convention plugins via an included `build-logic` build |
| Data | MongoDB (Atlas on GCP in the reference deployment) |
| External integrations | WireMock-mocked at the boundary — see [ADR-0002](docs/adr/0002-mocked-external-dependencies.md) |
| Infrastructure | Terraform, GCP (Cloud Run, Cloud Storage + CDN, Artifact Registry, Secret Manager) — see [ADR-0003](docs/adr/0003-gcp-target-architecture.md) |
| CI/CD | GitHub Actions, path-filtered affected-target builds, Workload Identity Federation to GCP |

Full rationale for each of these in [Architecture](docs/architecture.md) and the ADRs.

## Target repository layout

```
metal-desk/
├── libs/
│   ├── share/              # domain model — every service depends on this, nothing copies it
│   ├── payments/           # PaymentProvider abstraction + WireMock-backed adapters
│   └── mail/                # transactional mail abstraction
├── services/
│   ├── api/                 # customer-facing backend
│   └── pricing-bridge/     # market-data feed + spot-price computation
├── apps/
│   ├── admin/                # staff back office API (Spring Boot, no server-rendered UI)
│   ├── admin-web/            # staff-facing SPA (pnpm workspace) — see ADR-0005
│   └── web/                  # customer-facing SPA (pnpm workspace)
├── infra/gcp/                # Terraform
├── docs/                     # this documentation site
└── build-logic/              # Gradle convention plugins
```

Not built yet — this is the target from [Modernization Plan](docs/modernization-plan.md), shown here
so the documentation and the eventual code layout stay in sync as the latter is written.

## Contributing

External contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) to get started, or jump
straight to issues labeled
[`good first issue`](https://github.com/rednavis/metal-desk/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22).
The build order is tracked as issues, one per [Modernization Plan](docs/modernization-plan.md) phase.

## License

[MIT](LICENSE).
