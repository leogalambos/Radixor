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
"""Tests for reviewed-license and literal-source manifest metadata."""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "build-unimorph-source-manifest.py"
SPEC = importlib.util.spec_from_file_location("build_unimorph_source_manifest", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class BuildUnimorphSourceManifestTest(unittest.TestCase):
    """Covers exact exceptional licenses and non-invented source identities."""

    def test_exceptional_licenses_require_matching_repository_evidence(self) -> None:
        self.assertEqual(
            ("CC-BY-4.0", "https://creativecommons.org/licenses/by/4.0/"),
            MODULE.verified_license("ja-jp-default", "License: CC BY 4.0"),
        )
        self.assertEqual(
            ("LGPLLR", "https://spdx.org/licenses/LGPLLR.html"),
            MODULE.verified_license("klr-default", "License: LGPLLR"),
        )
        with self.assertRaisesRegex(ValueError, "does not state reviewed license"):
            MODULE.verified_license("hsi-default", "License: CC BY-SA 3.0")

    def test_ceb_and_evn_keep_literal_tba_origin_and_named_annotator(self) -> None:
        self.assertEqual("TBA", MODULE.source_origin("ceb-default", "Source: something else"))
        self.assertEqual("TBA", MODULE.source_origin("evn-default", "Source: something else"))
        self.assertEqual("UniMorph; Ran Zmigrod", MODULE.attribution("ceb-default", ""))
        self.assertEqual("UniMorph; Elena Klyachko", MODULE.attribution("evn-default", ""))

    def test_unknown_origin_is_explicit_instead_of_invented(self) -> None:
        self.assertEqual(
            "Not stated in pinned repository evidence",
            MODULE.source_origin("test-default", "# Language\nNo source here\n"),
        )

    def test_selected_auxiliary_schemas_and_projection_are_explicit(self) -> None:
        self.assertEqual(
            ("lemma-form-features-segmentation", "none"),
            MODULE.source_schema(
                "ca-es-default", "auxiliary-01", "cat.segmentations"
            ),
        )
        self.assertIn("UM4 argument projection", MODULE.PROJECTION_SEMANTICS["be-by-default"])


if __name__ == "__main__":
    unittest.main()
