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
"""Rebuild manifest-managed UniMorph dictionaries from an offline source cache.

The fetch phase is deliberately separate. This importer never accesses the
network and refuses sources, evidence, licenses, schemas, counts, or generated
bytes that differ from the authoritative manifest.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
import os
import tempfile
import unicodedata
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO


POLICY_VERSION = "radixor-unimorph-import-v1"
SUPPORTED_LICENSES = {"CC-BY-SA-3.0", "CC-BY-SA-4.0", "CC-BY-4.0", "LGPLLR"}
NORMAL_SCHEMA = "lemma-form-features"
SEGMENTATION_SCHEMA = "lemma-form-features-segmentation"
SDH_SCHEMA = "empty-lemma-form-features"
NO_EXCEPTIONS = "none"
CKT_EXCEPTION = "reject-line-67-malformed-two-column"
JOINERS = {"\u200c", "\u200d"}
REPORT_FIELDS = (
    "model_id",
    "source_rows",
    "accepted_rows",
    "rejected_rows",
    "rejection_counts",
    "cross_source_conflict_assignments",
    "cross_source_conflicting_surfaces",
    "intrinsic_ambiguous_surfaces",
    "output_groups",
    "output_forms",
    "output_sha256",
)


@dataclass(frozen=True)
class ImportStatistics:
    """Auditable counters and deterministic output identity for one model."""

    source_rows: int
    accepted_rows: int
    rejected_rows: int
    rejection_counts: str
    cross_source_conflict_assignments: int
    cross_source_conflicting_surfaces: int
    intrinsic_ambiguous_surfaces: int
    output_groups: int
    output_forms: int
    output_sha256: str


@dataclass(frozen=True)
class ParsedSource:
    """Normalized content and counters from one declared source file."""

    groups: dict[str, set[str]]
    surface_lemmas: dict[str, set[str]]
    source_rows: int
    accepted_rows: int
    rejected: Counter[str]


@dataclass(frozen=True)
class CombinedSources:
    """Unioned source content plus deterministic provenance audit fields."""

    groups: dict[str, set[str]]
    source_rows: int
    accepted_rows: int
    rejected: Counter[str]
    source_audit: str
    cross_source_conflict_assignments: int
    cross_source_conflicting_surfaces: int
    intrinsic_ambiguous_surfaces: int


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--topology", type=Path, required=True)
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--models-root", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--mode", choices=("verify", "update"), required=True)
    return parser.parse_args()


def sha256(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def parse_mapping(value: str, field: str) -> dict[str, str]:
    result: dict[str, str] = {}
    if not value:
        return result
    for item in value.split(";"):
        key, separator, mapped = item.partition(":")
        if not separator or not key or not mapped or key in result:
            raise ValueError(f"Invalid {field} mapping: {item!r}")
        result[key] = mapped
    return result


def parse_json_list(value: str, field: str) -> list[dict[str, object]]:
    try:
        decoded = json.loads(value)
    except json.JSONDecodeError as error:
        raise ValueError(f"Invalid JSON in {field}") from error
    if not isinstance(decoded, list) or any(not isinstance(item, dict) for item in decoded):
        raise ValueError(f"Expected a JSON object list in {field}")
    return decoded


def safe_cache_path(root: Path, model_id: str, name: str) -> Path:
    relative = Path(name)
    if relative.is_absolute() or ".." in relative.parts or len(relative.parts) != 1:
        raise ValueError(f"Unsafe cache filename for {model_id}: {name!r}")
    return root / model_id / relative


def active_standalones(path: Path) -> set[str]:
    result: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            model_id, role = stripped.split("=", 1)
            if role == "standalone" and model_id != "ar-default":
                result.add(model_id)
    return result


def validate_manifest(rows: list[dict[str, str]], topology: Path) -> None:
    model_ids = [row["model_id"] for row in rows]
    if len(model_ids) != len(set(model_ids)):
        raise ValueError("UniMorph source manifest contains duplicate model IDs")
    expected = active_standalones(topology)
    if set(model_ids) != expected:
        raise ValueError(
            "UniMorph source manifest does not match active non-Arabic standalones: "
            f"missing={sorted(expected - set(model_ids))}, "
            f"unexpected={sorted(set(model_ids) - expected)}"
        )
    for row in rows:
        model_id = row["model_id"]
        if row["policy_version"] != POLICY_VERSION:
            raise ValueError(f"Unsupported import policy for {model_id}: {row['policy_version']}")
        if row["license_id"] not in SUPPORTED_LICENSES:
            raise ValueError(f"Unsupported or unknown license for {model_id}: {row['license_id']!r}")
        if not row["license_uri"].startswith("https://"):
            raise ValueError(f"Invalid license URI for {model_id}")
        if (
            not row["license_scope"].strip()
            or not row["source_origin"].strip()
            or not row["attribution"].strip()
            or not row["projection_semantics"].strip()
            or not row["conflict_semantics"].strip()
        ):
            raise ValueError(f"Missing license or import semantics metadata for {model_id}")
        sources = parse_json_list(row["data_sources"], "data_sources")
        if not sources or sources[0].get("key") != "primary":
            raise ValueError(f"Data sources for {model_id} must begin with primary")
        source_keys: set[str] = set()
        for source in sources:
            source_key = source.get("key")
            schema = source.get("schema")
            exceptions = source.get("exceptions")
            if not isinstance(source_key, str) or not source_key or source_key in source_keys:
                raise ValueError(f"Invalid or duplicate source key for {model_id}: {source_key!r}")
            source_keys.add(source_key)
            if schema not in {NORMAL_SCHEMA, SEGMENTATION_SCHEMA, SDH_SCHEMA}:
                raise ValueError(f"Unsupported source schema for {model_id}: {schema!r}")
            expected_exception = (
                CKT_EXCEPTION
                if model_id == "ckt-default" and source_key == "primary"
                else NO_EXCEPTIONS
            )
            if exceptions != expected_exception:
                raise ValueError(
                    f"Unexpected exception policy for {model_id}/{source_key}: "
                    f"{exceptions!r}; expected {expected_exception!r}"
                )
            if (schema == SDH_SCHEMA) != (
                model_id == "sdh-default" and source_key == "primary"
            ):
                raise ValueError(
                    f"Shifted four-column schema is reserved for sdh-default primary, not "
                    f"{model_id}/{source_key}"
                )
            if schema == SEGMENTATION_SCHEMA and not (
                model_id == "ca-es-default" and source.get("path") == "cat.segmentations"
            ):
                raise ValueError(
                    f"Segmentation schema is reserved for audited cat.segmentations, not "
                    f"{model_id}/{source_key}"
                )
            for field in ("path", "local_name", "sha256"):
                value = source.get(field)
                if not isinstance(value, str) or not value:
                    raise ValueError(f"Missing source {field} for {model_id}/{source_key}")
            if len(str(source["sha256"])) != 64:
                raise ValueError(f"Invalid source SHA-256 for {model_id}/{source_key}")
        exclusions = parse_json_list(row["excluded_source_audit"], "excluded_source_audit")
        excluded_paths: set[str] = set()
        for exclusion in exclusions:
            path = exclusion.get("path")
            reason = exclusion.get("reason")
            detail = exclusion.get("detail")
            if (
                not isinstance(path, str)
                or not path
                or path in excluded_paths
                or path in {str(source["path"]) for source in sources}
                or not isinstance(reason, str)
                or not reason
                or not isinstance(detail, str)
                or not detail
            ):
                raise ValueError(f"Invalid excluded-source audit for {model_id}: {exclusion!r}")
            excluded_paths.add(path)
        source_audit = parse_json_list(row["source_audit"], "source_audit")
        if [audit.get("path") for audit in source_audit] != [
            source.get("path") for source in sources
        ]:
            raise ValueError(f"Per-source audit order differs from selected sources for {model_id}")
        if row["lowercase"] != "true":
            raise ValueError(f"Manifest-managed model must retain lowercase import policy: {model_id}")
        for field in (
            "source_rows", "accepted_rows", "rejected_rows",
            "cross_source_conflict_assignments", "cross_source_conflicting_surfaces",
            "intrinsic_ambiguous_surfaces", "output_groups", "output_forms"
        ):
            if int(row[field]) < 0:
                raise ValueError(f"Negative {field} for {model_id}")
        for field in ("revision", "output_sha256"):
            if len(row[field]) != (40 if field == "revision" else 64):
                raise ValueError(f"Invalid {field} for {model_id}")
        evidence_paths = parse_mapping(row["evidence_paths"], "evidence_paths")
        evidence_hashes = parse_mapping(row["evidence_sha256"], "evidence_sha256")
        if not evidence_paths or set(evidence_paths.values()) != set(evidence_hashes):
            raise ValueError(f"Incomplete repository-owned license evidence for {model_id}")


def invalid_field_reason(value: str) -> str | None:
    if not value:
        return "empty"
    if "#" in value or "//" in value:
        return "runtime-comment-marker"
    for character in value:
        category = unicodedata.category(character)
        if character.isspace() or category in {"Zl", "Zp"}:
            return "unicode-whitespace"
        if category == "Cc":
            return "control-character"
        if 0xD800 <= ord(character) <= 0xDFFF:
            return "malformed-surrogate"
        if character not in JOINERS and category[0] not in {"L", "M", "N", "P"}:
            return "unsupported-character"
    return None


def normalize_field(value: str) -> str:
    return unicodedata.normalize("NFC", value).lower()


def strip_line_ending(raw_line: bytes) -> bytes:
    if raw_line.endswith(b"\n"):
        raw_line = raw_line[:-1]
    if raw_line.endswith(b"\r"):
        raw_line = raw_line[:-1]
    return raw_line


def parse_source(
    source: BinaryIO, expected_sha256: str, schema: str, exceptions: str
) -> ParsedSource:
    groups: dict[str, set[str]] = {}
    surface_lemmas: dict[str, set[str]] = {}
    source_rows = 0
    accepted_rows = 0
    rejected: Counter[str] = Counter()
    digest = hashlib.sha256()
    for line_number, raw_line in enumerate(source, start=1):
        digest.update(raw_line)
        source_rows += 1
        decoded = strip_line_ending(raw_line).decode("utf-8", errors="strict")
        if not decoded:
            rejected["blank-row"] += 1
            continue
        columns = decoded.split("\t")
        if schema == NORMAL_SCHEMA:
            if len(columns) != 3:
                if exceptions == CKT_EXCEPTION and line_number == 67 and len(columns) == 2:
                    rejected["declared-ckt-malformed-row"] += 1
                    continue
                raise ValueError(
                    f"Unexpected {len(columns)}-column row at line {line_number}; expected 3"
                )
            lemma_value, form_value = columns[0], columns[1]
        elif schema == SEGMENTATION_SCHEMA:
            if len(columns) != 4:
                raise ValueError(
                    f"Unexpected {len(columns)}-column row at line {line_number}; expected 4"
                )
            lemma_value, form_value = columns[0], columns[1]
        else:
            if len(columns) != 4 or columns[0] != "":
                raise ValueError(
                    f"Unexpected shifted schema at line {line_number}; expected empty+lemma+form+features"
                )
            lemma_value, form_value = columns[1], columns[2]
        lemma = normalize_field(lemma_value)
        form = normalize_field(form_value)
        lemma_reason = invalid_field_reason(lemma)
        form_reason = invalid_field_reason(form)
        if lemma_reason is not None:
            rejected[f"lemma-{lemma_reason}"] += 1
            continue
        if form_reason is not None:
            rejected[f"form-{form_reason}"] += 1
            continue
        groups.setdefault(lemma, set()).add(form)
        surface_lemmas.setdefault(form, set()).add(lemma)
        accepted_rows += 1
    actual_sha256 = digest.hexdigest()
    if actual_sha256 != expected_sha256:
        raise ValueError(
            f"Source SHA-256 mismatch: expected {expected_sha256}, found {actual_sha256}"
        )
    return ParsedSource(groups, surface_lemmas, source_rows, accepted_rows, rejected)


def output_counts(groups: dict[str, set[str]]) -> tuple[int, int, int]:
    changed_pairs = sum(
        1 for lemma, forms in groups.items() for form in forms if form != lemma
    )
    return len(groups), len(groups) + changed_pairs, changed_pairs


def combine_sources(
    row: dict[str, str], cache: Path, verify_hashes: bool = True
) -> CombinedSources:
    model_id = row["model_id"]
    groups: dict[str, set[str]] = {}
    combined_surface_lemmas: dict[str, set[str]] = {}
    source_rows = 0
    accepted_rows = 0
    rejected: Counter[str] = Counter()
    source_audit: list[dict[str, object]] = []
    intrinsic_surfaces: set[str] = set()
    conflicting_surfaces: set[str] = set()
    conflict_assignments = 0
    seen_source_pairs: set[tuple[str, str]] = set()
    for source_spec in parse_json_list(row["data_sources"], "data_sources"):
        source_key = str(source_spec["key"])
        source_path = safe_cache_path(cache, model_id, str(source_spec["local_name"]))
        expected_sha256 = str(source_spec["sha256"])
        with source_path.open("rb") as source:
            parsed = parse_source(
                source,
                expected_sha256 if verify_hashes else sha256(source_path),
                str(source_spec["schema"]),
                str(source_spec["exceptions"]),
            )
        before_groups, before_forms, before_changed = output_counts(groups)
        before_surfaces = set(groups)
        for existing_forms in groups.values():
            before_surfaces.update(existing_forms)
        source_pairs = {
            (lemma, form)
            for lemma, forms in parsed.groups.items()
            for form in forms
        }
        added_source_pairs = len(source_pairs - seen_source_pairs)
        seen_source_pairs.update(source_pairs)
        for lemma, forms in parsed.groups.items():
            groups.setdefault(lemma, set()).update(forms)
        after_groups, after_forms, after_changed = output_counts(groups)
        after_surfaces = set(groups)
        for existing_forms in groups.values():
            after_surfaces.update(existing_forms)
        source_conflicting_surfaces: set[str] = set()
        source_conflict_assignments = 0
        include_identity_conflicts = model_id == "be-by-default"
        source_surface_lemmas = {
            surface: set(lemmas) for surface, lemmas in parsed.surface_lemmas.items()
        }
        if model_id == "be-by-default":
            for lemma in parsed.groups:
                source_surface_lemmas.setdefault(lemma, set()).add(lemma)
        for surface, lemmas in source_surface_lemmas.items():
            if len(lemmas) > 1:
                intrinsic_surfaces.add(surface)
            if source_key != "primary" and surface in combined_surface_lemmas:
                candidate_lemmas = {
                    lemma
                    for lemma in lemmas
                    if include_identity_conflicts or surface != lemma
                }
                new_conflicts = candidate_lemmas - combined_surface_lemmas[surface]
                if new_conflicts:
                    source_conflicting_surfaces.add(surface)
                    source_conflict_assignments += len(new_conflicts)
            combined_surface_lemmas.setdefault(surface, set()).update(lemmas)
        conflicting_surfaces.update(source_conflicting_surfaces)
        conflict_assignments += source_conflict_assignments
        source_rows += parsed.source_rows
        accepted_rows += parsed.accepted_rows
        rejected.update(parsed.rejected)
        source_audit.append({
            "accepted_rows": parsed.accepted_rows,
            "added_changed_pairs": after_changed - before_changed,
            "added_distinct_surfaces": len(after_surfaces - before_surfaces),
            "added_groups": after_groups - before_groups,
            "added_output_forms": after_forms - before_forms,
            "added_source_pairs": added_source_pairs,
            "intrinsic_ambiguous_surfaces": sum(
                len(lemmas) > 1 for lemmas in parsed.surface_lemmas.values()
            ),
            "cross_source_conflict_assignments": source_conflict_assignments,
            "cross_source_conflicting_surfaces": len(source_conflicting_surfaces),
            "key": source_key,
            "path": source_spec["path"],
            "rejected_rows": sum(parsed.rejected.values()),
            "rejection_counts": dict(sorted(parsed.rejected.items())),
            "source_rows": parsed.source_rows,
        })
    return CombinedSources(
        groups,
        source_rows,
        accepted_rows,
        rejected,
        json.dumps(source_audit, ensure_ascii=False, sort_keys=True, separators=(",", ":")),
        conflict_assignments,
        len(conflicting_surfaces),
        len(intrinsic_surfaces),
    )


def render_gzip(path: Path, model_id: str, revision: str, groups: dict[str, set[str]]) -> tuple[int, int]:
    output_forms = 0
    with path.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, compresslevel=9, mtime=0) as compressed:
            header = (
                "# Adapted from pinned UniMorph source data.\n"
                f"# Model: {model_id}\n"
                f"# Source revision: {revision}\n"
                f"# Import policy: {POLICY_VERSION}\n"
            )
            compressed.write(header.encode("utf-8"))
            for lemma in sorted(groups):
                variants = sorted(form for form in groups[lemma] if form != lemma)
                output_forms += 1 + len(variants)
                compressed.write("\t".join((lemma, *variants)).encode("utf-8"))
                compressed.write(b"\n")
    return len(groups), output_forms


def import_model(
    row: dict[str, str], cache: Path, destination: Path, update: bool
) -> ImportStatistics:
    model_id = row["model_id"]
    evidence_sources = parse_mapping(row["evidence_paths"], "evidence_paths")
    evidence_hashes = parse_mapping(row["evidence_sha256"], "evidence_sha256")
    for local_name in evidence_sources.values():
        evidence = safe_cache_path(cache, model_id, local_name)
        if sha256(evidence) != evidence_hashes[local_name]:
            raise ValueError(f"License evidence SHA-256 mismatch for {model_id}: {local_name}")
    combined = combine_sources(row, cache)
    if not combined.groups:
        raise ValueError(f"Pinned source produced no usable dictionary groups for {model_id}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary_handle = tempfile.NamedTemporaryFile(
        dir=destination.parent, prefix=destination.name + ".", suffix=".tmp", delete=False
    )
    temporary = Path(temporary_handle.name)
    temporary_handle.close()
    try:
        output_groups, output_forms = render_gzip(
            temporary, model_id, row["revision"], combined.groups
        )
        output_sha256 = sha256(temporary)
        statistics = ImportStatistics(
            combined.source_rows,
            combined.accepted_rows,
            sum(combined.rejected.values()),
            ";".join(
                f"{reason}={combined.rejected[reason]}" for reason in sorted(combined.rejected)
            ),
            combined.cross_source_conflict_assignments,
            combined.cross_source_conflicting_surfaces,
            combined.intrinsic_ambiguous_surfaces,
            output_groups,
            output_forms,
            output_sha256,
        )
        expected = ImportStatistics(
            int(row["source_rows"]),
            int(row["accepted_rows"]),
            int(row["rejected_rows"]),
            row["rejection_counts"],
            int(row["cross_source_conflict_assignments"]),
            int(row["cross_source_conflicting_surfaces"]),
            int(row["intrinsic_ambiguous_surfaces"]),
            int(row["output_groups"]),
            int(row["output_forms"]),
            row["output_sha256"],
        )
        if statistics != expected:
            raise ValueError(f"Import statistics mismatch for {model_id}: {statistics} != {expected}")
        if combined.source_audit != row["source_audit"]:
            raise ValueError(f"Per-source audit mismatch for {model_id}")
        if update:
            os.replace(temporary, destination)
        elif not destination.is_file() or sha256(destination) != output_sha256:
            raise ValueError(f"Checked-in dictionary differs from deterministic import for {model_id}")
        return statistics
    finally:
        temporary.unlink(missing_ok=True)


def write_report(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=REPORT_FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    temporary.replace(path)


def main() -> None:
    arguments = parse_arguments()
    with arguments.manifest.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source))
    validate_manifest(rows, arguments.topology)
    report_rows: list[dict[str, str]] = []
    for row in sorted(rows, key=lambda item: item["model_id"]):
        model_id = row["model_id"]
        destination = arguments.models_root / model_id / "src/modelInput/stemmer.gz"
        statistics = import_model(
            row, arguments.cache, destination, arguments.mode == "update"
        )
        report_rows.append({
            "model_id": model_id,
            "source_rows": str(statistics.source_rows),
            "accepted_rows": str(statistics.accepted_rows),
            "rejected_rows": str(statistics.rejected_rows),
            "rejection_counts": statistics.rejection_counts,
            "cross_source_conflict_assignments": str(
                statistics.cross_source_conflict_assignments
            ),
            "cross_source_conflicting_surfaces": str(
                statistics.cross_source_conflicting_surfaces
            ),
            "intrinsic_ambiguous_surfaces": str(statistics.intrinsic_ambiguous_surfaces),
            "output_groups": str(statistics.output_groups),
            "output_forms": str(statistics.output_forms),
            "output_sha256": statistics.output_sha256,
        })
    write_report(arguments.report, report_rows)
    action = "Verified" if arguments.mode == "verify" else "Updated"
    print(f"{action} {len(report_rows)} manifest-managed UniMorph models")


if __name__ == "__main__":
    main()
