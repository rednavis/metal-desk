# T-070 — Terraform root layout, the GCS backend and the dev environment

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md` §7](../docs/architecture.md#7-reference-deployment-gcp) or
> [ADR-0003](../docs/adr/0003-gcp-target-architecture.md), that document wins** — open an issue rather than
> implementing either version. Update this task's row in [the ledger](README.md) in the same pull request.

**Parent issue:** [#5 — Phase 5 — GCP infrastructure](https://github.com/rednavis/metal-desk/issues/5)

**This task:** [#48](https://github.com/rednavis/metal-desk/issues/48)

**Milestone:** M5 GCP infrastructure · **Estimate:** 3 h

**Preconditions** — Phase 3 closed, per the parent issue: "services need to exist for Cloud Run modules to deploy
something". No Terraform exists in the repository today.

**This task blocks every other M5 task.**

> [!WARNING]
> **`T-070` … `T-075` are write-and-validate only and cost nothing.** No `terraform apply` is required by any of them.
> Anything that applies real infrastructure bills real money — do not apply from this task.

**Goal** — Establish the Terraform layout: a versioned GCS state backend, `dev`/`staging`/`prod` as thin root
modules, a reusable module directory, and the conventions every later M5 task follows.

## 1. Why this task exists

Nine Terraform tasks are about to be written, and every one of them needs to know where files go, how state is
stored, how variables are named, and how an environment differs from a module. Deciding that once, in a task that
applies nothing and costs nothing, is the cheapest possible ordering.

The plan is specific about the shape:

> Environments as thin root modules (`dev`, `staging`, `prod`), state in a versioned GCS backend.

"Thin" is the load-bearing word: a root module wires modules together and supplies values. Logic lives in modules, so
that `prod` cannot drift from `dev` by accumulating its own resources.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Terraform, GCS backend, versioned state bucket **per environment** | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Environments as thin root modules; `dev`, `staging`, `prod` | [Plan Phase 5](../docs/modernization-plan.md), issue #5 |
| Reusable modules per Architecture §7 | [Plan Phase 5](../docs/modernization-plan.md) |
| GCP is the target, chosen over the AWS shape | [ADR-0003](../docs/adr/0003-gcp-target-architecture.md) |
| Exit criterion: `terraform plan` clean for `dev` | [Plan Phase 5](../docs/modernization-plan.md), issue #5 |
| No committed secrets; credentials come from Secret Manager | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules), [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| No long-lived service-account JSON keys | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |

## 3. Deliverables

| Path | What |
|---|---|
| `infra/terraform/envs/dev/` | `main.tf`, `variables.tf`, `backend.tf`, `terraform.tfvars.example`, `outputs.tf` |
| `infra/terraform/envs/staging/`, `envs/prod/` | Placeholder roots with their own backends, wired in `T-078` |
| `infra/terraform/modules/` | The empty module tree with a `README.md` naming the owning task for each |
| `infra/terraform/versions.tf` or per-root equivalent | Terraform and provider version constraints |
| `infra/README.md` | Layout, naming conventions, how to run plan, and the cost warning |
| `.gitignore` (modify) | `*.tfstate*`, `.terraform/`, `*.tfvars` (but **not** `*.tfvars.example`) |
| `.github/workflows/infra.yml` | `fmt -check`, `init -backend=false`, `validate`, and a linter |
| `docs/architecture.md` (modify) | §7 gains a pointer to the Terraform layout |

## 4. Specification

**Directory choice, stated once.** `README.md` currently shows an `infra/gcp/` directory that does not exist.
Pick `infra/terraform/` or `infra/gcp/` — **reconcile with `README.md` in this pull request either way**, so the
repository stops advertising a path that is not there. Do not create both.

**The state bucket is a chicken-and-egg problem. Address it explicitly.** State lives in a GCS bucket, and that bucket
cannot be created by the Terraform whose state it holds. The options are a one-time manual bootstrap, or a separate
tiny bootstrap root with local state. **Choose one, document the exact commands, and make it reproducible** — this is
the single most common place an infrastructure repository becomes undeployable by anyone but its author.

Per Architecture §7 each environment gets its **own** versioned bucket, with object versioning on and a documented
retention. Never share one bucket across environments without prefix isolation, and prefer separate buckets.

**Pin versions, and note where the version actually lives.** Constrain the Terraform version and each provider with
`~>` ranges. `gradle/libs.versions.toml` has no Terraform row and does not govern Terraform, so these constraints and
the CI workflow's `terraform_version` are two places for one fact — name both in a comment so an upgrade edits both.

**Naming convention, decided here.** Resource names need an environment prefix or suffix, and GCP has real constraints:
bucket names are globally unique, Cloud Run service names are per-region, and some resources cannot be renamed without
replacement. Define the pattern (for example `metaldesk-<env>-<resource>`), write it in `infra/README.md`, and use it
consistently from `T-071` onward.

**`terraform.tfvars.example` is committed; `terraform.tfvars` is not.** Mirror the `.env.example` pattern already in
`.gitignore`. The example carries a project id placeholder, a region, and nothing secret.

**CI validates without credentials.** `terraform fmt -check -recursive`, `terraform init -backend=false`, and
`terraform validate` in every directory containing a `.tf` file, plus a linter such as tflint. All three run with no
cloud access — which is what makes `T-070`…`T-075` reviewable by anyone. Note that with an empty `modules/` tree these
commands trivially pass; that is fine, and `T-071` gives them something to check.

**No credentials, no keys, no project ids that are real if they are sensitive.** Authentication for a real plan comes
from the operator's own `gcloud` credentials or, in CI, from Workload Identity Federation (`T-077`) — never a
service-account JSON key, per Architecture §7.

**Write the cost warning where it will be read.** `infra/README.md` states plainly which tasks apply real
infrastructure and that a `plan` costs nothing. A budget alert is worth adding as a deliverable of the first task that
actually applies; name that task here.

## 5. Acceptance criteria

1. `terraform fmt -check -recursive` passes over the whole Terraform tree.
2. `terraform init -backend=false && terraform validate` passes in `envs/dev`, `envs/staging` and `envs/prod`.
3. tflint (or the chosen linter) reports no findings.
4. `.github/workflows/infra.yml` runs all of the above with **no** cloud credentials and passes.
5. The Terraform and provider versions are constrained, and the CI workflow's version matches the constraint.
6. Each environment root declares its own GCS backend with a distinct versioned bucket.
7. The state-bucket bootstrap is documented as a reproducible command sequence, and was actually run or explicitly
   marked as not yet run.
8. `.gitignore` covers `*.tfstate*`, `.terraform/` and `*.tfvars`, and does **not** ignore `*.tfvars.example`.
9. `git status` is clean after running fmt, init and validate — no state or `.terraform` directory left tracked.
10. `README.md`'s infrastructure path claim matches the directory that exists.
11. `infra/README.md` states the naming convention and the cost warning, and names which task first bills money.
12. No credential, service-account key or `-----BEGIN` block anywhere in the tree.

## 6. Verification

```
cd <repo>/infra/terraform
terraform fmt -check -recursive
for d in envs/dev envs/staging envs/prod; do (cd $d && terraform init -backend=false && terraform validate); done
tflint --recursive
cd <repo> && git status --short   # expect clean
grep -rniE '\-\-\-\-\-BEGIN|private_key|service_account_key' infra   # expect nothing
grep -n 'tfstate\|tfvars\|.terraform' .gitignore
```

Expected: fmt clean; validate passing in all three roots; linter clean; clean tree; no key material; the ignore rules
present.

## 7. Out of scope

Every actual resource — networking (`T-071`), Cloud Run (`T-072`), Artifact Registry (`T-073`), CDN (`T-074`),
Secret Manager (`T-075`), IAP (`T-076`), Workload Identity Federation (`T-077`). Filling in `staging` and `prod`
(`T-078`). Any `terraform apply`. Kubernetes — Architecture §7 uses Cloud Run, not GKE.

## 8. Hazards

- **An undocumented state-bucket bootstrap** makes the repository deployable only by whoever created the bucket, and
  the failure appears as an opaque backend error.
- Sharing one state bucket across environments lets a `dev` mistake corrupt `prod` state.
- Creating both `infra/gcp/` and `infra/terraform/` leaves `README.md` right about one and the repository confusing.
- Committing `terraform.tfvars` puts a project id — and eventually a secret — in git. The example file is the pattern.
- A service-account JSON key in the repository contradicts Architecture §7 and will trip TruffleHog on the pull
  request.
- Leaving `.terraform/` or `*.tfstate` untracked-but-present makes `git status` noisy and eventually someone commits
  state, which can contain secrets in plain text.
- Putting resources in an environment root instead of a module is how `prod` and `dev` diverge; "thin" is the
  requirement.
- Running `terraform apply` from this task bills money for no deliverable.

## 9. On completion

Mark the T-070 row done in [`README.md`](README.md). Record the chosen directory, the naming convention, the exact
bootstrap commands and whether the buckets exist yet — every remaining M5 task depends on all four.
