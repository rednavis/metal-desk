output "budget_name" {
  description = "The budget's resource name."
  value       = google_billing_budget.this.name
}

output "amount" {
  description = "The monthly amount and its currency, as set."
  value       = "${var.amount} ${var.currency}"
}
