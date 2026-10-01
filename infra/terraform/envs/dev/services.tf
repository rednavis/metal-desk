# The three JVM services, each a call of the one cloud-run-service module. Nothing here declares a Cloud Run resource
# itself. Postures and their costs are tabulated in infra/README.md.

# Secrets are referenced, never valued: each entry names a secret declared in secrets.tf and a pinned version NUMBER (a
# moving alias is rejected by the module). The version is bumped here, deliberately, when a secret is rotated.

module "api" {
  source = "../../modules/cloud-run-service"

  name_prefix    = local.name_prefix
  name           = "api"
  region         = var.region
  image          = var.api_image
  container_port = 8082 # services/api pins server.port: 8082

  # The only service reachable from the internet: the storefront's backend.
  exposure = "public"

  # CI deploys new revisions of this service as the deployer, and may act as nothing else.
  deployer_members = [module.github_oidc.deployer_member]

  # Always keep one instance. The checkout path must not pay a JVM cold start (Architecture §7). This bills
  # continuously, with or without traffic, and is the largest idle cost of the deployment. A deliberate choice.
  min_instance_count = 1
  max_instance_count = 10

  vpc_egress = module.network.cloud_run_egress

  secret_env = {
    MONGODB_URI     = { secret = module.secret_mongodb_uri_api.secret_id, version = "1" }
    JWT_SIGNING_KEY = { secret = module.secret_jwt_signing_key.secret_id, version = "1" }
  }

  deletion_protection = false # dev is rebuilt freely
}

module "pricing_bridge" {
  source = "../../modules/cloud-run-service"

  name_prefix    = local.name_prefix
  name           = "pricing-bridge"
  region         = var.region
  image          = var.pricing_bridge_image
  container_port = 8083 # services/pricing-bridge pins server.port: 8083

  # No public consumer: only api calls it, service to service. Not publicly invokable.
  exposure         = "private"
  invoker_members  = [module.api.service_account_member]
  deployer_members = [module.github_oidc.deployer_member]

  # Always keep one instance, with CPU allocated between requests. T-039 built a persistent market-data
  # subscription; a service scaled to zero holds none, and a throttled one cannot read its feed, so the storefront
  # would show a frozen price. One instance at most: there is a single subscription to run.
  min_instance_count   = 1
  max_instance_count   = 1
  cpu_always_allocated = true

  vpc_egress = module.network.cloud_run_egress

  deletion_protection = false
}

module "admin" {
  source = "../../modules/cloud-run-service"

  name_prefix    = local.name_prefix
  name           = "admin"
  region         = var.region
  image          = var.admin_image
  container_port = 8081 # apps/admin pins server.port: 8081

  # Internal only: reachable through the load balancer (and IAP, T-076), never publicly invokable.
  exposure         = "internal-load-balancer"
  deployer_members = [module.github_oidc.deployer_member]

  # An internal tool, so it may scale to zero and accept a cold start rather than bill an idle instance.
  min_instance_count = 0
  max_instance_count = 3

  vpc_egress = module.network.cloud_run_egress

  secret_env = {
    MONGODB_URI           = { secret = module.secret_mongodb_uri_admin.secret_id, version = "1" }
    ADMIN_JWT_SIGNING_KEY = { secret = module.secret_admin_jwt_signing_key.secret_id, version = "1" }
  }

  deletion_protection = false
}
