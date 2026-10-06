# The secrets the deployed system needs, and the one service account that reads each. Only secrets with a consumer today
# exist: ADR-0002 mocks every external dependency, so there is no payment, mail or market-data credential to hold, and
# none is pre-created, because an empty secret named for a provider invites someone to fill it with a real key.
#
# Each is a container only: Terraform never sees a value. Populate them out of band (infra/README.md, "Secrets"), BEFORE
# applying the services, which refer to the version numbers in values.tf.
#
# One reader per secret. The database URI is two secrets, one per service, so api and admin can use separate Atlas
# database users and be rotated or revoked independently.
#
# pricing-bridge reads no secret: its fake feed needs none and it has no database.

module "secret_mongodb_uri_api" {
  source = "../../modules/secret"

  name_prefix     = local.name_prefix
  name            = "mongodb-uri-api"
  location        = var.region
  accessor_member = module.api.service_account_member

  deletion_protection = local.secret_deletion_protection
}

module "secret_jwt_signing_key" {
  source = "../../modules/secret"

  name_prefix     = local.name_prefix
  name            = "jwt-signing-key"
  location        = var.region
  accessor_member = module.api.service_account_member

  deletion_protection = local.secret_deletion_protection
}

module "secret_mongodb_uri_admin" {
  source = "../../modules/secret"

  name_prefix     = local.name_prefix
  name            = "mongodb-uri-admin"
  location        = var.region
  accessor_member = module.admin.service_account_member

  deletion_protection = local.secret_deletion_protection
}

module "secret_admin_jwt_signing_key" {
  source = "../../modules/secret"

  name_prefix     = local.name_prefix
  name            = "admin-jwt-signing-key"
  location        = var.region
  accessor_member = module.admin.service_account_member

  deletion_protection = local.secret_deletion_protection
}
