# A budget with alerts, applied BEFORE anything billable: the environment root makes every billable module depend on it, so
# Terraform cannot create a load balancer, a Cloud Run service or a registry until this exists. A budget does not cap
# spending; it warns, early enough to act, and the forecast alert warns before the money is spent.

resource "google_monitoring_notification_channel" "email" {
  for_each = toset(var.alert_emails)

  display_name = "${var.name_prefix} budget alert: ${each.value}"
  type         = "email"

  labels = {
    email_address = each.value
  }
}

resource "google_billing_budget" "this" {
  billing_account = var.billing_account
  display_name    = "${var.name_prefix}-budget"

  budget_filter {
    projects = ["projects/${var.project_number}"]
  }

  amount {
    specified_amount {
      currency_code = var.currency
      units         = tostring(var.amount)
    }
  }

  dynamic "threshold_rules" {
    for_each = toset(var.threshold_percents)

    content {
      threshold_percent = threshold_rules.value
      spend_basis       = "CURRENT_SPEND"
    }
  }

  # Warns when the month is on course to reach the whole budget, before it does.
  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "FORECASTED_SPEND"
  }

  all_updates_rule {
    monitoring_notification_channels = [for channel in google_monitoring_notification_channel.email : channel.id]

    # The billing account's own administrators are told as well as the listed addresses.
    disable_default_iam_recipients = false
  }
}
