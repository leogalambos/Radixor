#!/usr/bin/env python3
"""
Generate GitHub Pages badge endpoint JSON files from CI report artifacts.

This script derives compact machine-readable badge payloads from:

- JaCoCo XML coverage report
- PIT mutation testing XML report

The generated JSON files are intended to be consumed by Shields endpoint badges.
"""

from __future__ import annotations

import argparse
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
        required=False,
        help="Deprecated compatibility option. JMH speed badges are no longer generated."
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


def remove_file_if_present(target: Path) -> None:
    """Remove a previously generated file when it is present."""
    if target.is_file():
        target.unlink()


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


def main() -> int:
    """Generate all requested badge metadata files."""
    arguments = parse_args()

    jacoco_xml = Path(arguments.jacoco_xml)
    pit_xml = Path(arguments.pit_xml)
    run_metrics_dir = Path(arguments.run_metrics_dir)
    latest_metrics_dir = Path(arguments.latest_metrics_dir)

    payloads = {
        "coverage-badge.json": coverage_payload(jacoco_xml),
        "pitest-badge.json": mutation_payload(pit_xml)
    }

    for file_name, payload in payloads.items():
        write_json(run_metrics_dir / file_name, payload)
        write_json(latest_metrics_dir / file_name, payload)

    remove_file_if_present(run_metrics_dir / "jmh-badge.json")
    remove_file_if_present(latest_metrics_dir / "jmh-badge.json")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
