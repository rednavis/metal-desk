# T-077 — Workload Identity Federation for CI → CD

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md` §7](../docs/architecture.md#7-reference-deployment-gcp), that document wins.** Update
> this task's row in [the ledger](README.md) in the same pull request.

**Parent issue:** [#5 — Phase 5 — GCP infrastructure](https://github.com/rednavis/metal-desk/issues/5)

**This task:** [#55](https://github.com/rednavis/metal-desk/issues/55)

**Milestone:** M5 GCP infrastructure · **Estimate:** 4 h

**Preconditions** — `T-073` merged (there is an image to push) and `T-072` merged (there is a service to deploy).
Phase 4 closed (`T-065`), so CI is a real gate.

> [!WARNING]
> This task gives CI the ability to change real infrastructure. The trust configuration in §4 **is** the task — a
> permissive binding here lets any fork's pull request deploy to your project.

**Goal** — Let GitHub Actions authenticate to GCP with **no long-lived service-account key**, scoped so that only this
repository's `master` branch can push images and deploy.

## 1. Why this task exists

Architecture §7 is unambiguous:

> **CI → CD auth** — Workload Identity Federation from GitHub Actions. **No long-lived service-account JSON keys in
> CI.**

`T-073` left the image push conditional and unauthenticated for exactly this reason, and `T-064` established that
nothing in this repository gets a credential. This task is the mechanism that makes deployment possible while keeping
that rule.

It is also the highest-consequence configuration in M5. A workload identity pool whose attribute condition is too loose
will accept tokens from **any** GitHub repository, not just this one — and the failure mode is silent: everything
works, and so does everyone else's.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Workload Identity Federation; no long-lived JSON keys | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| No committed secrets of any kind | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules), `T-064` |
| Images go to per-service repositories, SHA-tagged | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp), `T-073` |
| Each Cloud Run service has its own runtime service account | `T-072` |
| Environments are `dev`, `staging`, `prod` | [Plan Phase 5](../docs/modernization-plan.md) |
| The repository is `rednavis/metal-desk`, default branch `master` | `git remote -v`, `CLAUDE.md` |

## 3. Deliverables

| Path | What |
|---|---|
| `infra/terraform/modules/github-oidc/` | The workload identity pool, provider, attribute condition and bindings |
| `infra/terraform/envs/dev/cicd.tf` | The deployer service account and its narrow roles |
| `.github/workflows/image.yml` (modify) | Real authentication; remove `T-073`'s conditional-push placeholder |
| `.github/workflows/deploy.yml` | Deploy to `dev`, and the gated path for other environments |
| `infra/README.md` (modify) | The trust configuration, the exact attribute condition, and what it permits |

## 4. Specification

**The attribute condition is the security boundary. Write it explicitly and restrictively.** Bind on the full
repository (`assertion.repository == "rednavis/metal-desk"`) **and** the ref
(`assertion.ref == "refs/heads/master"`), or use an equivalently narrow repository-scoped principal set. Requiring only
`assertion.repository_owner` accepts any repository in the organisation; requiring nothing accepts every repository on
GitHub. **State in `infra/README.md`, in prose, exactly what the condition permits and excludes**, so a reviewer can
check the claim without reading Terraform.

**Pull requests must not be able to deploy.** A `pull_request` trigger from a fork runs with the fork's context.
Restrict the ref as above, scope `permissions: id-token: write` to only the jobs that need it, and use a GitHub
environment with protection rules for anything beyond `dev`. Building on a pull request is fine; obtaining a deploy
token on one is not.

**Least privilege, per action.** The deployer service account needs:

- Artifact Registry writer on the three repositories from `T-073` — not project-wide;
- Cloud Run admin **only** on the services it deploys, plus `iam.serviceAccountUser` on the runtime service accounts it
  must attach;
- nothing else. No `roles/editor`, no `roles/owner`, and **no** project-level Secret Manager access — CI deploys a
  secret *reference* (`T-075`), it never reads a value.

**Terraform's own permissions are a separate question — answer it deliberately.** Applying Terraform needs far broader
rights than deploying a revision: it creates networks, load balancers and IAM. Decide whether CI applies Terraform at
all. **Recommend that it does not, initially** — let CI build, push and deploy revisions, and keep `terraform apply` a
human-run operation with the operator's own credentials. If CI does apply Terraform, it needs a second, more privileged
identity behind a protected environment with required review. State the decision and the reasoning.

**Separate identities per environment.** One pool can serve all three, but `dev`, `staging` and `prod` get
**different** service accounts with different bindings, so a `dev` deploy cannot touch `prod`. If the ref condition is
the only distinction, `prod` has no real protection — use GitHub environments with required reviewers for `staging` and
`prod`, and record that.

**Verify the negative cases, not just the happy path.** The only way to know the condition is tight is to try to break
it. At minimum, reason through and document three cases: a pull request from a fork, a push to a non-`master` branch,
and a workflow in a different repository in the same organisation. If any can obtain a token, the condition is wrong.
Where a live test is impractical, say what was verified by configuration review rather than implying it was exercised.

**No key material, ever.** No `credentials_json`, no service-account key file, no base64 blob. `T-064`'s scan will
catch an obvious one; the point is not to need it.

**`T-073`'s placeholder goes away.** It gated the push on a repository variable because no authentication existed.
Remove that scaffolding and replace it with the real condition, so there is one mechanism rather than two overlapping
ones.

## 5. Acceptance criteria

1. `terraform fmt -check -recursive`, `init -backend=false`, `validate` and the linter pass.
2. No `credentials_json`, key file, or `-----BEGIN` block anywhere in the repository — asserted by grep and by the
   secret scan.
3. The attribute condition binds on **both** the full repository name and the ref, or an equivalently narrow principal
   set — and `infra/README.md` states in prose what it permits.
4. A workflow in a different repository in the same organisation cannot obtain a token — verified by configuration
   review, and stated as such.
5. A pull request, including from a fork, cannot obtain a deploy token; `id-token: write` is scoped to the jobs that
   need it.
6. The deployer holds Artifact Registry writer on the three repositories only, not project-wide.
7. The deployer holds Cloud Run admin scoped to the deployed services plus `serviceAccountUser` on the runtime
   accounts — and nothing else.
8. The deployer has **no** Secret Manager access.
9. No `roles/editor` or `roles/owner` is granted anywhere — asserted by grep over the whole Terraform tree.
10. Whether CI applies Terraform is decided and recorded; if it does, a separate privileged identity and a protected
    environment are configured.
11. `dev`, `staging` and `prod` use distinct service accounts; `staging` and `prod` require reviewer approval.
12. `T-073`'s repository-variable push gate is removed.
13. Nothing was applied unless a real deploy was performed deliberately; `git status` is clean.

## 6. Verification

```
cd <repo>
grep -rniE 'credentials_json|\-\-\-\-\-BEGIN|service_account_key' .github/workflows/ infra/   # expect nothing
grep -rn 'roles/editor\|roles/owner' infra/terraform   # expect nothing
grep -rn 'attribute_condition' infra/terraform/modules/github-oidc
grep -rn 'id-token' .github/workflows/*.yml
grep -rn 'assertion.repository\b' infra/terraform   # expect the full owner/name, not just repository_owner
cd infra/terraform && terraform fmt -check -recursive && (cd envs/dev && terraform init -backend=false && terraform validate)
```

Expected: no key material; no broad roles; an explicit attribute condition naming the full repository; `id-token`
scoped per job; Terraform checks green.

## 7. Out of scope

Applying the infrastructure (`T-078` decides when `dev` is stood up). Rollback automation and progressive delivery.
Release versioning. Deployment monitoring and alerting. Frontend deployment credentials — `T-074`'s workflow uses this
same pool, so extend it rather than creating a second mechanism.

## 8. Hazards

- **An attribute condition on `repository_owner` alone** lets every repository in the organisation deploy to this
  project. Everything works, so nothing signals the problem.
- No attribute condition at all accepts tokens from any GitHub repository in the world — the catastrophic version of
  the same mistake.
- A fork pull request that can obtain a deploy token means an outside contributor can deploy arbitrary code.
- Granting the deployer `roles/editor` "to get the deploy working" discards every boundary `T-072`, `T-075` and
  `T-076` established.
- Giving CI Secret Manager access lets a compromised workflow read the Atlas connection string and the JWT signing key.
- Letting CI apply Terraform with broad rights means a pull request that edits a `.tf` file can rewrite IAM.
- Distinguishing environments only by branch gives `prod` no protection beyond branch permissions.
- Leaving `T-073`'s variable gate alongside the real condition creates two mechanisms and a false sense of control.

## 9. On completion

Mark the T-077 row done in [`README.md`](README.md). Record the exact attribute condition, the deployer's complete role
list, the decision about CI applying Terraform, and which negative cases were verified by review rather than exercised.
