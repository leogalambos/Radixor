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
"""Regression tests for topology-derived exact-root candidate coverage."""

from __future__ import annotations

import csv
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "validate-accuracy-candidate-coverage.py"
SPEC = importlib.util.spec_from_file_location("validate_accuracy_candidate_coverage", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class AccuracyCandidateCoverageTest(unittest.TestCase):
    """Covers active Snowball derivation and complete counter validation."""

    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        root = Path(self.temporary_directory.name)
        self.topology = root / "topology.properties"
        self.catalog = root / "catalog.csv"
        self.report = root / "accuracy.csv"
        self.topology.write_text(
            "ar-default=standalone\nca-es-default=standalone\n"
            "pl-pl-polimorf=optional\n",
            encoding="utf-8",
        )
        with self.catalog.open("w", encoding="utf-8", newline="") as target:
            writer = csv.DictWriter(target, fieldnames=MODULE.CATALOG_HEADER)
            writer.writeheader()
            writer.writerows([
                self.catalog_row("ARABIC", "AR", "ar-default"),
                self.catalog_row("CATALAN", "CA_ES", "ca-es-default"),
            ])

    @staticmethod
    def catalog_row(case: str, language: str, model_id: str) -> dict[str, str]:
        return {
            "Case": case,
            "Language": language,
            "Model ID": model_id,
            "Display language": case.title(),
            "Direct available": "true",
            "Lucene available": "true",
            "Homepage chart": "true",
            "Homepage aggregate": "true",
        }

    def write_report(self, candidates: set[str], omitted_counter: str | None = None) -> None:
        with self.report.open("w", encoding="utf-8", newline="") as target:
            writer = csv.DictWriter(
                target,
                fieldnames=["Benchmark", "Param: candidateName"],
            )
            writer.writeheader()
            for candidate in sorted(candidates):
                for counter in sorted(MODULE.COUNTERS):
                    if counter == omitted_counter and candidate == "SNOWBALL_CATALAN_DIRECT":
                        continue
                    writer.writerow({
                        "Benchmark": (
                            "org.egothor.stemmer.benchmark."
                            "StemmerComparisonBenchmarkQuality.exactRootAgreement:"
                            + counter
                        ),
                        "Param: candidateName": candidate,
                    })

    def test_accepts_catalog_and_optional_candidates_without_basque(self) -> None:
        active = MODULE.read_topology(self.topology)
        candidates = MODULE.expected_candidates(self.catalog, active)
        self.assertNotIn("SNOWBALL_BASQUE_DIRECT", candidates)
        self.assertEqual(
            {
                "SNOWBALL_ARABIC_DIRECT",
                "SNOWBALL_CATALAN_DIRECT",
                *MODULE.POLIMORF_CANDIDATES,
            },
            candidates,
        )
        self.write_report(candidates)
        MODULE.validate_report(self.report, candidates)

    def test_rejects_catalog_model_outside_active_topology(self) -> None:
        with self.catalog.open("a", encoding="utf-8", newline="") as target:
            writer = csv.DictWriter(target, fieldnames=MODULE.CATALOG_HEADER)
            writer.writerow(self.catalog_row("BASQUE", "EUS", "eus-default"))

        with self.assertRaisesRegex(ValueError, "inactive model eus-default"):
            MODULE.expected_candidates(self.catalog, MODULE.read_topology(self.topology))

    def test_rejects_missing_counter_for_active_candidate(self) -> None:
        candidates = MODULE.expected_candidates(
            self.catalog, MODULE.read_topology(self.topology)
        )
        self.write_report(candidates, omitted_counter="changedEvaluatedTokens")

        with self.assertRaisesRegex(ValueError, "SNOWBALL_CATALAN_DIRECT"):
            MODULE.validate_report(self.report, candidates)

    def test_runner_verifies_java_catalog_and_uses_dynamic_validator(self) -> None:
        runner = Path("tools/run-published-accuracy-benchmarks.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn("SnowballLanguageCatalogApplication", runner)
        self.assertIn("validate-accuracy-candidate-coverage.py", runner)
        self.assertNotIn("SNOWBALL_BASQUE_DIRECT", runner)


if __name__ == "__main__":
    unittest.main()
