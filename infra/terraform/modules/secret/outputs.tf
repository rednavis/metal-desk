# The module's contract. A Cloud Run service takes the secret_id in its secret_env, with a version number.

output "secret_id" {
  description = "The secret's id, <name_prefix>-<name>. Pass it as the secret of a Cloud Run service's secret_env entry, with a pinned version number. The output waits for the read grant, so a service that uses it is never created before its secret can be read."
  value       = google_secret_manager_secret.this.secret_id

  depends_on = [google_secret_manager_secret_iam_member.accessor]
}

output "id" {
  description = "The secret's full resource name, for gcloud commands and IAM."
  value       = google_secret_manager_secret.this.id
}
