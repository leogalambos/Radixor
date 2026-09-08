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

report_date="${1:-$(date +%F)}"
release_identity="${2:-}"
if [[ -z "${release_identity}" || "${release_identity}" == *[[:space:]]* || "${release_identity}" == *","* ]]; then
    printf 'Usage: %s <report-date> <source-identity>\n' "$0" >&2
    exit 2
fi
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${project_root}"

classpath_file="build/reports/jmh/jmh-runtime-classpath.txt"
if [[ ! -s "${classpath_file}" ]]; then
    printf 'Missing %s; run ./gradlew writeJmhRuntimeClasspath --no-daemon first.\n' "${classpath_file}" >&2
    exit 1
fi
IFS= read -r jmh_classpath < "${classpath_file}"

tmp_dir="${project_root}/build/tmp/jmh"
report_dir="${project_root}/build/reports/jmh"
mkdir -p "${tmp_dir}" "${report_dir}"

for governor_file in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
    governor="$(<"${governor_file}")"
    if [[ "${governor}" != "performance" ]]; then
        printf 'CPU governor is %s in %s; expected performance.\n' "${governor}" "${governor_file}" >&2
        exit 1
    fi
done

comparison_include='^(org\.egothor\.stemmer\.benchmark\.(EnglishStemmerComparisonBenchmark\.|MultiLanguageStemmerComparisonBenchmark\.|SnowballLanguageStemmerComparisonBenchmark\.).*|org\.egothor\.stemmer\.benchmark\.HunspellStemmerComparisonBenchmark\.luceneHunspellStemFilter)$'
coverage_include='^org\.egothor\.stemmer\.benchmark\.EnglishRadixorDictionaryCoverageBenchmark\.changedTokenStemmingSpeed$'
selection_file="${report_dir}/published-speed-benchmarks-${report_date}.txt"

java -Djava.io.tmpdir="${tmp_dir}" -cp "${jmh_classpath}" org.openjdk.jmh.Main \
    "${comparison_include}" -l > "${selection_file}"
if grep -Eq 'PolishPolimorf|BenchmarkQuality|GermanGoldstandard' "${selection_file}"; then
    printf 'The selected speed benchmark list contains an excluded benchmark.\n' >&2
    exit 1
fi
if ! grep -q 'MultiLanguageStemmerComparisonBenchmark.hebrewRadixor' "${selection_file}"; then
    printf 'The selected speed benchmark list omits Hebrew Radixor.\n' >&2
    exit 1
fi

environment_file="${report_dir}/performance-environment-${report_date}.txt"
source_patch="${report_dir}/measured-source-${report_date}.patch"
untracked_checksums="${report_dir}/measured-untracked-${report_date}.sha256"
git diff --binary > "${source_patch}"
git ls-files --others --exclude-standard -z -- src tools docs build.gradle mkdocs.yml \
    | sort -z \
    | xargs -0 --no-run-if-empty sha256sum > "${untracked_checksums}"
jmh_jar="${jmh_classpath%%:*}"
{
    printf 'Benchmark start: '
    date --iso-8601=seconds
    printf 'Project root: %s\n' "${project_root}"
    printf 'Core base commit: '
    git rev-parse HEAD
    printf 'Release identity: %s\n' "${release_identity}"
    printf 'JMH runtime classpath SHA-256: '
    sha256sum "${classpath_file}" | cut -d' ' -f1
    printf 'JMH executable JAR SHA-256: '
    sha256sum "${jmh_jar}" | cut -d' ' -f1
    printf 'Measured source patch SHA-256: '
    sha256sum "${source_patch}" | cut -d' ' -f1
    printf 'Untracked source checksum manifest SHA-256: '
    sha256sum "${untracked_checksums}" | cut -d' ' -f1
    printf 'Corpus report SHA-256: '
    sha256sum build/reports/jmh/benchmark-corpora.csv | cut -d' ' -f1
    printf 'Stemming-quality report SHA-256: '
    sha256sum build/reports/stemming-quality/stemming-quality.csv | cut -d' ' -f1
    printf '\nJava:\n'
    java -version 2>&1
    printf '\nKernel:\n'
    uname -a
    printf '\nCPU:\n'
    lscpu
    printf '\nCPU governors:\n'
    for governor_file in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
        printf '%s=' "${governor_file}"
        cat "${governor_file}"
    done
    printf 'Energy performance preference: '
    cat /sys/devices/system/cpu/cpu0/cpufreq/energy_performance_preference
    printf '\nMemory:\n'
    free -h
    printf '\nInitial load:\n'
    cat /proc/loadavg
    if command -v sensors >/dev/null 2>&1; then
        printf '\nInitial sensors:\n'
        sensors
    fi
    printf '\nSelected comparison benchmarks:\n'
    cat "${selection_file}"
    printf '\nWorking tree:\n'
    git status --short
} > "${environment_file}"

sleep 30
{
    printf '\nPre-run load after 30 s idle interval:\n'
    cat /proc/loadavg
    if command -v sensors >/dev/null 2>&1; then
        printf '\nPre-run sensors after 30 s idle interval:\n'
        sensors
    fi
} >> "${environment_file}"

common_arguments=(
    -f 3
    -wi 5
    -i 7
    -w 1s
    -r 1s
    -t 1
    -bm avgt
    -tu ns
    -jvmArgsAppend "-Djava.io.tmpdir=${tmp_dir} -Xms6g -Xmx6g"
    -rf csv
)

java -Djava.io.tmpdir="${tmp_dir}" -Xms512m -Xmx1g \
    -cp "${jmh_classpath}" org.openjdk.jmh.Main \
    "${comparison_include}" "${common_arguments[@]}" \
    -rff "${report_dir}/stemmer-speed-${report_date}.csv" \
    -o "${report_dir}/stemmer-speed-${report_date}.txt"

sleep 15

java -Djava.io.tmpdir="${tmp_dir}" -Xms512m -Xmx1g \
    -cp "${jmh_classpath}" org.openjdk.jmh.Main \
    "${coverage_include}" "${common_arguments[@]}" \
    -rff "${report_dir}/english-coverage-speed-${report_date}.csv" \
    -o "${report_dir}/english-coverage-speed-${report_date}.txt"

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
