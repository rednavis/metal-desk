#!/usr/bin/env bash
# The environment roots must be dev with different VALUES, and nothing more: no module, resource, variable or output may exist
# in one root and not the others, because that is the drift the thin-root design exists to prevent (and it is found during an
# incident). Run from anywhere; CI runs it (.github/workflows/infra.yml).
#
# Three checks:
#   1. every root declares exactly the same module, variable and output blocks as dev (and no resource of its own);
#   2. every file is byte-identical to dev's, EXCEPT the per-environment ones named below;
#   3. the values file, the backend and the tfvars example do differ (an environment that copied dev's backend would share its state).
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../terraform/envs" && pwd)"
# The only files allowed to differ between environments.
per_env=(values.tf backend.tf terraform.tfvars.example)

fail=0
blocks() { # list "kind name" for every top-level module/variable/output/resource/data block of the .tf files in a directory
  grep -hoE '^(module|variable|output|resource|data) +"[^"]+"( +"[^"]+")?' "$1"/*.tf | sed -E 's/ +/ /g' | sort
}

for env in staging prod; do
  if ! diff <(blocks "$root/dev") <(blocks "$root/$env") >/dev/null; then
    echo "DRIFT: $env declares different blocks than dev:" >&2
    diff <(blocks "$root/dev") <(blocks "$root/$env") >&2 || true
    fail=1
  fi
done

for env in dev staging prod; do
  if blocks "$root/$env" | grep -E '^(resource|data) ' >/dev/null; then
    echo "NOT THIN: $env declares a resource or data source itself; resources belong in a module:" >&2
    blocks "$root/$env" | grep -E '^(resource|data) ' >&2
    fail=1
  fi
done

is_per_env() { local f; for f in "${per_env[@]}"; do [ "$f" = "$1" ] && return 0; done; return 1; }

for env in staging prod; do
  for file in "$root/dev"/*.tf "$root/dev"/terraform.tfvars.example; do
    name="$(basename "$file")"
    is_per_env "$name" && continue
    if ! diff -q "$file" "$root/$env/$name" >/dev/null 2>&1; then
      echo "DRIFT: $env/$name differs from dev/$name; only ${per_env[*]} may differ:" >&2
      diff "$file" "$root/$env/$name" >&2 || true
      fail=1
    fi
  done
  for file in "$root/$env"/*.tf; do
    [ -e "$root/dev/$(basename "$file")" ] || { echo "DRIFT: $env/$(basename "$file") has no counterpart in dev" >&2; fail=1; }
  done
done

for f in values.tf backend.tf; do
  for env in staging prod; do
    if diff -q "$root/dev/$f" "$root/$env/$f" >/dev/null; then
      echo "COPIED: $env/$f is identical to dev's; it must be the environment's own." >&2
      fail=1
    fi
  done
done

[ "$fail" -eq 0 ] && echo "environment roots are in parity: same modules, variables and outputs; only values differ"
exit "$fail"
