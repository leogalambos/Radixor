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

"""Focused tests for active generalization documentation publication."""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "update-generalization-documentation.py"
SPEC = importlib.util.spec_from_file_location("update_generalization_documentation", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class LanguagePageIndexTest(unittest.TestCase):
    """Verifies that private benchmark pages never enter active publication."""

    def test_prohibited_table_is_excluded_from_active_languages(self) -> None:
        index = """# Languages

| Language | Java enum | Model ID | Relative dictionary size | Availability | Benchmark page |
| --- | --- | --- | --- | --- | --- |
| Active | `AA` | `aa-default` | ★☆☆☆☆ | Individual artifact | [Active](aa.md) |

<!-- PROHIBITED-BENCHMARK-MODELS:START -->
| Language | Private model ID | Relative dictionary size | Status | Benchmark page |
| --- | --- | --- | --- | --- | --- |
| Private | `bb-default` | ★☆☆☆☆ | Benchmark only | [Private](bb.md) |
<!-- PROHIBITED-BENCHMARK-MODELS:END -->
"""
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "index.md"
            path.write_text(index, encoding="utf-8")

            languages, pages = MODULE.read_language_pages(path)

        self.assertEqual({"AA": "Active"}, languages)
        self.assertEqual({"AA": "aa.md"}, pages)

    def test_duplicate_prohibited_sections_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "index.md"
            path.write_text(
                "\n".join((MODULE.PROHIBITED_SECTION_START,) * 2),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "duplicate prohibited-model"):
                MODULE.read_language_pages(path)


if __name__ == "__main__":
    unittest.main()
