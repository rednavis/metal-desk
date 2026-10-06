# Module: `github-oidc`

Workload Identity Federation for GitHub Actions: the pool, the provider with its **attribute condition**, and the **deployer**
service account the workflow impersonates. **There is no service-account key anywhere** (Architecture §7). Written and validated
by [`T-077`](../../../tasks/T-077-tf-workload-identity.md); **never applied** by that task.

## The attribute condition is the security boundary

A token is accepted only if all four clauses hold: the **full repository name**, its **numeric id**, the **branch** and the
**GitHub environment**. See [`infra/README.md`](../../../README.md#ci-to-cd-authentication) for what that permits and excludes in
prose. A condition on the repository *owner* alone would accept every repository in the organisation; none at all would accept
every repository on GitHub, and everything would still work. So `github_repository`, `github_repository_id` and `environment` are
**required, with no defaults**, and the condition is an output so it can be read without reading the module.

## Contract

**Inputs:** `name_prefix`, `environment`, `github_repository`, `github_repository_id`, `branch` (`master`).
**Outputs:** `workload_identity_provider` and `deployer_service_account_email` (the values of the GitHub environment's
`WIF_PROVIDER` and `WIF_SERVICE_ACCOUNT`; identifiers, not secrets), `deployer_member`, `attribute_condition`.

## The deployer holds nothing project-wide

The module creates the account and the right to impersonate it, and grants it **no role at all**. What it may do is granted on
single resources by the modules that own them: `writer_members` on an image repository (`artifact-registry`), `deployer_members`
on a Cloud Run service (`cloud-run-service`: `roles/run.developer` plus act-as on that service's runtime account, not `run.admin`),
`writer_members` on a site bucket (`static-site`). It has no Secret Manager access: CI deploys a secret reference, never a value.

## One pool per environment

Each environment is its own GCP project (`T-070`), so each calls this module with its own `environment`, getting its own pool,
provider and deployer. The provider's last clause is that environment, so a dev run can never obtain prod credentials.

## Also required before apply

The `iam.googleapis.com`, `iamcredentials.googleapis.com` and `sts.googleapis.com` APIs enabled, and the GitHub environments
created with their reviewers (instructions in `infra/README.md`).
