# How GitHub Actions authenticates to this environment: Workload Identity Federation, with no service-account key anywhere
# (Architecture §7). The module creates the pool, the provider with its attribute condition, and the deployer account; what
# the deployer may do is granted, resource by resource, by the modules that own the resources: writer_members on the three
# image repositories (registry.tf), deployer_members on the three Cloud Run services (services.tf), writer_members on the two
# site buckets (sites.tf). It holds no project-level role and no Secret Manager access.
#
# CI does NOT apply Terraform. It builds, pushes images and deploys revisions; `terraform apply` stays a human operation with
# the operator's own credentials. See infra/README.md for why.

module "github_oidc" {
  source = "../../modules/github-oidc"

  name_prefix = local.name_prefix
  environment = local.environment

  # Exactly one repository, by name and by numeric id (gh api repos/rednavis/metal-desk --jq .id), and exactly one branch.
  github_repository    = "rednavis/metal-desk"
  github_repository_id = "1371039595"
  branch               = "master"
}
