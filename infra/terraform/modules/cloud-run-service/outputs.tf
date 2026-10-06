# The module's contract. Later tasks consume these: T-075 grants the service account access to its secrets, T-073 to
# its image repository, T-076 fronts admin with IAP.

output "name" {
  description = "The Cloud Run service's name."
  value       = google_cloud_run_v2_service.this.name
}

output "uri" {
  description = "The service's run.app URL. Reachable only as far as its exposure allows."
  value       = google_cloud_run_v2_service.this.uri
}

output "location" {
  description = "The region the service runs in."
  value       = google_cloud_run_v2_service.this.location
}

output "service_account_email" {
  description = "The service's own account. Grant it access to the resources it needs, one at a time."
  value       = google_service_account.this.email
}

output "service_account_member" {
  description = "The service account as an IAM member string (serviceAccount:<email>), ready for a binding or for another service's invoker_members."
  value       = google_service_account.this.member
}
