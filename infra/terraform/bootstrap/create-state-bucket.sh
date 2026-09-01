#!/usr/bin/env bash
# Creates the versioned GCS bucket that holds one environment's Terraform state. Run it once per environment, with
# your own gcloud credentials, BEFORE the first `terraform init`. It is idempotent: running it again changes nothing
# that is already right.
#
#   infra/terraform/bootstrap/create-state-bucket.sh <env> <project-id> [location]
#
# Why a script and not Terraform: a bucket cannot hold the state of the Terraform that creates it, and a throwaway
# Terraform root would keep its own state on someone's laptop, which is the same problem one level down.
#
# The bucket name must equal the one in envs/<env>/backend.tf. Bucket names are global across all of GCP; if
# metaldesk-<env>-tfstate is taken, change it here and in backend.tf together.
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: $0 <dev|staging|prod> <project-id> [location]" >&2
  exit 2
fi

env="$1"
project="$2"
location="${3:-europe-west3}"
case "$env" in
  dev | staging | prod) ;;
  *)
    echo "environment must be dev, staging or prod, not '$env'" >&2
    exit 2
    ;;
esac

bucket="metaldesk-${env}-tfstate"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if gcloud storage buckets describe "gs://${bucket}" --project="${project}" >/dev/null 2>&1; then
  echo "gs://${bucket} already exists; making sure its settings are right"
else
  gcloud storage buckets create "gs://${bucket}" \
    --project="${project}" \
    --location="${location}" \
    --uniform-bucket-level-access \
    --public-access-prevention
fi

gcloud storage buckets update "gs://${bucket}" \
  --project="${project}" \
  --versioning \
  --lifecycle-file="${here}/lifecycle.json"

echo "gs://${bucket} is ready: versioning on, public access prevented, old versions pruned after 90 days (keeping 10)"
