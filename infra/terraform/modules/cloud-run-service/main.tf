# One Cloud Run service with its own identity. api, pricing-bridge and admin are three calls of this module; what
# differs between them is a parameter (variables.tf), never a copy of the resource.

locals {
  full_name = "${var.name_prefix}-${var.name}"

  # A service account id is 6 to 30 characters, and <prefix>-<name> overruns that for the longer environment and
  # service names (metaldesk-staging-pricing-bridge is 32). Keep the readable name when it fits; otherwise cut it and
  # add a short hash of the full name, so the id is still stable and unique.
  account_id = length(local.full_name) <= 30 ? local.full_name : "${substr(local.full_name, 0, 21)}-${substr(sha1(local.full_name), 0, 8)}"

  # Cloud Run's ingress setting for each exposure. Private is the default and the closed one.
  ingress = {
    "private"                = "INGRESS_TRAFFIC_INTERNAL_ONLY"
    "internal-load-balancer" = "INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"
    "public"                 = "INGRESS_TRAFFIC_ALL"
  }

  # Probe cadence. The startup probe gets startup_timeout_seconds in total (period * failures), which is how long a
  # JVM may take to come up before the instance is killed.
  startup_period_seconds = 10
  startup_failures       = ceil(var.startup_timeout_seconds / local.startup_period_seconds)
}

# Each service runs as its own account, with no project-level role at all. Access it needs is granted on the single
# resource that needs it: a secret (T-075), the image repository (T-073), another service (invoker_members).
resource "google_service_account" "this" {
  account_id   = local.account_id
  display_name = "${local.full_name} Cloud Run service"
  description  = "Runtime identity of ${local.full_name}. Holds no project-level role."
}

resource "google_cloud_run_v2_service" "this" {
  # Terraform owns the service's SHAPE (ingress, scaling, egress, secrets, probes); the CI deployer owns which image it runs
  # once the service exists (T-077), by deploying new revisions. So Terraform must not revert an image it did not set, and
  # must not fight the fields the deploy tooling stamps.
  lifecycle {
    ignore_changes = [
      template[0].containers[0].image,
      client,
      client_version,
    ]
  }

  name                = local.full_name
  location            = var.region
  description         = "MetalDesk ${var.name}"
  ingress             = local.ingress[var.exposure]
  deletion_protection = var.deletion_protection

  template {
    service_account = google_service_account.this.email

    scaling {
      min_instance_count = var.min_instance_count
      max_instance_count = var.max_instance_count
    }

    # Direct VPC egress, restricted to private ranges. The network tag is what makes the egress firewall rules apply.
    vpc_access {
      egress = var.vpc_egress.vpc_egress

      network_interfaces {
        network    = var.vpc_egress.network
        subnetwork = var.vpc_egress.subnetwork
        tags       = var.vpc_egress.network_tags
      }
    }

    containers {
      image = var.image

      ports {
        container_port = var.container_port
      }

      resources {
        limits = {
          cpu    = var.cpu
          memory = var.memory
        }
        cpu_idle          = !var.cpu_always_allocated
        startup_cpu_boost = true # a JVM is busiest while it starts
      }

      dynamic "env" {
        for_each = var.env
        content {
          name  = env.key
          value = env.value
        }
      }

      # References only: the value is read from Secret Manager when an instance starts and never appears here.
      dynamic "env" {
        for_each = var.secret_env
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = env.value.secret
              version = env.value.version
            }
          }
        }
      }

      startup_probe {
        period_seconds    = local.startup_period_seconds
        timeout_seconds   = 5
        failure_threshold = local.startup_failures

        http_get {
          path = var.health_path
        }
      }

      liveness_probe {
        period_seconds    = 30
        timeout_seconds   = 5
        failure_threshold = 3

        http_get {
          path = var.health_path
        }
      }
    }
  }
}

# Public access is an explicit opt-in: only exposure = "public" grants allUsers.
resource "google_cloud_run_v2_service_iam_member" "public" {
  count = var.exposure == "public" ? 1 : 0

  project  = google_cloud_run_v2_service.this.project
  location = google_cloud_run_v2_service.this.location
  name     = google_cloud_run_v2_service.this.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# Named callers (another service's account, for service-to-service calls), on this service only.
resource "google_cloud_run_v2_service_iam_member" "invoker" {
  for_each = toset(var.invoker_members)

  project  = google_cloud_run_v2_service.this.project
  location = google_cloud_run_v2_service.this.location
  name     = google_cloud_run_v2_service.this.name
  role     = "roles/run.invoker"
  member   = each.value
}

# The CI deployer: deploy revisions of THIS service (developer, not admin: admin can also rewrite the IAM policy, which
# would let CI make the service public), and act as this service's own runtime account to attach it. Nothing project-wide.
resource "google_cloud_run_v2_service_iam_member" "deployer" {
  for_each = toset(var.deployer_members)

  project  = google_cloud_run_v2_service.this.project
  location = google_cloud_run_v2_service.this.location
  name     = google_cloud_run_v2_service.this.name
  role     = "roles/run.developer"
  member   = each.value
}

resource "google_service_account_iam_member" "deployer_acts_as" {
  for_each = toset(var.deployer_members)

  service_account_id = google_service_account.this.name
  role               = "roles/iam.serviceAccountUser"
  member             = each.value
}
