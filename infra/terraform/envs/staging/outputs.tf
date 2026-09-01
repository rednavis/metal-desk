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

output "services" {
  description = "Each Cloud Run service's URL and service account."
  value = {
    api            = { uri = module.api.uri, service_account_email = module.api.service_account_email }
    pricing_bridge = { uri = module.pricing_bridge.uri, service_account_email = module.pricing_bridge.service_account_email }
    admin          = { uri = module.admin.uri, service_account_email = module.admin.service_account_email }
  }
}

output "registries" {
  description = "Each service's image repository address. CI pushes <repository>/<service>:<commit-sha> here (T-077)."
  value = {
    api            = module.registry_api.repository_url
    pricing_bridge = module.registry_pricing_bridge.repository_url
    admin          = module.registry_admin.repository_url
  }
}

output "sites" {
  description = "Each SPA's address, load balancer IP (point the domain's A record at it), bucket and access."
  value = {
    web = {
      url         = module.web.url
      ip_address  = module.web.ip_address
      bucket_name = module.web.bucket_name
      access      = module.web.access
    }
    admin_web = {
      url         = module.admin_web.url
      ip_address  = module.admin_web.ip_address
      bucket_name = module.admin_web.bucket_name
      access      = module.admin_web.access
    }
  }
}

output "github_actions" {
  description = "The values of this environment's GitHub environment variables (identifiers, not secrets): WIF_PROVIDER and WIF_SERVICE_ACCOUNT, and the condition the provider enforces."
  value = {
    WIF_PROVIDER        = module.github_oidc.workload_identity_provider
    WIF_SERVICE_ACCOUNT = module.github_oidc.deployer_service_account_email
    attribute_condition = module.github_oidc.attribute_condition
  }
}

output "api_url" {
  description = "The api's Cloud Run URL. Its /actuator/health/liveness answers once the revision is up; see infra/README.md for the health check and what it depends on."
  value       = module.api.uri
}

output "budget" {
  description = "The monthly budget this environment's project is held to."
  value       = module.budget.amount
}
