# Module: `secret`

> **THE RULE: this module creates a secret's container and who may read it, and never a version with data.**
> There is no `google_secret_manager_secret_version` here and no argument that could carry a value. A value that passes
> through Terraform is written to its **state**, a plain JSON file in a storage bucket, readable by anyone with access to the
> bucket and not encrypted at the value level. So Terraform creates an empty secret and never learns what goes in it: a
> person adds each version with `gcloud secrets versions add`, out of band. The commands are in
> [`infra/README.md`](../../../README.md#secrets).

Written and validated by [`T-075`](../../../../tasks/T-075-tf-secret-manager.md); **never applied** by that task.
`envs/dev/secrets.tf` calls it four times.

## What it creates

- `google_secret_manager_secret`: the empty container, stored in **one region**, with `deletion_protection` on by default
  (its values live only in Secret Manager, set by hand, so destroying the container loses them for good).
- `google_secret_manager_secret_iam_member`: `roles/secretmanager.secretAccessor`, **on this secret only**, for **one**
  service account (`accessor_member`, validated as a single `serviceAccount:` member). Never a project-level grant, which
  would let every service read every secret; never an administrative role for a runtime identity. A secret two services need
  is two secrets, so each can be rotated and revoked on its own.

## Contract

**Inputs:** `name_prefix`, `name`, `location`, `accessor_member`, `deletion_protection` (true).
**Outputs:** `secret_id` (waits for the read grant, so a service that uses it is never created before its secret can be
read), `id`.

A Cloud Run service uses it as `secret_env = { NAME = { secret = module.<x>.secret_id, version = "<number>" } }`. The
Cloud Run module accepts only a **version number**, never a moving alias.

## Not here

Populating a value, rotation automation, customer-managed encryption keys.
