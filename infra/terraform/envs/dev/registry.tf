# One repository per deployable service, each readable only by that service's own account. Nothing here declares a
# registry resource itself. Tagging and retention are described in infra/README.md.

module "registry_api" {
  source = "../../modules/artifact-registry"

  name_prefix    = local.name_prefix
  name           = "api"
  region         = var.region
  reader_members = [module.api.service_account_member]
}

module "registry_pricing_bridge" {
  source = "../../modules/artifact-registry"

  name_prefix    = local.name_prefix
  name           = "pricing-bridge"
  region         = var.region
  reader_members = [module.pricing_bridge.service_account_member]
}

module "registry_admin" {
  source = "../../modules/artifact-registry"

  name_prefix    = local.name_prefix
  name           = "admin"
  region         = var.region
  reader_members = [module.admin.service_account_member]
}
