#!/usr/bin/env bash
set -euo pipefail

report_date="${1:-$(date +%F)}"
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

accuracy_include='^org\.egothor\.stemmer\.benchmark\.(EnglishHunspellStemmerComparisonBenchmarkQuality|EnglishStemmerComparisonBenchmarkQuality|HunspellStemmerComparisonBenchmarkQuality|StemmerComparisonBenchmarkQuality)\..*$'
coverage_include='^org\.egothor\.stemmer\.benchmark\.EnglishRadixorDictionaryCoverageBenchmark\.exactRootAgreement$'
accuracy_csv="${report_dir}/stemmer-accuracy-${report_date}.csv"

common_arguments=(
    -f 0
    -wi 0
    -i 1
    -r 1ms
    -t 1
    -bm avgt
    -tu ns
    -rf csv
)

java -Djava.io.tmpdir="${tmp_dir}" -Xms6g -Xmx6g \
    -cp "${jmh_classpath}" org.openjdk.jmh.Main \
    "${accuracy_include}" "${common_arguments[@]}" \
    -rff "${accuracy_csv}" \
    -o "${report_dir}/stemmer-accuracy-${report_date}.txt"

for candidate in SNOWBALL_CZECH_DIRECT SNOWBALL_PERSIAN_DIRECT SNOWBALL_POLISH_DIRECT; do
    if ! awk -F, -v candidate="${candidate}" '
        NR > 1 {
            value = $8
            gsub(/^"|"$/, "", value)
            if (value == candidate) {
                found = 1
            }
        }
        END { exit found ? 0 : 1 }
    ' "${accuracy_csv}"; then
        printf 'Exact-root report omits %s.\n' "${candidate}" >&2
        exit 1
    fi
    for counter in correctMatches evaluatedTokens changedCorrectMatches changedEvaluatedTokens \
            rootPreservedMatches rootEvaluatedTokens; do
        if ! awk -F, -v candidate="${candidate}" -v suffix="exactRootAgreement:${counter}" '
            NR > 1 {
                benchmark = $1
                value = $8
                gsub(/^"|"$/, "", benchmark)
                gsub(/^"|"$/, "", value)
                if (value == candidate && benchmark ~ suffix "$") {
                    found = 1
                }
            }
            END { exit found ? 0 : 1 }
        ' "${accuracy_csv}"; then
            printf 'Exact-root report omits %s for %s.\n' "${counter}" "${candidate}" >&2
            exit 1
        fi
    done
done

java -Djava.io.tmpdir="${tmp_dir}" -Xms6g -Xmx6g \
    -cp "${jmh_classpath}" org.openjdk.jmh.Main \
    "${coverage_include}" "${common_arguments[@]}" \
    -rff "${report_dir}/english-coverage-accuracy-${report_date}.csv" \
    -o "${report_dir}/english-coverage-accuracy-${report_date}.txt"
