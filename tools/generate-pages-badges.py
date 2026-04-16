#!/usr/bin/env python3
"""
Generate GitHub Pages badge endpoint JSON files from CI report artifacts.

This script derives compact machine-readable badge payloads from:

- JaCoCo XML coverage report
- PIT mutation testing XML report
- JMH CSV benchmark report

The generated JSON files are intended to be consumed by Shields endpoint badges.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Generate GitHub Pages badge metadata from build reports."
    )
    parser.add_argument(
        "--jacoco-xml",
        required=True,
        help="Path to the JaCoCo XML report."
    )
    parser.add_argument(
        "--pit-xml",
        required=True,
        help="Path to the PIT XML report."
    )
    parser.add_argument(
        "--jmh-csv",
        required=True,
        help="Path to the JMH CSV report."
    )
    parser.add_argument(
        "--run-metrics-dir",
        required=True,
        help="Target directory for the current build badge JSON files."
    )
    parser.add_argument(
        "--latest-metrics-dir",
        required=True,
        help="Target directory for the latest build badge JSON files."
    )
    return parser.parse_args()


def write_json(target: Path, payload: dict[str, object]) -> None:
    """Write a badge payload as formatted UTF-8 JSON."""
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(payload, indent=2) + os.linesep, encoding="utf-8")


def unavailable_payload(label: str) -> dict[str, object]:
    """Create a standard payload for unavailable metrics."""
    return {
        "schemaVersion": 1,
        "label": label,
        "message": "not available",
        "color": "lightgrey"
    }


def color_for_percentage(value: float) -> str:
    """Select a badge color for a percentage value."""
    if value >= 85.0:
        return "brightgreen"
    if value >= 70.0:
        return "green"
    if value >= 55.0:
        return "yellow"
    if value >= 40.0:
        return "orange"
    return "red"


def color_for_speedup(value: float) -> str:
    """Select a badge color for a speedup factor."""
    if value >= 4.0:
        return "brightgreen"
    if value >= 3.0:
        return "green"
    if value >= 2.0:
        return "yellow"
    if value >= 1.0:
        return "orange"
    return "red"


def coverage_payload(jacoco_xml: Path) -> dict[str, object]:
    """Build a line coverage badge payload from a JaCoCo XML report."""
    if not jacoco_xml.is_file():
        return unavailable_payload("coverage")

    root = ET.parse(jacoco_xml).getroot()
    line_counter = None
    for counter in root.findall("counter"):
        if counter.attrib.get("type") == "LINE":
            line_counter = counter
            break

    if line_counter is None:
        return unavailable_payload("coverage")

    missed = int(line_counter.attrib.get("missed", "0"))
    covered = int(line_counter.attrib.get("covered", "0"))
    total = missed + covered
    percentage = 0.0 if total == 0 else (100.0 * covered / total)

    return {
        "schemaVersion": 1,
        "label": "coverage",
        "message": f"{percentage:.1f}%",
        "color": color_for_percentage(percentage)
    }


def mutation_payload(pit_xml: Path) -> dict[str, object]:
    """Build a mutation score badge payload from a PIT XML report."""
    if not pit_xml.is_file():
        return unavailable_payload("mutation")

    root = ET.parse(pit_xml).getroot()
    mutation_coverage = root.attrib.get("mutationCoverage")
    if mutation_coverage is not None:
        score = float(mutation_coverage)
    else:
        detected_statuses = {
            "KILLED",
            "TIMED_OUT",
            "MEMORY_ERROR",
            "RUN_ERROR",
            "NON_VIABLE"
        }
        mutations = root.findall("mutation")
        total = len(mutations)
        detected = sum(
            1
            for mutation in mutations
            if mutation.attrib.get("status") in detected_statuses
        )
        score = 0.0 if total == 0 else (100.0 * detected / total)

    return {
        "schemaVersion": 1,
        "label": "mutation",
        "message": f"{score:.1f}%",
        "color": color_for_percentage(score)
    }


def parse_family_count(row: dict[str, str]) -> int:
    """Extract the JMH familyCount parameter from a CSV row."""
    for key, value in row.items():
        if key.startswith("Param: ") and key.endswith("familyCount"):
            try:
                return int(value)
            except (TypeError, ValueError):
                return -1
    return -1


def benchmark_payload(jmh_csv: Path) -> dict[str, object]:
    """Build a benchmark speedup badge payload from a JMH CSV report."""
    if not jmh_csv.is_file():
        return unavailable_payload("english benchmark")

    with jmh_csv.open("r", encoding="utf-8", newline="") as input_file:
        rows = list(csv.DictReader(input_file))

    if not rows:
        return unavailable_payload("english benchmark")

    relevant_rows: list[tuple[int, str, float]] = []
    for row in rows:
        benchmark = row.get("Benchmark", "")
        if not benchmark.endswith(
            "EnglishStemmerComparisonBenchmark.radixorUsUkProfiPreferredStem"
        ) and not benchmark.endswith(
            "EnglishStemmerComparisonBenchmark.snowballOriginalPorter"
        ):
            continue

        try:
            score = float(row["Score"])
        except (KeyError, TypeError, ValueError):
            continue

        relevant_rows.append((parse_family_count(row), benchmark, score))

    if not relevant_rows:
        return unavailable_payload("english benchmark")

    best_family_count = max(family_count for family_count, _, _ in relevant_rows)
    radixor_score = None
    porter_score = None

    for family_count, benchmark, score in relevant_rows:
        if family_count != best_family_count:
            continue
        if benchmark.endswith(".radixorUsUkProfiPreferredStem"):
            radixor_score = score
        elif benchmark.endswith(".snowballOriginalPorter"):
            porter_score = score

    if radixor_score is None or porter_score is None or porter_score <= 0.0:
        return unavailable_payload("english benchmark")

    # score is time for the batch processing, i.e. longer => slower, i.e. speedup is porter/radixor
    speedup = porter_score / radixor_score
    family_suffix = "" if best_family_count < 0 else f" ({best_family_count})"
    return {
        "schemaVersion": 1,
        "label": "english benchmark",
        "message": f"{speedup:.1f}x vs Porter{family_suffix}",
        "color": color_for_speedup(speedup)
    }


def main() -> int:
    """Generate all requested badge metadata files."""
    arguments = parse_args()

    jacoco_xml = Path(arguments.jacoco_xml)
    pit_xml = Path(arguments.pit_xml)
    jmh_csv = Path(arguments.jmh_csv)
    run_metrics_dir = Path(arguments.run_metrics_dir)
    latest_metrics_dir = Path(arguments.latest_metrics_dir)

    payloads = {
        "coverage-badge.json": coverage_payload(jacoco_xml),
        "pitest-badge.json": mutation_payload(pit_xml),
        "jmh-badge.json": benchmark_payload(jmh_csv)
    }

    for file_name, payload in payloads.items():
        write_json(run_metrics_dir / file_name, payload)
        write_json(latest_metrics_dir / file_name, payload)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
