variable "name_prefix" {
  description = "Prefix of every resource name, metaldesk-<env>. Comes from the environment root's local.name_prefix."
  type        = string
}

variable "name" {
  description = "The site's short name, such as web or admin-web. Resources are named <name_prefix>-<name>-..."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*[a-z0-9]$", var.name))
    error_message = "name must be lowercase letters, digits and hyphens, starting with a letter and not ending with a hyphen."
  }
}

variable "region" {
  description = "The location of the bucket."
  type        = string
}

variable "project_number" {
  description = "The numeric number (not the id) of the GCP project. It names the load balancer's service agent, service-<number>@https-lb.iam.gserviceaccount.com, which is the one identity allowed to read the bucket, and makes the globally unique bucket name."
  type        = string

  validation {
    condition     = can(regex("^[0-9]+$", var.project_number))
    error_message = "project_number must be the project's numeric number, digits only."
  }
}

variable "domain" {
  description = <<-EOT
    The domain the site is served on, such as app.example.com. Required, with no default, and none can be invented: a
    Google-managed certificate is issued only for a domain you control whose DNS A record points at this module's
    ip_address output. An apply needs that domain, and the certificate stays PROVISIONING until the DNS record exists.
    There is no fallback to an unencrypted listener.
  EOT
  type        = string

  validation {
    condition     = can(regex("^([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}$", var.domain))
    error_message = "domain must be a lowercase fully qualified domain name such as app.example.com, without a scheme or a trailing dot."
  }
}

variable "access" {
  description = <<-EOT
    Who may fetch the site through the load balancer. The default is the closed one.
      restricted - only requests from allowed_ip_ranges get through; everything else is refused at the edge (Cloud Armor
                   edge security policy). For the staff SPA, which has no business being fetched by the public.
      public     - anyone. An explicit opt-in; for the storefront.
    The bucket itself is never public in either case: only the load balancer can read it.
  EOT
  type        = string
  default     = "restricted"

  validation {
    condition     = contains(["restricted", "public"], var.access)
    error_message = "access must be restricted or public."
  }
}

variable "allowed_ip_ranges" {
  description = "CIDR ranges allowed through when access is restricted (for example the office and VPN egress addresses). Required, and must not be empty, when access is restricted."
  type        = list(string)
  default     = []

  validation {
    condition     = alltrue([for range in var.allowed_ip_ranges : can(cidrhost(range, 0))])
    error_message = "Every entry of allowed_ip_ranges must be a valid CIDR block."
  }
}

variable "http_redirect" {
  description = "Also listen on port 80, only to redirect to https. Content is never served over http. Costs one more forwarding rule."
  type        = bool
  default     = true
}

variable "force_destroy" {
  description = "Allow destroying the bucket while it still holds objects. Off by default."
  type        = bool
  default     = false
}
