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
"""Build the authoritative offline-import manifest from a reviewed fetch inventory."""

from __future__ import annotations

import argparse
import csv
import importlib.util
import json
import re
import sys
import tempfile
from pathlib import Path


IMPORTER_PATH = Path(__file__).with_name("import-unimorph-models.py")
SPEC = importlib.util.spec_from_file_location("radixor_unimorph_importer", IMPORTER_PATH)
assert SPEC is not None and SPEC.loader is not None
IMPORTER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = IMPORTER
SPEC.loader.exec_module(IMPORTER)

SPECIAL_LICENSES = {
    "hsi-default": (
        "CC-BY-SA-4.0",
        "https://creativecommons.org/licenses/by-sa/4.0/",
    ),
    "ja-jp-default": (
        "CC-BY-4.0",
        "https://creativecommons.org/licenses/by/4.0/",
    ),
    "klr-default": ("LGPLLR", "https://spdx.org/licenses/LGPLLR.html"),
}
LICENSE_NEEDLES = {
    "CC-BY-SA-3.0": (
        "creativecommons.org/licenses/by-sa/3.0",
        "cc by-sa 3.0",
        "attribution-sharealike 3.0",
    ),
    "CC-BY-SA-4.0": (
        "creativecommons.org/licenses/by-sa/4.0",
        "cc by-sa 4.0",
        "attribution-sharealike 4.0",
    ),
    "CC-BY-4.0": (
        "creativecommons.org/licenses/by/4.0",
        "cc by 4.0",
        "attribution 4.0",
    ),
    "LGPLLR": ("lgpllr", "lesser general public license for linguistic resources"),
}
MANIFEST_FIELDS = (
    "model_id",
    "repository",
    "revision",
    "data_sources",
    "excluded_source_audit",
    "evidence_paths",
    "evidence_sha256",
    "license_id",
    "license_uri",
    "license_scope",
    "source_origin",
    "attribution",
    "projection_semantics",
    "conflict_semantics",
    "lowercase",
    "policy_version",
    "source_rows",
    "accepted_rows",
    "rejected_rows",
    "rejection_counts",
    "source_audit",
    "cross_source_conflict_assignments",
    "cross_source_conflicting_surfaces",
    "intrinsic_ambiguous_surfaces",
    "output_groups",
    "output_forms",
    "output_sha256",
)

PROJECTION_SEMANTICS = {
    "be-by-default": (
        "bel.args is the provided three-column UM4 argument projection; no generic "
        "deaccenting or synthesized transform is applied."
    ),
    "ca-es-default": (
        "cat.segmentations projects the first two columns of the declared "
        "lemma/form/features/segmentation schema."
    ),
}

CONFLICT_SEMANTICS = (
    "All valid lemma-form assignments are retained. Surface ambiguity is reported "
    "explicitly and is never resolved by dropping or overwriting an assignment."
)


def source_schema(model_id: str, source_key: str, path: str) -> tuple[str, str]:
    if model_id == "sdh-default" and source_key == "primary":
        return IMPORTER.SDH_SCHEMA, IMPORTER.NO_EXCEPTIONS
    if model_id == "ca-es-default" and path == "cat.segmentations":
        return IMPORTER.SEGMENTATION_SCHEMA, IMPORTER.NO_EXCEPTIONS
    exceptions = (
        IMPORTER.CKT_EXCEPTION
        if model_id == "ckt-default" and source_key == "primary"
        else IMPORTER.NO_EXCEPTIONS
    )
    return IMPORTER.NORMAL_SCHEMA, exceptions


def declared_sources(row: dict[str, str]) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for source in IMPORTER.parse_json_list(row["data_sources"], "data_sources"):
        schema, exceptions = source_schema(
            row["model_id"], str(source["key"]), str(source["path"])
        )
        result.append({**source, "schema": schema, "exceptions": exceptions})
    return result


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--topology", type=Path, required=True)
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def evidence_text(row: dict[str, str], cache: Path) -> tuple[str, str]:
    paths = IMPORTER.parse_mapping(row["evidence_paths"], "evidence_paths")
    hashes = IMPORTER.parse_mapping(row["evidence_sha256"], "evidence_sha256")
    chunks: list[str] = []
    readme = ""
    for source_path, local_name in paths.items():
        path = IMPORTER.safe_cache_path(cache, row["model_id"], local_name)
        if IMPORTER.sha256(path) != hashes.get(local_name):
            raise ValueError(f"Evidence SHA-256 mismatch for {row['model_id']}: {local_name}")
        text = path.read_text(encoding="utf-8", errors="strict")
        chunks.append(text)
        if Path(source_path).name.casefold().startswith("readme") and not readme:
            readme = text
    if not chunks:
        raise ValueError(f"No repository-owned evidence for active model {row['model_id']}")
    return "\n".join(chunks), readme


def verified_license(model_id: str, text: str) -> tuple[str, str]:
    license_id, license_uri = SPECIAL_LICENSES.get(
        model_id,
        ("CC-BY-SA-3.0", "https://creativecommons.org/licenses/by-sa/3.0/"),
    )
    folded = text.casefold()
    if not any(needle in folded for needle in LICENSE_NEEDLES[license_id]):
        raise ValueError(
            f"Pinned evidence for {model_id} does not state reviewed license {license_id}"
        )
    return license_id, license_uri


