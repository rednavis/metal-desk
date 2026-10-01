# Module: `artifact-registry`

One Docker repository for one service, with **immutable tags**, a **cleanup policy**, and pull access for the owning
service only. `envs/dev/registry.tf` calls it three times (`api`, `pricing-bridge`, `admin`). Written and validated by
[`T-073`](../../../tasks/T-073-tf-artifact-registry.md); **never applied** by that task.

## Contract

**Inputs:** `name_prefix`, `name`, `region`, `reader_members`, `keep_recent_count` (20), `delete_after_days` (90),
`delete_untagged_after_days` (7). **Outputs:** `repository_id`, `repository_url`, `location`.

## Tags

An image is `<repository_url>/<service>:<commit-sha>`, for example
`europe-west3-docker.pkg.dev/<project>/metaldesk-dev-api/api:<40-hex-sha>`. **Immutable tags are on**: a pushed tag
cannot be overwritten. The Cloud Run module accepts only a digest or a commit-SHA tag, so a deployment always names one
exact build; the registry is what makes that name stay true.

## Retention

| Rule | Value | Effect |
|---|---|---|
| Keep the most recent | **20** versions | Never deleted, whatever their age |
| Delete anything older than | **90 days** | Unless it is among the newest 20 |
| Delete untagged versions after | **7 days** | Leftover layers and re-pushed digests |

`KEEP` wins over `DELETE`, so the policy is "the newest 20, plus whatever is under 90 days old". **The policy cannot
know what is deployed.** A revision still running an image that is both outside the newest 20 and older than 90 days
would lose it, and a rollback to it would then need a rebuild. At this project's cadence that is a long way off; raise
`keep_recent_count` (or `delete_after_days`) for `prod` rather than assume it cannot happen. Retention for `prod` is
`T-078`'s to decide; these numbers are the starting point.

## Permissions

Pull is granted **per repository, per member**, with `roles/artifactregistry.reader` (`reader_members`). The dev root
passes each Cloud Run service's own account to its own repository only, so `pricing-bridge` cannot pull `api`'s image.
**No push role is granted to anyone** here: CI pushes through Workload Identity Federation (`T-077`), never a
long-lived key.

## Also required before apply

The `artifactregistry.googleapis.com` API enabled in the project. Which identity actually performs a pull (Cloud Run's
own service agent, or the service's runtime account) is not something this task could verify without a project; the grant
here is to the runtime account, as the task specifies. If a first deployment cannot pull its image, check the Cloud Run
service agent's access to the repository first.
