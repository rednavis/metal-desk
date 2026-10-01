# Modules

Reusable building blocks. Environment roots (`../envs/*`) call these; they contain no logic of their own. Each module
is created by the task named here, and none exists yet.

| Module | Task | What it will hold |
|---|---|---|
| `networking` | [T-071](../../../tasks/T-071-tf-networking.md) | VPC, serverless connector, Private Service Connect to MongoDB Atlas |
| `cloud-run-service` | [T-072](../../../tasks/T-072-tf-cloud-run.md) | One Cloud Run service; `api`, `pricing-bridge` and `admin` are three instances of it |
| `artifact-registry` | [T-073](../../../tasks/T-073-tf-artifact-registry.md) | Per-service image repositories, immutable tags |
| `web-cdn` | [T-074](../../../tasks/T-074-tf-web-cdn.md) | Bucket, HTTPS load balancer and Cloud CDN for `web` and `admin-web` |
| `secret-manager` | [T-075](../../../tasks/T-075-tf-secret-manager.md) | Secret containers and injection into Cloud Run, by reference only |
| `iap` | [T-076](../../../tasks/T-076-tf-iap.md) | Identity-Aware Proxy for `admin` and `admin-web` (see the note on ADR-0006 in `tasks/README.md`) |
| `workload-identity` | [T-077](../../../tasks/T-077-tf-workload-identity.md) | Workload Identity Federation for GitHub Actions |

Provider versions are pinned once, in the roots (`../envs/*/versions.tf`); a module declares which providers it needs
but no version tighter than that.
