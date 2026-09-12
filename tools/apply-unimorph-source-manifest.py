#!/usr/bin/env python3
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
"""Apply authoritative UniMorph source metadata and notices to model modules."""

from __future__ import annotations

import argparse
import csv
import json
import re
from pathlib import Path


LICENSE_TEXT = {
    "CC-BY-SA-3.0": (
        "Creative Commons Attribution-ShareAlike 3.0 Unported",
        "Creative Commons Attribution-ShareAlike 3.0",
    ),
    "CC-BY-SA-4.0": (
        "Creative Commons Attribution-ShareAlike 4.0 International",
        "Creative Commons Attribution-ShareAlike 4.0",
    ),
    "CC-BY-4.0": (
        "Creative Commons Attribution 4.0 International",
        "Creative Commons Attribution 4.0",
    ),
    "LGPLLR": (
        "Lesser General Public License For Linguistic Resources",
        "the Lesser General Public License For Linguistic Resources",
    ),
}
TRANSFORMATIONS = (
    "Strict UTF-8 validation, NFC normalization, lowercase conversion, structural unsafe-character "
    "rejection, grouping by lemma, exact deduplication, deterministic ordering, and reproducible GZip packaging"
)
LGPLLR_FILE_NAME = "LGPLLR.txt"
LGPLLR_TEXT_SOURCE = "https://raw.githubusercontent.com/spdx/license-list-data/main/text/LGPLLR.txt"
LGPLLR_TEXT_SHA256 = "e4e0f2f92769aad680aeca07f359521004dac4598d8b03def0e6fe507e871134"


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--models-root", type=Path, required=True)
    return parser.parse_args()


def groovy_quote(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "\\'")


def replace_assignment(text: str, name: str, value: str) -> str:
    pattern = re.compile(rf"^(\s*){re.escape(name)}\s*=\s*'[^']*(?:\\'[^']*)*'\s*$", re.MULTILINE)
    replacement = lambda match: f"{match.group(1)}{name} = '{groovy_quote(value)}'"
    updated, count = pattern.subn(replacement, text)
    if count != 1:
        raise ValueError(f"Expected one {name} assignment, found {count}")
    return updated


def update_build(path: Path, row: dict[str, str]) -> None:
    text = path.read_text(encoding="utf-8")
    expected_id = re.search(r"^\s*modelId\s*=\s*'([^']+)'\s*$", text, re.MULTILINE)
    if expected_id is None or expected_id.group(1) != row["model_id"]:
        raise ValueError(f"Build metadata model ID mismatch: {path}")
    if "manifestManaged = true" not in text:
        text, count = re.subn(
            r"^(\s*defaultModel\s*=\s*true\s*)$",
            r"\1\n    manifestManaged = true",
            text,
            count=1,
            flags=re.MULTILINE,
        )
        if count != 1:
            raise ValueError(f"Could not mark manifest-managed model: {path}")
    replacements = {
        "sourceVersion": row["revision"],
        "sourceRevision": row["revision"],
        "sourceRevisionStatus": "recorded",
        "sourceLicense": row["license_id"],
        "sourceLicenseUri": row["license_uri"],
        "sourceAttribution": row["attribution"],
        "sourceVerificationDate": "2026-09-11",
        "transformationsSummary": TRANSFORMATIONS,
    }
    for name, value in replacements.items():
        text = replace_assignment(text, name, value)
    if row["license_id"] == "LGPLLR" and "licenseFileName" not in text:
        text, count = re.subn(
            r"^(\s*sourceLicenseUri\s*=\s*'[^']+'\s*)$",
            rf"\1\n    licenseFileName = '{LGPLLR_FILE_NAME}'",
            text,
            count=1,
            flags=re.MULTILINE,
        )
        if count != 1:
            raise ValueError(f"Could not configure packaged LGPLLR text: {path}")
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(text, encoding="utf-8")
    temporary.replace(path)


