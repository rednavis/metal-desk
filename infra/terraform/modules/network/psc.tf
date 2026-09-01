# Private Service Connect to MongoDB Atlas: one internal address and one forwarding rule per Atlas service attachment.
# The attachment URIs are owned by Atlas and have no default (variables.tf); nothing here invents one.

resource "google_compute_address" "atlas" {
  for_each = var.atlas_service_attachments

  name         = "${var.name_prefix}-atlas-${each.key}"
  region       = var.region
  subnetwork   = google_compute_subnetwork.psc.id
  address_type = "INTERNAL"
  description  = "Private Service Connect endpoint address for the Atlas service attachment ${each.key}."
}

resource "google_compute_forwarding_rule" "atlas" {
  for_each = var.atlas_service_attachments

  name                  = "${var.name_prefix}-atlas-${each.key}"
  region                = var.region
  network               = google_compute_network.this.id
  ip_address            = google_compute_address.atlas[each.key].id
  target                = each.value
  load_balancing_scheme = "" # a Private Service Connect endpoint, not a load balancer
}

# The Atlas connection string's domain resolves, inside this VPC only, to the endpoint addresses.
resource "google_dns_managed_zone" "atlas" {
  name        = "${var.name_prefix}-atlas"
  dns_name    = var.atlas_dns_name
  visibility  = "private"
  description = "Resolves the MongoDB Atlas private hostnames to the Private Service Connect endpoints."

  private_visibility_config {
    networks {
      network_url = google_compute_network.this.id
    }
  }
}

resource "google_dns_record_set" "atlas" {
  for_each = var.atlas_hostnames

  managed_zone = google_dns_managed_zone.atlas.name
  name         = each.value
  type         = "A"
  ttl          = 300
  rrdatas      = [google_compute_address.atlas[each.key].address]
}
