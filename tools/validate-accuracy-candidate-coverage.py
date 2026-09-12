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
"""Validate exact-root candidate coverage from active topology authorities."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path


CATALOG_HEADER = [
    "Case",
    "Language",
    "Model ID",
    "Display language",
    "Direct available",
    "Lucene available",
    "Homepage chart",
    "Homepage aggregate",
]
COUNTERS = {
    "correctMatches",
    "evaluatedTokens",
    "changedCorrectMatches",
    "changedEvaluatedTokens",
    "rootPreservedMatches",
    "rootEvaluatedTokens",
}
POLIMORF_CANDIDATES = {
    "POLISH_POLIMORF_RADIXOR",
    "POLISH_POLIMORF_LUCENE_MORFOLOGIK_FILTER",
    "POLISH_POLIMORF_SNOWBALL_DIRECT",
}


def read_topology(path: Path) -> set[str]:
    """Return exact active model IDs from the strict role topology."""

    models: set[str] = set()
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        model_id, separator, role = line.partition("=")
        if (
            not separator
            or not model_id
            or role not in {"default", "standalone", "optional"}
            or model_id in models
        ):
            raise ValueError(f"Invalid active topology line: {raw_line!r}")
        models.add(model_id)
    if not models:
        raise ValueError("Active topology is empty")
    return models


def expected_candidates(catalog_path: Path, active_models: set[str]) -> set[str]:
    """Derive expected direct Snowball and optional PoliMorf candidates."""

    with catalog_path.open(encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames != CATALOG_HEADER:
            raise ValueError("Snowball catalog schema differs from the Java authority")
        rows = list(reader)
    candidates: set[str] = set()
    for row in rows:
        model_id = row["Model ID"]
        if model_id not in active_models:
            raise ValueError(
                f"Snowball catalog references inactive model {model_id} for {row['Case']}"
            )
        if row["Direct available"] != "true":
            raise ValueError(f"Snowball catalog direct case is unavailable: {row['Case']}")
        candidate = f"SNOWBALL_{row['Case']}_DIRECT"
        if candidate in candidates:
            raise ValueError(f"Duplicate Snowball candidate in catalog: {candidate}")
        candidates.add(candidate)
    if "pl-pl-polimorf" in active_models:
        candidates.update(POLIMORF_CANDIDATES)
    return candidates


def validate_report(report_path: Path, candidates: set[str]) -> None:
    """Require every auxiliary exact-root counter for every expected candidate."""

    found: dict[str, set[str]] = {candidate: set() for candidate in candidates}
    with report_path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        required = {"Benchmark", "Param: candidateName"}
        if reader.fieldnames is None or not required.issubset(reader.fieldnames):
            raise ValueError("Accuracy report lacks benchmark or candidate columns")
        for row in reader:
            candidate = row["Param: candidateName"]
            if candidate not in found:
                continue
            prefix = (
                "org.egothor.stemmer.benchmark.StemmerComparisonBenchmarkQuality."
                "exactRootAgreement:"
            )
            benchmark = row["Benchmark"]
            if benchmark.startswith(prefix):
                found[candidate].add(benchmark.removeprefix(prefix))
    missing = {
        candidate: sorted(COUNTERS - counters)
        for candidate, counters in found.items()
        if counters != COUNTERS
    }
    if missing:
        details = "; ".join(
            f"{candidate}: {','.join(counters)}"
            for candidate, counters in sorted(missing.items())
        )
        raise ValueError(f"Exact-root report has incomplete active candidate coverage: {details}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--snowball-catalog", type=Path, required=True)
    parser.add_argument("--topology", type=Path, required=True)
    arguments = parser.parse_args()
    candidates = expected_candidates(
        arguments.snowball_catalog, read_topology(arguments.topology)
    )
    validate_report(arguments.report, candidates)
    print(f"Validated exact-root coverage for {len(candidates)} active candidates.")


if __name__ == "__main__":
    main()
