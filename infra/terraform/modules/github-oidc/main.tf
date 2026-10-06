# Lets GitHub Actions authenticate to this environment's GCP project with NO long-lived key: a workflow presents the
# short-lived OpenID Connect token GitHub issues to that run, Google checks it against the condition below, and a match
# may impersonate the deployer service account. There is no service-account key anywhere in this design.
#
# THE ATTRIBUTE CONDITION IS THE SECURITY BOUNDARY. It is written out in full, explicitly, and is as narrow as it can be.

locals {
  # A token is accepted only if EVERY clause holds:
  #   repository     the full owner/name, not just the owner: an owner-only check would accept every repository in the
  #                  organisation, and no check at all would accept every repository on GitHub
  #   repository_id  the numeric id, so a renamed, deleted or re-created repository cannot inherit the trust
  #   ref            the run is on the allowed branch: a pull request (refs/pull/N/merge), a fork's branch, a tag and any
  #                  other branch all fail here
  #   environment    the job declared this environment, which is where GitHub applies protection rules and required
  #                  reviewers, so a job that skipped them cannot obtain a token for it
  attribute_condition = join(" && ", [
    "assertion.repository == \"${var.github_repository}\"",
    "assertion.repository_id == \"${var.github_repository_id}\"",
    "assertion.ref == \"refs/heads/${var.branch}\"",
    "assertion.environment == \"${var.environment}\"",
  ])
}

resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "${var.name_prefix}-github"
  display_name              = "${var.name_prefix} GitHub Actions"
  description               = "GitHub Actions runs of ${var.github_repository} on ${var.branch}, environment ${var.environment}. Nothing else."
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  display_name                       = "GitHub OIDC"

  attribute_mapping = {
    "google.subject"          = "assertion.sub"
    "attribute.repository"    = "assertion.repository"
    "attribute.repository_id" = "assertion.repository_id"
    "attribute.ref"           = "assertion.ref"
    "attribute.environment"   = "assertion.environment"
  }

  # Without a condition the provider would accept a token from ANY GitHub repository. The provider refuses to be
  # created without one for GitHub's issuer; this is the narrowest one that does the job.
  attribute_condition = local.attribute_condition

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# The identity CI acts as. It holds NO project-level role: what it may do is granted on individual resources by the modules
# that own them (the image repositories, the Cloud Run services, the site buckets), through their deployer_members and
# writer_members inputs. It has no Secret Manager access at all: CI deploys a secret REFERENCE, it never reads a value.
resource "google_service_account" "deployer" {
  account_id   = "${var.name_prefix}-deployer"
  display_name = "${var.name_prefix} deployer (GitHub Actions)"
  description  = "Impersonated by ${var.github_repository} workflows on ${var.branch}, environment ${var.environment}. Holds no project-level role and no key."
}

# Only identities from this repository (as the provider already enforced) may impersonate the deployer. Bound to the one
# repository attribute, not to the whole pool, so a second provider added to the pool later would not inherit this.
resource "google_service_account_iam_member" "impersonate" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repository}"
}
