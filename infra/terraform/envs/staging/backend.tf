# State for this environment lives in its own versioned GCS bucket, never shared with another environment.
# The bucket cannot be created by the Terraform whose state it holds: bootstrap/create-state-bucket.sh creates it
# once, with gcloud. See infra/README.md ("The state buckets").
terraform {
  backend "gcs" {
    bucket = "metaldesk-staging-tfstate"
    prefix = "terraform/state"
  }
}
