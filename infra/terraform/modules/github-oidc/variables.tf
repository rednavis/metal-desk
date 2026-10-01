variable "name_prefix" {
  description = "Prefix of every resource name, metaldesk-<env>. Comes from the environment root's local.name_prefix."
  type        = string
}

variable "environment" {
  description = "The environment this pool serves: dev, staging or prod. Each environment is its own GCP project, so each has its own pool and its own deployer account, and a dev workflow can never obtain prod credentials. The provider accepts only a job that declares this GitHub environment."
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging or prod."
  }
}

variable "github_repository" {
  description = "The one GitHub repository allowed to authenticate, as owner/name (for example rednavis/metal-desk). Required, no default: a looser value is the failure this module exists to prevent."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9._-]+$", var.github_repository))
    error_message = "github_repository must be the full owner/name of one repository."
  }
}

variable "github_repository_id" {
  description = "The numeric id of that repository (gh api repos/<owner>/<name> --jq .id). Checked as well as the name, so a deleted and re-created repository, or one that takes over the old name, is not trusted. Not a secret."
  type        = string

  validation {
    condition     = can(regex("^[0-9]+$", var.github_repository_id))
    error_message = "github_repository_id must be the repository's numeric id."
  }
}

variable "branch" {
  description = "The only branch whose workflow runs may authenticate."
  type        = string
  default     = "master"

  validation {
    condition     = can(regex("^[A-Za-z0-9._/-]+$", var.branch))
    error_message = "branch must be a plain branch name."
  }
}
