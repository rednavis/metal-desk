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
