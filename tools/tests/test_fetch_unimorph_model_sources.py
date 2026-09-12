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
"""Tests for the network-isolated selection logic of the UniMorph fetcher."""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from unittest import mock
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "fetch-unimorph-model-sources.py"
SPEC = importlib.util.spec_from_file_location("fetch_unimorph_model_sources", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class FetchSelectionTest(unittest.TestCase):
    """Covers the authoritative topology boundary before any network access."""

    QUARANTINED = {
        "cy-gb-default",
        "eus-default",
        "fy-nl-default",
        "gd-gb-default",
        "hsb-default",
        "ka-ge-default",
        "kca-default",
        "ket-default",
        "kmr-default",
        "ko-kr-default",
        "oci-default",
        "om-et-default",
        "sah-default",
        "san-default",
        "sl-si-default",
        "slp-default",
        "tat-default",
        "tg-tj-default",
        "tk-tm-default",
        "tyv-default",
        "ur-pk-default",
        "vec-default",
        "vot-default",
    }

    def test_selects_exactly_supported_new_non_arabic_models(self) -> None:
        selected = MODULE.active_standalones(Path("models/model-projects.properties"))

        self.assertEqual(123, len(selected))
        self.assertIn("ar-default", selected)
        self.assertNotIn("cs-cz-default", selected)
        self.assertNotIn("eus-default", selected)
        self.assertEqual(122, len([model_id for model_id in selected if model_id != "ar-default"]))

    def test_topology_parser_ignores_non_standalone_roles(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            topology = Path(directory) / "model-projects.properties"
            topology.write_text(
                "aa-default=default\nbb-default=standalone\ncc-default=optional\n",
                encoding="utf-8",
            )

            self.assertEqual(["bb-default"], MODULE.active_standalones(topology))

    def test_auxiliary_selection_and_rejections_match_completed_audit(self) -> None:
        self.assertEqual(7, len(MODULE.AUXILIARY_DATA_PATHS))
        self.assertEqual(
            ("kaz.noun.tsv", "kaz.sm"),
            MODULE.AUXILIARY_DATA_PATHS["kk-kz-default"],
        )
        self.assertNotIn("zu-za-default", MODULE.AUXILIARY_DATA_PATHS)
        zulu = MODULE.excluded_source_audit(
            "zu-za-default", "zul", ["zul", "zul.sm"]
        )
        self.assertEqual("zulu-sm-conflict", zulu[0]["reason"])
        karelian = MODULE.excluded_source_audit(
            "krl-default", "krl", ["krl", "krl-a", "krl-b"]
        )
        self.assertEqual(2, len(karelian))
        self.assertTrue(all(item["reason"] == "karelian-dialect" for item in karelian))

    def test_quarantine_is_exact_and_disjoint_from_active_topology(self) -> None:
        quarantine = {
            line.split("=", 1)[0]
            for line in Path("models/model-quarantine.properties")
            .read_text(encoding="utf-8")
            .splitlines()
            if line and not line.startswith("#")
        }
        active = {
            line.split("=", 1)[0]
            for line in Path("models/model-projects.properties")
            .read_text(encoding="utf-8")
            .splitlines()
            if line and not line.startswith("#")
        }

        self.assertEqual(self.QUARANTINED, quarantine)
        self.assertTrue(quarantine.isdisjoint(active))

    def test_no_license_evidence_is_inventoried_and_fetch_continues(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            models = root / "models"
            topology = root / "topology.properties"
            cache = root / "cache"
            inventory = root / "inventory.csv"
            topology.write_text(
                "aa-default=standalone\nbb-default=standalone\n",
                encoding="utf-8",
            )
            revision = "1" * 40
            for model_id, repository_code in (("aa-default", "aa"), ("bb-default", "bb")):
                model = models / model_id
                model.mkdir(parents=True)
                (model / "build.gradle").write_text(
                    f"sourceRepository = 'https://github.com/unimorph/{repository_code}'\n"
                    f"sourceRevision = '{revision}'\n",
                    encoding="utf-8",
                )

            def fake_tree(repository: str, _revision: str, _clone: Path) -> list[str]:
                return ["aa"] if repository.endswith("/aa") else ["bb", "docs/README.md"]

            def fake_extract(
                _clone: Path, _revision: str, source_path: str, target: Path
            ) -> None:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(source_path + "\n", encoding="utf-8")

            arguments = [
                str(SCRIPT),
                "--topology", str(topology),
                "--models-root", str(models),
                "--cache", str(cache),
                "--inventory", str(inventory),
            ]
            with mock.patch.object(sys, "argv", arguments), mock.patch.object(
                MODULE, "repository_tree", side_effect=fake_tree
            ), mock.patch.object(MODULE, "extract_blob", side_effect=fake_extract):
                MODULE.main()

            import csv

            with inventory.open(encoding="utf-8", newline="") as source:
                rows = list(csv.DictReader(source))
            self.assertEqual(2, len(rows))
            self.assertEqual("unresolved", rows[0]["license_evidence_status"])
            self.assertIn("No repository-owned", rows[0]["license_evidence_reason"])
            self.assertEqual("present", rows[1]["license_evidence_status"])
            self.assertEqual("docs/README.md:evidence-00-README.md", rows[1]["evidence_paths"])
            sources = json.loads(rows[1]["data_sources"])
            self.assertEqual("bb", sources[0]["path"])


if __name__ == "__main__":
    unittest.main()
