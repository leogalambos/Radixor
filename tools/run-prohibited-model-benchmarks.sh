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

phase="${1:-}"
report_date="${2:-$(date +%F)}"
release_identity="${3:-}"
if [[ $# -lt 1 || $# -gt 3 ]] \
        || [[ ! "${phase}" =~ ^(prepare|corpus|quality|generalization|speed|snowball|all)$ ]] \
        || [[ -z "${release_identity}" || "${release_identity}" == *[[:space:]]* ]]; then
    printf 'Usage: %s <prepare|corpus|quality|generalization|speed|snowball|all> <date> <source-identity>\n' "$0" >&2
    exit 2
fi

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${project_root}"
state_dir="build/prohibited-model-benchmark"
report_dir="build/reports/prohibited-models/${report_date}"
manifest="${state_dir}/models.tsv"
classpath_file="${state_dir}/runtime-classpath.txt"
lock_file="${state_dir}/exclusive.lock"
mkdir -p "${state_dir}" "${report_dir}"
exec 9>"${lock_file}"
if ! flock -n 9; then
    printf 'Another prohibited-model benchmark lifecycle owns %s.\n' "${lock_file}" >&2
    exit 1
fi

cleanup() {
    local status=$?
    trap - EXIT HUP INT TERM
    if ! python3 tools/prohibited-model-lifecycle.py recover; then
        printf 'Failed to restore the prohibited-model lifecycle; manual recovery is required.\n' >&2
        status=1
    fi
    exit "${status}"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

python3 tools/prohibited-model-lifecycle.py recover
python3 tools/prohibited-model-lifecycle.py stage
python3 tools/prohibited-model-lifecycle.py write-benchmark-manifest "${manifest}"
if [[ -f "${report_dir}/models.tsv" ]]; then
    cmp --silent "${manifest}" "${report_dir}/models.tsv" \
        || { printf 'Existing prohibited benchmark model manifest differs.\n' >&2; exit 1; }
else
    cp "${manifest}" "${report_dir}/models.tsv"
fi
./gradlew --no-daemon -PradixorInternalProhibitedBenchmark=true \
    writeProhibitedBenchmarkRuntimeClasspath
IFS= read -r benchmark_classpath < "${classpath_file}"

if [[ "${phase}" == snowball ]]; then
    comparator_catalog="${report_dir}/prohibited-snowball-cases-${report_date}.tsv"
    java -cp "${benchmark_classpath}" \
        org.egothor.stemmer.benchmark.ProhibitedSnowballLanguageCatalogApplication \
        "${manifest}" "${comparator_catalog}"
    source_manifest="${report_dir}/prohibited-snowball-source-inputs.sha256"
    sha256sum models/model-quarantine.properties models/prohibited-model-sources.sha256 \
        tools/prohibited-model-lifecycle.py tools/run-prohibited-model-benchmarks.sh \
        src/jmh/java/org/egothor/stemmer/benchmark/ProhibitedSnowballLanguageCase.java \
        src/jmh/java/org/egothor/stemmer/benchmark/ProhibitedSnowballAccuracyApplication.java \
        src/jmh/java/org/egothor/stemmer/benchmark/ProhibitedModelStemmerBenchmark.java \
        src/test/java/org/egothor/stemmer/benchmark/quality/ProhibitedSnowballQualityApplication.java \
        "${report_dir}/models.tsv" "${comparator_catalog}" > "${source_manifest}"
    runtime_manifest="${report_dir}/prohibited-snowball-runtime-classpath-content.sha256"
else
    source_manifest="${report_dir}/prohibited-source-inputs.sha256"
    {
        sha256sum models/model-quarantine.properties models/prohibited-model-sources.sha256 \
            tools/prohibited-model-lifecycle.py tools/run-prohibited-model-benchmarks.sh
        sha256sum "${report_dir}/models.tsv"
    } > "${source_manifest}"
    runtime_manifest="${report_dir}/runtime-classpath-content.sha256"
fi
bash tools/write-classpath-content-manifest.sh "${classpath_file}" "${runtime_manifest}"

run_corpus() {
    local output="${report_dir}/prohibited-benchmark-corpora-${report_date}.csv"
    java -Xms512m -Xmx6g -cp "${benchmark_classpath}" \
        org.egothor.stemmer.benchmark.ProhibitedBenchmarkCorpusReportApplication \
        "${manifest}" "${output}"
    python3 tools/validate-prohibited-benchmark-reports.py --manifest "${manifest}" --corpus "${output}"
}

run_quality() {
    local output="${report_dir}/prohibited-stemming-quality-${report_date}.csv"
    java -Xms512m -Xmx6g -cp "${benchmark_classpath}" \
        org.egothor.stemmer.benchmark.quality.ProhibitedStemmingQualityApplication \
        "${manifest}" "${output}"
    python3 tools/validate-prohibited-benchmark-reports.py --manifest "${manifest}" --quality "${output}"
}

run_generalization() {
    local output="${report_dir}/prohibited-dictionary-generalization-${report_date}.csv"
    local fragments="${state_dir}/generalization-${report_date}"
    mkdir -p "${fragments}"
    local first=true
    while IFS=$'\t' read -r model_id language display_name model_version model_sha dictionary; do
        if [[ "${model_id}" == model_id ]]; then
            continue
        fi
        local fragment="${fragments}/${model_id}.csv"
        java -Xms512m -Xmx8g -cp "${benchmark_classpath}" \
            org.egothor.stemmer.benchmark.generalization.DictionaryGeneralizationApplication \
            "${fragment}" "${release_identity}" "$(git rev-parse HEAD)" dirty \
            src/test/java/org/egothor/stemmer/benchmark/generalization/DictionaryGeneralizationApplication.java \
            --guarded-model "${model_id}" "${language}" "${model_version}" "${model_sha}" "${dictionary}"
        if [[ "${first}" == true ]]; then
            cp "${fragment}" "${output}"
            first=false
        else
            tail -n +2 "${fragment}" >> "${output}"
        fi
    done < "${manifest}"
    python3 tools/validate-prohibited-benchmark-reports.py \
        --manifest "${manifest}" --generalization "${output}"
}

run_speed() {
    local output="${report_dir}/prohibited-stemmer-speed-${report_date}.csv"
    local model_ids
    model_ids="$(tail -n +2 "${manifest}" | cut -f1 | paste -sd, -)"
    java -Djava.io.tmpdir="${state_dir}" -Xms512m -Xmx1g -cp "${benchmark_classpath}" \
        org.openjdk.jmh.Main \
        '^org\.egothor\.stemmer\.benchmark\.ProhibitedModelStemmerBenchmark\.radixor$' \
        -p "modelId=${model_ids}" -f 3 -wi 3 -i 5 -w 1s -r 1s -t 1 -bm avgt -tu ns \
        -jvmArgsAppend "-Dradixor.prohibited.modelRoot=${project_root}/models -Dradixor.prohibited.manifest=${project_root}/${manifest} -Xms6g -Xmx6g" \
        -rf csv -rff "${output}" -o "${report_dir}/prohibited-stemmer-speed-${report_date}.txt"
    python3 tools/validate-prohibited-benchmark-reports.py --manifest "${manifest}" --speed "${output}"
}

run_snowball() {
    local accuracy_output="${report_dir}/prohibited-snowball-accuracy-${report_date}.csv"
    local quality_output="${report_dir}/prohibited-snowball-quality-${report_date}.csv"
    local speed_output="${report_dir}/prohibited-snowball-speed-${report_date}.csv"
    local comparator_ids
    comparator_ids="$(tail -n +2 "${comparator_catalog}" | cut -f1 | paste -sd, -)"
    [[ -n "${comparator_ids}" ]] || { printf 'Prohibited Snowball comparator catalog is empty.\n' >&2; exit 1; }
    java -Xms512m -Xmx6g -cp "${benchmark_classpath}" \
        org.egothor.stemmer.benchmark.ProhibitedSnowballAccuracyApplication \
        "${manifest}" "${accuracy_output}"
    java -Xms512m -Xmx6g -cp "${benchmark_classpath}" \
        org.egothor.stemmer.benchmark.quality.ProhibitedSnowballQualityApplication \
        "${manifest}" "${quality_output}"
    java -Djava.io.tmpdir="${state_dir}" -Xms512m -Xmx1g -cp "${benchmark_classpath}" \
        org.openjdk.jmh.Main \
        '^org\.egothor\.stemmer\.benchmark\.ProhibitedModelStemmerBenchmark\.snowballDirect$' \
        -p "modelId=${comparator_ids}" -f 3 -wi 3 -i 5 -w 1s -r 1s -t 1 -bm avgt -tu ns \
        -jvmArgsAppend "-Dradixor.prohibited.modelRoot=${project_root}/models -Dradixor.prohibited.manifest=${project_root}/${manifest} -Xms6g -Xmx6g" \
        -rf csv -rff "${speed_output}" -o "${report_dir}/prohibited-snowball-speed-${report_date}.txt"
    python3 tools/validate-prohibited-benchmark-reports.py \
        --manifest "${manifest}" --comparator-catalog "${comparator_catalog}" \
        --snowball-accuracy "${accuracy_output}" --snowball-quality "${quality_output}" \
        --snowball-speed "${speed_output}"
}

case "${phase}" in
    prepare) ;;
    corpus) run_corpus ;;
    quality) run_quality ;;
    generalization) run_generalization ;;
    speed) run_speed ;;
    snowball) run_snowball ;;
    all) run_corpus; run_quality; run_generalization; run_speed ;;
