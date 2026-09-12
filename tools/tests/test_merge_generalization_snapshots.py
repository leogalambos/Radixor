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
"""Tests the deterministic active generalization snapshot merge."""

from __future__ import annotations

import csv
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY / "tools" / "merge-generalization-snapshots.py"
SPEC = importlib.util.spec_from_file_location("merge_generalization", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class MergeGeneralizationSnapshotsTest(unittest.TestCase):
    """Validates exact coverage, disjointness, and mixed provenance."""

    def test_merges_archive_and_active_continuation_with_mixed_provenance(self) -> None:
        topology = MODULE.read_topology(REPOSITORY / "models/model-projects.properties")
        corpus = MODULE.read_corpus_models(
            REPOSITORY / "docs/benchmarks/data/java-benchmark-corpora-2026-09-11.csv"
        )
        archive_models = sorted(model for model, role in topology.items() if role == "default")
        continuation_models = sorted(model for model, role in topology.items() if role == "standalone")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "archive.csv"
            continuation = root / "continuation.csv"
            self._write_fixture(archive, archive_models, corpus, "a" * 40, "1" * 64)
            self._write_fixture(continuation, continuation_models, corpus, "b" * 40, "2" * 64)

            rows = MODULE.merge(
                archive, continuation,
                REPOSITORY / "docs/benchmarks/data/java-benchmark-corpora-2026-09-11.csv",
                REPOSITORY / "models/model-projects.properties",
            )

        self.assertEqual(7150, len(rows))
        self.assertEqual(2, len({row["source_revision"] for row in rows}))
        self.assertNotIn("pl-pl-polimorf", {row["model_id"] for row in rows})

    @staticmethod
    def _write_fixture(
        path: Path,
        models: list[str],
        corpus: dict[str, str],
        revision: str,
        generator: str,
    ) -> None:
        with path.open("w", newline="", encoding="utf-8") as target:
            writer = csv.DictWriter(target, fieldnames=MODULE.EXPECTED_HEADER, lineterminator="\n")
            writer.writeheader()
            for model_id in models:
                for seed in sorted(MODULE.EXPECTED_SEEDS, key=int):
                    for percentage in range(100, 0, -10):
                        row = {name: "0" for name in MODULE.EXPECTED_HEADER}
                        row.update({
                            "protocol_version": "radixor-generalization-v1",
                            "radixor_java_version": "fixture",
                            "source_revision": revision,
                            "source_state": "clean",
                            "generator_sha256": generator,
                            "language": corpus[model_id],
                            "model_id": model_id,
                            "model_version": "fixture",
                            "model_sha256": "3" * 64,
                            "seed": seed,
                            "requested_percent": str(percentage),
                        })
                        writer.writerow(row)


if __name__ == "__main__":
    unittest.main()
