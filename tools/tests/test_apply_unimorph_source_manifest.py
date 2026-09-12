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
"""Tests for manifest-driven model metadata and notice generation."""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "apply-unimorph-source-manifest.py"
SPEC = importlib.util.spec_from_file_location("apply_unimorph_source_manifest", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ApplyUnimorphSourceManifestTest(unittest.TestCase):
    """Covers exact revision enforcement and auditable notice fields."""

    def test_build_update_replaces_legacy_sentinel_and_marks_manifest_management(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            build = Path(directory) / "build.gradle"
            build.write_text(
                """radixorModel {
    modelId = 'test-default'
    defaultModel = true
    sourceVersion = 'unavailable'
    sourceRevision = 'not-recorded-in-legacy-import'
    sourceRevisionStatus = 'not-recorded-in-legacy-import'
    sourceLicense = 'CC-BY-SA-3.0'
    sourceLicenseUri = 'https://creativecommons.org/licenses/by-sa/3.0/'
    sourceAttribution = 'old'
    sourceVerificationDate = '2026-09-10'
    transformationsSummary = 'old'
}
""",
                encoding="utf-8",
            )
            row = self.row()
            MODULE.update_build(build, row)
            updated = build.read_text(encoding="utf-8")
            self.assertIn("manifestManaged = true", updated)
            self.assertIn("sourceRevision = '" + "1" * 40 + "'", updated)
            self.assertNotIn("not-recorded-in-legacy-import", updated)

    def test_notice_preserves_literal_origin_attribution_and_audit_counts(self) -> None:
        notice = MODULE.render_notice(self.row())
        self.assertIn("Source origin: TBA", notice)
        self.assertIn("Attribution: UniMorph; Named Annotator", notice)
        self.assertIn("Import rows: source=2; accepted=1; rejected=1", notice)
        self.assertIn("Selected upstream data:\n- test [lemma-form-features]", notice)
        self.assertIn("cross-source-assignments=1", notice)
        self.assertIn("Deterministic GZip SHA-256: " + "4" * 64, notice)

    def test_lgpllr_notice_records_full_license_and_legible_source_packaging(self) -> None:
        row = self.row()
        row["license_id"] = "LGPLLR"
        row["license_uri"] = "https://spdx.org/licenses/LGPLLR.html"
        notice = MODULE.render_notice(row)
        self.assertIn("Packaged canonical license text: META-INF/LICENSES/LGPLLR.txt", notice)
        self.assertIn(MODULE.LGPLLR_TEXT_SOURCE, notice)
        self.assertIn(MODULE.LGPLLR_TEXT_SHA256, notice)
        self.assertIn("complete machine-readable legible\ndictionary form", notice)

    @staticmethod
    def row() -> dict[str, str]:
        return {
            "model_id": "test-default",
            "revision": "1" * 40,
            "license_id": "CC-BY-SA-3.0",
            "license_uri": "https://creativecommons.org/licenses/by-sa/3.0/",
            "attribution": "UniMorph; Named Annotator",
            "repository": "https://github.com/unimorph/test",
            "source_origin": "TBA",
            "data_sources": json.dumps([{
                "key": "primary",
                "path": "test",
                "local_name": "data",
                "sha256": "2" * 64,
                "schema": "lemma-form-features",
                "exceptions": "none",
            }]),
            "excluded_source_audit": "[]",
            "evidence_paths": "README.md:evidence-00-README.md",
            "evidence_sha256": "evidence-00-README.md:" + "3" * 64,
            "policy_version": "radixor-unimorph-import-v1",
            "projection_semantics": "Direct lemma/form projection.",
            "conflict_semantics": "Retain all assignments.",
            "source_rows": "2",
            "accepted_rows": "1",
            "rejected_rows": "1",
            "rejection_counts": "blank-row=1",
            "source_audit": json.dumps([{
                "path": "test",
                "source_rows": 2,
                "accepted_rows": 1,
                "rejected_rows": 1,
                "added_groups": 1,
                "added_distinct_surfaces": 1,
                "added_source_pairs": 1,
                "added_output_forms": 1,
                "added_changed_pairs": 0,
                "intrinsic_ambiguous_surfaces": 0,
                "rejection_counts": {"blank-row": 1},
            }]),
            "cross_source_conflict_assignments": "1",
            "cross_source_conflicting_surfaces": "1",
            "intrinsic_ambiguous_surfaces": "0",
            "output_groups": "1",
            "output_forms": "1",
            "output_sha256": "4" * 64,
        }


if __name__ == "__main__":
    unittest.main()
