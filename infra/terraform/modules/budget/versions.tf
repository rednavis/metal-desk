# A module states what it needs, not the exact versions: the environment roots pin them (envs/*/versions.tf).
terraform {
  required_version = ">= 1.16"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 8.5"
    }
  }
}