def section_lines(readme: str, heading: str) -> list[str]:
    lines = readme.splitlines()
    result: list[str] = []
    active = False
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("#"):
            title = stripped.lstrip("#").strip().casefold().rstrip(":")
            if active:
                break
            active = title == heading.casefold()
            continue
        if active and stripped:
            cleaned = stripped.lstrip("-* ")
            if cleaned:
                result.append(cleaned)
    return result


def source_origin(model_id: str, readme: str) -> str:
    if model_id in {"ceb-default", "evn-default"}:
        return "TBA"
    for line in readme.splitlines():
        match = re.match(r"^\s*(?:[-*]\s*)?(?:`[^`]+`:\s*)?Source\s*:\s*(.+?)\s*$", line, re.IGNORECASE)
        if match:
            return match.group(1)
    return "Not stated in pinned repository evidence"


def attribution(model_id: str, readme: str) -> str:
    if model_id == "ceb-default":
        return "UniMorph; Ran Zmigrod"
    if model_id == "evn-default":
        return "UniMorph; Elena Klyachko"
    annotators = section_lines(readme, "annotators")
    names = "; ".join(line for line in annotators if not line.casefold().startswith("tba"))
    return "UniMorph" + (f"; {names}" if names else "")


def measured_fields(row: dict[str, str], cache: Path) -> dict[str, str]:
    model_id = row["model_id"]
    combined = IMPORTER.combine_sources(row, cache)
    if not combined.groups:
        raise ValueError(f"Pinned source produced no usable groups for {model_id}")
    with tempfile.TemporaryDirectory() as directory:
        output = Path(directory) / "stemmer.gz"
        output_groups, output_forms = IMPORTER.render_gzip(
            output, model_id, row["revision"], combined.groups
        )
        output_sha256 = IMPORTER.sha256(output)
    return {
        "source_rows": str(combined.source_rows),
        "accepted_rows": str(combined.accepted_rows),
        "rejected_rows": str(sum(combined.rejected.values())),
        "rejection_counts": ";".join(
            f"{reason}={combined.rejected[reason]}" for reason in sorted(combined.rejected)
        ),
        "source_audit": combined.source_audit,
        "cross_source_conflict_assignments": str(
            combined.cross_source_conflict_assignments
        ),
        "cross_source_conflicting_surfaces": str(
            combined.cross_source_conflicting_surfaces
        ),
        "intrinsic_ambiguous_surfaces": str(combined.intrinsic_ambiguous_surfaces),
        "output_groups": str(output_groups),
        "output_forms": str(output_forms),
        "output_sha256": output_sha256,
    }


def build_rows(
    inventory_rows: list[dict[str, str]], topology: Path, cache: Path
) -> list[dict[str, str]]:
    expected = IMPORTER.active_standalones(topology)
    inventory = {row["model_id"]: row for row in inventory_rows}
    missing = expected - set(inventory)
    if missing:
        raise ValueError(f"Fetch inventory is missing active models: {sorted(missing)}")
    rows: list[dict[str, str]] = []
    for model_id in sorted(expected):
        source = inventory[model_id]
        if source["license_evidence_status"] != "present":
            raise ValueError(f"Active model has unresolved license evidence: {model_id}")
        all_evidence, readme = evidence_text(source, cache)
        license_id, license_uri = verified_license(model_id, all_evidence)
        sources = declared_sources(source)
        selected_paths = [str(item["path"]) for item in sources]
        row = {
            "model_id": model_id,
            "repository": source["repository"],
            "revision": source["revision"],
            "data_sources": json.dumps(
                sources, ensure_ascii=False, sort_keys=True, separators=(",", ":")
            ),
            "excluded_source_audit": source["excluded_source_audit"],
            "evidence_paths": source["evidence_paths"],
            "evidence_sha256": source["evidence_sha256"],
            "license_id": license_id,
            "license_uri": license_uri,
            "license_scope": (
                "Selected repository data files at pinned revision: " + ", ".join(selected_paths)
            ),
            "source_origin": source_origin(model_id, readme),
            "attribution": attribution(model_id, readme),
            "projection_semantics": PROJECTION_SEMANTICS.get(
                model_id,
                "Each declared three-column source directly projects lemma and surface form.",
            ),
            "conflict_semantics": CONFLICT_SEMANTICS,
            "lowercase": "true",
            "policy_version": IMPORTER.POLICY_VERSION,
        }
        row.update(measured_fields(row, cache))
        rows.append(row)
    return rows


def write_manifest(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=MANIFEST_FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    temporary.replace(path)


def main() -> None:
    arguments = parse_arguments()
    with arguments.inventory.open(encoding="utf-8", newline="") as source:
        inventory_rows = list(csv.DictReader(source))
    rows = build_rows(inventory_rows, arguments.topology, arguments.cache)
    IMPORTER.validate_manifest(rows, arguments.topology)
    write_manifest(arguments.output, rows)
    print(f"Wrote {len(rows)} pinned model records to {arguments.output}")


if __name__ == "__main__":
    main()
