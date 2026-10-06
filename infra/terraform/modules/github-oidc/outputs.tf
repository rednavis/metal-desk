# The module's contract. The first two are what a GitHub environment's variables hold; they are identifiers, not secrets.

output "workload_identity_provider" {
  description = "The provider's full resource name, for the WIF_PROVIDER variable of the GitHub environment (projects/<number>/locations/global/workloadIdentityPools/<pool>/providers/github)."
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "deployer_service_account_email" {
  description = "The deployer's email, for the WIF_SERVICE_ACCOUNT variable of the GitHub environment."
  value       = google_service_account.deployer.email
}

output "deployer_member" {
  description = "The deployer as an IAM member (serviceAccount:<email>), for the deployer_members and writer_members inputs of the modules that grant it access to one resource."
  value       = google_service_account.deployer.member
}

output "attribute_condition" {
  description = "The provider's attribute condition, as applied, so a reviewer can read what is permitted without reading the module."
  value       = local.attribute_condition
}
