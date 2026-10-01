# Modules

Reusable building blocks. Environment roots (`../envs/*`) call these; they contain no logic of their own. Each module
is created by the task named here; those without a link do not exist yet.

| Module | Task | What it will hold |
|---|---|---|
| [`network`](network/README.md) | [T-071](../../../tasks/T-071-tf-networking.md) | **Written.** VPC, subnets, Direct VPC egress settings, Private Service Connect to MongoDB Atlas, default-deny egress firewall |
| [`cloud-run-service`](cloud-run-service/README.md) | [T-072](../../../tasks/T-072-tf-cloud-run.md) | **Written.** One Cloud Run service with its own service account; `api`, `pricing-bridge` and `admin` are three calls of it |
| [`artifact-registry`](artifact-registry/README.md) | [T-073](../../../tasks/T-073-tf-artifact-registry.md) | **Written.** One image repository per service: immutable tags, cleanup policy, pull access for the owning service only |
| `web-cdn` | [T-074](../../../tasks/T-074-tf-web-cdn.md) | Bucket, HTTPS load balancer and Cloud CDN for `web` and `admin-web` |
| `secret-manager` | [T-075](../../../tasks/T-075-tf-secret-manager.md) | Secret containers and injection into Cloud Run, by reference only |
| `iap` | [T-076](../../../tasks/T-076-tf-iap.md) | Identity-Aware Proxy for `admin` and `admin-web` (see the note on ADR-0006 in `tasks/README.md`) |
| `workload-identity` | [T-077](../../../tasks/T-077-tf-workload-identity.md) | Workload Identity Federation for GitHub Actions |

Provider versions are pinned once, in the roots (`../envs/*/versions.tf`); a module declares which providers it needs
but no version tighter than that.
