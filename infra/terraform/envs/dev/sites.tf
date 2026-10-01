# The two single-page apps, each a call of the one static-site module. Nothing here declares a bucket or a load
# balancer itself. The cache policy, the deep-link rewrite and the cost are described in infra/README.md.

module "web" {
  source = "../../modules/static-site"

  name_prefix    = local.name_prefix
  name           = "web"
  region         = var.region
  project_number = var.project_number
  domain         = var.web_domain

  # The storefront is for everyone: an explicit opt-in to public. The bucket behind it is still not public.
  access = "public"

  writer_members = [module.github_oidc.deployer_member]
}

module "admin_web" {
  source = "../../modules/static-site"

  name_prefix    = local.name_prefix
  name           = "admin-web"
  region         = var.region
  project_number = var.project_number
  domain         = var.admin_web_domain

  # The staff SPA is not for the public. Identity-Aware Proxy cannot sit in front of a backend bucket (see
  # modules/static-site/README.md), so it is restricted at the edge to known addresses; the real gate is still apps/admin's
  # own login (ADR-0006).
  access            = "restricted"
  allowed_ip_ranges = var.admin_web_allowed_ip_ranges

  writer_members = [module.github_oidc.deployer_member]
}
