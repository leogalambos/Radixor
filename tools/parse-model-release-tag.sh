#!/usr/bin/env bash
set -euo pipefail

tag="${1:-}"
repository_root="${2:-.}"

if [[ "${tag}" =~ ^model/([a-z]{2}(-[a-z]{2})?-[a-z0-9]+(-[a-z0-9]+)*)@([0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?)$ ]]; then
    model_id="${BASH_REMATCH[1]}"
    model_version="${BASH_REMATCH[4]}"
    module="${repository_root}/models/${model_id}"
    [[ -d "${module}" ]] || { echo "Unknown model module: models/${model_id}" >&2; exit 2; }
    [[ -f "${module}/model-version.txt" ]] || { echo "Missing model version: models/${model_id}/model-version.txt" >&2; exit 2; }
    recorded_version="$(tr -d '[:space:]' < "${module}/model-version.txt")"
    [[ "${recorded_version}" == "${model_version}" ]] || {
        echo "Tag version ${model_version} does not match models/${model_id}/model-version.txt: ${recorded_version}" >&2
        exit 2
    }
    grep -Eq "^[[:space:]]*modelId[[:space:]]*=[[:space:]]*'${model_id}'" "${module}/build.gradle" || {
        echo "Descriptor model ID does not match module ${model_id}." >&2
        exit 2
    }
    printf 'MODEL_ID=%s\nMODEL_VERSION=%s\nGRADLE_PROJECT=:models:%s\n' "${model_id}" "${model_version}" "${model_id}"
elif [[ "${tag}" =~ ^release@([0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?)$ ]]; then
    printf 'CORE_VERSION=%s\n' "${BASH_REMATCH[1]}"
elif [[ "${tag}" =~ ^models-catalog@([0-9]{4}\.[0-9]+)$ ]]; then
    catalog_version="$(tr -d '[:space:]' < "${repository_root}/models/catalog-version.txt")"
    [[ "${catalog_version}" == "${BASH_REMATCH[1]}" ]] || {
        echo "Tag version ${BASH_REMATCH[1]} does not match models/catalog-version.txt: ${catalog_version}" >&2
        exit 2
    }
    printf 'CATALOG_VERSION=%s\n' "${BASH_REMATCH[1]}"
else
    echo "Invalid release tag: ${tag}" >&2
    exit 2
fi
