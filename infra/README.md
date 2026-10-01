# Infrastructure

Terraform for the reference GCP deployment ([Architecture §7](../docs/architecture.md#7-reference-deployment-gcp),
[ADR-0003](../docs/adr/0003-gcp-target-architecture.md)). Everything is under [`terraform/`](terraform/).

> [!WARNING]
> **Writing, formatting, validating and linting Terraform costs nothing, and `terraform plan` costs nothing.**
> **`terraform apply` bills real money.** Tasks `T-070` … `T-077` are write-and-validate only; none of them applies
> anything. **[`T-078`](../tasks/T-078-phase5-gate.md) is the first task that applies real infrastructure** and is
> where a budget alert must be added, before the first apply. Do not `apply` from any other task.

## Layout

```
infra/terraform/
├── envs/
│   ├── dev/         a thin root: values and module calls, no resources of its own
│   ├── staging/     placeholder, wired in T-078
│   └── prod/        placeholder, wired in T-078
├── modules/         reusable modules, one per concern (see modules/README.md for the owning task)
├── bootstrap/       the one-time script that creates a state bucket
└── .tflint.hcl      linter configuration
```

**Thin roots.** A root supplies values and wires modules together; logic lives in a module. That is what keeps `prod`
from drifting away from `dev` by accumulating resources of its own. Each root has the same file set:
`versions.tf` (version constraints), `backend.tf`, `variables.tf`, `main.tf`, `outputs.tf` and
`terraform.tfvars.example`.

## Naming

Every resource is named `metaldesk-<env>-<resource>`, where `<env>` is `dev`, `staging` or `prod`: for example
`metaldesk-dev-api` for a Cloud Run service. Roots expose the prefix as `local.name_prefix` and modules take it as an
input; nothing builds a name by hand. Things to know about GCP when naming:

- **Bucket names are global** across all of GCP, so a bucket needs the prefix *and* something unlikely to collide. If
  a name is taken, change it everywhere it appears.
- Cloud Run service names are unique per project and region, and several resources cannot be renamed without being
  replaced. Choose a name once.
- One GCP project per environment (`project_id` is a variable), so an environment's blast radius is its project.

## Versions

| What | Constraint | Where it is stated |
|---|---|---|
| Terraform | `~> 1.16` | `envs/*/versions.tf` **and** `terraform_version` in [`.github/workflows/infra.yml`](../.github/workflows/infra.yml) |
| `hashicorp/google` | `~> 8.5` | `envs/*/versions.tf` only |
| tflint | pinned | `tflint_version` in `infra.yml` |

Each root commits its `.terraform.lock.hcl` (provider checksums for linux and macOS, amd64 and arm64). To upgrade a
provider, change the constraint, then in each root run `terraform init -upgrade` followed by
`terraform providers lock -platform=linux_amd64 -platform=linux_arm64 -platform=darwin_amd64 -platform=darwin_arm64`.

`gradle/libs.versions.toml` does not govern Terraform. The Terraform version is stated in two places, so **an upgrade
edits both**; the workflow pins an exact release inside the `~>` range.

## The state buckets

State lives in a GCS bucket, and that bucket cannot be created by the Terraform whose state it holds. Each
environment has **its own** bucket, `metaldesk-<env>-tfstate`, with object versioning on, public access prevented, and
old versions pruned after 90 days while the newest 10 are always kept (state history is the way back from a bad
apply). The bucket is created once, by a script and not by Terraform, because a throwaway Terraform root would keep
its own state on someone's laptop, which is the same problem one level down.

Run this once per environment, with your own credentials and an existing project:

```bash
gcloud auth login
gcloud auth application-default login            # what terraform itself will use
infra/terraform/bootstrap/create-state-bucket.sh dev     <dev-project-id>
infra/terraform/bootstrap/create-state-bucket.sh staging <staging-project-id>
infra/terraform/bootstrap/create-state-bucket.sh prod    <prod-project-id>
```

The script is idempotent. The bucket name must match `envs/<env>/backend.tf`.

> **Status: not yet run.** No project exists for these environments yet, so no bucket exists. Until `T-078` creates
> them, `terraform init` *without* `-backend=false` fails with a backend error; that is expected, and it is why CI
> uses `-backend=false`.

## IP plan

One VPC per environment. Each environment owns a `/16`, **chosen so the three never overlap**, so peering any two of
them later stays possible. Only the first two subnets are carved out so far; the rest of the `/16` is reserved.

| Environment | Reserved | `run` subnet (Cloud Run Direct VPC egress) | `psc` subnet (Atlas endpoints) | Wired in |
|---|---|---|---|---|
| `dev` | `10.10.0.0/16` | `10.10.0.0/24` | `10.10.1.0/26` | `envs/dev` ([T-071](../tasks/T-071-tf-networking.md)) |
| `staging` | `10.20.0.0/16` | `10.20.0.0/24` | `10.20.1.0/26` | `T-078` |
| `prod` | `10.30.0.0/16` | `10.30.0.0/24` | `10.30.1.0/26` | `T-078` |

- **`run`, a `/24`:** Direct VPC egress takes one address per *running* instance, and Cloud Run needs at least a `/26`.
  A `/24` (251 usable) leaves room for every service to scale out; widen it before the instance count approaches that.
- **`psc`, a `/26`:** one address per Atlas service attachment. Atlas can issue several (the count depends on its
  current scheme, which this repository has not confirmed); a `/26` (59 usable) leaves room for dozens.
- **Egress mechanism:** Direct VPC egress, not a Serverless VPC Access connector. It has **no idle cost**, where a
  connector bills around the clock. The comparison and what would make us switch are in
  [`modules/network/README.md`](terraform/modules/network/README.md).
- **Egress is default-deny** for Cloud Run instances: only the Atlas endpoints and Google APIs (HTTPS) are allowed.
- **Manual prerequisite:** the Atlas service attachments belong to Atlas and have no default; a real `plan` needs
  them. See the module README. `dev` fails its plan with a pointer to it until they are supplied.

## Service postures and what they cost

The three JVM services are three calls of [`modules/cloud-run-service`](terraform/modules/cloud-run-service/README.md)
in `envs/dev/services.tf`. Each runs as **its own service account with no project-level role**; access is granted on
the one resource that needs it.

| Service | Exposure | Ingress / invoker | Min / max instances | CPU | Port | Why |
|---|---|---|---|---|---|---|
| `api` | `public` | all / `allUsers` (explicit opt-in) | **1** / 10 | on requests | 8082 | The storefront's backend. The checkout path must not pay a JVM cold start (Architecture §7) |
| `pricing-bridge` | `private` | internal / only `api`'s account | **1** / 1 | **always allocated** | 8083 | No public consumer. It holds a persistent market-data subscription (`T-039`); scaled to zero it holds none, and with CPU throttled between requests it cannot read its feed, so the storefront would show a frozen price |
| `admin` | `internal-load-balancer` | internal and load balancers / nobody public | 0 / 3 | on requests | 8081 | An internal tool: it may cold-start. Fronted by a load balancer and IAP in `T-076` |

> **Standing cost.** `api` with a minimum of 1 and `pricing-bridge` with a minimum of 1 **and CPU always allocated** are
> billed continuously, with or without traffic. `api`'s minimum is the single largest idle cost in the deployment and a
> deliberate latency decision, not an oversight; `pricing-bridge`'s always-on CPU is the more expensive of the two per
> instance. `admin` costs nothing at idle. None of this is spent until something is applied (`T-078`).

- **Images** are pinned by digest or a commit-SHA tag; a mutable tag fails validation. `T-073` builds them.
- **Secrets are references**, `{ secret, version }`, never values; the module has no variable that takes one. `T-075`
  declares them (`secrets.tf`) and grants each service's account access to its own: see [Secrets](#secrets).
- **Not covered:** `pricing-bridge` as a Cloud Run Job. A poll-based feed would want that shape; this module models the
  always-on service `T-039` built.

## Images: tags, retention and who may pull

Each service has **its own repository**, `metaldesk-<env>-<service>`, created by
[`modules/artifact-registry`](terraform/modules/artifact-registry/README.md) in `envs/dev/registry.tf`. The images are
built by [`deploy/images/Dockerfile`](../deploy/README.md).

- **Tag = the full commit SHA**, `<repository>/<service>:<sha>`. **Tags are immutable** (an Artifact Registry setting),
  so a tag can never be repointed and "commit SHA, never a mutable tag" is enforced by the registry, not by convention.
  The Cloud Run module accepts only a digest or such a tag.
- **Retention**, per repository: keep the **20** most recent versions whatever their age; delete anything older than
  **90 days** unless it is among those 20; delete **untagged** versions after **7 days**. The policy cannot know what is
  deployed, so raise the numbers for `prod` (`T-078`) rather than assume a deployed image is always recent.
- **Pull**: each Cloud Run service's own account gets `roles/artifactregistry.reader` on **its own repository only**.
  **Push is granted to nobody here**: CI pushes through Workload Identity Federation (`T-077`), never a key.
- Storing images is not free; the retention policy is part of the cost control, not an optimisation.

## The two single-page sites

`web` and `admin-web` are two calls of [`modules/static-site`](terraform/modules/static-site/README.md) in
`envs/dev/sites.tf`: a **private** bucket, read only by the load balancer's service agent, behind a global external HTTPS
load balancer with Cloud CDN. Details, decisions and what is unverified are in the module README; the short version:

- **Identity-Aware Proxy cannot front a backend bucket** (Google: "Backend buckets aren't supported with IAP", and IAP isn't
  compatible with Cloud CDN). The task's "admin-web behind IAP" therefore cannot be built, and the current Architecture §7 and
  ADR-0006 no longer ask for it (the staff SPA signs its users in itself). `admin-web` is instead **restricted to
  `admin_web_allowed_ip_ranges` at the edge** (Cloud Armor edge policy) and is not publicly reachable; `web` is public by an
  explicit `access = "public"`. If per-user IAP is wanted, the alternative is an IAP-protected backend service (a server), a
  decision for `T-076`.
- **Deep links** (`/orders/123`) are rewritten to `index.html` by the URL map. `/assets/*` and other file paths are served as they
  are, so a missing chunk is a real 404, not the HTML shell. A client-side route must not end in a file extension. **Not yet
  proven on a real load balancer.**
- **Two cache policies**, set as `Cache-Control` at upload: hashed `/assets/*` are `public, max-age=31536000, immutable`;
  `index.html` is `no-cache`; other files an hour. No cache invalidation is ever needed. The deploy uploads assets first and
  `index.html` last.
- **DNS prerequisite:** each site needs `web_domain` / `admin_web_domain` (required, no default) that you control, with an A
  record pointing at the `sites` output's `ip_address`; until it exists the managed certificate stays `PROVISIONING`.
- **Standing cost.** A global external load balancer bills **per forwarding rule, per hour, whether or not anyone visits**,
  plus egress. Each site has an `:443` rule and, by default, a `:80` redirect rule: **four rules for two sites** (two with
  `http_redirect = false`). `admin-web`'s Cloud Armor edge policy adds a small fixed charge. After `api`'s minimum instance this
  is the largest idle cost of the deployment. Check current prices before the first apply (`T-078`).
- **API base URL.** The apps default to `/api`, the same-origin path of a deployment behind one load balancer; this stack routes
  only the bundle, **not `/api`**, and the backends have no CORS configuration, so a cross-origin API URL would not work today.
  Serving `/api` from the same load balancer (a backend service for the API) is open work for `T-078`. Deployment sets
  `VITE_API_BASE_URL` per environment at build time and nothing is compiled in by default.

## Secrets

**Terraform never holds a secret value.** `modules/secret` creates the empty container and a per-service read grant; there
is no `google_secret_manager_secret_version` and no data argument of any kind anywhere in the tree (a value passed that way
lands in Terraform state, a plain JSON file in a bucket). A person adds each version with `gcloud`, out of band. Nothing in this
repository, in an image, or in a plan is a secret, and `CONTRIBUTING.md` still forbids any real credential or sandbox key.

### Inventory

ADR-0002 mocks every external dependency, so the real list is short, and **only secrets with a consumer today exist**. No
payment, mail or market-data secret is pre-created: every provider is WireMock- or fake-backed, and an empty secret named for
a provider invites someone to paste a real key.

| Secret id (`metaldesk-<env>-...`) | Read by | Why |
|---|---|---|
| `mongodb-uri-api` | `api` | The `api`'s MongoDB Atlas connection string (the one real external system) |
| `jwt-signing-key` | `api` | Signs customer tokens (`JWT_SIGNING_KEY`) |
| `mongodb-uri-admin` | `admin` | The `admin`'s MongoDB Atlas connection string |
| `admin-jwt-signing-key` | `admin` | Signs staff tokens (`ADMIN_JWT_SIGNING_KEY`); a different key from the customer one, so neither token is accepted by the other service |

`pricing-bridge` reads **no** secret (a fake feed, no database) and is granted none. Each secret has **exactly one**
reader, granted on that secret and nothing project-wide; no runtime identity holds any Secret Manager administrative role.
The database URI is two secrets, not one, so `api` and `admin` can use separate Atlas database users and be rotated or
revoked independently.

> The task specification predates ADR-0006 and says `admin` needs no secret. It does: staff sign in to `apps/admin`, which
> signs its own tokens and has its own database connection. The inventory follows the code, and ADR-0006 wins over the spec.

### Populating a value (once per environment, before the services are applied)

**Who:** a platform operator with permission to add secret versions on these secrets (for example
`roles/secretmanager.secretVersionAdder`), using their own `gcloud` login. No service account holds that permission, and
Terraform does not grant it. **Run these before applying `services.tf`**, which refers to version `1` of each; a Cloud Run
service whose secret version does not exist fails to deploy, which is the safe failure.

```bash
# The two signing keys. Generated on your machine and piped straight in: the value is never displayed or stored.
# Shape: Base64 text of at least 32 random bytes (the application rejects anything shorter or not Base64).
openssl rand -base64 32 | tr -d '\n' | gcloud secrets versions add metaldesk-<env>-jwt-signing-key       --project=<project-id> --data-file=-
openssl rand -base64 32 | tr -d '\n' | gcloud secrets versions add metaldesk-<env>-admin-jwt-signing-key --project=<project-id> --data-file=-

# The two database connection strings: the PRIVATE connection string Atlas shows for this environment's private endpoint, for
# the database user made for THAT service. Shape only: an Atlas URI of the form <scheme>://<user>:<password>@<host>/<database>?<options>.
# `read -s` keeps it out of the terminal and the shell history.
read -rs ATLAS_URI
printf %s "$ATLAS_URI" | gcloud secrets versions add metaldesk-<env>-mongodb-uri-api --project=<project-id> --data-file=-
unset ATLAS_URI
read -rs ATLAS_URI
printf %s "$ATLAS_URI" | gcloud secrets versions add metaldesk-<env>-mongodb-uri-admin --project=<project-id> --data-file=-
unset ATLAS_URI

# Check that version 1 exists (this lists versions; it does not print a value).
gcloud secrets versions list metaldesk-<env>-jwt-signing-key --project=<project-id>
```

Never `gcloud secrets versions access` in a shared terminal or a CI log. The Atlas cluster and its private endpoint are not
created by this repository (`T-071`); the connection strings exist only once they are.

### Pinned versions, never `latest`

A service refers to a secret by **version number** (`version = "1"` in `services.tf`), in every environment; the Cloud Run
module rejects a moving alias. That is stricter than the specification, which allowed `latest` outside `prod`. The trade-off:

- With `latest`, a new version takes effect whenever an instance next starts, so during a rollout old and new instances
  disagree. Survivable for a connection string, **actively broken for a signing key**: a token signed by one instance would be
  rejected by another, intermittently.
- Pinned, a rotation is a deliberate deploy, at the price of one edit and one apply.

### Rotation (a procedure, not automation)

1. **Add** the new version (the same `gcloud secrets versions add` command): it becomes version *N+1*; the old one still works.
2. **Point the service at it**: change `version = "N"` to *N+1* in `services.tf` and apply. Cloud Run starts new revisions on
   the new version.
3. **Verify**: the revision is healthy (`/actuator/health`) and the behaviour works (a sign-in, a read).
4. **Disable the old version**: `gcloud secrets versions disable N --secret=<id> --project=<project-id>`. Destroy it later,
   once nothing can need it.

For a **signing key**, rotation signs everyone out (one HMAC key, no key set): customer tokens last 15 minutes and staff
tokens 30, so plan for it. For a **database URI**, create the new Atlas user or password first, switch, then remove the old
credential in Atlas.

### The JWT signing key must come from Secret Manager

Both applications have a startup fallback: with an empty `JWT_SIGNING_KEY` / `ADMIN_JWT_SIGNING_KEY` they **generate a random
key and log a warning** ("Development only", `JwtConfiguration`). In a deployed environment that is unacceptable: each instance
would sign with a different key, and a token valid on one instance would fail on another, intermittently. The deployed
configuration **must** supply the secrets above, and the fallback **must be disabled outside local development**. Terraform
cannot do that by itself (a Cloud Run service that references a missing secret version fails to start, which prevents the
common mistake, but an *empty* version would still trigger the fallback). Failing startup on a blank key outside a local
profile is an **application change, raised here and not made** (`T-032`).

## Running it

```bash
cd infra/terraform/envs/dev
cp terraform.tfvars.example terraform.tfvars     # gitignored; set project_id
terraform init
terraform plan                                   # free; reads state, changes nothing
```

Authentication is **your own `gcloud` credentials** locally and Workload Identity Federation in CI (`T-077`). There is
no service-account JSON key anywhere in this repository, by design (Architecture §7), and `terraform.tfvars`,
`*.tfstate*` and `.terraform/` are gitignored. `terraform.tfvars.example` is committed and holds nothing secret.

## Checks

The same commands CI runs ([`infra.yml`](../.github/workflows/infra.yml)), with no cloud access:

```bash
cd infra/terraform
terraform fmt -check -recursive
for d in envs/dev envs/staging envs/prod; do (cd "$d" && terraform init -backend=false -input=false && terraform validate); done
tflint --recursive
```
