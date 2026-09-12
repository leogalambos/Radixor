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
"""Tests for the deterministic pinned Arabic model import."""

from __future__ import annotations

import gzip
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY / "tools" / "import-unimorph-arabic.py"
SPEC = importlib.util.spec_from_file_location("import_unimorph_arabic", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ImportUnimorphArabicTest(unittest.TestCase):
    """Covers merging, combining marks, filtering, and reproducible GZip."""

    def test_merges_both_sources_and_preserves_combining_marks(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "ara_atb"
            second = root / "ara_new"
            first.write_text("كَتَبَ\tيَكْتُبُ\tV\nكَتَبَ\tword-with-dash\tV\n", encoding="utf-8")
            second.write_text("كَتَبَ\tكُتِبَ\tV\nعِلْم\tعُلُوم\tN\n", encoding="utf-8")

            groups = MODULE.read_groups([first, second])
            content = MODULE.render(groups)
            first_output = root / "first.gz"
            second_output = root / "second.gz"
            MODULE.write_gzip(first_output, content)
            MODULE.write_gzip(second_output, content)

            self.assertEqual(first_output.read_bytes(), second_output.read_bytes())
            decoded = gzip.decompress(first_output.read_bytes()).decode("utf-8")
            self.assertIn("كَتَبَ\tكُتِبَ\tيَكْتُبُ", decoded)
            self.assertIn("عِلْم\tعُلُوم", decoded)
            self.assertNotIn("word-with-dash", decoded)

    def test_rejects_unpinned_source_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "ara_atb"
            source.write_text("unexpected", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                MODULE.verify_source(source, "ara_atb")

    def test_pins_both_datasets_and_upstream_license_evidence(self) -> None:
        self.assertEqual(
            {"ara_atb", "ara_new", "README.md"}, set(MODULE.SOURCES)
        )
        notice = (
            REPOSITORY / "models/ar-default/src/modelInput/NOTICE-model-data.txt"
        ).read_text(encoding="utf-8")
        self.assertIn("PATB/CalimaStar-derived", notice)
        self.assertIn("CC BY-SA 3.0", notice)
        for digest in MODULE.SOURCES.values():
            self.assertIn(digest, notice)


if __name__ == "__main__":
    unittest.main()
