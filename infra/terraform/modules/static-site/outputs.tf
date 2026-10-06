# The module's contract.

output "domain" {
  description = "The domain the site is served on."
  value       = var.domain
}

output "ip_address" {
  description = "The load balancer's address. Create a DNS A record from the domain to it, or the managed certificate never becomes active."
  value       = google_compute_global_address.site.address
}

output "url" {
  description = "The site's address."
  value       = "https://${var.domain}"
}

output "bucket_name" {
  description = "The bucket the bundle is uploaded to (by CI, T-077). Not publicly readable."
  value       = google_storage_bucket.site.name
}

output "access" {
  description = "Who may fetch the site: restricted or public."
  value       = var.access
}
