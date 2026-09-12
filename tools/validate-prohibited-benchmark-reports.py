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

"""Validate exact coverage and protocol of private prohibited-model reports."""

from __future__ import annotations

import argparse
import csv
import math
from collections import Counter
from pathlib import Path


def manifest_ids(path: Path) -> tuple[str, ...]:
    """Read the exact model IDs from the guarded TSV manifest."""

    with path.open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    ids = tuple(row["model_id"] for row in rows)
    if not ids or len(ids) != len(set(ids)) or ids != tuple(sorted(ids)):
        raise ValueError("The guarded benchmark manifest IDs must be unique and sorted.")
    return ids


def read_csv(path: Path) -> list[dict[str, str]]:
    """Read one nonempty UTF-8 CSV report."""

    with path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if not rows:
        raise ValueError(f"Report is empty: {path}.")
    return rows


def comparator_cases(path: Path, prohibited: tuple[str, ...]) -> dict[str, str]:
    """Read the exact guarded prohibited/Snowball intersection."""

    with path.open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    expected_fields = [
        "model_id", "language", "display_language", "candidate", "speed_benchmark",
    ]
    if not rows or list(rows[0]) != expected_fields:
        raise ValueError("Invalid prohibited Snowball comparator catalog.")
    result: dict[str, str] = {}
    for row in rows:
        model_id = row["model_id"]
        if model_id not in prohibited or model_id in result \
                or not row["candidate"].startswith("SNOWBALL_") \
                or row["speed_benchmark"] != "ProhibitedModelStemmerBenchmark.snowballDirect":
            raise ValueError("Invalid prohibited Snowball comparator identity.")
        result[model_id] = row["candidate"]
    return result


def require_exact_ids(actual: set[str], expected: tuple[str, ...], label: str) -> None:
    """Require exact cohort coverage."""

    if actual != set(expected):
        raise ValueError(
            f"{label} model coverage differs: missing={sorted(set(expected) - actual)}, "
            f"extra={sorted(actual - set(expected))}."
        )


def validate_corpus(path: Path, expected: tuple[str, ...]) -> None:
    """Validate exact corpus model coverage."""

    rows = read_csv(path)
    require_exact_ids({row["Model ID"] for row in rows}, expected, "Corpus")


def validate_quality(path: Path, expected: tuple[str, ...]) -> None:
    """Validate two single-output quality policies per model."""

    rows = read_csv(path)
    require_exact_ids({row["Dictionary model ID"] for row in rows}, expected, "Quality")
    counts = Counter(row["Dictionary model ID"] for row in rows)
    invalid = {model_id: count for model_id, count in counts.items() if count != 2}
    if invalid:
        raise ValueError(f"Quality report must contain two processing modes per model: {invalid}.")


def validate_generalization(path: Path, expected: tuple[str, ...]) -> None:
    """Validate five seeds by ten knowledge levels per model."""

    rows = read_csv(path)
    require_exact_ids({row["model_id"] for row in rows}, expected, "Generalization")
    counts = Counter(row["model_id"] for row in rows)
    invalid = {model_id: count for model_id, count in counts.items() if count != 50}
    if invalid:
        raise ValueError(f"Generalization report must contain 50 scenarios per model: {invalid}.")


def validate_speed(path: Path, expected: tuple[str, ...]) -> None:
    """Validate exact 3-fork, 5-measurement, single-thread speed coverage."""

    rows = read_csv(path)
    parameter = next((name for name in rows[0] if name.strip() == "Param: modelId"), None)
    if parameter is None:
        raise ValueError("Speed report omits Param: modelId.")
    require_exact_ids({row[parameter] for row in rows}, expected, "Speed")
    for row in rows:
        benchmark = row["Benchmark"]
        if not benchmark.endswith("ProhibitedModelStemmerBenchmark.radixor"):
            raise ValueError(f"Unexpected prohibited speed benchmark: {benchmark}.")
        if row["Threads"] != "1" or row["Samples"] != "15":
            raise ValueError("Prohibited speed rows must use one thread and 3 x 5 = 15 samples.")
        score = float(row["Score"])
        error = float(row["Score Error (99.9%)"])
        if not math.isfinite(score) or not math.isfinite(error) or score <= 0 or error < 0:
            raise ValueError("Prohibited speed score and error must be finite and non-negative.")


