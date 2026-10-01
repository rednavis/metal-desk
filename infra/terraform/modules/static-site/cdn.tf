# The backend bucket, with Cloud CDN and its cache policy, and the edge restriction for a restricted site.
#
# TWO CACHE POLICIES, not one. Vite emits content-hashed files under /assets/ and a non-hashed index.html. The policy
# lives in the objects' Cache-Control metadata, set when the bundle is uploaded (.github/workflows/frontend-deploy.yml),
# and the CDN honours it (USE_ORIGIN_HEADERS):
#   /assets/*   public, max-age=31536000, immutable   a hashed file never changes, so cache it for a year
#   index.html  no-cache                              a deploy takes effect at once; no cache invalidation is ever needed
# One long TTL on everything would pin users to the previous bundle until it expired, and invalidating on every deploy
# is slow and rate-limited. With the split there is nothing to invalidate.

resource "google_compute_backend_bucket" "site" {
  name        = "${local.site_id}-backend"
  bucket_name = google_storage_bucket.site.name
  description = "The ${var.name} bundle, served only through the load balancer."
  enable_cdn  = true

  # Applied before the CDN cache, so a refused request never reaches it. Absent for a public site.
  edge_security_policy = local.restricted ? google_compute_security_policy.edge[0].id : null

  cdn_policy {
    cache_mode = "USE_ORIGIN_HEADERS"

    # A missing file is remembered only briefly, so a deploy that adds the file is not shadowed by a cached 404.
    negative_caching = true
    negative_caching_policy {
      code = 404
      ttl  = 10
    }
  }

  lifecycle {
    precondition {
      condition     = !local.restricted || length(var.allowed_ip_ranges) > 0
      error_message = "A restricted site needs allowed_ip_ranges: with none, nobody could reach it. Set access = \"public\" only if the site is meant to be open."
    }
  }
}

# The restriction for access = "restricted": an allow list at the edge, refusing everything else.
resource "google_compute_security_policy" "edge" {
  count = local.restricted ? 1 : 0

  name        = "${local.site_id}-edge"
  description = "Only allowed_ip_ranges may fetch ${var.name}."
  type        = "CLOUD_ARMOR_EDGE"

  # An edge rule takes at most ten ranges, so a longer list is split across rules.
  dynamic "rule" {
    for_each = chunklist(var.allowed_ip_ranges, 10)

    content {
      action      = "allow"
      priority    = 1000 + rule.key
      description = "Allowed ranges, part ${rule.key + 1}."

      match {
        versioned_expr = "SRC_IPS_V1"
        config {
          src_ip_ranges = rule.value
        }
      }
    }
  }

  rule {
    action      = "deny(403)"
    priority    = 2147483647
    description = "Default: refuse everything else."

    match {
      versioned_expr = "SRC_IPS_V1"
      config {
        src_ip_ranges = ["*"]
      }
    }
  }
}
