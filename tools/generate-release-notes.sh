#!/usr/bin/env bash
set -Eeuo pipefail

current_tag="${GITHUB_REF_NAME:-${1:-}}"
if [[ -z "${current_tag}" ]]; then
  echo "Current tag is not set. Provide it as GITHUB_REF_NAME or as the first argument." >&2
  exit 1
fi

release_prefix="release@"

if [[ "${current_tag}" != "${release_prefix}"* ]]; then
  echo "Current tag '${current_tag}' does not start with expected prefix '${release_prefix}'." >&2
  exit 1
fi

git fetch --tags --force >/dev/null 2>&1 || true

all_versions="$(git tag --list "${release_prefix}*" | sed "s/^${release_prefix}//" | sort -V)"

previous_tag=""
for version in ${all_versions}; do
  if [[ "${release_prefix}${version}" == "${current_tag}" ]]; then
    break
  fi
  previous_tag="${release_prefix}${version}"
done

if [[ -n "${previous_tag}" ]]; then
  range="${previous_tag}..${current_tag}"
else
  range="${current_tag}"
fi

echo "Generating release notes for range: ${range}" >&2

declare -a CATEGORY_ORDER=(
  "feat|Features"
  "fix|Bug Fixes"
  "perf|Performance"
  "refactor|Refactoring"
  "docs|Documentation"
  "test|Tests"
  "build|Build System"
  "ci|CI/CD"
  "style|Style"
  "chore|Maintenance"
  "revert|Reverts"
)

declare -A CATEGORY_TITLES
declare -A CATEGORY_ITEMS

for entry in "${CATEGORY_ORDER[@]}"; do
  key="${entry%%|*}"
  title="${entry##*|}"
  CATEGORY_TITLES["${key}"]="${title}"
  CATEGORY_ITEMS["${key}"]=""
done

supported_prefix_pattern='^(feat|fix|perf|refactor|docs|test|build|ci|style|chore|revert)(\([^)]+\))?!?:[[:space:]]*(.+)$'
separator=$'\x1f'

append_line() {
  local line="$1"
  local normalized_line
  local category
  local message

  normalized_line="$(printf '%s' "${line}" | tr -d '\r' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
  [[ -z "${normalized_line}" ]] && return 0

  if [[ "${normalized_line}" =~ ${supported_prefix_pattern} ]]; then
    category="${BASH_REMATCH[1]}"
    message="${BASH_REMATCH[3]}"

    [[ -z "${message}" ]] && return 0

    CATEGORY_ITEMS["${category}"]+="- ${message}"$'\n'
  fi
}

while IFS="${separator}" read -r commit_hash subject body; do
  [[ -z "${commit_hash}" ]] && continue

  if [[ "${subject}" =~ ^Merge[[:space:]] ]] || [[ "${subject}" == "Initial commit" ]]; then
    continue
  fi

  append_line "${subject}"

  while IFS= read -r body_line; do
    append_line "${body_line}"
  done <<< "${body}"
done < <(git log "${range}" --no-merges --pretty=format:"%H${separator}%s${separator}%b")

body_text="## What's New"

for entry in "${CATEGORY_ORDER[@]}"; do
  key="${entry%%|*}"
  title="${CATEGORY_TITLES[${key}]}"
  items="${CATEGORY_ITEMS[${key}]}"

  if [[ -n "${items}" ]]; then
    body_text+=$'\n\n'"### ${title}"$'\n'
    body_text+="$(printf '%s' "${items}" | sed '/^[[:space:]]*$/d')"
  fi
done

if [[ "${body_text}" == "## What's New" ]]; then
  body_text+=$'\n\n'"No categorized changes were found in commit subjects or bodies for this release range."
fi

printf '%s\n' "${body_text}"
