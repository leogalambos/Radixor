#!/usr/bin/env bash
set -euo pipefail

bundle="${1:?Usage: publish-central-bundle.sh BUNDLE COORDINATES}"
coordinates="${2:?Usage: publish-central-bundle.sh BUNDLE COORDINATES}"

[[ "${GITHUB_REF_TYPE:-}" == "tag" ]] || { echo "Maven Central publication requires a release tag." >&2; exit 2; }
[[ -f "${bundle}" ]] || { echo "Central bundle does not exist: ${bundle}" >&2; exit 2; }
[[ -n "${CENTRAL_BEARER_TOKEN:-}" ]] || { echo "CENTRAL_BEARER_TOKEN is required." >&2; exit 2; }

header_file="$(mktemp)"
trap 'rm -f "${header_file}"' EXIT
printf 'Authorization: Bearer %s\n' "${CENTRAL_BEARER_TOKEN}" > "${header_file}"
curl --fail --silent --show-error --request POST --header @"${header_file}" \
    --form "bundle=@${bundle}" --form "name=${coordinates}" \
    "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC"
