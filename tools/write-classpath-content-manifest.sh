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
    printf 'Usage: %s <classpath-file> <output-manifest>\n' "$0" >&2
    exit 2
fi

classpath_file="$1"
output_manifest="$2"
if [[ ! -s "${classpath_file}" ]]; then
    printf 'Missing or empty runtime classpath: %s\n' "${classpath_file}" >&2
    exit 1
fi

mapfile -t classpath_lines < "${classpath_file}"
if [[ "${#classpath_lines[@]}" -ne 1 || -z "${classpath_lines[0]}" \
        || "${classpath_lines[0]}" == :* || "${classpath_lines[0]}" == *: \
        || "${classpath_lines[0]}" == *::* ]]; then
    printf 'The runtime classpath must be one line with no empty entries.\n' >&2
    exit 1
fi

output_directory="$(dirname -- "${output_manifest}")"
if [[ ! -d "${output_directory}" ]]; then
    printf 'Missing manifest output directory: %s\n' "${output_directory}" >&2
    exit 1
fi
working_directory="$(mktemp -d "${output_directory%/}/.radixor-classpath-content.XXXXXX")"
trap 'rm -rf -- "${working_directory}"' EXIT
temporary_manifest="${working_directory}/manifest"
printf 'radixor-classpath-content-manifest-v1\n' > "${temporary_manifest}"

IFS=: read -r -a classpath_entries <<< "${classpath_lines[0]}"
entry_number=0
for classpath_entry in "${classpath_entries[@]}"; do
    entry_number=$((entry_number + 1))
    entry_type=''
    entry_digest=''
    if [[ -f "${classpath_entry}" ]]; then
        entry_type='file'
        digest_output="$(sha256sum < "${classpath_entry}")"
        entry_digest="${digest_output%% *}"
    elif [[ -d "${classpath_entry}" ]]; then
        entry_type='directory-tree-v1'
        entry_file_list="${working_directory}/entry-${entry_number}.files"
        LC_ALL=C find -L "${classpath_entry}" -type f -printf '%P\0' \
            | LC_ALL=C sort -z > "${entry_file_list}"
        entry_digest="$({
            while IFS= read -r -d '' relative_path; do
                printf '%s\0' "${relative_path}"
                file_digest_output="$(sha256sum < "${classpath_entry%/}/${relative_path}")"
                printf '%s\0' "${file_digest_output%% *}"
            done < "${entry_file_list}"
        } | sha256sum | cut -d' ' -f1)"
    elif [[ ! -e "${classpath_entry}" && ! -L "${classpath_entry}" ]]; then
        entry_type='missing'
        entry_digest='-'
    else
        printf 'Classpath entry is not a regular file, directory, or absent path: %s\n' \
            "${classpath_entry}" >&2
        exit 1
    fi

    printf '%06d\t%s\t%s\t' "${entry_number}" "${entry_type}" \
        "${entry_digest}" >> "${temporary_manifest}"
    printf '%q\n' "${classpath_entry}" >> "${temporary_manifest}"
done

mv -- "${temporary_manifest}" "${output_manifest}"
