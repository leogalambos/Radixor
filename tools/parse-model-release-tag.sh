#!/usr/bin/env bash
###############################################################################
# Copyright (C) 2026, Leo Galambos
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice,
#    this list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors
#    may be used to endorse or promote products derived from this software
#    without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.
###############################################################################

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
