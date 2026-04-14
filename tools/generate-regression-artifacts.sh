#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

SOURCE_DIR="${PROJECT_DIR}/src/test/resources/regression/sources"
GOLDEN_DIR="${PROJECT_DIR}/src/test/resources/regression/golden"
BUILD_DIR="${PROJECT_DIR}/build/tmp/regression-artifacts"

MAIN_CLASS="org.egothor.stemmer.RegressionArtifactGenerator"

usage() {
    cat <<'EOF'
Generate deterministic compiled trie regression artifacts and SHA-256 sidecar files.

Usage:
  generate-regression-artifacts.sh [--clean] [--case <id>]...

Options:
  --clean       Remove previously generated temporary files before execution.
  --case <id>   Generate only the selected case. May be repeated.
  --help        Show this help.

Known case identifiers:
  01-mini-ranked-store-original
  02-mini-unordered-store-original
  03-branching-ranked-no-store-original

Notes:
  - This script expects a helper Java class:
      org.egothor.stemmer.RegressionArtifactGenerator
  - The helper should compile the stemmer source into a .gz artifact using the
    project's real binary writer implementation.
  - The script writes:
      src/test/resources/regression/golden/*.gz
      src/test/resources/regression/golden/*.gz.sha256
EOF
}

log() {
    printf '[INFO] %s\n' "$*"
}

fail() {
    printf '[ERROR] %s\n' "$*" >&2
    exit 1
}

require_file() {
    local path="$1"
    [[ -f "${path}" ]] || fail "Required file not found: ${path}"
}

compute_sha256() {
    local file_path="$1"
    local file_name
    file_name="$(basename "${file_path}")"

    if command -v sha256sum >/dev/null 2>&1; then
        local digest
        digest="$(sha256sum "${file_path}" | awk '{print $1}')"
        printf '%s  %s\n' "${digest}" "${file_name}"
        return 0
    fi

    if command -v shasum >/dev/null 2>&1; then
        local digest
        digest="$(shasum -a 256 "${file_path}" | awk '{print $1}')"
        printf '%s  %s\n' "${digest}" "${file_name}"
        return 0
    fi

    if command -v openssl >/dev/null 2>&1; then
        local digest
        digest="$(openssl dgst -sha256 "${file_path}" | awk '{print $2}')"
        printf '%s  %s\n' "${digest}" "${file_name}"
        return 0
    fi

    fail "No SHA-256 tool available. Install sha256sum, shasum, or openssl."
}

declare -a REQUESTED_CASES=()
CLEAN="false"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean)
            CLEAN="true"
            shift
            ;;
        --case)
            [[ $# -ge 2 ]] || fail "Missing value for --case."
            REQUESTED_CASES+=("$2")
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            fail "Unknown argument: $1"
            ;;
    esac
done

mkdir -p "${GOLDEN_DIR}"
mkdir -p "${BUILD_DIR}"

if [[ "${CLEAN}" == "true" ]]; then
    log "Cleaning temporary directory: ${BUILD_DIR}"
    rm -rf "${BUILD_DIR}"
    mkdir -p "${BUILD_DIR}"
fi

declare -a CASE_IDS=()
declare -A CASE_SOURCE=()
declare -A CASE_STORE_ORIGINAL=()
declare -A CASE_REDUCTION_MODE=()
declare -A CASE_ARTIFACT=()

CASE_IDS+=("01-mini-ranked-store-original")
CASE_SOURCE["01-mini-ranked-store-original"]="${SOURCE_DIR}/mini-en.stemmer"
CASE_STORE_ORIGINAL["01-mini-ranked-store-original"]="true"
CASE_REDUCTION_MODE["01-mini-ranked-store-original"]="MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS"
CASE_ARTIFACT["01-mini-ranked-store-original"]="${GOLDEN_DIR}/mini-en-ranked-storeorig.gz"

CASE_IDS+=("02-mini-unordered-store-original")
CASE_SOURCE["02-mini-unordered-store-original"]="${SOURCE_DIR}/mini-en.stemmer"
CASE_STORE_ORIGINAL["02-mini-unordered-store-original"]="true"
CASE_REDUCTION_MODE["02-mini-unordered-store-original"]="MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS"
CASE_ARTIFACT["02-mini-unordered-store-original"]="${GOLDEN_DIR}/mini-en-unordered-storeorig.gz"

CASE_IDS+=("03-branching-ranked-no-store-original")
CASE_SOURCE["03-branching-ranked-no-store-original"]="${SOURCE_DIR}/branching-en.stemmer"
CASE_STORE_ORIGINAL["03-branching-ranked-no-store-original"]="false"
CASE_REDUCTION_MODE["03-branching-ranked-no-store-original"]="MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS"
CASE_ARTIFACT["03-branching-ranked-no-store-original"]="${GOLDEN_DIR}/branching-en-ranked-no-storeorig.gz"

is_requested_case() {
    local case_id="$1"

    if [[ ${#REQUESTED_CASES[@]} -eq 0 ]]; then
        return 0
    fi

    local requested
    for requested in "${REQUESTED_CASES[@]}"; do
        if [[ "${requested}" == "${case_id}" ]]; then
            return 0
        fi
    done

    return 1
}

validate_requested_cases() {
    if [[ ${#REQUESTED_CASES[@]} -eq 0 ]]; then
        return 0
    fi

    local requested
    local known
    local found

    for requested in "${REQUESTED_CASES[@]}"; do
        found="false"
        for known in "${CASE_IDS[@]}"; do
            if [[ "${requested}" == "${known}" ]]; then
                found="true"
                break
            fi
        done
        [[ "${found}" == "true" ]] || fail "Unknown case identifier: ${requested}"
    done
}

run_generator() {
    local input_file="$1"
    local output_file="$2"
    local store_original="$3"
    local reduction_mode="$4"

    "${PROJECT_DIR}/gradlew" \
        --no-daemon \
        -q \
        testClasses \
        regressionArtifactGenerator \
        -PregressionInput="${input_file}" \
        -PregressionOutput="${output_file}" \
        -PregressionStoreOriginal="${store_original}" \
        -PregressionReductionMode="${reduction_mode}"
}

# Fallback path when the project does not expose a generic run task.
run_generator_with_javaexec_fallback() {
    local input_file="$1"
    local output_file="$2"
    local store_original="$3"
    local reduction_mode="$4"

    "${PROJECT_DIR}/gradlew" \
        --no-daemon \
        -q \
        testClasses \
        -PregressionGeneratorMainClass="${MAIN_CLASS}" \
        -PregressionGeneratorArgs="--input=${input_file} --output=${output_file} --store-original=${store_original} --reduction-mode=${reduction_mode}" \
        regressionArtifactGenerator
}

generate_case() {
    local case_id="$1"
    local source_file="${CASE_SOURCE[${case_id}]}"
    local artifact_file="${CASE_ARTIFACT[${case_id}]}"
    local sha_file="${artifact_file}.sha256"
    local store_original="${CASE_STORE_ORIGINAL[${case_id}]}"
    local reduction_mode="${CASE_REDUCTION_MODE[${case_id}]}"
    local temp_output="${BUILD_DIR}/$(basename "${artifact_file}")"

    require_file "${source_file}"

    log "Generating case: ${case_id}"
    log "  source: ${source_file}"
    log "  artifact: ${artifact_file}"
    log "  reduction mode: ${reduction_mode}"
    log "  store original: ${store_original}"

    rm -f "${temp_output}"

    if "${PROJECT_DIR}/gradlew" tasks --all 2>/dev/null | grep -q '^run '; then
        run_generator "${source_file}" "${temp_output}" "${store_original}" "${reduction_mode}"
    elif "${PROJECT_DIR}/gradlew" tasks --all 2>/dev/null | grep -q '^regressionArtifactGenerator '; then
        run_generator_with_javaexec_fallback "${source_file}" "${temp_output}" "${store_original}" "${reduction_mode}"
    else
        fail "No supported Gradle execution path found. Expected a 'run' or 'regressionArtifactGenerator' task."
    fi

    require_file "${temp_output}"

    mv "${temp_output}" "${artifact_file}"
    compute_sha256 "${artifact_file}" > "${sha_file}"

    log "  wrote artifact: ${artifact_file}"
    log "  wrote digest:   ${sha_file}"
}

validate_requested_cases

for case_id in "${CASE_IDS[@]}"; do
    if is_requested_case "${case_id}"; then
        generate_case "${case_id}"
    fi
done

log "Regression artifacts were generated successfully."
