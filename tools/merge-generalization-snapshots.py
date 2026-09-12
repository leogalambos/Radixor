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
###############################################################################
# Copyright (C) 2026, Leo Galambos
# All rights reserved.
###############################################################################

"""Merge the frozen 20-model archive with the active standalone continuation."""

from __future__ import annotations

import argparse
import csv
import hashlib
from dataclasses import dataclass
from pathlib import Path


EXPECTED_HEADER = [
    "protocol_version", "radixor_java_version", "source_revision", "source_state",
    "generator_sha256", "language", "model_id", "model_version", "model_sha256",
    "seed", "requested_percent", "selected_rows", "total_rows", "withheld_rows",
    "whole_correct", "whole_total", "whole_changed_correct", "whole_changed_total",
    "whole_root_correct", "whole_root_total", "withheld_correct", "withheld_total",
    "withheld_changed_correct", "withheld_changed_total", "withheld_root_correct",
    "withheld_root_total", "unseen_correct", "unseen_total", "unseen_changed_correct",
    "unseen_changed_total", "unseen_root_correct", "unseen_root_total",
    "excluded_overlap_occurrences",
]
PROVENANCE = (
    "radixor_java_version", "source_revision", "source_state", "generator_sha256"
)
EXPECTED_PERCENTS = set(range(10, 101, 10))
EXPECTED_SEEDS = {
    "2654435761", "2611923443488327891", "7046029254386353131",
    "11400714819323198485", "15111065706836454659",
}


@dataclass(frozen=True)
class InputSnapshot:
    """Validated rows, model identities, and provenance for one source."""

    rows: list[dict[str, str]]
    model_ids: frozenset[str]
    provenance: frozenset[tuple[str, ...]]


def read_topology(path: Path) -> dict[str, str]:
    """Read the strict model-role topology without Java-properties escapes."""

    topology: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        model_id, separator, role = stripped.partition("=")
        if not separator or not model_id or role not in {"default", "standalone", "optional"}:
            raise ValueError(f"Invalid topology line: {line}")
        if model_id in topology:
            raise ValueError(f"Duplicate topology model: {model_id}")
        topology[model_id] = role
    return topology


def read_corpus_models(path: Path) -> dict[str, str]:
    """Return exact default model-to-language identities from the current corpus CSV."""

    models: dict[str, str] = {}
    with path.open(newline="", encoding="utf-8") as source:
        reader = csv.DictReader(source)
        required = {"Language", "Model ID"}
        if reader.fieldnames is None or not required.issubset(reader.fieldnames):
            raise ValueError("Corpus catalog lacks Language and Model ID columns")
        for row in reader:
            if row["Model ID"] == "pl-pl-polimorf":
                continue
            previous = models.setdefault(row["Model ID"], row["Language"])
            if previous != row["Language"]:
                raise ValueError(f"Corpus model has conflicting languages: {row['Model ID']}")
    if not models:
        raise ValueError("Corpus catalog contains no language defaults")
    return models


def read_snapshot(path: Path, expected_models: set[str], corpus_models: dict[str, str]) -> InputSnapshot:
    """Read one complete, internally consistent 50-scenario-per-model source."""

    with path.open(newline="", encoding="utf-8") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames != EXPECTED_HEADER:
            raise ValueError(f"Generalization header differs in {path}")
        rows = list(reader)
    by_model: dict[str, list[dict[str, str]]] = {}
    keys: set[tuple[str, str, str]] = set()
    for row in rows:
        model_id = row["model_id"]
        key = (model_id, row["seed"], row["requested_percent"])
        if key in keys:
            raise ValueError(f"Duplicate generalization scenario: {key}")
        keys.add(key)
        if model_id not in expected_models or corpus_models.get(model_id) != row["language"]:
            raise ValueError(f"Unexpected model/language identity in {path}: {model_id}/{row['language']}")
        if row["protocol_version"] != "radixor-generalization-v1":
            raise ValueError(f"Unexpected generalization protocol in {path}")
        by_model.setdefault(model_id, []).append(row)
    if set(by_model) != expected_models:
        raise ValueError(f"Generalization model coverage differs in {path}")
    for model_id, model_rows in by_model.items():
        seeds = {row["seed"] for row in model_rows}
        percents = {int(row["requested_percent"]) for row in model_rows}
        if len(model_rows) != 50 or seeds != EXPECTED_SEEDS or percents != EXPECTED_PERCENTS:
            raise ValueError(f"Incomplete 5-by-10 scenario matrix for {model_id}")
    provenance = frozenset(tuple(row[name] for name in PROVENANCE) for row in rows)
    if len(provenance) != 1:
        raise ValueError(f"Provenance changes within {path}")
    return InputSnapshot(rows, frozenset(by_model), provenance)


def merge(
    archive: Path,
    continuation: Path,
    corpus: Path,
    topology_path: Path,
) -> list[dict[str, str]]:
    """Validate and deterministically combine the archived and active rows."""

    topology = read_topology(topology_path)
    corpus_models = read_corpus_models(corpus)
    archive_models = {model_id for model_id, role in topology.items() if role == "default"}
    continuation_models = {model_id for model_id, role in topology.items() if role == "standalone"}
    if len(archive_models) != 20 or not continuation_models:
        raise ValueError("Topology must preserve 20 archived defaults and active standalone defaults")
    if archive_models & continuation_models or archive_models | continuation_models != set(corpus_models):
        raise ValueError("Topology and current corpus default identities differ")
    archived = read_snapshot(archive, archive_models, corpus_models)
    continued = read_snapshot(continuation, continuation_models, corpus_models)
    if archived.model_ids & continued.model_ids:
        raise ValueError("Generalization source model sets overlap")
    if archived.provenance == continued.provenance:
        raise ValueError("Combined snapshot must retain distinct historical and continuation provenance")
    rows = archived.rows + continued.rows
    expected_rows = len(corpus_models) * 50
    if len(rows) != expected_rows:
        raise ValueError(
            f"Combined snapshot must contain {expected_rows:,} rows, found {len(rows):,}"
        )
    rows.sort(key=lambda row: (
        row["language"], row["model_id"], int(row["seed"]), -int(row["requested_percent"])
    ))
    return rows


def write_snapshot(path: Path, rows: list[dict[str, str]]) -> str:
    """Write the active merged snapshot and return its SHA-256."""

    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=EXPECTED_HEADER, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    return hashlib.sha256(path.read_bytes()).hexdigest()


def update_manifest(path: Path, snapshot_name: str) -> None:
    """Replace only the generalization pointer in the shared active manifest."""

    lines = path.read_text(encoding="utf-8").splitlines()
    entry = f"generalization={snapshot_name}"
    matches = [index for index, line in enumerate(lines) if line.startswith("generalization=")]
    if len(matches) != 1:
        raise ValueError("Active manifest must contain exactly one generalization pointer")
    lines[matches[0]] = entry
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--continuation", type=Path, required=True)
    parser.add_argument("--corpus", type=Path, required=True)
    parser.add_argument("--topology", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    arguments = parser.parse_args()
    rows = merge(arguments.archive, arguments.continuation, arguments.corpus, arguments.topology)
    checksum = write_snapshot(arguments.output, rows)
    checksum_path = arguments.output.with_suffix(".sha256")
    checksum_path.write_text(f"{checksum}  {arguments.output.name}\n", encoding="utf-8")
    update_manifest(arguments.manifest, arguments.output.name)
    print(f"Merged {len(rows)} scenarios into {arguments.output} ({checksum}).")


if __name__ == "__main__":
    main()
