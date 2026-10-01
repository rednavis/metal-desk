# The dev environment: a thin root. It supplies values and wires modules together (modules/, from T-071);
# it declares no resources of its own, so the environments cannot drift apart.
# The environment `T-078` plans and (with a billing project) applies.

locals {
  environment = "dev"

  # Every resource name starts with this: metaldesk-<env>-<resource>. See infra/README.md.
  name_prefix = "metaldesk-${local.environment}"
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# Addresses follow the IP plan in infra/README.md. They are values, so they live here; the module names none.
module "network" {
  source = "../../modules/network"

  name_prefix = local.name_prefix
  region      = var.region

  run_subnet_cidr = "10.10.0.0/24"
  psc_subnet_cidr = "10.10.1.0/26"

  # The published private.googleapis.com range; see modules/network/README.md.
  google_apis_cidr = "199.36.153.8/30"

  # Owned by MongoDB Atlas; no default exists. See the manual prerequisite in modules/network/README.md.
  atlas_service_attachments = var.atlas_service_attachments
  atlas_dns_name            = var.atlas_dns_name
  atlas_hostnames           = var.atlas_hostnames
}
