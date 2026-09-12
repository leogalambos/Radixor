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
"""Replace selected rows in a CSV snapshot while preserving all other bytes."""

from __future__ import annotations

import argparse
import csv
import io
import math
import os
import tempfile
from pathlib import Path


def parse_arguments() -> argparse.Namespace:
    """Parse the base, replacement, output, and exact selector arguments."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--replacement", type=Path, action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--key-field", action="append", required=True,
        help="CSV field forming the ordered composite row key; repeat for each component.",
    )
    parser.add_argument(
        "--selector", action="append", default=[],
        help="Exact FIELD=VALUE selector; rows matching any selector are replaced.",
    )
    parser.add_argument(
        "--selector-group", action="append", nargs="+", default=[],
        help=(
            "Conjunctive FIELD=VALUE selector group; rows match when every selector in "
            "one repeated group matches."
        ),
    )
    parser.add_argument(
        "--require-field", action="append", default=[],
        help="Require FIELD=VALUE on every replacement row; repeat as needed.",
    )
    parser.add_argument(
        "--require-positive-finite-field", action="append", default=[],
        help="Require a positive finite decimal in this replacement field.",
    )
    parser.add_argument("--expected-replacement-count", type=int)
    parser.add_argument("--expected-output-row-count", type=int)
    return parser.parse_args()


def parse_selectors(values: list[str]) -> list[tuple[str, str]]:
    """Parse non-empty exact field selectors."""

    selectors: list[tuple[str, str]] = []
    for value in values:
        field, separator, expected = value.partition("=")
        if not separator or not field or not expected:
            raise ValueError(f"Invalid exact CSV selector: {value}")
        selectors.append((field, expected))
    return selectors


def read_raw(path: Path) -> tuple[str, list[str], list[tuple[str, dict[str, str]]]]:
    """Read logical one-line CSV records and retain their original bytes as text."""

    lines = path.read_bytes().decode("utf-8").splitlines(keepends=True)
    if not lines:
        raise ValueError(f"CSV snapshot is empty: {path}")
    header = next(csv.reader([lines[0].rstrip("\r\n")]))
    records: list[tuple[str, dict[str, str]]] = []
    for line in lines[1:]:
        values = next(csv.reader([line.rstrip("\r\n")]))
        if len(values) != len(header):
            raise ValueError(f"CSV record width differs from its header: {path}")
        records.append((line, dict(zip(header, values, strict=True))))
    return lines[0], header, records


def matches(row: dict[str, str], selectors: list[tuple[str, str]]) -> bool:
    """Return whether a row matches at least one exact selector."""

    return any(row.get(field) == expected for field, expected in selectors)


def matches_groups(
    row: dict[str, str], selector_groups: list[list[tuple[str, str]]],
) -> bool:
    """Return whether a row matches every selector in at least one group."""

    return any(
        all(row.get(field) == expected for field, expected in group)
        for group in selector_groups
    )


def keyed_rows(
    rows: list[tuple[str, dict[str, str]]], key_fields: list[str], source: Path,
) -> dict[tuple[str, ...], tuple[str, dict[str, str]]]:
    """Index rows by a unique, ordered composite key."""

    keyed: dict[tuple[str, ...], tuple[str, dict[str, str]]] = {}
    for line, row in rows:
        key = tuple(row[field] for field in key_fields)
        if key in keyed:
            raise ValueError(f"Duplicate composite key {key!r} in {source}")
        keyed[key] = (line, row)
    return keyed


def replace(
    base: Path, replacements: list[Path], output: Path,
    selectors: list[tuple[str, str]], key_fields: list[str],
    required_fields: list[tuple[str, str]] | None = None,
    positive_finite_fields: list[str] | None = None,
    expected_replacement_count: int | None = None,
    expected_output_row_count: int | None = None,
    selector_groups: list[list[tuple[str, str]]] | None = None,
) -> tuple[int, int]:
    """Replace selected records and return removed and inserted counts."""

    header_line, header, base_rows = read_raw(base)
    if not key_fields or len(set(key_fields)) != len(key_fields):
        raise ValueError("Composite key fields must be non-empty and unique")
    if any(field not in header for field in key_fields):
        raise ValueError("A composite key field is absent from the base CSV header")
    base_by_key = keyed_rows(base_rows, key_fields, base)
    if expected_output_row_count is not None and len(base_rows) != expected_output_row_count:
        raise ValueError(
            f"Base row count is {len(base_rows)}, expected {expected_output_row_count}"
        )
    if not selectors and not selector_groups:
        raise ValueError("At least one exact selector or selector group is required")
    selected_keys = {
        key
        for key, (_, row) in base_by_key.items()
        if matches(row, selectors) or matches_groups(row, selector_groups or [])
    }
    if not selected_keys:
        raise ValueError("Base snapshot contains no selected rows")
    replacement_by_key: dict[tuple[str, ...], dict[str, str]] = {}
    for replacement in replacements:
        _, replacement_header, replacement_rows = read_raw(replacement)
        if replacement_header != header:
            raise ValueError(
                f"Replacement CSV header differs from the base header: {replacement}"
            )
        selected = keyed_rows(replacement_rows, key_fields, replacement)
        if (
            any(
                not matches(row, selectors)
                and not matches_groups(row, selector_groups or [])
                for _, row in selected.values()
            )
            or not selected
        ):
            raise ValueError(f"Replacement contains unselected rows or is empty: {replacement}")
        for key, (_, row) in selected.items():
            for field, expected in required_fields or []:
                if row.get(field) != expected:
                    raise ValueError(
                        f"Replacement field {field!r} is {row.get(field)!r}, expected "
                        f"{expected!r}: {replacement}"
                    )
            for field in positive_finite_fields or []:
                try:
                    value = float(row[field])
                except (KeyError, ValueError) as error:
                    raise ValueError(
                        f"Replacement field {field!r} is not a decimal: {replacement}"
                    ) from error
                if not math.isfinite(value) or value <= 0.0:
                    raise ValueError(
                        f"Replacement field {field!r} must be positive and finite: "
                        f"{replacement}"
                    )
            if key in replacement_by_key:
                raise ValueError(f"Duplicate replacement composite key {key!r}")
            replacement_by_key[key] = row
    if (
        expected_replacement_count is not None
        and len(replacement_by_key) != expected_replacement_count
    ):
        raise ValueError(
            f"Replacement row count is {len(replacement_by_key)}, expected "
            f"{expected_replacement_count}"
        )
    missing = selected_keys - replacement_by_key.keys()
    extra = replacement_by_key.keys() - selected_keys
    if missing or extra:
        raise ValueError(
            f"Replacement key coverage differs from selected base rows; missing={sorted(missing)!r}, "
            f"extra={sorted(extra)!r}"
        )
    line_ending = "\r\n" if header_line.endswith("\r\n") else "\n"
    output_lines: list[str] = []
    for original_line, row in base_rows:
        key = tuple(row[field] for field in key_fields)
        replacement_row = replacement_by_key.get(key)
        if replacement_row is None:
            output_lines.append(original_line)
            continue
        buffer = io.StringIO(newline="")
        writer = csv.writer(buffer, quoting=csv.QUOTE_ALL, lineterminator=line_ending)
        writer.writerow([replacement_row.get(field, "") for field in header])
        output_lines.append(buffer.getvalue())
    payload = (header_line + "".join(output_lines)).encode("utf-8")
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{output.name}.", suffix=".tmp", dir=output.parent
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as target:
            target.write(payload)
            target.flush()
            os.fsync(target.fileno())
        os.replace(temporary, output)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise
    return len(selected_keys), len(replacement_by_key)


def main() -> None:
    """Perform one exact snapshot-row replacement."""

    arguments = parse_arguments()
    removed, inserted = replace(
        arguments.base,
        arguments.replacement,
        arguments.output,
        parse_selectors(arguments.selector),
        arguments.key_field,
        parse_selectors(arguments.require_field),
        arguments.require_positive_finite_field,
        arguments.expected_replacement_count,
        arguments.expected_output_row_count,
        [parse_selectors(group) for group in arguments.selector_group],
    )
    print(f"Replaced {removed} rows with {inserted} rows in {arguments.output}")


if __name__ == "__main__":
    main()
