# The module's contract. The cloud-run-service module (T-072) consumes these and does not reach into the module.

output "network_id" {
  description = "The VPC's id."
  value       = google_compute_network.this.id
}

output "network_name" {
  description = "The VPC's name."
  value       = google_compute_network.this.name
}

output "subnet_ids" {
  description = "The subnet ids, keyed run and psc."
  value = {
    run = google_compute_subnetwork.run.id
    psc = google_compute_subnetwork.psc.id
  }
}

output "cloud_run_egress" {
  description = "What a Cloud Run service sets for Direct VPC egress: the network and subnetwork to attach to, the network tag the egress firewall rules select, and the egress mode. Private ranges only, so Google APIs and Secret Manager do not route through the VPC."
  value = {
    network      = google_compute_network.this.name
    subnetwork   = google_compute_subnetwork.run.name
    network_tags = [local.run_tag]
    vpc_egress   = "PRIVATE_RANGES_ONLY"
  }
}

output "atlas_dns_name" {
  description = "The private DNS domain that resolves the Atlas hostnames inside this VPC."
  value       = google_dns_managed_zone.atlas.dns_name
}

output "atlas_endpoint_addresses" {
  description = "The internal address of each Private Service Connect endpoint, keyed like atlas_service_attachments."
  value       = { for key, address in google_compute_address.atlas : key => address.address }
}
