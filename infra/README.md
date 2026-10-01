# Infrastructure

Terraform for the reference GCP deployment ([Architecture §7](../docs/architecture.md#7-reference-deployment-gcp),
[ADR-0003](../docs/adr/0003-gcp-target-architecture.md)). Everything is under [`terraform/`](terraform/).

> [!WARNING]
> **Writing, formatting, validating and linting Terraform costs nothing, and `terraform plan` costs nothing.**
> **`terraform apply` bills real money.** Tasks `T-070` … `T-077` are write-and-validate only; none of them applies
> anything. **[`T-078`](../tasks/T-078-phase5-gate.md) is the first task that applies real infrastructure** and is
> where a budget alert must be added, before the first apply. Do not `apply` from any other task.

## Layout

```
infra/terraform/
├── envs/
│   ├── dev/         a thin root: values and module calls, no resources of its own
│   ├── staging/     placeholder, wired in T-078
│   └── prod/        placeholder, wired in T-078
├── modules/         reusable modules, one per concern (see modules/README.md for the owning task)
├── bootstrap/       the one-time script that creates a state bucket
└── .tflint.hcl      linter configuration
```

**Thin roots.** A root supplies values and wires modules together; logic lives in a module. That is what keeps `prod`
from drifting away from `dev` by accumulating resources of its own. Each root has the same file set:
`versions.tf` (version constraints), `backend.tf`, `variables.tf`, `main.tf`, `outputs.tf` and
`terraform.tfvars.example`.

## Naming

Every resource is named `metaldesk-<env>-<resource>`, where `<env>` is `dev`, `staging` or `prod`: for example
`metaldesk-dev-api` for a Cloud Run service. Roots expose the prefix as `local.name_prefix` and modules take it as an
input; nothing builds a name by hand. Things to know about GCP when naming:

- **Bucket names are global** across all of GCP, so a bucket needs the prefix *and* something unlikely to collide. If
  a name is taken, change it everywhere it appears.
- Cloud Run service names are unique per project and region, and several resources cannot be renamed without being
  replaced. Choose a name once.
- One GCP project per environment (`project_id` is a variable), so an environment's blast radius is its project.

## Versions

| What | Constraint | Where it is stated |
|---|---|---|
| Terraform | `~> 1.16` | `envs/*/versions.tf` **and** `terraform_version` in [`.github/workflows/infra.yml`](../.github/workflows/infra.yml) |
| `hashicorp/google` | `~> 8.5` | `envs/*/versions.tf` only |
| tflint | pinned | `tflint_version` in `infra.yml` |

Each root commits its `.terraform.lock.hcl` (provider checksums for linux and macOS, amd64 and arm64). To upgrade a
provider, change the constraint, then in each root run `terraform init -upgrade` followed by
`terraform providers lock -platform=linux_amd64 -platform=linux_arm64 -platform=darwin_amd64 -platform=darwin_arm64`.

`gradle/libs.versions.toml` does not govern Terraform. The Terraform version is stated in two places, so **an upgrade
edits both**; the workflow pins an exact release inside the `~>` range.

## The state buckets

State lives in a GCS bucket, and that bucket cannot be created by the Terraform whose state it holds. Each
environment has **its own** bucket, `metaldesk-<env>-tfstate`, with object versioning on, public access prevented, and
old versions pruned after 90 days while the newest 10 are always kept (state history is the way back from a bad
apply). The bucket is created once, by a script and not by Terraform, because a throwaway Terraform root would keep
its own state on someone's laptop, which is the same problem one level down.

Run this once per environment, with your own credentials and an existing project:

```bash
gcloud auth login
gcloud auth application-default login            # what terraform itself will use
infra/terraform/bootstrap/create-state-bucket.sh dev     <dev-project-id>
infra/terraform/bootstrap/create-state-bucket.sh staging <staging-project-id>
infra/terraform/bootstrap/create-state-bucket.sh prod    <prod-project-id>
```

The script is idempotent. The bucket name must match `envs/<env>/backend.tf`.

> **Status: not yet run.** No project exists for these environments yet, so no bucket exists. Until `T-078` creates
> them, `terraform init` *without* `-backend=false` fails with a backend error; that is expected, and it is why CI
> uses `-backend=false`.

## IP plan

One VPC per environment. Each environment owns a `/16`, **chosen so the three never overlap**, so peering any two of
them later stays possible. Only the first two subnets are carved out so far; the rest of the `/16` is reserved.

| Environment | Reserved | `run` subnet (Cloud Run Direct VPC egress) | `psc` subnet (Atlas endpoints) | Wired in |
|---|---|---|---|---|
| `dev` | `10.10.0.0/16` | `10.10.0.0/24` | `10.10.1.0/26` | `envs/dev` ([T-071](../tasks/T-071-tf-networking.md)) |
| `staging` | `10.20.0.0/16` | `10.20.0.0/24` | `10.20.1.0/26` | `T-078` |
| `prod` | `10.30.0.0/16` | `10.30.0.0/24` | `10.30.1.0/26` | `T-078` |

- **`run`, a `/24`:** Direct VPC egress takes one address per *running* instance, and Cloud Run needs at least a `/26`.
  A `/24` (251 usable) leaves room for every service to scale out; widen it before the instance count approaches that.
- **`psc`, a `/26`:** one address per Atlas service attachment. Atlas can issue several (the count depends on its
  current scheme, which this repository has not confirmed); a `/26` (59 usable) leaves room for dozens.
- **Egress mechanism:** Direct VPC egress, not a Serverless VPC Access connector. It has **no idle cost**, where a
  connector bills around the clock. The comparison and what would make us switch are in
  [`modules/network/README.md`](terraform/modules/network/README.md).
- **Egress is default-deny** for Cloud Run instances: only the Atlas endpoints and Google APIs (HTTPS) are allowed.
- **Manual prerequisite:** the Atlas service attachments belong to Atlas and have no default; a real `plan` needs
  them. See the module README. `dev` fails its plan with a pointer to it until they are supplied.

## Service postures and what they cost

The three JVM services are three calls of [`modules/cloud-run-service`](terraform/modules/cloud-run-service/README.md)
in `envs/dev/services.tf`. Each runs as **its own service account with no project-level role**; access is granted on
the one resource that needs it.

| Service | Exposure | Ingress / invoker | Min / max instances | CPU | Port | Why |
|---|---|---|---|---|---|---|
| `api` | `public` | all / `allUsers` (explicit opt-in) | **1** / 10 | on requests | 8082 | The storefront's backend. The checkout path must not pay a JVM cold start (Architecture §7) |
| `pricing-bridge` | `private` | internal / only `api`'s account | **1** / 1 | **always allocated** | 8083 | No public consumer. It holds a persistent market-data subscription (`T-039`); scaled to zero it holds none, and with CPU throttled between requests it cannot read its feed, so the storefront would show a frozen price |
| `admin` | `internal-load-balancer` | internal and load balancers / nobody public | 0 / 3 | on requests | 8081 | An internal tool: it may cold-start. Fronted by a load balancer and IAP in `T-076` |

> **Standing cost.** `api` with a minimum of 1 and `pricing-bridge` with a minimum of 1 **and CPU always allocated** are
> billed continuously, with or without traffic. `api`'s minimum is the single largest idle cost in the deployment and a
> deliberate latency decision, not an oversight; `pricing-bridge`'s always-on CPU is the more expensive of the two per
> instance. `admin` costs nothing at idle. None of this is spent until something is applied (`T-078`).

- **Images** are pinned by digest or a commit-SHA tag; a mutable tag fails validation. `T-073` builds them.
- **Secrets are references**, `{ secret, version }`, never values; the module has no variable that takes one. `T-075`
  creates `<prefix>-mongodb-uri`, `<prefix>-jwt-signing-key` and `<prefix>-admin-jwt-signing-key` (the names
  `services.tf` uses) and grants each service's account access to its own.
- **Not covered:** `pricing-bridge` as a Cloud Run Job. A poll-based feed would want that shape; this module models the
  always-on service `T-039` built.

## Images: tags, retention and who may pull

Each service has **its own repository**, `metaldesk-<env>-<service>`, created by
[`modules/artifact-registry`](terraform/modules/artifact-registry/README.md) in `envs/dev/registry.tf`. The images are
built by [`deploy/images/Dockerfile`](../deploy/README.md).

- **Tag = the full commit SHA**, `<repository>/<service>:<sha>`. **Tags are immutable** (an Artifact Registry setting),
  so a tag can never be repointed and "commit SHA, never a mutable tag" is enforced by the registry, not by convention.
  The Cloud Run module accepts only a digest or such a tag.
- **Retention**, per repository: keep the **20** most recent versions whatever their age; delete anything older than
  **90 days** unless it is among those 20; delete **untagged** versions after **7 days**. The policy cannot know what is
  deployed, so raise the numbers for `prod` (`T-078`) rather than assume a deployed image is always recent.
- **Pull**: each Cloud Run service's own account gets `roles/artifactregistry.reader` on **its own repository only**.
  **Push is granted to nobody here**: CI pushes through Workload Identity Federation (`T-077`), never a key.
- Storing images is not free; the retention policy is part of the cost control, not an optimisation.

## Running it

```bash
cd infra/terraform/envs/dev
cp terraform.tfvars.example terraform.tfvars     # gitignored; set project_id
terraform init
terraform plan                                   # free; reads state, changes nothing
```

Authentication is **your own `gcloud` credentials** locally and Workload Identity Federation in CI (`T-077`). There is
no service-account JSON key anywhere in this repository, by design (Architecture §7), and `terraform.tfvars`,
`*.tfstate*` and `.terraform/` are gitignored. `terraform.tfvars.example` is committed and holds nothing secret.

## Checks

The same commands CI runs ([`infra.yml`](../.github/workflows/infra.yml)), with no cloud access:

```bash
cd infra/terraform
terraform fmt -check -recursive
for d in envs/dev envs/staging envs/prod; do (cd "$d" && terraform init -backend=false -input=false && terraform validate); done
tflint --recursive
```
