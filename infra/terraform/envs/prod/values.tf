# The values of the prod environment, and the ONLY file (with backend.tf and terraform.tfvars.example) that differs between
# environments: prod keeps two api instances so one can be replaced without a cold start, and keeps images longer.
# `infra/scripts/check-env-parity.sh` fails CI if any other file differs. Amounts are starting points to revisit at the first
# real apply, from the cost table in infra/README.md.

locals {
  environment = "prod"

  # Every resource name starts with this: metaldesk-<env>-<resource>. See infra/README.md.
  name_prefix = "metaldesk-${local.environment}"

  # The monthly budget, in the billing account's own currency. Applied before anything billable (main.tf).
  budget = {
    amount   = 600
    currency = "EUR"
  }

  # The IP plan (infra/README.md): each environment owns a /16 and these are carved out of it, so no two overlap.
  network = {
    run_subnet_cidr = "10.30.0.0/24"
    psc_subnet_cidr = "10.30.1.0/26"
  }

  # Instance counts. A minimum above zero bills continuously; the reasons are recorded in services.tf and infra/README.md.
  api            = { min_instances = 2, max_instances = 20 }
  pricing_bridge = { min_instances = 1, max_instances = 1 }
  admin          = { min_instances = 0, max_instances = 5 }

  # Refuse to destroy the services / the secret containers (the secrets' values live only in Secret Manager).
  services_deletion_protection = true
  secret_deletion_protection   = true

  # The pinned version of each secret a service reads. Bump deliberately when a secret is rotated (infra/README.md, "Secrets").
  secret_versions = {
    mongodb_uri_api       = "1"
    jwt_signing_key       = "1"
    mongodb_uri_admin     = "1"
    admin_jwt_signing_key = "1"
  }

  # Image retention per repository (infra/README.md): keep the newest N whatever their age, delete the rest after D days.
  registry = { keep_recent_count = 50, delete_after_days = 365 }

  # The one repository and branch whose workflow runs may authenticate (modules/github-oidc), by name and numeric id.
  github = {
    repository    = "rednavis/metal-desk"
    repository_id = "1371039595"
    branch        = "master"
  }
}
