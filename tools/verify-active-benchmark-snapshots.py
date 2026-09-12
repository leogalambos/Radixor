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
"""Verify active benchmark pointers, checksums, and immutable archive identities."""

from __future__ import annotations

import csv
import hashlib
from pathlib import Path
import re


ARCHIVES = {
    "stemming-quality.csv": "85763189eab4d0fbb047c2d5d3554c66abf9732182bd0d8fd758d7aef680e66f",
    "dictionary-generalization.csv": "e6479840b9307ae03bd0873e55f397811e975125d621a8b8716d4c1a166b3ff2",
    "dictionary-generalization-sources.sha256": "ff40b8729f5b648992a4d916df715d54f6804139be23a9453f6949c9105d45b1",
}
PERFORMANCE_KEYS = {
    "corpus",
    "accuracy",
    "speed",
    "coverage_accuracy",
    "coverage_speed",
    "python_csv",
    "python_json",
}
REQUIRED_KEYS = PERFORMANCE_KEYS | {
    "quality",
    "generalization",
    "generalization_sources",
    "comparator",
}
GENERALIZATION_SOURCE_PATHS = {
    "src/main/java/org/egothor/stemmer/WordTraversalDirection.java",
    "src/main/java/org/egothor/stemmer/FrequencyTrie.java",
    "src/main/java/org/egothor/stemmer/PatchCommandEncoder.java",
    "src/main/java/org/egothor/stemmer/StemmerPatchTrieLoader.java",
    "src/test/java/org/egothor/stemmer/benchmark/generalization/DictionaryGeneralizationApplication.java",
}


def digest(path: Path) -> str:
    """Return the lowercase SHA-256 of one file."""

    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_generalization_provenance(data_directory: Path, pointers: dict[str, str]) -> None:
    """Bind the active standalone continuation rows to the dated source manifest."""

    source_hashes: dict[str, str] = {}
    source_manifest = data_directory / pointers["generalization_sources"]
    for line in source_manifest.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
        if match is None or match.group(2) in source_hashes:
            raise ValueError("Generalization source manifest contains an invalid or duplicate entry")
        source_hashes[match.group(2)] = match.group(1)
    if set(source_hashes) != GENERALIZATION_SOURCE_PATHS:
        raise ValueError("Generalization source manifest file set differs from the measured-source schema")

    application_path = (
        "src/test/java/org/egothor/stemmer/benchmark/generalization/"
        "DictionaryGeneralizationApplication.java"
    )
    provenance_languages: dict[tuple[str, str, str], set[str]] = {}
    with (data_directory / pointers["generalization"]).open(
        encoding="utf-8", newline=""
    ) as source:
        for row in csv.DictReader(source):
            key = (row["source_revision"], row["source_state"], row["generator_sha256"])
            provenance_languages.setdefault(key, set()).add(row["language"])
    with (data_directory / pointers["corpus"]).open(encoding="utf-8", newline="") as source:
        active_defaults = {
            row["Language"] for row in csv.DictReader(source)
            if row["Model ID"] != "pl-pl-polimorf"
        }
    continuation_count = len(active_defaults) - 20
    sizes = sorted(len(languages) for languages in provenance_languages.values())
    if sizes == [20, continuation_count]:
        current = next(
            key for key, languages in provenance_languages.items()
            if len(languages) == continuation_count
        )
    elif sizes == [1, 20, continuation_count - 1]:
        current = next(
            key for key, languages in provenance_languages.items() if languages == {"AR"}
        )
    else:
        raise ValueError("Active generalization snapshot has an unexpected provenance split")
    if current[2] != source_hashes[application_path]:
        raise ValueError("Current generalization generator does not match its source manifest")


def verify(data_directory: Path) -> None:
    """Verify the lifecycle manifest and every selected local snapshot."""

    manifest = data_directory / "active-snapshots.properties"
    pointers: dict[str, str] = {}
    for line in manifest.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or key in pointers:
            raise ValueError("Active snapshot manifest contains an invalid or duplicate entry")
        pointers[key] = value
    if set(pointers) != REQUIRED_KEYS:
        raise ValueError("Active snapshot manifest keys differ from the required lifecycle schema")
    for name, expected in ARCHIVES.items():
        if digest(data_directory / name) != expected:
            raise ValueError(f"Immutable benchmark archive changed: {name}")
    performance = {
        line.split()[1]: line.split()[0]
        for line in (data_directory / "performance-snapshots.sha256").read_text(encoding="utf-8").splitlines()
        if line.strip()
    }
    for key, name in pointers.items():
        snapshot = data_directory / name
        if not snapshot.is_file():
            raise ValueError(f"Active {key} snapshot does not exist: {name}")
        if key in PERFORMANCE_KEYS:
            expected = performance.get(name)
        else:
            checksum = snapshot.with_suffix(".sha256")
            fields = checksum.read_text(encoding="utf-8").split()
            expected = fields[0] if len(fields) == 2 and fields[1] == name else None
        if expected is None or digest(snapshot) != expected:
            raise ValueError(f"Active {key} snapshot checksum is missing or stale: {name}")
    verify_generalization_provenance(data_directory, pointers)


if __name__ == "__main__":
    verify(Path("docs/benchmarks/data"))
    print("Verified active benchmark snapshot lifecycle.")
