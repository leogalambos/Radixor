#!/usr/bin/env bash
set -euo pipefail

tag="${1:-}"

version_pattern='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'

if [[ "${tag}" =~ ^python@(${version_pattern})$ ]]; then
    printf 'PYTHON_DISTRIBUTION=radixor\nPYTHON_VERSION=%s\n' "${BASH_REMATCH[1]}"
elif [[ "${tag}" =~ ^python-models-standard@(${version_pattern})$ ]]; then
    printf 'PYTHON_DISTRIBUTION=radixor-models-standard\nPYTHON_VERSION=%s\n' "${BASH_REMATCH[1]}"
else
    echo "Invalid Python release tag: ${tag}" >&2
    exit 2
fi
