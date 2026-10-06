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

variable "api_image" {
  description = "The api image, pinned by digest or commit-SHA tag. Required, no default: T-073 builds and pushes it."
  type        = string
}

variable "pricing_bridge_image" {
  description = "The pricing-bridge image, pinned by digest or commit-SHA tag. Required, no default."
  type        = string
}

variable "admin_image" {
  description = "The admin image, pinned by digest or commit-SHA tag. Required, no default."
  type        = string
}

variable "project_number" {
  description = "The numeric number of the GCP project (not its id). Names the load balancer's service agent and the bucket. Required, no default."
  type        = string
}

variable "web_domain" {
  description = "The domain the storefront is served on. Required, no default: a managed certificate needs a real domain you control, and its DNS A record must point at the load balancer (modules/static-site/README.md)."
  type        = string
}

variable "admin_web_domain" {
  description = "The domain the staff SPA is served on. Required, no default; same DNS prerequisite as web_domain."
  type        = string
}

variable "admin_web_allowed_ip_ranges" {
  description = "CIDR ranges allowed to fetch the staff SPA (office and VPN egress). Required, no default: with none, nobody could reach it."
  type        = list(string)
}

variable "billing_account_id" {
  description = "The billing account the project is billed to (three groups of six hexadecimal characters). Required, no default: the budget watches the project's spend on it."
  type        = string
}

variable "budget_alert_emails" {
  description = "Who is emailed when a budget threshold is crossed. Required, no default, not empty; personal addresses, so only in the gitignored terraform.tfvars."
  type        = list(string)
}
