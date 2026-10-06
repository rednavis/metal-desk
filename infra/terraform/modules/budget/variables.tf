variable "name_prefix" {
  description = "Prefix of every resource name, metaldesk-<env>. Comes from the environment root's local.name_prefix."
  type        = string
}

variable "billing_account" {
  description = "The billing account id the project is billed to (the part after billingAccounts/, such as 012345-6789AB-CDEF01 in shape). Required, no default. The identity applying this needs a billing role on the account; a budget is a billing-account resource, not a project one."
  type        = string

  validation {
    condition     = can(regex("^[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}$", var.billing_account))
    error_message = "billing_account must be a billing account id: three groups of six hexadecimal characters separated by hyphens."
  }
}

variable "project_number" {
  description = "The numeric number of the project the budget watches. The budget is filtered to this project, so one environment's spend is never mixed with another's."
  type        = string

  validation {
    condition     = can(regex("^[0-9]+$", var.project_number))
    error_message = "project_number must be the project's numeric number, digits only."
  }
}

variable "amount" {
  description = "The monthly budget, in whole units of currency. A reference build is entirely avoidable cost: choose it deliberately, from the cost table in infra/README.md."
  type        = number

  validation {
    condition     = var.amount >= 1 && floor(var.amount) == var.amount
    error_message = "amount must be a whole number of at least 1."
  }
}

variable "currency" {
  description = "The budget's currency code, such as EUR. It must be the billing account's own currency, or the API refuses the budget."
  type        = string

  validation {
    condition     = can(regex("^[A-Z]{3}$", var.currency))
    error_message = "currency must be a three-letter code such as EUR."
  }
}

variable "alert_emails" {
  description = "Who is emailed when a threshold is crossed. Required, no default, and not empty: an alert nobody receives is not an alert. These are personal addresses, so they belong in the gitignored terraform.tfvars, never in the repository."
  type        = list(string)

  validation {
    condition     = length(var.alert_emails) > 0 && alltrue([for email in var.alert_emails : can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", email))])
    error_message = "alert_emails must hold at least one valid email address."
  }
}

variable "threshold_percents" {
  description = "Fractions of the amount at which a current-spend alert is sent (0.5 is half). A forecast alert at 100 percent is always added, which warns before the money is spent."
  type        = list(number)
  default     = [0.5, 0.9, 1.0]

  validation {
    condition     = length(var.threshold_percents) > 0 && alltrue([for percent in var.threshold_percents : percent > 0])
    error_message = "threshold_percents must be a non-empty list of positive fractions."
  }
}
