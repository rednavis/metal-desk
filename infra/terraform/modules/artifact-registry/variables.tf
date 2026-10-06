variable "name_prefix" {
  description = "Prefix of every resource name, metaldesk-<env>. Comes from the environment root's local.name_prefix."
  type        = string
}

variable "name" {
  description = "The service this repository holds images for, such as api. The repository is named <name_prefix>-<name>."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*[a-z0-9]$", var.name))
    error_message = "name must be lowercase letters, digits and hyphens, starting with a letter and not ending with a hyphen."
  }
}

variable "region" {
  description = "The region of the repository. Keep it in the region the service runs in, so image pulls stay local."
  type        = string
}

variable "reader_members" {
  description = "IAM members (serviceAccount:<email>) that may pull from this repository, and nothing else. Pass the Cloud Run service account of the service this repository belongs to, and no other. Push is never granted here: CI pushes through Workload Identity Federation (T-077), not a long-lived key."
  type        = list(string)
  default     = []
}

variable "writer_members" {
  description = "IAM members (serviceAccount:<email>) that may push images to this repository: the CI deployer, which authenticates through Workload Identity Federation (T-077), never a long-lived key. Granted on this repository only. Not for a person or a runtime service account."
  type        = list(string)
  default     = []
}

variable "keep_recent_count" {
  description = "Always keep this many of the most recent image versions, however old."
  type        = number
  default     = 20

  validation {
    condition     = var.keep_recent_count >= 1
    error_message = "keep_recent_count must be at least 1, or the registry would delete the image that is running."
  }
}

variable "delete_after_days" {
  description = "Delete a version older than this many days unless it is among the keep_recent_count newest. Bounds the registry's growth."
  type        = number
  default     = 90

  validation {
    condition     = var.delete_after_days >= 1
    error_message = "delete_after_days must be at least 1."
  }
}

variable "delete_untagged_after_days" {
  description = "Delete an untagged version (a build's leftover layers, or a re-pushed digest) after this many days."
  type        = number
  default     = 7

  validation {
    condition     = var.delete_untagged_after_days >= 1
    error_message = "delete_untagged_after_days must be at least 1."
  }
}
