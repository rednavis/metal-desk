# Version constraints. The Terraform version is stated in two places that must move together: here and
# `terraform_version` in .github/workflows/infra.yml. Provider versions live only here.
terraform {
  required_version = "~> 1.16"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.5"
    }
  }
}
