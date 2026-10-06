# One Docker repository for one service. Per-service repositories (Architecture §7) mean a service account can be given
# pull access to its own images and no one else's.

locals {
  repository_id = "${var.name_prefix}-${var.name}"
}

resource "google_artifact_registry_repository" "this" {
  repository_id = local.repository_id
  location      = var.region
  format        = "DOCKER"
  description   = "Container images of the ${var.name} service. Tags are commit SHAs and immutable."

  docker_config {
    # A pushed tag can never be repointed. This is what makes "commit SHA, never a mutable tag" (Architecture §7)
    # something the registry enforces rather than something a convention hopes for, and what lets a deployment that
    # names a tag mean one exact build.
    immutable_tags = true
  }

  # Without a cleanup policy a registry only ever grows. Evaluated together: KEEP wins over DELETE, so the newest
  # keep_recent_count versions survive whatever their age.
  cleanup_policy_dry_run = false

  cleanup_policies {
    id     = "delete-untagged"
    action = "DELETE"

    condition {
      tag_state  = "UNTAGGED"
      older_than = "${var.delete_untagged_after_days * 86400}s"
    }
  }

  cleanup_policies {
    id     = "delete-old"
    action = "DELETE"

    condition {
      tag_state  = "ANY"
      older_than = "${var.delete_after_days * 86400}s"
    }
  }

  cleanup_policies {
    id     = "keep-recent"
    action = "KEEP"

    most_recent_versions {
      keep_count = var.keep_recent_count
    }
  }
}

# Pull access, per member, on this repository only. Not a project-level role.
resource "google_artifact_registry_repository_iam_member" "reader" {
  for_each = toset(var.reader_members)

  project    = google_artifact_registry_repository.this.project
  location   = google_artifact_registry_repository.this.location
  repository = google_artifact_registry_repository.this.name
  role       = "roles/artifactregistry.reader"
  member     = each.value
}

# Push access, per member, on this repository only: for the CI deployer. Not a project-level role, and never granted to a
# runtime account or to anything that holds a key.
resource "google_artifact_registry_repository_iam_member" "writer" {
  for_each = toset(var.writer_members)

  project    = google_artifact_registry_repository.this.project
  location   = google_artifact_registry_repository.this.location
  repository = google_artifact_registry_repository.this.name
  role       = "roles/artifactregistry.writer"
  member     = each.value
}
