# One VPC per environment, with two regional subnets and no automatic ones. Nothing here names an address, project or
# region: every value is a variable (see variables.tf), supplied by the environment root.

resource "google_compute_network" "this" {
  name                    = "${var.name_prefix}-vpc"
  auto_create_subnetworks = false
  description             = "The ${var.name_prefix} network: Cloud Run egress and the private path to MongoDB Atlas."
}

# Cloud Run's Direct VPC egress attaches here. Private Google Access lets instances here reach Google APIs (Secret
# Manager, Artifact Registry) over Google's private path rather than the public internet.
resource "google_compute_subnetwork" "run" {
  name                     = "${var.name_prefix}-run"
  region                   = var.region
  network                  = google_compute_network.this.id
  ip_cidr_range            = var.run_subnet_cidr
  private_ip_google_access = true
}

# The Private Service Connect endpoint addresses live here. Private Google Access is on for the same reason as above,
# so a workload placed in this subnet later does not need public egress for Google APIs either.
resource "google_compute_subnetwork" "psc" {
  name                     = "${var.name_prefix}-psc"
  region                   = var.region
  network                  = google_compute_network.this.id
  ip_cidr_range            = var.psc_subnet_cidr
  private_ip_google_access = true
}

locals {
  # The network tag the egress firewall rules select, and that Cloud Run instances must carry (the egress output
  # hands it to the cloud-run-service module). A rule that applies to a tag cannot catch unrelated future workloads.
  run_tag = "${var.name_prefix}-run"
}
