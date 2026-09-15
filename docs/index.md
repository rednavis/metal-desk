---
title: Home
nav_order: 1
description: "MetalDesk — a reference precious-metals e-commerce platform: business requirements, target architecture, and a from-scratch modernization plan."
permalink: /
---

# MetalDesk — a reference precious-metals trading platform

This site documents a reference architecture for a precious-metals e-commerce and trading
platform — live spot pricing, a shopping cart with a manager-mediated handoff for large orders, and
multi-provider payments — built as a from-scratch, MIT-licensed monorepo targeting GCP, with every
external dependency mocked at the boundary.

It exists to demonstrate a specific piece of engineering judgment: how to design a small multi-service
system so that a well-known failure mode (a shared domain model that forks because nobody actually
depends on it) is structurally prevented rather than relying on discipline to avoid it. See
[Lessons Learned](lessons-learned.md) for the failure mode itself, and
[Architecture](architecture.md) for how the module boundaries here respond to it.

## Start here

| Page | What's in it |
|---|---|
| [Business Requirements](business-requirements.md) | The functional and business-rule spec this system implements — catalog, checkout, tiered fulfillment, payments. |
| [Architecture](architecture.md) | C4 system context and containers, the domain model, the payment-provider abstraction, and the GCP reference deployment. |
| [Modernization Plan](modernization-plan.md) | The build order — from an empty repository to a deployed system — and the standards held constant across every phase. |
| [Lessons Learned](lessons-learned.md) | The engineering failure modes this design exists to prevent, stated generally rather than as a specific case study. |
| [ADRs](adr/) | The decisions that were actually weighed against an alternative, with their accepted costs written down alongside their benefits. |

## Provenance

See the [repository README](https://github.com/rednavis/metal-desk#provenance) for what this project
is derived from and what it deliberately is not.
