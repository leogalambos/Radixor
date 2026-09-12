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

"""Tests exact coverage and protocol validation for prohibited reports."""

from __future__ import annotations

import csv
import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "validate-prohibited-benchmark-reports.py"
SPEC = importlib.util.spec_from_file_location("validate_prohibited_benchmark_reports", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ProhibitedReportValidationTest(unittest.TestCase):
    """Binds speed rows to the exact private cohort and canonical JMH protocol."""

    def write_speed(self, directory: Path, model_ids: tuple[str, ...], *, threads: str = "1",
                    samples: str = "15") -> Path:
        """Write a minimal JMH-compatible speed report."""

        path = directory / "speed.csv"
        fields = (
            "Benchmark", "Mode", "Threads", "Samples", "Score", "Score Error (99.9%)",
            "Unit", "Param: modelId",
        )
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields)
            writer.writeheader()
            for model_id in model_ids:
                writer.writerow({
                    "Benchmark": "org.egothor.stemmer.benchmark.ProhibitedModelStemmerBenchmark.radixor",
                    "Mode": "avgt", "Threads": threads, "Samples": samples,
                    "Score": "100.0", "Score Error (99.9%)": "2.0", "Unit": "ns/op",
                    "Param: modelId": model_id,
                })
        return path

    def test_accepts_exact_coverage_and_rejects_protocol_or_cohort_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            expected = ("aa-default", "bb-default")
            MODULE.validate_speed(self.write_speed(directory, expected), expected)
            with self.assertRaisesRegex(ValueError, "one thread"):
                MODULE.validate_speed(self.write_speed(directory, expected, threads="2"), expected)
            with self.assertRaisesRegex(ValueError, "15 samples"):
                MODULE.validate_speed(self.write_speed(directory, expected, samples="21"), expected)
            with self.assertRaisesRegex(ValueError, "coverage differs"):
                MODULE.validate_speed(self.write_speed(directory, ("aa-default",)), expected)

    def test_snowball_reports_are_bound_to_exact_catalog_and_protocol(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            cases = {"eus-default": "SNOWBALL_BASQUE_DIRECT"}
            catalog = directory / "comparators.tsv"
            catalog.write_text(
                "model_id\tlanguage\tdisplay_language\tcandidate\tspeed_benchmark\n"
                "eus-default\tEUS\tBasque\tSNOWBALL_BASQUE_DIRECT\t"
                "ProhibitedModelStemmerBenchmark.snowballDirect\n",
                encoding="utf-8",
            )
            self.assertEqual(cases, MODULE.comparator_cases(catalog, ("eus-default", "fy-nl-default")))

            accuracy = directory / "accuracy.csv"
            with accuracy.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=(
                    "Candidate", "Dictionary model ID", "Total tokens", "Already-root tokens",
                    "Changed tokens", "All exact matches", "Changed exact matches",
                    "Root preserved matches",
                ))
                writer.writeheader()
                writer.writerow({
                    "Candidate": "SNOWBALL_BASQUE_DIRECT", "Dictionary model ID": "eus-default",
                    "Total tokens": "10", "Already-root tokens": "2", "Changed tokens": "8",
                    "All exact matches": "5", "Changed exact matches": "4",
                    "Root preserved matches": "1",
                })
            MODULE.validate_snowball_accuracy(accuracy, cases)

            quality = directory / "quality.csv"
            with quality.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(
                    stream,
                    fieldnames=("Stemmer", "Dictionary model ID", "Dictionary mode", "Output policy"),
                )
                writer.writeheader()
                for mode in ("ALL_WORDS", "LOWERCASE_GROUPS_ONLY"):
                    writer.writerow({
                        "Stemmer": "SNOWBALL_BASQUE_DIRECT",
                        "Dictionary model ID": "eus-default",
                        "Dictionary mode": mode,
                        "Output policy": "PRIMARY_OUTPUT",
                    })
            MODULE.validate_snowball_quality(quality, cases)

            speed = self.write_speed(directory, ("eus-default",))
            text = speed.read_text(encoding="utf-8").replace(
                "ProhibitedModelStemmerBenchmark.radixor",
                "ProhibitedModelStemmerBenchmark.snowballDirect",
            )
            speed.write_text(text, encoding="utf-8")
            MODULE.validate_snowball_speed(speed, cases)

            speed.write_text(text.replace(",1,15,", ",2,15,"), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "one thread"):
                MODULE.validate_snowball_speed(speed, cases)


if __name__ == "__main__":
    unittest.main()
