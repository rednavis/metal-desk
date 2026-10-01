variable "project_id" {
  description = "The GCP project this environment lives in. One project per environment."
  type        = string
}

variable "region" {
  description = "The region for regional resources (Cloud Run, Artifact Registry, networking)."
  type        = string
  default     = "europe-west3"
}
