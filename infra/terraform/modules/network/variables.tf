variable "name_prefix" {
  description = "Prefix of every resource name, metaldesk-<env>. Comes from the environment root's local.name_prefix."
  type        = string
}

variable "region" {
  description = "Region of the subnets and of the Private Service Connect endpoints. Must be the region Cloud Run runs in."
  type        = string
}

variable "run_subnet_cidr" {
  description = "CIDR of the subnet Cloud Run's Direct VPC egress attaches to. Every running instance takes one address, so size it for the peak instance count; a /26 is the smallest Cloud Run accepts."
  type        = string

  validation {
    condition     = can(cidrhost(var.run_subnet_cidr, 0)) && tonumber(split("/", var.run_subnet_cidr)[1]) <= 26
    error_message = "run_subnet_cidr must be a CIDR block of /26 or larger (a smaller prefix number), for example a /24."
  }
}

variable "psc_subnet_cidr" {
  description = "CIDR of the subnet that holds the Private Service Connect endpoint addresses, one per Atlas service attachment."
  type        = string

  validation {
    condition     = can(cidrhost(var.psc_subnet_cidr, 0))
    error_message = "psc_subnet_cidr must be a valid CIDR block."
  }
}

variable "atlas_service_attachments" {
  description = <<-EOT
    The MongoDB Atlas service attachments to create a Private Service Connect endpoint for, keyed by a short name of
    your choosing (lowercase letters, digits, hyphens), valued by the attachment's URI,
    projects/<atlas-project>/regions/<region>/serviceAttachments/<name>.

    There is NO default and none can be invented: Atlas issues these when a private endpoint is created on the Atlas
    side, which is a manual step (Atlas console > Network Access > Private Endpoint, or the Atlas provider). See this
    module's README. An empty map is rejected, so a plan made before the Atlas side exists fails here and says why.
  EOT
  type        = map(string)

  validation {
    condition     = length(var.atlas_service_attachments) > 0
    error_message = "atlas_service_attachments is empty. Create the private endpoint on the Atlas side first and copy its service attachment URIs here (see modules/network/README.md)."
  }

  validation {
    condition = alltrue([
      for key, uri in var.atlas_service_attachments :
      can(regex("^[a-z0-9-]+$", key)) && can(regex("^projects/[^/]+/regions/[^/]+/serviceAttachments/[^/]+$", uri))
    ])
    error_message = "Each key must be lowercase letters, digits and hyphens, and each value a service attachment URI of the form projects/<project>/regions/<region>/serviceAttachments/<name>."
  }
}

variable "atlas_dns_name" {
  description = "The DNS domain Atlas's private connection strings live under, with a trailing dot. A private Cloud DNS zone for it is attached to the VPC. Take it from the Atlas connection string; see this module's README."
  type        = string

  validation {
    condition     = can(regex("\\.$", var.atlas_dns_name))
    error_message = "atlas_dns_name must be a fully qualified domain name with a trailing dot."
  }
}

variable "atlas_hostnames" {
  description = "Fully qualified hostnames (trailing dot) to point at an endpoint, keyed by the atlas_service_attachments key they resolve to. Empty until the Atlas connection details are known."
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for key in keys(var.atlas_hostnames) : contains(keys(var.atlas_service_attachments), key)])
    error_message = "Every key of atlas_hostnames must also be a key of atlas_service_attachments."
  }
}

variable "atlas_port_range" {
  description = "TCP ports Atlas serves on through a Private Service Connect endpoint. This is Atlas's documented range, not an environment value."
  type        = string
  default     = "1024-65535"
}

variable "google_apis_cidr" {
  description = "The Google APIs virtual IP range a Cloud Run instance may reach over HTTPS: the published private.googleapis.com range. Passed in, not written here, so no address lives in the module."
  type        = string

  validation {
    condition     = can(cidrhost(var.google_apis_cidr, 0))
    error_message = "google_apis_cidr must be a valid CIDR block."
  }
}
