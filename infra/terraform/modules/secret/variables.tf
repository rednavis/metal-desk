variable "name_prefix" {
  description = "Prefix of every resource name, metaldesk-<env>. Comes from the environment root's local.name_prefix."
  type        = string
}

variable "name" {
  description = "What the secret is, such as jwt-signing-key. The secret's id is <name_prefix>-<name>."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*[a-z0-9]$", var.name))
    error_message = "name must be lowercase letters, digits and hyphens, starting with a letter and not ending with a hyphen."
  }
}

variable "location" {
  description = "The one region the secret is stored in. Keeping it to one region keeps the data where the deployment is."
  type        = string
}

variable "accessor_member" {
  description = "The single IAM member (serviceAccount:<email>) allowed to read this secret, and nothing else. One secret has one reader: a secret two services need is two secrets, so each can be rotated and revoked on its own."
  type        = string

  validation {
    condition     = can(regex("^serviceAccount:.+@.+$", var.accessor_member))
    error_message = "accessor_member must be one service account, written serviceAccount:<email>."
  }
}

variable "deletion_protection" {
  description = "Refuse to destroy the secret. On by default: its values live only in Secret Manager, set by hand, so destroying the container loses them and Terraform cannot put them back."
  type        = bool
  default     = true
}
