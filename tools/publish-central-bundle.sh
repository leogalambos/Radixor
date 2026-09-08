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
