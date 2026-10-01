# The module's contract. CI (T-077) pushes to repository_url; the environment root composes image references from it.

output "repository_id" {
  description = "The repository's id, <name_prefix>-<name>."
  value       = google_artifact_registry_repository.this.repository_id
}

output "repository_url" {
  description = "The repository's Docker address, <region>-docker.pkg.dev/<project>/<repository>. An image reference is <repository_url>/<service>:<commit-sha>."
  value       = "${google_artifact_registry_repository.this.location}-docker.pkg.dev/${google_artifact_registry_repository.this.project}/${google_artifact_registry_repository.this.repository_id}"
}

output "location" {
  description = "The repository's region."
  value       = google_artifact_registry_repository.this.location
}
