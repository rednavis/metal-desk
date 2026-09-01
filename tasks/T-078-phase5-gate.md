# T-078 — Phase 5 exit gate: a clean `dev` plan and a served health check

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/modernization-plan.md`](../docs/modernization-plan.md), the plan wins** — it defines the criterion this
> task proves. Update this task's row in [the ledger](README.md) in the same pull request.

**Parent issue:** [#5 — Phase 5 — GCP infrastructure](https://github.com/rednavis/metal-desk/issues/5)

**This task:** [#56](https://github.com/rednavis/metal-desk/issues/56)

**Milestone:** M5 GCP infrastructure · **Estimate:** 4 h

**Preconditions** — `T-070` … `T-077` merged.

**This task closes Phase 5 and issue #5.**

> [!WARNING]
> **This is the first task that applies real infrastructure and bills real money.** `api` runs with
> `min-instances ≥ 1` and two load balancers carry standing hourly charges. Read `infra/README.md`'s cost section and
> set a budget alert **before** the first `apply` — see §4. Tear down when finished (§4) unless the environment is
> meant to stay up.

**Goal** — Prove the exit criterion: `terraform plan` is clean for `dev`, and deploying `api` to Cloud Run serves a
health check with **no manual configuration step beyond `terraform apply`**.

## 1. Why this task exists

The plan's criterion has a clause that is easy to skip past:

> **Exit criteria:** `terraform plan` is clean for a `dev` environment; deploying `api` to Cloud Run serves a health
> check **with no manual configuration step beyond `terraform apply`**.

"No manual configuration step" is the hard part, and several earlier tasks deliberately created manual prerequisites:
`T-070`'s state-bucket bootstrap, `T-071`'s Atlas private endpoint, `T-075`'s secret population, `T-076`'s OAuth brand.
Each was correct to defer — but this task has to confront the total, decide which are genuinely outside
`terraform apply`, and either automate the rest or **state plainly that the criterion is met with named exceptions.**

Claiming the criterion while four manual steps remain would be the single most misleading thing this repository could
assert.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| The exact exit criterion | [Plan Phase 5](../docs/modernization-plan.md), issue #5 |
| Environments as thin roots: `dev`, `staging`, `prod` | [Plan Phase 5](../docs/modernization-plan.md), `T-070` |
| Actuator on `/actuator`; Cloud Logging + Cloud Trace | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Manual prerequisites recorded by earlier tasks | `T-070`, `T-071`, `T-075`, `T-076` |
| Keep `docs/architecture.md` in sync in the same PR | `CLAUDE.md` |
| No production data, no real credentials | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |

## 3. Deliverables

| Path | What |
|---|---|
| `infra/terraform/envs/staging/`, `envs/prod/` | Filled in from `dev`, differing only in values — see §4 |
| `infra/terraform/modules/budget/` | The budget and alert, applied **before** anything billable |
| `infra/README.md` (modify) | The bootstrap runbook, the manual-prerequisite list, the cost table, the teardown runbook |
| `docs/modernization-plan.md` (modify) | Phase 5 recorded as met, with its exceptions named |
| `README.md` (modify) | Correct the infrastructure-layout claim if still stale |
| `tasks/README.md` (modify) | Phase 5 closed, with the evidence |

## 4. Specification

**Apply the budget first.** Before any billable resource exists, create a project budget with alert thresholds and a
notification target. A cost surprise in a reference build is entirely avoidable, and doing this first costs nothing.
Record the amount chosen.

**Enumerate every manual prerequisite, then shrink the list.** Collect what `T-070`, `T-071`, `T-075` and `T-076`
recorded. For each, decide:

| Prerequisite | Verdict |
|---|---|
| GCS state bucket (`T-070`) | Genuinely outside `apply` — Terraform cannot create its own backend. A legitimate exception; document the bootstrap command. |
| Atlas cluster and private endpoint (`T-071`) | Outside this repository's scope. `api`'s **health check** must not depend on it — see below. |
| Secret values (`T-075`) | Outside `apply` by design; a value in state is the thing `T-075` forbids. Document the `gcloud` command. |
| IAP OAuth brand (`T-076`) | Often a one-time manual object. Affects `admin`, **not** `api`'s health check. |

Then the honest reading of the criterion becomes tractable: it names **`api` serving a health check**, not the whole
platform. So the question is narrow — can `api` reach `/actuator/health` after `terraform apply`, given the bootstrap
and secrets?

**Make `api`'s health check not depend on Atlas.** This is the substantive technical decision in this task. Spring
Boot's default `/actuator/health` aggregates every health indicator, including MongoDB — so with no Atlas cluster,
`api` deploys and reports DOWN, and the criterion fails for a reason unrelated to the infrastructure.

Resolve it properly: use a **liveness** probe group that excludes external dependencies, and keep readiness aggregating
them. That is the correct Kubernetes/Cloud Run pattern anyway — liveness answers "is the process healthy", readiness
answers "can it serve traffic". Configure `T-072`'s probes accordingly, and state in the ledger that the criterion is
met against the liveness group with readiness pending a real Atlas cluster. **Do not disable the Mongo health indicator
to make the check green** — that would hide a real signal to pass a test.

**`terraform plan` clean means clean twice.** Apply, then plan again and get "No changes". A plan that still shows
drift immediately after an apply means something is computed non-deterministically or a resource is being modified
outside Terraform. Run it twice and paste both outputs into the ledger. This is the criterion's first half and it is
routinely reported without being checked.

**`staging` and `prod` are `dev` with different values, and nothing more.** Copy the root, change the project, region,
CIDR ranges (from `T-071`'s IP plan), scaling values and secret references. **No resource may exist in one environment
root and not the others** — that is the drift the thin-root design exists to prevent. Verify by diffing the roots and
confirming only values differ. Do **not** apply `staging` or `prod`; they are validated only, and say so.

**Write the teardown runbook, and use it.** Record the exact command sequence to destroy `dev`, what `destroy` will not
remove (the state bucket, populated secrets, the OAuth brand), and how to verify nothing billable is orphaned — check
load balancers, forwarding rules, static IPs and Cloud Run services with a minimum instance count, since those are the
ones that keep charging. Unless the environment is meant to stay up, **tear it down in this task** and record the
verification.

**Report the criterion honestly.** If it is met with named exceptions, write exactly that in
`docs/modernization-plan.md` and the ledger — the exceptions, why each is genuinely outside `terraform apply`, and what
would remove it. A phase recorded as met when it is not is a defect that propagates into every later decision.

## 5. Acceptance criteria

1. A budget with alert thresholds exists and was applied **before** any billable resource.
2. `terraform fmt -check -recursive`, `init -backend=false`, `validate` and the linter pass for all three environment
   roots.
3. `terraform apply` succeeds for `dev`, and the output is recorded.
4. **`terraform plan` immediately after the apply reports no changes** — both plan outputs recorded in the ledger.
5. The deployed `api` serves its health check over its Cloud Run URL, and the response is recorded.
6. The health check passes via a liveness probe group that excludes external dependencies; the Mongo health indicator
   is **not** disabled — asserted by reading the configuration.
7. Readiness correctly reports not-ready without Atlas, and that is documented as expected rather than hidden.
8. Every manual prerequisite is enumerated in `infra/README.md` with its exact command and a stated reason why it is
   outside `apply`.
9. `staging` and `prod` roots validate and declare the same resource set as `dev` — verified by diffing the roots.
10. Neither `staging` nor `prod` was applied, and that is stated.
11. The teardown runbook exists, was executed (unless the environment is deliberately kept), and the orphan check found
    nothing billable left — specifically: no forwarding rule, no static IP, no Cloud Run service with a non-zero
    minimum instance count.
12. `docs/modernization-plan.md` records Phase 5 as met **with its exceptions named**, and `README.md`'s
    infrastructure-layout claim is accurate.
13. No credential, secret value or real project identifier that should be private appears in any committed file.

## 6. Verification

```
cd <repo>/infra/terraform/envs/dev
terraform init && terraform plan -out=tf.plan && terraform apply tf.plan
terraform plan            # expect: No changes. Your infrastructure matches the configuration.
API_URL=$(terraform output -raw api_url) && curl -sS "$API_URL/actuator/health"
cd ../staging && terraform init -backend=false && terraform validate
cd ../prod    && terraform init -backend=false && terraform validate
diff <(grep -o 'module "[a-z-]*"' ../dev/*.tf | sort -u) <(grep -o 'module "[a-z-]*"' *.tf | sort -u)
gcloud compute forwarding-rules list; gcloud compute addresses list; gcloud run services list
cd <repo> && git status --short   # expect clean
```

Expected: apply succeeds; the second plan reports no changes; the health endpoint responds; both other roots validate
and declare the same modules; after teardown the three `gcloud` listings are empty; a clean tree.

## 7. Out of scope

Observability beyond what Architecture §7 already gives (Cloud Logging, Cloud Trace, Actuator) — dashboards, SLOs and
alert policies are worth a follow-up phase and are not in this one. Atlas cluster provisioning. Deploying the SPAs to a
real domain (`T-074` needs a domain; note it as an exception if absent). Progressive delivery, autoscaling tuning,
disaster recovery. Load testing.

## 8. Hazards

- **Reporting the criterion as met while four manual steps remain** is the failure this task most needs to avoid. Name
  the exceptions.
- **Disabling the Mongo health indicator** to make `/actuator/health` green passes the criterion by removing a real
  signal, and the next person inherits a service that cannot report a database outage.
- Not running `plan` a second time hides non-deterministic configuration that will show as drift on every future run.
- Applying before the budget exists risks a cost surprise for no reason.
- Letting `staging` or `prod` declare a different resource set than `dev` is exactly the drift the thin-root design
  prevents, and it will be discovered during an incident.
- `terraform destroy` does not remove the state bucket, populated secrets or the OAuth brand — assuming it does leaves
  billable or sensitive objects behind.
- A forgotten forwarding rule, static IP, or a Cloud Run service with a minimum instance count keeps billing after a
  "teardown". These three are the ones to check by name.
- Committing a real project id or a secret value while documenting the runbook — `T-064`'s scan runs on this pull
  request too.

## 9. On completion

Mark the T-078 row done in [`README.md`](README.md) and record **Phase 5 closed**, with: the budget amount, both plan
outputs, the health-check response, the enumerated manual prerequisites, and the teardown verification. Comment the
same evidence on [issue #5](https://github.com/rednavis/metal-desk/issues/5) and close it — being explicit about the
exceptions rather than claiming an unqualified pass.
