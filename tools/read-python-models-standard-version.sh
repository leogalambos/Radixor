#!/usr/bin/env bash

set -euo pipefail

version_file="${1:-python/models-standard-version.txt}"
expected_version="${2:-}"

if [[ ! -f "${version_file}" ]]; then
  echo "Standard-model version file does not exist: ${version_file}" >&2
  exit 1
fi

mapfile -t version_lines < "${version_file}"
if [[ "${#version_lines[@]}" -ne 1 ]] || [[ "$(wc -l < "${version_file}")" -ne 1 ]]; then
  echo "Standard-model version file must contain exactly one newline-terminated line." >&2
  exit 1
fi

version="${version_lines[0]}"
if [[ ! "${version}" =~ ^2\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Standard-model version must be a canonical stable 2.x.y version: ${version}" >&2
  exit 1
fi

if [[ -n "${expected_version}" ]] && [[ "${version}" != "${expected_version}" ]]; then
  echo "Standard-model release tag version ${expected_version} does not match ${version_file}: ${version}" >&2
  exit 1
fi

printf '%s\n' "${version}"
