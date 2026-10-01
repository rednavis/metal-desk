# One static site: a private bucket, read only by the load balancer, behind an HTTPS external load balancer with Cloud
# CDN. `web` and `admin-web` are two calls of this module; what differs is a parameter, never a copy.
#
# Nothing here is public. The only path to the content is the load balancer, so a restriction placed on the load
# balancer (access = "restricted") cannot be bypassed by fetching the bucket directly.

locals {
  site_id = "${var.name_prefix}-${var.name}"

  # A restricted site with nobody allowed would be unreachable; a public site with a list would be misleading.
  restricted = var.access == "restricted"
}

resource "google_storage_bucket" "site" {
  # Bucket names are global; the project number makes this one unique without inventing a suffix.
  name          = "${local.site_id}-site-${var.project_number}"
  location      = var.region
  force_destroy = var.force_destroy

  uniform_bucket_level_access = true

  # No object can ever be made publicly readable, by an ACL or by a stray IAM binding. This is the guard against the
  # worst failure available here: a bundle fetched straight from the bucket, around whatever protects the load balancer.
  public_access_prevention = "enforced"

  # Deliberately no `website` block. The bucket's index and not-found page settings apply only to requests arriving
  # through a CNAME or A redirect to Cloud Storage, not to a backend bucket behind a load balancer, so they would be
  # inert. Deep links are handled by the URL map below.

  labels = {
    site = var.name
  }
}

# The load balancer's own service agent may read objects; nobody else is granted anything. This identity exists only
# once a backend bucket exists in the project, hence the dependency. Writing the bundle is CI's job, through
# Workload Identity Federation (T-077), and is not granted here.
resource "google_storage_bucket_iam_member" "load_balancer_reader" {
  bucket = google_storage_bucket.site.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:service-${var.project_number}@https-lb.iam.gserviceaccount.com"

  depends_on = [google_compute_backend_bucket.site]
}

# Single page application routing. A bucket-backed load balancer has no rewrite engine of its own and returns 404 for
# /orders/123, because no such object exists; the URL map rewrites those requests to /index.html. Rules are evaluated in
# priority order.
resource "google_compute_url_map" "site" {
  name        = "${local.site_id}-urlmap"
  description = "Serves ${var.name}: hashed assets and files as they are, every other path as the single page."

  # A request for any other host (the load balancer's bare IP, say) is sent to the real domain, never served.
  default_url_redirect {
    host_redirect          = var.domain
    https_redirect         = true
    redirect_response_code = "MOVED_PERMANENTLY_DEFAULT"
    strip_query            = false
  }

  host_rule {
    hosts        = [var.domain]
    path_matcher = "site"
  }

  path_matcher {
    name            = "site"
    default_service = google_compute_backend_bucket.site.id

    # 1. Content-hashed bundles. Served as they are, so a MISSING chunk is a real 404 and never the HTML shell: serving
    #    HTML for a missing .js file gives a blank page and a MIME-type console error that is very hard to attribute.
    route_rules {
      priority = 1
      service  = google_compute_backend_bucket.site.id

      match_rules {
        prefix_match = "/assets/"
      }
    }

    # 2. Anything else that names a file (favicon.svg, robots.txt, a source map): also served as it is, so a missing
    #    file is a real 404. This is why client-side routes must not end in a file extension.
    route_rules {
      priority = 2
      service  = google_compute_backend_bucket.site.id

      match_rules {
        regex_match = "^/.*\\.[A-Za-z0-9]+$"
      }
    }

    # 3. Everything else is a client-side route (/, /catalog, /orders/123): serve the single page and let the router
    #    take over. The whole path is replaced, so a deep link and a reload both work.
    route_rules {
      priority = 3

      match_rules {
        path_template_match = "/{path=**}"
      }

      route_action {
        url_rewrite {
          path_template_rewrite = "/index.html"
        }

        weighted_backend_services {
          backend_service = google_compute_backend_bucket.site.id
          weight          = 100
        }
      }
    }
  }
}

# HTTPS only. A managed certificate needs the domain and its DNS record (see the domain variable); until the record
# exists the certificate stays PROVISIONING and https does not answer, which is the safe failure.
resource "google_compute_managed_ssl_certificate" "site" {
  # The suffix changes with the domain, so a domain change creates the new certificate before removing the old one.
  name = "${local.site_id}-cert-${substr(sha1(var.domain), 0, 6)}"

  managed {
    domains = [var.domain]
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_compute_global_address" "site" {
  name        = "${local.site_id}-ip"
  description = "The address ${var.domain} must point an A record at."
}

resource "google_compute_target_https_proxy" "site" {
  name             = "${local.site_id}-https"
  url_map          = google_compute_url_map.site.id
  ssl_certificates = [google_compute_managed_ssl_certificate.site.id]
}

resource "google_compute_global_forwarding_rule" "https" {
  name                  = "${local.site_id}-https"
  ip_address            = google_compute_global_address.site.address
  ip_protocol           = "TCP"
  port_range            = "443"
  target                = google_compute_target_https_proxy.site.id
  load_balancing_scheme = "EXTERNAL_MANAGED"
}

# Port 80 exists only to send people to https; it never serves content. It is one more forwarding rule, and so one more
# standing charge, and can be turned off with http_redirect = false.
resource "google_compute_url_map" "http_redirect" {
  count = var.http_redirect ? 1 : 0

  name        = "${local.site_id}-http-redirect"
  description = "Redirects every http request to https."

  default_url_redirect {
    https_redirect         = true
    redirect_response_code = "MOVED_PERMANENTLY_DEFAULT"
    strip_query            = false
  }
}

resource "google_compute_target_http_proxy" "http_redirect" {
  count = var.http_redirect ? 1 : 0

  name    = "${local.site_id}-http"
  url_map = google_compute_url_map.http_redirect[0].id
}

resource "google_compute_global_forwarding_rule" "http_redirect" {
  count = var.http_redirect ? 1 : 0

  name                  = "${local.site_id}-http"
  ip_address            = google_compute_global_address.site.address
  ip_protocol           = "TCP"
  port_range            = "80"
  target                = google_compute_target_http_proxy.http_redirect[0].id
  load_balancing_scheme = "EXTERNAL_MANAGED"
}
