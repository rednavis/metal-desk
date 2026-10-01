# Egress is default-deny. Cloud Run instances (tagged local.run_tag) may reach only what is listed below; every
# allowance says what needs it. Nothing opens general internet egress: every external dependency is mocked in this
# build (ADR-0002) and the real market-data feed does not exist.
#
# A lower priority number wins. The allowances are 1000, the deny is 65000.

resource "google_compute_firewall" "deny_all_egress" {
  name        = "${var.name_prefix}-deny-all-egress"
  network     = google_compute_network.this.name
  description = "Default-deny egress for Cloud Run instances; the allow rules below open only what is needed."
  direction   = "EGRESS"
  priority    = 65000

  destination_ranges = ["0.0.0.0/0"]
  target_tags        = [local.run_tag]

  deny {
    protocol = "all"
  }
}

# Needed by: api and admin, to reach MongoDB Atlas through the Private Service Connect endpoints.
resource "google_compute_firewall" "allow_atlas_egress" {
  name        = "${var.name_prefix}-allow-atlas-egress"
  network     = google_compute_network.this.name
  description = "Cloud Run to the Atlas Private Service Connect endpoints."
  direction   = "EGRESS"
  priority    = 1000

  destination_ranges = [for address in google_compute_address.atlas : "${address.address}/32"]
  target_tags        = [local.run_tag]

  allow {
    protocol = "tcp"
    ports    = [var.atlas_port_range]
  }
}

# Needed by: every service, for Secret Manager and Artifact Registry over Private Google Access (HTTPS only).
resource "google_compute_firewall" "allow_google_apis_egress" {
  name        = "${var.name_prefix}-allow-google-apis-egress"
  network     = google_compute_network.this.name
  description = "Cloud Run to Google APIs over Private Google Access."
  direction   = "EGRESS"
  priority    = 1000

  destination_ranges = [var.google_apis_cidr]
  target_tags        = [local.run_tag]

  allow {
    protocol = "tcp"
    ports    = ["443"]
  }
}
