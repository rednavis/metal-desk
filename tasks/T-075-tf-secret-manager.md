# T-075 — Terraform module: Secret Manager and secret injection

> **Picking this up?** Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) first, then claim the parent issue
> and work on a branch. **If anything below disagrees with
> [`docs/architecture.md` §7](../docs/architecture.md#7-reference-deployment-gcp) or
> [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules), that document wins.** Update this task's row in
> [the ledger](README.md) in the same pull request.

**Parent issue:** [#5 — Phase 5 — GCP infrastructure](https://github.com/rednavis/metal-desk/issues/5)

**This task:** [#53](https://github.com/rednavis/metal-desk/issues/53)

**Milestone:** M5 GCP infrastructure · **Estimate:** 3 h

**Preconditions** — `T-070` and `T-072` merged. `T-072`'s module already accepts secret **references** only.

> [!WARNING]
> **Write-and-validate only, and read §4 before writing a single line.** This is the one M5 task where a mistake puts a
> real secret into git or into Terraform state permanently.

**Goal** — Declare the secrets the deployed system needs, grant each service access to only its own, and wire them into
Cloud Run as environment variables at deploy time — **without any secret value entering this repository or Terraform
state**.

## 1. Why this task exists

Two documents make the same demand from different angles. Architecture §7:

> **Secrets** — Secret Manager. Injected as Cloud Run environment variables at deploy time — **never baked into an image
> or committed to source.**

And [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules): no real external credentials or sandbox keys, **ever**.

The hazard is specific and easy to trip. Terraform's `google_secret_manager_secret_version` takes the secret **data** as
an argument. If a value reaches that argument, it is stored in Terraform state — and state is a JSON file in a GCS
bucket, unencrypted at the value level, readable by anyone with bucket access. That is a worse outcome than a `.env`
file, because it is less obvious.

## 2. Documents to obey

| What | Pinned by |
|---|---|
| Secret Manager, injected as env vars at deploy time | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |
| Never baked into an image, never committed | [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp), [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules) |
| No real credentials or sandbox keys, ever; CI scans every PR | [`CONTRIBUTING.md`](../CONTRIBUTING.md#ground-rules), `T-064` |
| Each service has its own service account | `T-072` |
| Every external dependency is mocked, so few real secrets exist | [ADR-0002](../docs/adr/0002-mocked-external-dependencies.md) |
| JWT signing key is externally configured | `T-032` |
| Atlas connection string is needed by `services/api` | `T-030`, [Architecture §7](../docs/architecture.md#7-reference-deployment-gcp) |

## 3. Deliverables

| Path | What |
|---|---|
| `infra/terraform/modules/secret/main.tf` | A secret's container and its IAM — **no version data** |
| `.../secret/variables.tf`, `outputs.tf`, `README.md` | The contract, and the explicit no-value rule |
| `infra/terraform/envs/dev/secrets.tf` | The declared secrets and which service account reads each |
| `infra/README.md` (modify) | The full secret inventory, and the exact commands to populate a value out of band |
| `docs/architecture.md` (modify) | §7 gains a pointer to the inventory |

## 4. Specification

**The module creates the secret container and never a version with data.** Declare
`google_secret_manager_secret` plus IAM. Do **not** declare `google_secret_manager_secret_version` with a `secret_data`
argument anywhere. If a version resource is needed at all, it must be one whose data Terraform does not hold, and the
safer default is: Terraform creates the empty secret, a human or CI populates versions with `gcloud secrets versions
add`, and Terraform never learns the value. Write that rule at the top of the module README.

**Document the out-of-band population commands precisely**, because this is where an infrastructure repository becomes
unusable by anyone but its author. For each secret: the `gcloud` command, who runs it, and what a valid value looks like
in shape only — never an example that could be mistaken for a real value.

**The secret inventory, derived from what actually exists.** Because ADR-0002 mocks every external dependency, the real
list is short — which is a feature worth stating:

| Secret | Consumer | Why it exists |
|---|---|---|
| Atlas connection string | `services/api` | `T-030`; the only real external system in the deployment |
| JWT signing key | `services/api` | `T-032`; must not be a development default in a deployed environment |
| Payment provider credentials | — | **None.** Every provider is WireMock-backed (`T-018`, `T-019`) |
| Mail provider credentials | — | **None.** `libs/mail` uses the in-process fake (`T-020`) |
| Market-data feed credentials | — | **None.** `T-039` uses the fake feed |

Declare only the secrets that have a consumer today. Do not pre-create empty secrets for the mocked providers — an
empty secret named for a payment provider invites someone to fill it with a real key, which `CONTRIBUTING.md` forbids
outright.

**IAM is per secret, per service account, and read-only.** Grant `roles/secretmanager.secretAccessor` on the individual
secret to the one service account that needs it. Never grant at project level, and never grant `secretmanager.admin` to
a runtime identity. `services/pricing-bridge` and `apps/admin` need no secret in this build — if that stays true, grant
them nothing and say so.

**Wire references into `T-072`'s module.** It already takes a map of env-var name to secret version reference. Pass
`latest` or a pinned version — **prefer a pinned version for `prod`** so a rotation is a deliberate deploy rather than
an instant change under a running service, and note the trade-off.

**Rotation is a documented procedure, not automation.** State how to rotate: add a new version, update the reference or
redeploy, verify, then disable the old version. Note that with `latest`, a new version takes effect on the next
instance start, which means a partial rollout while old instances run — that is the trade-off behind pinning.

**The JWT signing key deserves a specific note.** `T-032` was allowed a development default generated at startup. In a
deployed environment that is unacceptable — instances would sign with different keys and tokens would fail randomly
across instances. Record that the deployed configuration **must** supply this secret, and that `T-032`'s startup
fallback has to be disabled outside local development.

**Nothing in this pull request may look like a credential.** `T-064`'s TruffleHog scan runs on every PR; a
realistic-looking example value will fail it, correctly.

## 5. Acceptance criteria

1. `terraform fmt -check -recursive`, `init -backend=false`, `validate` and the linter pass.
2. **No `secret_data` argument appears anywhere in the Terraform tree** — asserted by grep. This is the primary
   criterion.
3. No secret value, no realistic example credential and no `-----BEGIN` block anywhere in the repository; the secret
   scan passes.
4. Only secrets with an actual consumer are declared; no placeholder secret exists for a mocked provider.
5. Each secret grants `secretAccessor` to exactly one service account, on the individual secret, not at project level.
6. No runtime service account holds any Secret Manager admin or project-level secret role.
7. `services/pricing-bridge` and `apps/admin` are granted no secret access, or their need is documented.
8. `infra/README.md` lists every secret with the exact `gcloud` command to populate it and who runs it.
9. The rotation procedure is documented, including the `latest`-versus-pinned trade-off.
10. It is recorded that the deployed JWT configuration must come from Secret Manager and that `T-032`'s startup
    fallback must be disabled outside local development.
11. `T-072`'s invocations reference the secrets and pass no plaintext.
12. Nothing was applied; `git status` is clean.

## 6. Verification

```
cd <repo>
grep -rn 'secret_data' infra/                                  # expect nothing
grep -rniE '\-\-\-\-\-BEGIN|mongodb\+srv://[^$<]|AIza|ya29\.' infra/ docs/   # expect nothing
cd infra/terraform && terraform fmt -check -recursive
(cd envs/dev && terraform init -backend=false && terraform validate)
grep -rn 'roles/secretmanager' modules/ envs/dev | grep -v secretAccessor   # expect nothing
cd <repo> && git status --short   # expect clean
```

Expected: no `secret_data` anywhere; no credential-shaped strings; Terraform checks green; only the accessor role
granted; a clean tree.

## 7. Out of scope

Populating any real secret value. Creating the Atlas cluster or obtaining its connection string. Rotation automation.
Customer-managed encryption keys. IAP configuration (`T-076`). CI authentication (`T-077`) — that is Workload Identity
Federation, deliberately *not* a secret. Any `apply`.

## 8. Hazards

- **A `secret_data` argument puts the value in Terraform state**, which is a plain JSON file in a GCS bucket. It is
  worse than a committed `.env` because it looks like it was done properly. AC-2 is the single most important check in
  this task.
- A realistic example connection string in `infra/README.md` will trip the secret scan, and if it does not, it will be
  copied into a real configuration.
- Pre-creating secrets named for payment or mail providers invites someone to paste a real sandbox key, which
  `CONTRIBUTING.md` forbids without qualification.
- A project-level `secretAccessor` grant lets every service read every secret, undoing `T-072`'s per-service identity
  boundary.
- Using `latest` in production means a rotation takes effect mid-rollout, with old and new instances disagreeing —
  survivable for a connection string, actively broken for a signing key.
- Leaving `T-032`'s generate-at-startup fallback enabled in a deployed environment produces tokens that validate on one
  instance and fail on another, intermittently.
- Undocumented population commands make the environment reproducible only by whoever first created it.

## 9. On completion

Mark the T-075 row done in [`README.md`](README.md). Record the secret inventory, the `latest`-versus-pinned decision
per environment, and the `T-032` fallback requirement — `T-078` needs all three to stand up `staging` and `prod`.
