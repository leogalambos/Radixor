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

"""Tests prohibited-model report validation and documentation rendering."""

from __future__ import annotations

import argparse
import importlib.util
import re
import sys
import tempfile
import unittest
from pathlib import Path

from jinja2 import DictLoader, Environment


SCRIPT = Path(__file__).resolve().parents[1] / "update-prohibited-benchmark-documentation.py"
SPEC = importlib.util.spec_from_file_location("update_prohibited_benchmark_documentation", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ProhibitedBenchmarkDocumentationTest(unittest.TestCase):
    """Covers ratings, warnings, zero denominators, and navigation status."""

    def test_landing_links_render_inside_container(self) -> None:
        landing = Path("docs/overrides/landing.html").read_text(encoding="utf-8")
        environment = Environment(
            loader=DictLoader({
                "base.html": "{% block container %}{% endblock %}",
                "landing.html": landing,
            }),
            autoescape=True,
        )
        rendered = environment.get_template("landing.html").render(base_url="")
        links = set(re.findall(
            r'href="/benchmarks/languages/([^/]+)/"', rendered
        ))
        expected = {
            path.stem for path in Path("docs/benchmarks/languages").glob("*.md")
            if path.name != "index.md"
        }

        self.assertEqual(expected, links)
        self.assertEqual(166, len(links))
        self.assertIn("ko-kr", links)
        ratings = re.findall(
            r'<span class="dictionary-rating" role="img" '
            r'aria-label="Relative dictionary size ([1-5]) of 5; '
            r'([0-9,]+) distinct usable word forms"[^>]*>([★☆]{5})</span>',
            rendered,
        )
        self.assertEqual(166, len(ratings))
        for stars_text, _count, glyphs in ratings:
            stars = int(stars_text)
            self.assertEqual("★" * stars + "☆" * (5 - stars), glyphs)

        prohibited = re.search(
            r'<!-- PROHIBITED-LANGUAGE-PAGES:START -->(.*?)'
            r'<!-- PROHIBITED-LANGUAGE-PAGES:END -->',
            rendered,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(prohibited)
        assert prohibited is not None
        block = prohibited.group(1)
        self.assertIn("23 benchmark-only dictionaries", block)
        self.assertIn("Not distributed or installable; license evidence unresolved", block)
        self.assertLess(
            block.index("23 benchmark-only dictionaries"),
            block.index('class="rx2-language-list"'),
        )
        css = Path("docs/assets/stylesheets/landing-v2.css").read_text(encoding="utf-8")
        self.assertIn(
            ".rx2-languages-strip{grid-template-columns:minmax(0,1fr);align-items:start}",
            css,
        )
        self.assertIn(".rx2-languages-strip .rx2-language-list{width:100%", css)

    def test_rendered_page_is_unambiguously_non_installable(self) -> None:
        model = MODULE.Model(
            "aa-default", "AA", "Example", "aa.md", "globe", "Historical language",
            12, 3, "1.0.0", "a" * 64, 2,
        )
        corpus = {
            "Model ID": "aa-default", "Total tokens": "12", "Already-root tokens": "12",
            "Changed tokens": "0", "Speed timing workload": "root-only tokens",
            "Speed timing tokens": "5000", "All exact matches": "12",
            "Changed exact matches": "0", "Root preserved matches": "12",
            "Command class": "PreserveCommand", "Command count": "12",
        }
        quality = [
            {
                "Dictionary mode": mode, "Over-stemming error pairs": "0",
                "Over-stemming possible pairs": "0", "Over-stemming percentage": "",
                "Under-stemming error pairs": "0", "Under-stemming possible pairs": "0",
                "Under-stemming percentage": "",
            }
            for mode in ("ALL_WORDS", "LOWERCASE_GROUPS_ONLY")
        ]
        generalization = []
        for requested in range(10, 101, 10):
            for seed in range(5):
                generalization.append(
                    {
                        "requested_percent": str(requested), "unseen_total": "0",
                        "unseen_correct": "0", "unseen_changed_total": "0",
                        "unseen_changed_correct": "0", "unseen_root_total": "0",
                        "unseen_root_correct": "0",
                    }
                )
        speed = {"Score": "1000000", "Score Error (99.9%)": "1000"}
        page = MODULE.render_page(
            model, ("https://example.invalid", "No license evidence"), [corpus], speed,
            quality, generalization, "2026-09-11", "4.3.0.dirty", 167,
        )
        self.assertIn("not distributed", page)
        self.assertIn("4.4.0", page)
        self.assertNotIn("dirty", page)
        self.assertIn("no Maven coordinates", page)
        self.assertIn("★★☆☆☆", page)
        self.assertIn("Changed exact | Root preserved", page)
        self.assertIn("| Private Radixor filesystem model | 100.000% | n/a | 100.000% |", page)
        self.assertGreaterEqual(page.count("n/a"), 3)
        self.assertNotIn("org.egothor:radixor-model-aa-default", page)

    def test_basque_page_renders_exact_snowball_accuracy_quality_and_speed(self) -> None:
        model = MODULE.Model(
            "eus-default", "EUS", "Basque", "eus.md", "ES", "Spain",
            10, 2, "1.0.0", "a" * 64, 1,
        )
        corpus = {
            "Model ID": "eus-default", "Total tokens": "10", "Already-root tokens": "2",
            "Changed tokens": "8", "Speed timing workload": "changed tokens",
            "Speed timing tokens": "5000", "All exact matches": "9",
            "Changed exact matches": "7", "Root preserved matches": "2",
            "Command class": "PatchCommand", "Command count": "10",
        }
        quality = [
            {
                "Stemmer": stemmer, "Dictionary mode": mode,
                "Over-stemming error pairs": "1", "Over-stemming possible pairs": "20",
                "Over-stemming percentage": "5", "Under-stemming error pairs": "2",
                "Under-stemming possible pairs": "10", "Under-stemming percentage": "20",
            }
            for stemmer in ("EUS_RADIXOR", "SNOWBALL_BASQUE_DIRECT")
            for mode in ("ALL_WORDS", "LOWERCASE_GROUPS_ONLY")
        ]
        generalization = [
            {
                "requested_percent": str(requested), "unseen_total": "1", "unseen_correct": "1",
                "unseen_changed_total": "1", "unseen_changed_correct": "1",
                "unseen_root_total": "0", "unseen_root_correct": "0",
            }
            for requested in range(10, 101, 10)
            for _seed in range(5)
        ]
        page = MODULE.render_page(
            model, ("https://example.invalid", "No evidence"), [corpus],
            {"Score": "1000000", "Score Error (99.9%)": "1000"}, quality[:2],
            generalization, "2026-09-11", "4.3.0.dirty", 167,
            {
                "model_id": "eus-default", "candidate": "SNOWBALL_BASQUE_DIRECT",
            },
            {
                "All exact matches": "4", "Total tokens": "10",
                "Changed exact matches": "3", "Changed tokens": "8",
                "Root preserved matches": "1", "Already-root tokens": "2",
            },
            quality[2:], {"Score": "500000", "Score Error (99.9%)": "500"},
        )
        self.assertEqual(4, page.count("Official Snowball 3.1.0 direct"))
        self.assertIn("| Official Snowball 3.1.0 direct | 40.000% | 37.500% | 50.000% |", page)
        self.assertIn("| Official Snowball 3.1.0 direct | 0.500 | 0.001 | 100.0 |", page)

    def test_star_assignment_uses_combined_population(self) -> None:
        models = [
            MODULE.Model("aa-default", "AA", "A", "aa.md", "globe", "A", 2, 1, "1", "a" * 64),
            MODULE.Model("bb-default", "BB", "B", "bb.md", "globe", "B", 6, 1, "1", "b" * 64),
        ]
        ranked, stars = MODULE.assign_stars(models, {"cc-default": 1, "dd-default": 3, "ee-default": 5})
        self.assertEqual({model.model_id: model.stars for model in ranked}, {"aa-default": 2, "bb-default": 5})
        self.assertEqual(stars["cc-default"], 1)
        self.assertEqual(stars["ee-default"], 4)

    def test_provenance_binds_date_release_sources_and_runtime_classpath(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            sources = []
            for relative in (
                "models/model-quarantine.properties",
                "models/prohibited-model-sources.sha256",
                "tools/prohibited-model-lifecycle.py",
                "tools/run-prohibited-model-benchmarks.sh",
                "reports/models.tsv",
            ):
                path = directory / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(relative, encoding="utf-8")
                sources.append(f"{MODULE.sha256(path)}  {path}")
            source_hashes = directory / "prohibited-source-inputs.sha256"
            source_hashes.write_text("\n".join(sources) + "\n", encoding="utf-8")
            runtime_manifest = directory / "runtime-classpath-content.sha256"
            runtime_manifest.write_text("a" * 64 + "  runtime.jar\n", encoding="utf-8")
            model_manifest = directory / "reports/models.tsv"
            quarantine_digest = next(
                line.split()[0]
                for line in sources
                if line.endswith("models/model-quarantine.properties")
            )
            source_manifest_digest = next(
                line.split()[0]
                for line in sources
                if line.endswith("models/prohibited-model-sources.sha256")
            )
            provenance = directory / "provenance.txt"
            provenance.write_text(
                "Benchmark class: prohibited documentation-only models\n"
                "Benchmark date: 2026-09-11\n"
                "Source identity: 4.3.0.dirty\n"
                f"Quarantine manifest SHA-256: {quarantine_digest}\n"
                f"Source hash manifest SHA-256: {source_manifest_digest}\n"
                f"Runtime classpath manifest SHA-256: {MODULE.sha256(runtime_manifest)}\n"
                "Protocol: 3 warmup iterations, 5 measurement iterations, 3 forks, 1 thread, 1 s iterations\n",
                encoding="utf-8",
            )
            arguments = argparse.Namespace(
                provenance=provenance, source_hashes=source_hashes,
                runtime_classpath=runtime_manifest, model_manifest=model_manifest,
                date="2026-09-11", release_version="4.3.0.dirty",
            )
            MODULE.validate_run_provenance(arguments)
            model_manifest.write_text("mutated", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "model manifest differs"):
                MODULE.validate_run_provenance(arguments)

    def test_active_rating_updates_language_page_not_index_reference(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            docs = Path(temporary)
            pages = docs / "benchmarks/languages"
            pages.mkdir(parents=True)
            original = MODULE.rating(1, 10)
            (pages / "aa.md").write_text(
                f"# A {original}\n\n| `aa-default` | identity |\n\nrelative to all active user-facing models\n",
                encoding="utf-8",
            )
            (pages / "index.md").write_text(
                f"| `aa-default` | {original} |\n",
                encoding="utf-8",
            )
            MODULE.update_active_ratings(
                docs, {"aa-default": 10}, {"aa-default": 4}, MODULE.Publication("update"),
            )
            self.assertIn("★★★★☆", (pages / "aa.md").read_text(encoding="utf-8"))
            self.assertIn("★☆☆☆☆", (pages / "index.md").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
