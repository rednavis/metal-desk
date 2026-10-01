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