def validate_snowball_accuracy(path: Path, cases: dict[str, str]) -> None:
    """Validate one exact-root result per prohibited Snowball case."""

    rows = read_csv(path)
    require_exact_ids({row["Dictionary model ID"] for row in rows}, tuple(cases), "Snowball accuracy")
    if len(rows) != len(cases):
        raise ValueError("Snowball accuracy must contain exactly one row per comparator.")
    for row in rows:
        model_id = row["Dictionary model ID"]
        if row["Candidate"] != cases[model_id]:
            raise ValueError(f"Snowball accuracy candidate differs for {model_id}.")
        total = int(row["Total tokens"])
        root = int(row["Already-root tokens"])
        changed = int(row["Changed tokens"])
        if total <= 0 or root + changed != total:
            raise ValueError(f"Snowball accuracy population differs for {model_id}.")
        for numerator, denominator in (
            ("All exact matches", total),
            ("Changed exact matches", changed),
            ("Root preserved matches", root),
        ):
            value = int(row[numerator])
            if value < 0 or value > denominator:
                raise ValueError(f"Snowball accuracy count {numerator} is invalid for {model_id}.")


def validate_snowball_quality(path: Path, cases: dict[str, str]) -> None:
    """Validate both pairwise processing modes per prohibited Snowball case."""

    rows = read_csv(path)
    require_exact_ids({row["Dictionary model ID"] for row in rows}, tuple(cases), "Snowball quality")
    counts = Counter(row["Dictionary model ID"] for row in rows)
    if any(count != 2 for count in counts.values()):
        raise ValueError("Snowball quality must contain exactly two rows per comparator.")
    for row in rows:
        model_id = row["Dictionary model ID"]
        if row["Stemmer"] != cases[model_id] or row["Output policy"] != "PRIMARY_OUTPUT":
            raise ValueError(f"Snowball quality candidate or policy differs for {model_id}.")


def validate_snowball_speed(path: Path, cases: dict[str, str]) -> None:
    """Validate canonical JMH protocol and exact comparator coverage."""

    rows = read_csv(path)
    parameter = next((name for name in rows[0] if name.strip() == "Param: modelId"), None)
    if parameter is None:
        raise ValueError("Snowball speed report omits Param: modelId.")
    require_exact_ids({row[parameter] for row in rows}, tuple(cases), "Snowball speed")
    if len(rows) != len(cases):
        raise ValueError("Snowball speed must contain exactly one row per comparator.")
    for row in rows:
        if not row["Benchmark"].endswith("ProhibitedModelStemmerBenchmark.snowballDirect"):
            raise ValueError(f"Unexpected prohibited Snowball speed benchmark: {row['Benchmark']}.")
        if row["Threads"] != "1" or row["Samples"] != "15":
            raise ValueError("Prohibited Snowball speed rows must use one thread and 15 samples.")
        score = float(row["Score"])
        error = float(row["Score Error (99.9%)"])
        if not math.isfinite(score) or not math.isfinite(error) or score <= 0 or error < 0:
            raise ValueError("Prohibited Snowball speed score and error must be finite and non-negative.")


def main() -> int:
    """Validate every supplied report and print its exact model coverage."""

    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--corpus", type=Path)
    parser.add_argument("--quality", type=Path)
    parser.add_argument("--generalization", type=Path)
    parser.add_argument("--speed", type=Path)
    parser.add_argument("--comparator-catalog", type=Path)
    parser.add_argument("--snowball-accuracy", type=Path)
    parser.add_argument("--snowball-quality", type=Path)
    parser.add_argument("--snowball-speed", type=Path)
    arguments = parser.parse_args()
    expected = manifest_ids(arguments.manifest)
    if arguments.corpus:
        validate_corpus(arguments.corpus, expected)
    if arguments.quality:
        validate_quality(arguments.quality, expected)
    if arguments.generalization:
        validate_generalization(arguments.generalization, expected)
    if arguments.speed:
        validate_speed(arguments.speed, expected)
    snowball_reports = (
        arguments.snowball_accuracy, arguments.snowball_quality, arguments.snowball_speed,
    )
    if any(snowball_reports):
        if arguments.comparator_catalog is None or not all(snowball_reports):
            parser.error("Snowball validation requires its catalog and all three comparator reports.")
        cases = comparator_cases(arguments.comparator_catalog, expected)
        validate_snowball_accuracy(arguments.snowball_accuracy, cases)
        validate_snowball_quality(arguments.snowball_quality, cases)
        validate_snowball_speed(arguments.snowball_speed, cases)
    elif arguments.comparator_catalog is not None:
        parser.error("A Snowball comparator catalog requires all three comparator reports.")
    if not any((arguments.corpus, arguments.quality, arguments.generalization, arguments.speed,
                *snowball_reports)):
        parser.error("At least one report must be supplied.")
    print(f"Validated prohibited benchmark coverage for {len(expected)} exact model IDs.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