esac

if [[ "${phase}" == snowball ]]; then
    provenance="${report_dir}/prohibited-snowball-provenance.txt"
    benchmark_class='prohibited documentation-only Snowball comparisons'
else
    provenance="${report_dir}/provenance.txt"
    benchmark_class='prohibited documentation-only models'
fi
{
    printf 'Benchmark class: %s\n' "${benchmark_class}"
    printf 'Benchmark date: %s\n' "${report_date}"
    printf 'Source identity: %s\n' "${release_identity}"
    printf 'Base commit: %s\n' "$(git rev-parse HEAD)"
    printf 'Quarantine manifest SHA-256: %s\n' "$(sha256sum models/model-quarantine.properties | cut -d' ' -f1)"
    printf 'Source hash manifest SHA-256: %s\n' "$(sha256sum models/prohibited-model-sources.sha256 | cut -d' ' -f1)"
    printf 'Runtime classpath manifest SHA-256: %s\n' \
        "$(sha256sum "${runtime_manifest}" | cut -d' ' -f1)"
    printf 'Protocol: 3 warmup iterations, 5 measurement iterations, 3 forks, 1 thread, 1 s iterations\n'
} > "${provenance}"

find "${report_dir}" -maxdepth 1 -type f ! -name 'snapshot.sha256' -print0 \
    | sort -z | xargs -0 sha256sum > "${report_dir}/snapshot.sha256"
