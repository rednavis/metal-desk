---
title: "ADR-0003: GCP target architecture"
parent: ADRs
nav_order: 3
---

# ADR-0003: GCP as the reference deployment target

**Status:** Accepted

## Context

A system with this shape — a handful of small stateless services, a static SPA, a managed document
store, and no need for anything resembling a compute cluster — has to pick a concrete cloud target
to make [Architecture §7](../architecture.md#7-reference-deployment-gcp) and
[Modernization Plan, Phase 5](../modernization-plan.md#phase-5--gcp-infrastructure) buildable rather
than abstract. A 2022-era version of a system like this would typically default to AWS ECS Fargate
behind an ALB, deployed via CloudFormation. This reference build deliberately targets GCP instead.

## Decision

| Component | GCP service | Why |
|---|---|---|
| `api`, `pricing-bridge` | Cloud Run | Fully managed, scale-to-zero-capable containers; no cluster to operate for four small stateless services. |
| `admin` | Cloud Run, behind Identity-Aware Proxy | IAP replaces a hand-rolled staff-auth layer entirely — one less thing this codebase has to get right. |
| `web` | Cloud Storage + external HTTPS Load Balancer + Cloud CDN | A static SPA needs a CDN and a bucket, not an application server or a container. |
| Document store | MongoDB Atlas on GCP (Private Service Connect) | Keeps the document-model fit from [Architecture §3](../architecture.md#3-domain-model) without a first-party GCP document database or self-hosting MongoDB. |
| Images | Artifact Registry | Immutable, commit-SHA-tagged images — never `latest` in any environment. |
| Secrets | Secret Manager | Injected as Cloud Run environment variables at deploy time. |
| CI → CD auth | Workload Identity Federation | No long-lived service-account JSON key ever stored in GitHub. |
| IaC | Terraform, GCS backend | Versioned state per environment. |

## Consequences

**Positive:**
- Every compute component is serverless-managed (Cloud Run); there is no cluster, node pool, or
  autoscaling group configuration for a system that fundamentally doesn't need one.
- Workload Identity Federation removes the single most common CI/CD secret-hygiene failure — a
  long-lived cloud credential sitting in a CI secret store — by design, not by policy.
- The reusable Cloud Run Terraform module covers three of the four deployables, keeping the
  infrastructure code proportional to the system's actual size.

**Costs, accepted deliberately:**
- MongoDB Atlas is a paid managed dependency outside GCP's own billing/IAM boundary (mitigated by
  Private Service Connect for the network path, but not eliminated as an operational dependency).
- This target is a fresh design, not a lift-and-shift of any prior deployment — there is no
  "migrate the existing infrastructure" phase in this plan, because there is no prior infrastructure
  in this repository to migrate. Anyone adapting this reference architecture for a real system with
  existing infrastructure would need their own transition plan, which is out of scope here.

**Explicitly not decided here:** Kubernetes/GKE is not evaluated — nothing about this system's shape
justifies the operational cost of a cluster, so it wasn't a serious candidate rather than a rejected
one.
