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

"""Tests for deterministic publication of current Java benchmark reports."""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "update-benchmark-documentation.py"
SPEC = importlib.util.spec_from_file_location("update_benchmark_documentation", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def speed_row(score: float, error: float = 1_000.0) -> dict[str, str]:
    """Create the JMH fields consumed by the documentation updater."""
    return {
        "Score": str(score),
        "Score Error (99.9%)": str(error),
        "Unit": "ns/op",
    }


class BenchmarkDocumentationTest(unittest.TestCase):
    """Covers current-report selection and measured-corpus arithmetic."""

    def test_speed_uses_timing_token_denominator(self) -> None:
        radixor = MODULE.Key("example.persianRadixor", ())
        snowball = MODULE.Key(
            "example.snowballDirect", (("languageCaseName", "PERSIAN"),)
        )
        data = MODULE.JmhData(
            primary={
                radixor: speed_row(250_000.0),
                snowball: speed_row(500_000.0),
            },
            auxiliary={},
        )
        text = """## Speed

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `persianRadixor` | pending | pending | pending | pending | baseline |
| Official Snowball direct | `snowballDirect[PERSIAN]` | pending | pending | pending | pending | direct |

## Interpretation Notes
"""

        updated = MODULE.update_speed_table(text, data, 5_000, "FA_IR")

        self.assertIn("| 0.250 | 0.001 | 50.0 | 1.000 |", updated)
        self.assertIn("| 0.500 | 0.001 | 100.0 | 2.000 |", updated)
        self.assertEqual(
            updated,
            MODULE.update_speed_table(updated, data, 5_000, "FA_IR"),
        )

    def test_language_parameter_resolves_ambiguous_method(self) -> None:
        czech = MODULE.Key(
            "example.luceneHunspellStemFilter",
            (("languageCaseName", "CZECH"),),
        )
        polish = MODULE.Key(
            "example.luceneHunspellStemFilter",
            (("languageCaseName", "POLISH"),),
        )
        data = MODULE.JmhData(
            primary={czech: speed_row(10.0), polish: speed_row(20.0)},
            auxiliary={},
        )

        selected = MODULE.select_speed_key("luceneHunspellStemFilter", data, "CS_CZ")

        self.assertEqual(czech, selected)

    def test_partially_pending_speed_row_is_rejected(self) -> None:
        text = """## Speed

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `persianRadixor` | pending | 0.001 | pending | pending | baseline |

## Interpretation Notes
"""

        with self.assertRaisesRegex(ValueError, "Partially pending speed row"):
            MODULE.update_speed_table(
                text, MODULE.JmhData(primary={}, auxiliary={}), 5_000, "FA_IR"
            )

    def test_malformed_accuracy_row_is_rejected(self) -> None:
        text = """## Accuracy

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Official Snowball direct | 75.00% | 62.500% | 100.000% | direct |

## Speed
"""

        with self.assertRaisesRegex(ValueError, "Malformed accuracy row"):
            MODULE.update_accuracy_table(
                text, MODULE.JmhData(primary={}, auxiliary={}), "FA_IR", {}
            )

    def test_pending_accuracy_row_uses_current_auxiliary_counters(self) -> None:
        key = MODULE.Key(
            "example.exactRootAgreement",
            (("candidateName", "SNOWBALL_PERSIAN_DIRECT"),),
        )
        counters = {
            "correctMatches": 75.0,
            "evaluatedTokens": 100.0,
            "changedCorrectMatches": 50.0,
            "changedEvaluatedTokens": 80.0,
            "rootPreservedMatches": 20.0,
            "rootEvaluatedTokens": 20.0,
        }
        data = MODULE.JmhData(primary={}, auxiliary={key: counters})
        text = """## Accuracy

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Official Snowball direct | pending | pending | pending | direct |

## Speed
"""

        updated = MODULE.update_accuracy_table(text, data, "FA_IR", {})

        self.assertIn("| 75.000% | 62.500% | 100.000% |", updated)
        self.assertEqual(
            updated,
            MODULE.update_accuracy_table(updated, data, "FA_IR", {}),
        )


if __name__ == "__main__":
    unittest.main()
