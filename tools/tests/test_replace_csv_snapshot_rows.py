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
"""Tests for exact byte-preserving CSV snapshot row replacement."""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "replace-csv-snapshot-rows.py"
SPEC = importlib.util.spec_from_file_location("replace_csv_snapshot_rows", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ReplaceCsvSnapshotRowsTest(unittest.TestCase):
    """Ensures exact selection and preservation of unselected records."""

    def test_replaces_only_selected_rows(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base.csv"
            replacement = root / "replacement.csv"
            output = root / "output.csv"
            base.write_text('Language,Value\r\n"AR","old"\r\n"EN","same"\r\n', encoding="utf-8")
            replacement.write_text('Language,Value\r\n"AR","new"\r\n', encoding="utf-8")

            removed, inserted = MODULE.replace(
                base, [replacement], output, [("Language", "AR")], ["Language"]
            )

            self.assertEqual((1, 1), (removed, inserted))
            with output.open(encoding="utf-8", newline="") as source:
                actual = source.read()
            self.assertEqual(
                'Language,Value\r\n"AR","new"\r\n"EN","same"\r\n', actual
            )

    def test_composite_key_prevents_finnish_candidate_collision(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base.csv"
            replacement = root / "replacement.csv"
            output = root / "output.csv"
            base.write_text(
                'Language,Candidate,Value\n"FI","RADIXOR","old-r"\n'
                '"FI","SNOWBALL","old-s"\n"EN","RADIXOR","byte-identical"\n',
                encoding="utf-8",
            )
            replacement.write_text(
                'Language,Candidate,Value\n"FI","RADIXOR","new-r"\n'
                '"FI","SNOWBALL","new-s"\n',
                encoding="utf-8",
            )

            self.assertEqual(
                (2, 2),
                MODULE.replace(
                    base, [replacement], output, [("Language", "FI")],
                    ["Language", "Candidate"],
                ),
            )
            self.assertEqual(
                'Language,Candidate,Value\n"FI","RADIXOR","new-r"\n'
                '"FI","SNOWBALL","new-s"\n"EN","RADIXOR","byte-identical"\n',
                output.read_text(encoding="utf-8"),
            )
            with self.assertRaisesRegex(ValueError, "Duplicate composite key"):
                MODULE.replace(
                    base, [replacement], output, [("Language", "FI")], ["Language"]
                )

    def test_rejects_missing_extra_and_duplicate_replacement_keys(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base.csv"
            output = root / "output.csv"
            base.write_text(
                "Language,Candidate,Value\nFI,A,old-a\nFI,B,old-b\n", encoding="utf-8"
            )
            fixtures = {
                "missing": "Language,Candidate,Value\nFI,A,new-a\n",
                "extra": "Language,Candidate,Value\nFI,A,new-a\nFI,B,new-b\nFI,C,new-c\n",
                "duplicate": "Language,Candidate,Value\nFI,A,new-a\nFI,A,newer-a\n",
            }
            for name, content in fixtures.items():
                replacement = root / f"{name}.csv"
                replacement.write_text(content, encoding="utf-8")
                with self.subTest(name=name), self.assertRaises(ValueError):
                    MODULE.replace(
                        base, [replacement], output, [("Language", "FI")],
                        ["Language", "Candidate"],
                    )

    def test_rejects_header_that_omits_base_provenance_column(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base.csv"
            replacement = root / "replacement.csv"
            output = root / "output.csv"
            base.write_text(
                "Language,Value,Provenance\nFI,old,pinned\n", encoding="utf-8"
            )
            replacement.write_text("Language,Value\nFI,new\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "header differs"):
                MODULE.replace(
                    base, [replacement], output, [("Language", "FI")], ["Language"]
                )
            self.assertFalse(output.exists())

    def test_failed_atomic_replace_preserves_existing_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base.csv"
            replacement = root / "replacement.csv"
            output = root / "output.csv"
            base.write_text("Language,Value\nFI,old\n", encoding="utf-8")
            replacement.write_text("Language,Value\nFI,new\n", encoding="utf-8")
            output.write_bytes(b"existing-output\n")

            with mock.patch.object(MODULE.os, "replace", side_effect=OSError("injected")):
                with self.assertRaisesRegex(OSError, "injected"):
                    MODULE.replace(
                        base, [replacement], output, [("Language", "FI")], ["Language"]
                    )

            self.assertEqual(b"existing-output\n", output.read_bytes())
            self.assertEqual([], list(root.glob(".output.csv.*.tmp")))

    def test_validates_measurement_guards_before_writing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base.csv"
            replacement = root / "replacement.csv"
            output = root / "output.csv"
            header = "Language,Samples,Score,Error\n"
            base.write_text(header + "FI,15,1.0,0.1\n", encoding="utf-8")
            replacement.write_text(header + "FI,15,2.0,0.2\n", encoding="utf-8")

            self.assertEqual(
                (1, 1),
                MODULE.replace(
                    base,
                    [replacement],
                    output,
                    [("Language", "FI")],
                    ["Language"],
                    [("Samples", "15")],
                    ["Score", "Error"],
                    1,
                    1,
                ),
            )
            replacement.write_text(header + "FI,15,nan,0.2\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "positive and finite"):
                MODULE.replace(
                    base,
                    [replacement],
                    output,
                    [("Language", "FI")],
                    ["Language"],
                    [("Samples", "15")],
                    ["Score", "Error"],
                    1,
                    1,
                )

    def test_selector_groups_require_all_fields_and_combine_as_alternatives(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base.csv"
            replacement = root / "replacement.csv"
            output = root / "output.csv"
            base.write_text(
                "Benchmark,Language,Value\nR,FI,old-r\nS,FI,old-s\nH,FI,untouched\n",
                encoding="utf-8",
            )
            replacement.write_text(
                "Benchmark,Language,Value\nR,FI,new-r\nS,FI,new-s\n",
                encoding="utf-8",
            )

            self.assertEqual(
                (2, 2),
                MODULE.replace(
                    base,
                    [replacement],
                    output,
                    [],
                    ["Benchmark", "Language"],
                    selector_groups=[
                        [("Benchmark", "R"), ("Language", "FI")],
                        [("Benchmark", "S"), ("Language", "FI")],
                    ],
                ),
            )
            self.assertIn("H,FI,untouched", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
