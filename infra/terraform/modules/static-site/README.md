# Module: `static-site`

One single-page app on Cloud Storage behind an external HTTPS load balancer with Cloud CDN. `web` and `admin-web` are two
calls of this module in [`envs/dev/sites.tf`](../../envs/dev/sites.tf). Written and validated by
[`T-074`](../../../tasks/T-074-tf-web-cdn.md); **never applied** by that task. Nothing below was exercised against a real
load balancer; what was and was not verified is in the last section.

## What it creates

| File | Resources |
|---|---|
| `main.tf` | A **private** bucket; the URL map (single-page routing); the managed certificate, global address, HTTPS proxy and `:443` forwarding rule; optionally a `:80` listener that only redirects |
| `cdn.tf` | The backend bucket with Cloud CDN and its cache policy; for a restricted site, a Cloud Armor **edge** security policy |

## Contract

**Inputs:** `name_prefix`, `name`, `region`, `project_number`, `domain` (**required, no default**), `access`
(`restricted` by default, or `public`), `allowed_ip_ranges`, `http_redirect` (true), `force_destroy` (false).
**Outputs:** `domain`, `ip_address`, `url`, `bucket_name`, `access`.

## The bucket is never public

`public_access_prevention = "enforced"`, uniform bucket-level access, and **one** grant: `roles/storage.objectViewer` to the
load balancer's own service agent, `service-<project number>@https-lb.iam.gserviceaccount.com`. That identity exists only once
a backend bucket exists in the project, so the grant depends on it. No public principal of any kind, no write role:
uploading the bundle is CI's job through Workload Identity Federation (`T-077`). So the only path to the content is the load
balancer, and a restriction placed on the load balancer cannot be bypassed by fetching the bucket directly. That bypass is
the most serious failure available in this module, and it is closed by construction.

## Identity-Aware Proxy cannot front a backend bucket

The task asked for `admin-web` "behind the same IAP as `admin`", with a warning to verify that combination. **It is not
supported.** Google's documentation on IAP for an external Application Load Balancer lists the supported backends (instance
groups, zonal/serverless/internet/hybrid/Private Service Connect NEGs) and states: *"Backend buckets aren't supported with
IAP."* It also states that **IAP isn't compatible with Cloud CDN**. So a static site served from a bucket through the CDN
cannot have IAP on it, and nothing in this module pretends otherwise.

What the current documents say about `admin-web` instead: [Architecture §7](../../../docs/architecture.md#7-reference-deployment-gcp)
and [ADR-0005](../../../docs/adr/0005-consolidated-react-frontend.md)/[ADR-0006](../../../docs/adr/0006-staff-login-and-mongock-migrations.md)
describe it as a static SPA that "signs its users in itself" and "holds no secret"; the API refuses everything without a
token. They no longer describe it as behind IAP, which is why the spec's wording (written before ADR-0006) is out of date.
Documents win over the spec, so the module does the following instead, and the choice is recorded in the ledger:

- **`access = "restricted"` (the default)** puts a Cloud Armor *edge security policy* on the backend bucket: only
  `allowed_ip_ranges` get through (office, VPN), everything else gets `403` before it reaches the cache. `admin-web` uses it,
  so it is **not** left publicly reachable. This is network restriction "as far as the deployment allows" (Architecture §7),
  not user authentication.
- The real gate remains `apps/admin`'s own login (ADR-0006).
- **If per-user IAP in front of `admin-web` is wanted**, the alternative is an IAP-protected **backend service**, for example
  a small Cloud Run service that serves the bundle, behind a serverless NEG. That is an application server (Architecture §7
  says there is none for the frontend) and gives up the CDN, so it is a decision for `T-076`, not something to slide in here.

## Single-page routing (deep links)

A bucket-backed load balancer returns `404` for `/orders/123`, because no such object exists. The mechanism is a **URL map
rewrite**, three rules in priority order:

1. `prefix /assets/` is served **as it is**. A missing hashed chunk is a real `404`, never the HTML shell, so there is no blank
   page with a MIME-type console error.
2. Any other path that **names a file** (`regex ^/.*\.[A-Za-z0-9]+$`: `favicon.svg`, `robots.txt`, a source map) is served as
   it is, for the same reason. Consequence: **a client-side route must not end in a file extension.**
3. Everything else (`/`, `/catalog`, `/orders/123`) is rewritten, whole path, to `/index.html` (`path_template_rewrite`).

**Chosen over the two alternatives, for reasons from Google's documentation:**

| Alternative | Why not |
|---|---|
| The bucket's `not_found_page` / `main_page_suffix` | Documented as applying *"only [to] requests that come to Cloud Storage through a CNAME or A redirect"*, not to a backend bucket behind a load balancer: they would be inert. It also returns the page with status `404`. The module sets no `website` block |
| A custom error response policy mapping `404` to `/index.html` | Documented as not working *"if your global external Application Load Balancer only has backend buckets"* (it needs a backend service attached), and it is incompatible with requests carrying an `Authorization` header |

## Cache policy: two, not one

Vite emits content-hashed files under `/assets/` and a non-hashed `index.html`. The policy is the objects' `Cache-Control`,
set at upload by [`frontend-deploy.yml`](../../../.github/workflows/frontend-deploy.yml), and the CDN honours it
(`USE_ORIGIN_HEADERS`):

| Objects | `Cache-Control` | Why |
|---|---|---|
| `/assets/*` | `public, max-age=31536000, immutable` | A hashed file never changes |
| other files | `public, max-age=3600` | Not hashed, but rarely changed |
| `index.html` | `no-cache` | A deploy takes effect at once |

With that split **no cache invalidation is ever needed**. A `404` is cached for only 10 seconds, so a deploy that adds a file is
not shadowed by a remembered miss. The deploy uploads assets first and `index.html` last, so nobody can load a page naming
assets that are not there yet.

## Certificate and DNS

`domain` is **required, with no default**: a Google-managed certificate is issued only for a domain you control. An apply needs
one, and the certificate stays `PROVISIONING` until the domain's **A record points at the `ip_address` output**. Until then
https does not answer, which is the safe failure. There is **no fallback to an unencrypted listener**; the optional `:80`
listener only redirects to https. Other hosts (the bare IP) are redirected to the domain, never served.

## What was and was not verified

- **Verified:** `fmt`, `validate`, `tflint`; offline plans (46 resources for `envs/dev`): both buckets private with
  `public_access_prevention = enforced`, the single `objectViewer` binding for the load balancer's agent, the three route rules,
  `USE_ORIGIN_HEADERS`, the edge policy only on the restricted site, `access = "restricted"` with no ranges refused, a malformed
  domain refused.
- **Not verified, because nothing was applied:** that the load balancer accepts the rewrite rule with a backend bucket as its
  target (`weighted_backend_services` pointing at a bucket), that `path_template_rewrite` with a fixed path behaves as the
  deep-link rule needs, and that a missing `/assets/x.js` returns `404` rather than HTML. **`T-078` must check these on a real
  load balancer:** `curl -I https://<domain>/orders/123` is `200` with the HTML; `/assets/missing.js` is `404`; a direct
  `storage.googleapis.com` request for an object is `403`; and, for the restricted site, a request from an address outside the
  allow list is `403`. **If the rewrite is rejected**, the fallback is a backend service for the single-page routing (which also
  makes custom error responses available), at the cost of an application server.
- Cloud Armor edge policies and the managed certificate bill; see `infra/README.md`.
