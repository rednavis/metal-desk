# Module: `budget`

A monthly budget with email alerts, for one environment's project. **It is applied before anything billable**: the
environment root makes every billable module (`network`, the Cloud Run services, the registries, the sites) `depends_on` this
one, so Terraform cannot create them first. Written and validated by [`T-078`](../../../tasks/T-078-phase5-gate.md); **never
applied** by that task.

A budget **does not cap spending**. It warns at 50%, 90% and 100% of current spend, and at 100% of **forecast** spend, which
fires while the month is still on course rather than after the money is gone.

**Inputs:** `name_prefix`, `billing_account`, `project_number`, `amount`, `currency` (the billing account's own),
`alert_emails` (required, not empty; personal addresses, so only in the gitignored `terraform.tfvars`), `threshold_percents`.
**Outputs:** `budget_name`, `amount`.

**Also required before apply:** the `billingbudgets.googleapis.com` and `monitoring.googleapis.com` APIs enabled, and a billing
role on the billing account for whoever applies (a budget belongs to the account, not the project). The root's provider sets
`user_project_override` and `billing_project`, which the Billing Budgets API requires.
