# Module: `network`

One VPC per environment, the private path from Cloud Run to MongoDB Atlas, and a default-deny egress posture.
Written and validated by [`T-071`](../../../tasks/T-071-tf-networking.md); **never applied** by that task.

## What it creates

| File | Resources |
|---|---|
| `main.tf` | The VPC (no automatic subnets); a `run` subnet for Cloud Run's Direct VPC egress; a `psc` subnet for endpoint addresses. Private Google Access is on for both. |
| `psc.tf` | Per Atlas service attachment: an internal address and a Private Service Connect forwarding rule. A private Cloud DNS zone for the Atlas domain, with an `A` record per hostname you supply. |
| `firewall.tf` | Deny all egress for instances tagged `<name_prefix>-run` (priority 65000), then allow only Atlas on its port range and Google APIs on 443 (priority 1000). Each rule says what needs it. |

## Inputs

`name_prefix`, `region`, `run_subnet_cidr`, `psc_subnet_cidr`, `atlas_service_attachments`, `atlas_dns_name`,
`atlas_hostnames`, `atlas_port_range`, `google_apis_cidr`. See `variables.tf`. **No address, project id or region is
written in the module**; the CIDRs and the Google APIs range come from the environment root.

## Outputs (the contract `T-072` consumes)

| Output | Use |
|---|---|
| `network_id`, `network_name` | The VPC |
| `subnet_ids` | `{ run, psc }` |
| `cloud_run_egress` | `{ network, subnetwork, network_tags, vpc_egress }`: set these on a Cloud Run service's `vpc_access` for Direct VPC egress. `vpc_egress` is `PRIVATE_RANGES_ONLY`, and the **tag must be set** or the firewall rules do not apply to the service |
| `atlas_dns_name` | The private zone's domain |
| `atlas_endpoint_addresses` | Endpoint address per attachment key |

## Egress mechanism: Direct VPC egress, not a Serverless VPC Access connector

Both let Cloud Run reach a private address. **Direct VPC egress is chosen.**

| | Direct VPC egress (chosen) | Serverless VPC Access connector |
|---|---|---|
| Idle cost | **None.** There is no resource to bill; instances take addresses from the `run` subnet while they run | An always-on connector (two or more VM-backed instances) bills every hour, with or without traffic |
| Scaling | Scales with Cloud Run, no separate capacity to size | A throughput tier to pick, and a `/28` that cannot be resized in place |
| Firewall | Rules apply per instance, selectable by network tag (used here) | Rules apply to the connector's address range, so they cannot tell services apart |
| Constraint | The subnet must be big enough: one address per running instance, `/26` at least | None of that |

**What would make us switch to a connector:** a Cloud Run feature or region this project needs that Direct VPC egress
does not support, or instance counts so high that subnet sizing becomes the problem. Neither applies now.

## The manual Atlas prerequisite

The service attachments are **owned by MongoDB Atlas**, so this repository cannot create them and the module has no
default for them. Before a real `plan` or `apply`:

1. In Atlas, for the cluster's project, create a **Private Endpoint** for Google Cloud in the same region as this
   environment. Atlas shows the service attachment name(s) and the connection details.
2. Put each attachment's URI (`projects/<atlas-project>/regions/<region>/serviceAttachments/<name>`) into
   `atlas_service_attachments` in the environment's `terraform.tfvars`, under a short key.
3. Put the domain of the private connection string into `atlas_dns_name` (with a trailing dot) and, if Atlas gives
   per-endpoint hostnames, map them in `atlas_hostnames`.

An empty `atlas_service_attachments` is rejected with a message pointing here, so a plan made too early fails clearly.

> **Unverified.** How Atlas names its hostnames for Google Cloud, and how many attachments it issues, depend on
> Atlas's current Private Endpoint scheme. The module takes any number of attachments and any hostname map for that
> reason. **Confirm the DNS arrangement against the Atlas console when the endpoint exists**; nothing here has run
> against a real Atlas project.

## Also required before apply

The `compute.googleapis.com` and `dns.googleapis.com` APIs enabled in the project. This module does not enable them.
