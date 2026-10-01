output "environment" {
  description = "The environment this root deploys."
  value       = local.environment
}

output "name_prefix" {
  description = "The prefix every resource name in this environment starts with."
  value       = local.name_prefix
}

output "project_id" {
  description = "The GCP project of this environment."
  value       = var.project_id
}

output "region" {
  description = "The default region of this environment."
  value       = var.region
}
