# T-071 — Terraform module: networking and Private Service Connect

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md` §7](../docs/architecture.md#7-reference-deployment-gcp) or
> [ADR-0003](../docs/adr/0003-gcp-target-architecture.md), that document wins.** Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#5 — Phase 5 — GCP infrastructure](https://github.com/rednavis/metal-desk/issues/5)

**This task:** [#49](https://github.com/rednavis/metal-desk/issues/49)

**Milestone:** M5 GCP infrastructure · **Estimate:** 3 h

**Preconditions** — `T-070` merged. Use its naming convention and module layout.

> [!WARNING]
> **Write-and-validate only.** A VPC and a Private Service Connect endpoint cost money once applied. Do not apply from
> this task.

**Goal** — Build the networking module: the VPC and subnets, the egress path Cloud Run needs to reach MongoDB Atlas
over Private Service Connect, and the firewall posture — written and validated, not applied.

## 1. Why this task exists

Architecture §7 makes one networking decision explicit and consequential:

> **Document store** — MongoDB Atlas on GCP, **via Private Service Connect**. No first-party GCP document database with
> this data model's fit; Atlas keeps MongoDB without self-hosting it.

Private Service Connect is what keeps database traffic off the public internet. It also means the Cloud Run services
cannot reach Atlas with default settings: Cloud Run is serverless and needs explicit VPC egress to use a private
endpoint at all. Getting this module wrong produces services that deploy successfully and cannot connect, which is a
slow failure to diagnose.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Atlas reached via Private Service Connect | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `api`, `pricing-bridge`, `admin` run on Cloud Run | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `admin` and `admin-web` are internal-only, behind IAP | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| `web` and `admin-web` are static, served via LB + CDN | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Environments are thin roots; logic lives in modules | [Plan Phase 5](../docs/modernization-plan.md), `T-070` |
| Naming convention and layout | `T-070` |
| No secrets committed | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |

## 3. Deliverables

| Path | What |
|---|---|
| `infra/terraform/modules/network/main.tf` | VPC, subnets, the serverless VPC access connector or Direct VPC egress config |
| `.../network/psc.tf` | The Private Service Connect endpoint for Atlas |
| `.../network/firewall.tf` | Default-deny egress posture plus the explicit allowances |
| `.../network/variables.tf`, `outputs.tf`, `README.md` | Inputs, outputs and the module's contract |
| `infra/terraform/envs/dev/main.tf` (modify) | Wire the module with dev values |
| `infra/README.md` (modify) | The IP plan and why each range was chosen |

## 4. Specification

**One VPC per environment, with a written IP plan.** Subnets need CIDR ranges, and a serverless VPC access connector
needs its own `/28`. Pick ranges that do not overlap between `dev`, `staging` and `prod` — even though they are
separate VPCs today — because the first time anyone peers them, overlapping ranges make it impossible. Write the plan
in `infra/README.md` as a table, not as a comment in a `.tf` file.

**Decide between a Serverless VPC Access connector and Direct VPC egress, and record why.** Both let Cloud Run reach
a private endpoint; they differ in cost, throughput and how they scale. A connector is an always-on billed resource;
direct egress is newer and has its own constraints. **This is a real decision with a cost consequence** — state which
you chose, what it costs at idle, and what would make you switch.

**The Atlas PSC endpoint needs a service attachment that this repository does not own.** Atlas provides it when a
private endpoint is configured on the Atlas side, and that is a manual step in the Atlas console or a separate
provider. **Model it as a variable with no default and document the manual prerequisite.** Do not invent a plausible
service-attachment URI — a fake value that passes `validate` and fails at `apply` is worse than an obvious gap.

If the Atlas side cannot be represented at all in this build, say so: the module still declares the endpoint and the
DNS, the value comes from a variable, and the ledger records that a real `apply` needs an Atlas cluster first.

**Egress is default-deny with explicit allowances.** Cloud Run services need to reach the Atlas endpoint, Secret
Manager, and Google APIs. They do **not** need general internet egress in this build — every external dependency is
mocked (ADR-0002), and `services/pricing-bridge`'s real feed does not exist. Start closed and enumerate what is
opened, each with a comment naming why. This is easier now than after something depends on accidental openness.

**Private Google Access for the API endpoints**, so Secret Manager and Artifact Registry are reachable without public
egress.

**Outputs are the module's contract.** Emit the VPC id, the subnet ids, the connector id or egress settings, and the
Atlas endpoint's DNS name. `T-072` consumes them; do not make it reach into the module's internals.

**No CIDR, project id or endpoint URI hard-coded in the module.** Everything is a variable with the dev values in the
environment root.

## 5. Acceptance criteria

1. `terraform fmt -check -recursive`, `init -backend=false`, `validate` and the linter all pass, including the new
   module and the modified `dev` root.
2. The module declares no hard-coded CIDR, project id, region or service-attachment URI — asserted by grep.
3. The IP plan is documented as a table in `infra/README.md`, with non-overlapping ranges across all three
   environments.
4. The connector-versus-direct-egress choice is recorded with its idle cost.
5. The Atlas service attachment is a variable with no default, and the manual Atlas prerequisite is documented.
6. Egress is default-deny, and every allowance carries a comment naming what needs it.
7. Private Google Access is enabled for the subnets that need it.
8. Outputs expose the VPC, subnets, egress configuration and Atlas DNS name; nothing else is needed by `T-072`.
9. The `dev` root remains thin: it passes values and declares no networking resource of its own.
10. `terraform plan` is **not** run against a real project as part of this task, and nothing was applied.
11. No credential or key anywhere in the tree; `git status` clean after validation.

## 6. Verification

```
cd <repo>/infra/terraform
terraform fmt -check -recursive
(cd envs/dev && terraform init -backend=false && terraform validate)
tflint --recursive
grep -rnE '10\.[0-9]+\.[0-9]+\.[0-9]+/|projects/[a-z0-9-]+' modules/network   # expect nothing outside variable descriptions
grep -rn 'resource "google_compute' envs/dev   # expect nothing: the root is thin
cd <repo> && git status --short   # expect clean
```

Expected: all checks green; no hard-coded addresses or project ids in the module; no networking resources in the
environment root; a clean tree.

## 7. Out of scope

Cloud Run services (`T-072`). Artifact Registry (`T-073`). The CDN and load balancer (`T-074`) — it has its own
networking surface. Secret Manager (`T-075`). IAP (`T-076`). Creating the Atlas cluster or its private endpoint.
Cloud NAT unless an allowance genuinely requires it — if so, note the cost. Any `apply`.

## 8. Hazards

- **Inventing an Atlas service-attachment URI** so `validate` passes produces a configuration that fails at `apply`
  with an unhelpful error, and it looks complete in review.
- Overlapping CIDR ranges across environments are free to fix today and impossible to fix after peering.
- Forgetting that Cloud Run needs explicit VPC egress yields services that deploy and then time out on every database
  call — a failure that looks like an application bug.
- A serverless VPC access connector bills whether or not traffic flows; choosing it without recording the idle cost
  hides a standing charge.
- Opening general internet egress "to be safe" removes the containment that makes ADR-0002's mocked-dependency posture
  meaningful.
- Putting a firewall rule or subnet in the `dev` root rather than the module guarantees `prod` differs.
- A `/28` that is too small for the connector fails at apply time with a quota-shaped error.

## 9. On completion

Mark the T-071 row done in [`README.md`](README.md). Record the IP plan, the egress mechanism and its idle cost, and
the Atlas prerequisite — `T-072` consumes the outputs and `T-078` needs the plan to fill in `staging` and `prod`.
