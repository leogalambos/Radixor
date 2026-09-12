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

if [[ "$#" -ne 2 ]]; then
    printf 'Usage: %s <speed-csv> <model-topology>\n' "$0" >&2
    exit 2
fi

speed_csv="$1"
model_topology="$2"
if [[ ! -s "${speed_csv}" ]]; then
    printf 'Missing or empty speed report: %s\n' "${speed_csv}" >&2
    exit 1
fi
if [[ ! -s "${model_topology}" ]]; then
    printf 'Missing or empty model topology: %s\n' "${model_topology}" >&2
    exit 1
fi

validation_root="${TMPDIR:-${PWD}/build/tmp}"
mkdir -p "${validation_root}"
validation_dir="$(mktemp -d "${validation_root%/}/radixor-speed-coverage.XXXXXX")"
trap 'rm -rf "${validation_dir}"' EXIT
expected_models="${validation_dir}/expected-user-facing-models.txt"
measured_models="${validation_dir}/measured-user-facing-models.txt"

awk -F= '!/^#/ && NF == 2 { print $1 }' "${model_topology}" | sort > "${expected_models}"
awk -F, '
    NR == 1 {
        for (field_number = 1; field_number <= NF; field_number++) {
            name = $field_number
            sub(/\r$/, "", name)
            gsub(/^"|"$/, "", name)
            if (name == "Param: modelId") column = field_number
        }
        next
    }
    column > 0 {
        value = $column
        sub(/\r$/, "", value)
        gsub(/^"|"$/, "", value)
        if (value != "") print value
    }
' "${speed_csv}" | sort -u > "${measured_models}"

expected_count="$(wc -l < "${expected_models}")"
if [[ "${expected_count}" -eq 0 ]]; then
    printf 'The model topology does not contain any active model IDs.\n' >&2
    exit 1
fi
if [[ "$(wc -l < "${measured_models}")" -ne "${expected_count}" ]] \
        || ! cmp -s "${expected_models}" "${measured_models}"; then
    printf 'The speed report does not cover exactly the %s active model IDs.\n' "${expected_count}" >&2
    diff -u "${expected_models}" "${measured_models}" >&2 || true
    exit 1
fi

printf 'Validated exact speed coverage for %s active model IDs.\n' "${expected_count}"
