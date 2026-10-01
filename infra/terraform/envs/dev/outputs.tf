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

output "network" {
  description = "The network module's outputs, for the services that will attach to it."
  value = {
    network_id       = module.network.network_id
    subnet_ids       = module.network.subnet_ids
    cloud_run_egress = module.network.cloud_run_egress
    atlas_dns_name   = module.network.atlas_dns_name
  }
}
