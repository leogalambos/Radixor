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

if [[ "$#" -lt 1 || "$#" -gt 2 || -z "$1" || "$1" == *[[:space:]]* \
        || ( "$#" -eq 2 && "$2" != "--retrospective-pin-after-main" ) ]]; then
    printf 'Usage: %s <report-date> [--retrospective-pin-after-main]\n' "$0" >&2
    exit 2
fi

report_date="$1"
operation="${2:-resume}"
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${project_root}"

report_dir="${project_root}/build/reports/jmh"
classpath_file="${report_dir}/jmh-runtime-classpath.txt"
classpath_content_manifest="${report_dir}/jmh-classpath-content-${report_date}.sha256"
main_csv="${report_dir}/stemmer-speed-${report_date}.csv"
coverage_csv="${report_dir}/english-coverage-speed-${report_date}.csv"
coverage_text="${report_dir}/english-coverage-speed-${report_date}.txt"
environment_file="${report_dir}/performance-environment-${report_date}.txt"
tmp_dir="${project_root}/build/tmp/jmh"

if [[ ! -s "${classpath_file}" || ! -s "${environment_file}" ]]; then
    printf 'The existing classpath or performance-environment file is missing.\n' >&2
    exit 1
fi
if [[ -e "${coverage_csv}" || -e "${coverage_text}" ]]; then
    printf 'Coverage output already exists; refusing to overwrite it.\n' >&2
    exit 1
fi

mkdir -p "${project_root}/build/tmp"
working_directory="$(mktemp -d "${project_root}/build/tmp/speed-recovery.XXXXXX")"
trap 'rm -rf -- "${working_directory}"' EXIT
candidate_manifest="${working_directory}/classpath-content.sha256"

bash tools/validate-speed-model-coverage.sh \
    "${main_csv}" "${project_root}/models/model-projects.properties"
bash tools/write-classpath-content-manifest.sh \
    "${classpath_file}" "${candidate_manifest}"
IFS= read -r jmh_classpath < "${classpath_file}"
jmh_jar="${jmh_classpath%%:*}"
expected_classpath_checksum="$(sed -n 's/^JMH runtime classpath SHA-256: //p' "${environment_file}")"
expected_jar_checksum="$(sed -n 's/^JMH executable JAR SHA-256: //p' "${environment_file}")"
actual_classpath_checksum_output="$(sha256sum < "${classpath_file}")"
actual_classpath_checksum="${actual_classpath_checksum_output%% *}"
actual_jar_checksum_output="$(sha256sum < "${jmh_jar}")"
actual_jar_checksum="${actual_jar_checksum_output%% *}"
if [[ -z "${expected_classpath_checksum}" \
        || "${actual_classpath_checksum}" != "${expected_classpath_checksum}" \
        || -z "${expected_jar_checksum}" \
        || "${actual_jar_checksum}" != "${expected_jar_checksum}" ]]; then
    printf 'The JMH classpath or executable differs from the recorded main run.\n' >&2
    exit 1
fi

if [[ "${operation}" == "--retrospective-pin-after-main" ]]; then
    if grep -Eq '^(JMH runtime classpath content manifest SHA-256|Main speed report SHA-256): ' \
            "${environment_file}"; then
        printf 'Recovery checksums are already recorded; refusing to replace them.\n' >&2
        exit 1
    fi
    if [[ -e "${classpath_content_manifest}" ]] \
            && ! cmp -s "${candidate_manifest}" "${classpath_content_manifest}"; then
        printf 'An existing classpath content manifest has different content.\n' >&2
        exit 1
    fi

    manifest_checksum_output="$(sha256sum < "${candidate_manifest}")"
    manifest_checksum="${manifest_checksum_output%% *}"
    main_checksum_output="$(sha256sum < "${main_csv}")"
    main_checksum="${main_checksum_output%% *}"
    candidate_environment="${working_directory}/performance-environment.txt"
    cp -p -- "${environment_file}" "${candidate_environment}"
    {
        printf '\nRetrospective coverage-recovery pin: '
        date --iso-8601=seconds
        printf '%s\n' \
            'Retrospective pin timing: captured after the completed main speed measurement and before coverage resume.' \
            'Retrospective pin scope: current complete runtime classpath content and the immutable completed main CSV.' \
            'Retrospective pin limitation: this is not contemporaneous main-run provenance.'
        printf 'JMH runtime classpath content manifest: %s\n' \
            "${classpath_content_manifest}"
        printf 'JMH runtime classpath content manifest SHA-256: %s\n' \
            "${manifest_checksum}"
        printf 'Main speed report SHA-256: %s\n' "${main_checksum}"
        printf 'Retrospective coverage-recovery pin end.\n'
    } >> "${candidate_environment}"

    if [[ ! -e "${classpath_content_manifest}" ]]; then
        mv -- "${candidate_manifest}" "${classpath_content_manifest}"
    fi
    mv -- "${candidate_environment}" "${environment_file}"
    printf 'Recorded retrospective recovery pin for %s.\n' "${report_date}"
    exit 0
