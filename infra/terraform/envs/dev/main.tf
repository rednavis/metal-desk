# One environment: a thin root. It supplies values (values.tf, the ONLY file that differs between environments, besides the backend
# bucket and the tfvars example) and wires modules together; it declares no resources of its own, so the environments cannot
# drift apart. `infra/scripts/check-env-parity.sh` fails CI if the roots differ in anything but their values.

provider "google" {
  project = var.project_id
  region  = var.region

  # The Billing Budgets API is called on behalf of this project and needs these two.
  user_project_override = true
  billing_project       = var.project_id
}

# Applied first: every billable module below depends on it, so nothing can bill before the alerts exist.
module "budget" {
  source = "../../modules/budget"

  name_prefix     = local.name_prefix
  billing_account = var.billing_account_id
  project_number  = var.project_number
  amount          = local.budget.amount
  currency        = local.budget.currency
  alert_emails    = var.budget_alert_emails
}

# Addresses follow the IP plan in infra/README.md. They are values, in values.tf; the module names none.
module "network" {
  source     = "../../modules/network"
  depends_on = [module.budget]

  name_prefix = local.name_prefix
  region      = var.region

  run_subnet_cidr = local.network.run_subnet_cidr
  psc_subnet_cidr = local.network.psc_subnet_cidr

  # The published private.googleapis.com range; see modules/network/README.md.
  google_apis_cidr = "199.36.153.8/30"

  # Owned by MongoDB Atlas; no default exists. See the manual prerequisite in modules/network/README.md.
  atlas_service_attachments = var.atlas_service_attachments
  atlas_dns_name            = var.atlas_dns_name
  atlas_hostnames           = var.atlas_hostnames
}
