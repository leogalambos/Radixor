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
"""Tests for the strict, deterministic offline UniMorph model importer."""

from __future__ import annotations

import gzip
import hashlib
import importlib.util
import json
import sys
import tempfile
import unittest
import unicodedata
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "import-unimorph-models.py"
SPEC = importlib.util.spec_from_file_location("import_unimorph_models", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ImportUnimorphModelsTest(unittest.TestCase):
    """Covers structural Unicode policy, schemas, provenance, and reproducibility."""

    def parse(self, content: bytes, schema: str = MODULE.NORMAL_SCHEMA,
              exceptions: str = MODULE.NO_EXCEPTIONS):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "data"
            source.write_bytes(content)
            with source.open("rb") as stream:
                return MODULE.parse_source(
                    stream, hashlib.sha256(content).hexdigest(), schema, exceptions
                )

    def test_structural_policy_preserves_orthography_and_rejects_runtime_hazards(self) -> None:
        content = (
            "Root\tmark\u0301\tN\n"
            "join\u200c\tjoin\u200d\tN\n"
            "o'neil\tco-op\tN\n"
            "colon:a\tslash/a\tN\n"
            "bad space\tform\tN\n"
            "nbsp\u00a0bad\tform\tN\n"
            "hash\tbad#form\tN\n"
            "double\tbad//form\tN\n"
            "control\tbad\u0085form\tN\n"
            "\n"
        ).encode("utf-8")
        parsed = self.parse(content)

        self.assertEqual(10, parsed.source_rows)
        self.assertEqual(4, parsed.accepted_rows)
        self.assertEqual(6, sum(parsed.rejected.values()))
        self.assertIn(unicodedata.normalize("NFC", "mark\u0301"), parsed.groups["root"])
        self.assertIn("join\u200d", parsed.groups["join\u200c"])
        self.assertIn("co-op", parsed.groups["o'neil"])
        self.assertIn("slash/a", parsed.groups["colon:a"])
        self.assertEqual(2, parsed.rejected["form-runtime-comment-marker"])

    def test_declared_ckt_and_sdh_schema_exceptions_are_exact(self) -> None:
        ckt_lines = ["lemma\tform\tN\n"] * 66
        ckt_lines.append("malformed pair  malformed pair\tV;SG  \n")
        content = "".join(ckt_lines).encode("utf-8")
        parsed = self.parse(
            content, MODULE.NORMAL_SCHEMA, MODULE.CKT_EXCEPTION
        )
        self.assertEqual(67, parsed.source_rows)
        self.assertEqual(66, parsed.accepted_rows)
        self.assertEqual(1, parsed.rejected["declared-ckt-malformed-row"])
        with self.assertRaisesRegex(ValueError, "Unexpected 2-column"):
            self.parse(content, MODULE.NORMAL_SCHEMA, MODULE.NO_EXCEPTIONS)

        shifted = "\tLemma\tForm\tNFIN;V\n".encode("utf-8")
        parsed = self.parse(shifted, MODULE.SDH_SCHEMA)
        self.assertEqual({"form"}, parsed.groups["lemma"])
        with self.assertRaisesRegex(ValueError, "expected empty"):
            self.parse("not-empty\tLemma\tForm\tN\n".encode("utf-8"), MODULE.SDH_SCHEMA)

    def test_rendered_gzip_is_byte_identical_and_tracks_identity_groups(self) -> None:
        groups = {"root": {"root", "variant"}, "identity": {"identity"}}
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first.gz"
            second = Path(directory) / "second.gz"
            first_counts = MODULE.render_gzip(first, "test-default", "1" * 40, groups)
            second_counts = MODULE.render_gzip(second, "test-default", "1" * 40, groups)

            self.assertEqual((2, 3), first_counts)
            self.assertEqual(first_counts, second_counts)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            decoded = gzip.decompress(first.read_bytes()).decode("utf-8")
            self.assertIn("identity\n", decoded)
            self.assertIn("root\tvariant\n", decoded)

    def test_multiple_declared_sources_preserve_conflicts_and_four_column_schema(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            model = root / "test-default"
            model.mkdir()
            primary = b"root\tshared\tV\nidentity\tidentity\tN\n"
            auxiliary = b"other\tshared\tV\tseg\nother\tnew\tV\tseg\n"
            (model / "data").write_bytes(primary)
            (model / "data-01").write_bytes(auxiliary)
            row = {
                "model_id": "test-default",
                "data_sources": json.dumps([
                    {
                        "key": "primary",
                        "path": "test",
                        "local_name": "data",
                        "sha256": hashlib.sha256(primary).hexdigest(),
                        "schema": MODULE.NORMAL_SCHEMA,
                        "exceptions": MODULE.NO_EXCEPTIONS,
                    },
                    {
                        "key": "auxiliary-01",
                        "path": "test.segmentations",
                        "local_name": "data-01",
                        "sha256": hashlib.sha256(auxiliary).hexdigest(),
                        "schema": MODULE.SEGMENTATION_SCHEMA,
                        "exceptions": MODULE.NO_EXCEPTIONS,
                    },
                ]),
            }

            combined = MODULE.combine_sources(row, root)

            self.assertEqual({"shared", "new"}, combined.groups["other"])
            self.assertEqual(1, combined.cross_source_conflict_assignments)
            self.assertEqual(1, combined.cross_source_conflicting_surfaces)
            audit = json.loads(combined.source_audit)
            self.assertEqual(1, audit[1]["added_groups"])
            self.assertEqual(3, audit[1]["added_output_forms"])

    def test_manifest_rejects_unknown_license_and_optional_or_baseline_models(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            topology = Path(directory) / "topology.properties"
            topology.write_text(
                "new-default=standalone\nold-default=default\noptional=optional\n",
                encoding="utf-8",
            )
            row = {
                "model_id": "new-default",
                "policy_version": MODULE.POLICY_VERSION,
                "license_id": "UNKNOWN",
                "license_uri": "https://example.test/license",
                "license_scope": "selected data",
                "source_origin": "fixture",
                "attribution": "upstream",
                "projection_semantics": "direct",
                "conflict_semantics": "retain",
                "data_sources": json.dumps([{
                    "key": "primary",
                    "path": "new",
                    "local_name": "data",
                    "sha256": "2" * 64,
                    "schema": MODULE.NORMAL_SCHEMA,
                    "exceptions": MODULE.NO_EXCEPTIONS,
                }]),
                "excluded_source_audit": "[]",
                "source_audit": "[]",
                "lowercase": "true",
                "source_rows": "1",
                "accepted_rows": "1",
                "rejected_rows": "0",
                "cross_source_conflict_assignments": "0",
                "cross_source_conflicting_surfaces": "0",
                "intrinsic_ambiguous_surfaces": "0",
                "output_groups": "1",
                "output_forms": "1",
                "revision": "1" * 40,
                "output_sha256": "3" * 64,
                "evidence_paths": "README.md:evidence-00-README.md",
                "evidence_sha256": "evidence-00-README.md:" + "4" * 64,
            }
            with self.assertRaisesRegex(ValueError, "Unsupported or unknown license"):
                MODULE.validate_manifest([row], topology)
            row["license_id"] = "CC-BY-SA-3.0"
            row["model_id"] = "optional"
            with self.assertRaisesRegex(ValueError, "does not match"):
                MODULE.validate_manifest([row], topology)

    def test_checked_in_auxiliary_audit_pins_paths_hashes_deltas_and_conflicts(self) -> None:
        import csv

        manifest = Path("models/unimorph-sources.csv")
        with manifest.open(encoding="utf-8", newline="") as source:
            rows = {row["model_id"]: row for row in csv.DictReader(source)}
        expected = {
            "be-by-default": ([
                ("bel.args", "8be3aecfec1266cc022e2759a0ba533506bf6f0b0e6d83b8912761af8121119e"),
            ], (0, 8633, 9383, 8712, 8712, 9, 9)),
            "ca-es-default": ([
                ("cat.segmentations", "1d672c2e11e5d6d17667a7ddc02749ee1da2d91d43187abe02479a61108e9637"),
            ], (13629, 67569, 70430, 71255, 57626, 964, 935)),
            "grc-default": ([
                ("grc.args", "049336a4e14077eae796466e57ab1b8d87723613ec21997761a2df8d3e9ff98e"),
            ], (0, 12168, 12572, 12271, 12271, 28, 28)),
            "hy-am-default": ([
                ("hye.args", "003b12b42ccf28d4569aee38f8d0db53a45fdf1a79ea1a23d4886479794ab6e5"),
            ], (44, 2706, 2914, 2917, 2873, 205, 202)),
            "kk-kz-default": ([
                ("kaz.noun.tsv", "6ba352297a03423fdd21b3a1875d230c0f3c4153d5e33d1e5dcd37980efc0579"),
                ("kaz.sm", "af873ae1660456bcdf98846e3f82c47d3080a4412c15f5f48e40a8c38366b3b4"),
            ], (1718, 34866, 34818, 34912, 33194, 7, 7)),
            "swc-default": ([
                ("swc.sm", "58ffd2f4d68d443ce89554c1a5827514e68348e7743a0716700f26a47b892f24"),
            ], (83, 4421, 4338, 4421, 4338, 0, 0)),
            "uz-uz-default": ([
                ("uzb_verbs", "6ba66de9d7d44d6450480331337af8b700f6bf81fc53d467727304e7bfde8d9a"),
            ], (280, 8755, 8478, 8758, 8478, 0, 0)),
        }
        for model_id, (expected_sources, expected_metrics) in expected.items():
            sources = json.loads(rows[model_id]["data_sources"])[1:]
            self.assertEqual(
                expected_sources,
                [(source["path"], source["sha256"]) for source in sources],
            )
            audits = json.loads(rows[model_id]["source_audit"])[1:]
            actual_metrics = (
                sum(audit["added_groups"] for audit in audits),
                sum(audit["added_distinct_surfaces"] for audit in audits),
                sum(audit["added_source_pairs"] for audit in audits),
                sum(audit["added_output_forms"] for audit in audits),
                sum(audit["added_changed_pairs"] for audit in audits),
                int(rows[model_id]["cross_source_conflict_assignments"]),
                int(rows[model_id]["cross_source_conflicting_surfaces"]),
            )
            self.assertEqual(expected_metrics, actual_metrics, model_id)

        zulu_exclusions = json.loads(rows["zu-za-default"]["excluded_source_audit"])
        self.assertIn(
            ("zul.sm", "zulu-sm-conflict"),
            [(item["path"], item["reason"]) for item in zulu_exclusions],
        )
        self.assertEqual(
            24,
            len(json.loads(rows["krl-default"]["excluded_source_audit"])),
        )


if __name__ == "__main__":
    unittest.main()
