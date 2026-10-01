variable "project_id" {
  description = "The GCP project this environment lives in. One project per environment."
  type        = string
}

variable "region" {
  description = "The region for regional resources (Cloud Run, Artifact Registry, networking)."
  type        = string
  default     = "europe-west3"
}

variable "atlas_service_attachments" {
  description = "Atlas Private Service Connect service attachment URIs, keyed by a short name. Required, no default: they come from the Atlas console (modules/network/README.md)."
  type        = map(string)
}

variable "atlas_dns_name" {
  description = "The domain of Atlas's private connection string, with a trailing dot. Required, no default."
  type        = string
}

variable "atlas_hostnames" {
  description = "Atlas hostnames (trailing dot) to point at an endpoint, keyed like atlas_service_attachments."
  type        = map(string)
  default     = {}
}