fi

if [[ ! -s "${classpath_content_manifest}" ]]; then
    printf 'The recorded classpath content manifest is missing.\n' >&2
    exit 1
fi

recorded_manifest_path="$(sed -n 's/^JMH runtime classpath content manifest: //p' "${environment_file}")"
expected_manifest_checksum="$(sed -n 's/^JMH runtime classpath content manifest SHA-256: //p' "${environment_file}")"
expected_main_checksum="$(sed -n 's/^Main speed report SHA-256: //p' "${environment_file}")"
actual_manifest_checksum_output="$(sha256sum < "${classpath_content_manifest}")"
actual_manifest_checksum="${actual_manifest_checksum_output%% *}"
actual_main_checksum_output="$(sha256sum < "${main_csv}")"
actual_main_checksum="${actual_main_checksum_output%% *}"
if [[ -z "${expected_classpath_checksum}" \
        || "${actual_classpath_checksum}" != "${expected_classpath_checksum}" \
        || -z "${expected_jar_checksum}" \
        || "${actual_jar_checksum}" != "${expected_jar_checksum}" \
        || "${recorded_manifest_path}" != "${classpath_content_manifest}" \
        || -z "${expected_manifest_checksum}" \
        || "${actual_manifest_checksum}" != "${expected_manifest_checksum}" ]] \
        || ! cmp -s "${candidate_manifest}" "${classpath_content_manifest}"; then
    printf 'The complete JMH runtime classpath differs from the recorded main run.\n' >&2
    exit 1
fi
if [[ -z "${expected_main_checksum}" \
        || "${actual_main_checksum}" != "${expected_main_checksum}" ]]; then
    printf 'The main speed report differs from the recorded validated report.\n' >&2
    exit 1
fi
coverage_include='^org\.egothor\.stemmer\.benchmark\.EnglishRadixorDictionaryCoverageBenchmark\.changedTokenStemmingSpeed$'
common_arguments=(
    -f 3
    -wi 3
    -i 5
    -w 1s
    -r 1s
    -t 1
    -bm avgt
    -tu ns
    -jvmArgsAppend "-Djava.io.tmpdir=${tmp_dir} -Xms6g -Xmx6g"
    -rf csv
)

{
    printf '\nCoverage-only resume start: '
    date --iso-8601=seconds
    printf 'Existing main speed report: %s\n' "${main_csv}"
    printf 'Validated main speed report SHA-256: %s\n' "${actual_main_checksum}"
    printf 'Validated complete classpath manifest SHA-256: %s\n' \
        "${actual_manifest_checksum}"
    printf 'Idle delay skipped because the failed post-run validation already left the machine idle.\n'
} >> "${environment_file}"

java -Djava.io.tmpdir="${tmp_dir}" -Xms512m -Xmx1g \
    -cp "${jmh_classpath}" org.openjdk.jmh.Main \
    "${coverage_include}" "${common_arguments[@]}" \
    -rff "${coverage_csv}" \
    -o "${coverage_text}"

{
    printf '\nFinal load:\n'
    cat /proc/loadavg
    if command -v sensors >/dev/null 2>&1; then
        printf '\nFinal sensors:\n'
        sensors
    fi
    printf '\nBenchmark end: '
    date --iso-8601=seconds
} >> "${environment_file}"
