# THE RULE OF THIS MODULE: it creates the secret's CONTAINER and who may read it, and NEVER a version with data.
#
# There is no google_secret_manager_secret_version here, and no argument that could carry a value. A value passed through
# Terraform is written to its state, which is a plain JSON file in a storage bucket, readable by anyone with access to the
# bucket and not encrypted at the value level. So Terraform creates an empty secret and never learns what goes in it: a
# person (or CI) adds each version out of band with `gcloud secrets versions add`. The commands are in infra/README.md.

locals {
  secret_id = "${var.name_prefix}-${var.name}"
}

resource "google_secret_manager_secret" "this" {
  secret_id           = local.secret_id
  deletion_protection = var.deletion_protection

  labels = {
    purpose = var.name
  }

  replication {
    user_managed {
      replicas {
        location = var.location
      }
    }
  }
}

# Read-only, on this one secret, for one service account. Never a project-level grant (which would let every service read
# every secret), and never an administrative role for a runtime identity.
resource "google_secret_manager_secret_iam_member" "accessor" {
  secret_id = google_secret_manager_secret.this.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = var.accessor_member
}