def render_notice(row: dict[str, str]) -> str:
    full_name, distribution_name = LICENSE_TEXT[row["license_id"]]
    evidence = row["evidence_paths"] or "none"
    evidence_hashes = row["evidence_sha256"] or "none"
    packaged_license = ""
    if row["license_id"] == "LGPLLR":
        packaged_license = f"""Packaged canonical license text: META-INF/LICENSES/{LGPLLR_FILE_NAME}
Packaged license text source: {LGPLLR_TEXT_SOURCE}
Packaged license text SHA-256: {LGPLLR_TEXT_SHA256}
The model JAR and sources JAR contain the complete machine-readable legible
dictionary form used by Radixor, so recipients can modify the linguistic resource.
"""
    selected_data = "\n".join(
        f"- {source['path']} [{source['schema']}], SHA-256 {source['sha256']}"
        for source in json.loads(row["data_sources"])
    )
    source_audit = "\n".join(
        "- {path}: source={source_rows}; accepted={accepted_rows}; rejected={rejected_rows}; "
        "added-groups={added_groups}; added-distinct-surfaces={added_distinct_surfaces}; "
        "added-source-pairs={added_source_pairs}; added-output-forms={added_output_forms}; "
        "added-changed-pairs={added_changed_pairs}; intrinsic-ambiguous-surfaces="
        "{intrinsic_ambiguous_surfaces}; rejection-reasons={reasons}".format(
            **source,
            reasons=(
                ";".join(
                    f"{reason}={count}"
                    for reason, count in source["rejection_counts"].items()
                )
                or "none"
            ),
        )
        for source in json.loads(row["source_audit"])
    )
    exclusions = json.loads(row["excluded_source_audit"])
    excluded_data = (
        "\n".join(
            f"- {source['path']}: {source['reason']} — {source['detail']}"
            for source in exclusions
        )
        or "- none"
    )
    return f"""Radixor model-data notice

Radixor-derived model data

Copyright (C) 2026, Leo Galambos.

Copyright and, where applicable, database rights are claimed in the
Radixor-specific selection, verification, cleaning, normalization,
grouping, deduplication, filtering, reformatting, metadata preparation,
and packaging of this model, to the extent protected by applicable law.

The underlying morphological data remains attributed to UniMorph and
the upstream contributors identified in this notice.

This derived model data, including Radixor's protectable contributions,
is distributed under {distribution_name}.

Model ID: {row['model_id']}
Source project: UniMorph
Official repository: {row['repository']}
Selected upstream data:
{selected_data}
Source origin: {row['source_origin']}
Attribution: {row['attribution']}
License:
{full_name}

Canonical license URI: {row['license_uri']}
{packaged_license}Source revision: {row['revision']}
Revision status: recorded
Repository evidence paths: {evidence}
Repository evidence SHA-256: {evidence_hashes}
Import policy: {row['policy_version']}
Projection semantics: {row['projection_semantics']}
Conflict semantics: {row['conflict_semantics']}
Import rows: source={row['source_rows']}; accepted={row['accepted_rows']}; rejected={row['rejected_rows']}
Rejection reasons: {row['rejection_counts'] or 'none'}
Per-source import audit:
{source_audit}
Surface conflict audit: cross-source-assignments={row['cross_source_conflict_assignments']}; cross-source-surfaces={row['cross_source_conflicting_surfaces']}; intrinsic-ambiguous-surfaces={row['intrinsic_ambiguous_surfaces']}
Audited excluded upstream files:
{excluded_data}
Generated dictionary: groups={row['output_groups']}; forms={row['output_forms']}
Deterministic GZip SHA-256: {row['output_sha256']}

Radixor modifications: {TRANSFORMATIONS}.

Neither UniMorph nor any upstream contributor endorses Radixor.

Upstream information verified: 2026-09-11
"""


def main() -> None:
    arguments = parse_arguments()
    with arguments.manifest.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source))
    for row in rows:
        model_root = arguments.models_root / row["model_id"]
        update_build(model_root / "build.gradle", row)
        notice = model_root / "src/modelInput/NOTICE-model-data.txt"
        temporary = notice.with_suffix(notice.suffix + ".tmp")
        temporary.write_text(render_notice(row), encoding="utf-8")
        temporary.replace(notice)
    print(f"Applied pinned metadata and notices to {len(rows)} UniMorph model modules")


if __name__ == "__main__":
    main()
