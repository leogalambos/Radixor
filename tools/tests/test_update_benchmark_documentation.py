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
import re
import sys
import tempfile
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


def protocol_row(samples: int, threads: int = 1) -> dict[str, str]:
    """Create the concurrency fields used to infer a speed protocol."""

    row = speed_row(1_000.0)
    row["Samples"] = str(samples)
    row["Threads"] = str(threads)
    return row


class BenchmarkDocumentationTest(unittest.TestCase):
    """Covers current-report selection and measured-corpus arithmetic."""

    @staticmethod
    def model_info(index: int, distinct_forms: int) -> MODULE.ModelInfo:
        """Create compact model metadata for pure ranking tests."""

        return MODULE.ModelInfo(
            model_id=f"aa-{index:03d}-default",
            role="standalone",
            language=f"AA_{index:03d}",
            display_name=f"Language {index} — Test",
            language_name=f"Language {index}",
            version="1.0.0",
            right_to_left=False,
            dictionary_rows=1,
            distinct_forms=distinct_forms,
            sha256="0" * 64,
        )

    def test_ranked_star_tiers_have_approved_populations(self) -> None:
        infos = [self.model_info(index, index + 1) for index in range(144)]

        ranked = MODULE.assign_star_tiers(infos)

        self.assertEqual(
            [29, 29, 29, 29, 28],
            [sum(info.stars == star for info in ranked) for star in range(1, 6)],
        )
        self.assertEqual(1, ranked[0].stars)
        self.assertEqual(5, ranked[-1].stars)

    def test_rating_has_five_visible_positions_and_accessible_count(self) -> None:
        info = self.model_info(1, 10_095)
        info = MODULE.replace(info, stars=3)

        rating = MODULE.rating_markup(info)

        self.assertIn("★★★☆☆", rating)
        self.assertIn('role="img"', rating)
        self.assertIn("10,095 distinct usable word forms", rating)

    def test_page_rating_preserves_existing_rich_sections(self) -> None:
        info = MODULE.replace(self.model_info(1, 500), stars=2)
        text = """# Example Stemmer Benchmarks

Introduction.

<!-- DICTIONARY-GENERALIZATION:START -->
measured rich content
<!-- DICTIONARY-GENERALIZATION:END -->
"""

        updated = MODULE._with_rating(text, info)

        self.assertIn("★★☆☆☆", updated)
        self.assertIn("measured rich content", updated)
        self.assertEqual(updated, MODULE._with_rating(updated, info))

    def test_generic_model_parameter_selects_exact_speed_row(self) -> None:
        expected = MODULE.Key(
            "example.radixor", (("modelId", "ady-default"),)
        )
        other = MODULE.Key(
            "example.radixor", (("modelId", "afb-default"),)
        )
        data = MODULE.JmhData(
            primary={expected: speed_row(10.0), other: speed_row(20.0)},
            auxiliary={},
        )

        selected = MODULE.select_speed_key(
            "radixor[ady-default]", data, "ADY"
        )

        self.assertEqual(expected, selected)

    def test_snowball_mapping_excludes_serbo_croatian(self) -> None:
        cases = MODULE.read_snowball_catalog(
            Path("docs/benchmarks/data/snowball-language-cases.csv")
        )
        mapping = {
            case.language: (case.case_name, case.lucene_available)
            for case in cases
        }
        self.assertNotIn("HBS", mapping)
        self.assertEqual(("SESOTHO", False), mapping["ST_ZA"])
        self.assertEqual({"SESOTHO"}, MODULE.LANGUAGE_IDENTITY_WORDS["ST_ZA"])

    def test_homepage_snowball_comparison_has_exact_29_case_coverage(self) -> None:
        cases = MODULE.read_snowball_catalog(
            Path("docs/benchmarks/data/snowball-language-cases.csv")
        )
        corpus = MODULE.read_corpora(
            Path("docs/benchmarks/data/java-benchmark-corpora-2026-09-11.csv")
        )
        speed = MODULE.read_jmh(
            Path("docs/benchmarks/data/java-stemmer-speed-2026-09-11.csv")
        )

        section = MODULE.render_snowball_homepage(cases, corpus, speed, "2026-09-11")

        self.assertIn("all <b>29</b>", section)
        self.assertIn("2.104× over all 29 comparable changed-token cases", section)
        self.assertEqual(29, section.count("<tr><td>"))
        self.assertEqual(29, section.count('class="rx2-ratio-row"'))
        english = next(case for case in cases if case.case_name == "ENGLISH")
        self.assertEqual("snowballEnglishPorter2", english.speed_method)
        self.assertEqual("ENGLISH_SNOWBALL_PORTER2", english.quality_candidate)
        self.assertIn('class="rx2-ratio-axis"', section)
        self.assertNotIn('class="rx2-bar"', section)
        self.assertNotIn("root-only", section)
        self.assertEqual(29, section.count("<td>yes</td>"))

        invalid_corpus = dict(corpus)
        invalid_case = next(case for case in cases if case.language == "AR")
        invalid_case = MODULE.replace(invalid_case, homepage_aggregate=False)
        invalid_corpus[invalid_case.model_id] = dict(corpus[invalid_case.model_id])
        with self.assertRaisesRegex(ValueError, "excludes changed-token corpus"):
            MODULE.render_snowball_homepage(
                [invalid_case if case.language == "AR" else case for case in cases],
                invalid_corpus,
                speed,
                "2026-09-11",
            )

    def test_exact_snowball_pages_use_generic_java_baseline(self) -> None:
        cases = MODULE.read_snowball_catalog(
            Path("docs/benchmarks/data/snowball-language-cases.csv")
        )
        case = next(item for item in cases if item.case_name == "FINNISH")
        text = Path("docs/benchmarks/languages/finnish.md").read_text(encoding="utf-8")

        normalized = MODULE.normalize_exact_snowball_speed_table(text, case)

        self.assertIn("| Radixor | `radixor[fi-fi-default]` |", normalized)
        self.assertIn("| Official Snowball direct (Java) | `snowballDirect[FINNISH]` |", normalized)
        self.assertNotIn("`finnishRadixor`", normalized)

    def test_homepage_contains_only_python_performance_story(self) -> None:
        css = Path("docs/assets/stylesheets/landing-v2.css").read_text(encoding="utf-8")
        landing = Path("docs/overrides/landing.html").read_text(encoding="utf-8")

        self.assertIn("Measured Python runtime performance", landing)
        self.assertIn("Python (PyO3) performance", landing)
        self.assertNotIn("JAVA-SNOWBALL-COMPARISON", landing)
        self.assertNotIn("Java runtime minima", landing)
        self.assertNotIn("lowest runtime point estimate", landing)
        self.assertNotIn("282 / 282", landing)
        self.assertNotIn("9 of 19", landing)
        self.assertIn(".rx2-highlight-stat{min-width:0;container-type:inline-size}", css)

    def test_java_runtime_minimum_headline_is_data_derived(self) -> None:
        infos = MODULE.load_model_infos()
        defaults = {info.language: info for info in infos if info.role != "optional"}
        speed = MODULE.read_jmh(
            Path("docs/benchmarks/data/java-stemmer-speed-2026-09-11.csv")
        )

        self.assertEqual(
            (18, 30), MODULE.speed_homepage_counts(Path("docs"), defaults, speed)
        )

    def test_python_throughput_bars_have_visible_language_labels(self) -> None:
        landing = Path("docs/overrides/landing.html").read_text(encoding="utf-8")

        self.assertEqual(31, landing.count('class="rx2-bar-label"'))
        self.assertIn('class="rx2-bar-label" aria-hidden="true">Finnish</span>', landing)
        self.assertIn("PyStemmer 3.1.0 (Snowball C)", landing)

    def test_homepage_links_every_language_page_including_korean(self) -> None:
        landing = Path("docs/overrides/landing.html").read_text(encoding="utf-8")
        linked_pages = set(re.findall(
            r'href="\{\{ base_url \}\}/benchmarks/languages/([^/]+)/"', landing
        ))
        expected_pages = {
            path.stem for path in Path("docs/benchmarks/languages").glob("*.md")
            if path.name != "index.md"
        }

        self.assertEqual(expected_pages, linked_pages)
        self.assertEqual(166, len(linked_pages))
        self.assertIn("ko-kr", linked_pages)
        self.assertIn(">Korean</span>", landing)

        link_elements = re.findall(
            r'<a class="rx2-language(?: rx2-language--prohibited)?".*?</a>',
            landing,
            flags=re.DOTALL,
        )
        self.assertEqual(166, len(link_elements))
        for link in link_elements:
            ratings = re.findall(
                r'<span class="dictionary-rating" role="img" '
                r'aria-label="Relative dictionary size ([1-5]) of 5; '
                r'([0-9,]+) distinct usable word forms"[^>]*>([★☆]{5})</span>',
                link,
            )
            self.assertEqual(1, len(ratings), link)
            stars = int(ratings[0][0])
            self.assertEqual("★" * stars + "☆" * (5 - stars), ratings[0][2])
        polish = next(link for link in link_elements if ">Polish</span>" in link)
        self.assertIn("pl-pl-unimorph + pl-pl-polimorf", polish)
        self.assertIn("size rating represents the page-primary pl-pl-unimorph model", polish)

    def test_active_language_heading_is_dynamic_and_above_full_width_grid(self) -> None:
        landing = Path("docs/overrides/landing.html").read_text(encoding="utf-8")
        css = Path("docs/assets/stylesheets/landing-v2.css").read_text(encoding="utf-8")
        infos = MODULE.load_model_infos()
        defaults = {info.language: info for info in infos if info.role != "optional"}
        section = re.search(
            r'<!-- JAVA-LANGUAGE-PAGES:START -->(.*?)<!-- JAVA-LANGUAGE-PAGES:END -->',
            landing,
            flags=re.S,
        )

        self.assertIsNotNone(section)
        rendered = section.group(1)
        heading = f"{len(infos)} models · {len(defaults)} language pages"
        self.assertIn('class="rx2-languages-strip rx2-languages-strip--active"', rendered)
        self.assertIn(f'<div class="rx2-language-label"><strong>{heading}</strong></div>', rendered)
        self.assertLess(rendered.index(heading), rendered.index('class="rx2-language-list"'))
        self.assertNotIn("Complete Java quality evidence", rendered)
        self.assertIn(
            ".rx2-languages-strip{grid-template-columns:minmax(0,1fr);align-items:start}",
            css,
        )
        self.assertIn(
            ".rx2-languages-strip .rx2-language-list{width:100%", css
        )

    def test_homepage_geography_has_exact_default_coverage(self) -> None:
        infos = MODULE.load_model_infos()
        defaults = {info.language for info in infos if info.role != "optional"}
        geography = MODULE.read_homepage_geography(
            Path("docs/benchmarks/data/homepage-language-geography.csv"), defaults
        )

        self.assertEqual(143, len(geography))
        self.assertEqual("🌐", MODULE.geography_glyph(geography["AR"].icon))
        self.assertEqual("🇯🇵", MODULE.geography_glyph(geography["JA_JP"].icon))
        self.assertEqual("🇲🇽", MODULE.geography_glyph(geography["AZG"].icon))
        self.assertEqual("🇳🇵", MODULE.geography_glyph(geography["KLR"].icon))
        self.assertEqual("Transnational language", geography["AR"].label)

    def test_homepage_css_keeps_dense_counts_and_ratio_rows_responsive(self) -> None:
        css = Path("docs/assets/stylesheets/landing-v2.css").read_text(encoding="utf-8")

        self.assertIn(
            ".rx2-highlight-stats{width:100%;grid-template-columns:repeat(4,minmax(0,1fr))}",
            css,
        )
        self.assertIn(
            "@media(max-width:650px){\n  .rx2-highlight-stats{grid-template-columns:repeat(2,minmax(0,1fr))}",
            css,
        )
        self.assertIn(".rx2-highlight-stat{min-width:0;container-type:inline-size}", css)
        self.assertIn(".rx2-ratio-axis,.rx2-ratio-row", css)
        self.assertIn("width:max(2px,var(--width))", css)

    def test_homepage_python_chart_uses_authoritative_geography_icons(self) -> None:
        landing = Path("docs/overrides/landing.html").read_text(encoding="utf-8")
        icons = re.findall(
            r'<span class="rx2-chart-icon" aria-hidden="true" title="([^"]+)">([^<]+)</span>',
            landing,
        )

        self.assertEqual(31, len(icons))
        self.assertEqual(4, sum(glyph == "🌐" for _label, glyph in icons))
        self.assertEqual(27, sum(glyph != "🌐" for _label, glyph in icons))
        self.assertNotIn("rx2-bar-globe", landing)
        css = Path("docs/assets/stylesheets/landing-v2.css").read_text(encoding="utf-8")
        self.assertIn(".rx2-chart-icon {", css)
        self.assertIn(".rx2-chart-wrap:focus-visible {", css)
        self.assertNotIn(".rx2-bar:focus-visible", css)
        self.assertRegex(
            css,
            r"(?s)\.rx2-chart-wrap \{.*?--rx2-chart-plot-height: 168px;.*?"
            r"--rx2-chart-label-gutter: 128px;.*?overflow-x: auto;.*?overflow-y: hidden;.*?"
            r"scrollbar-width: none;.*?-ms-overflow-style: none;.*?\}",
        )
        self.assertRegex(
            css,
            r"(?s)\.rx2-chart-wrap::\-webkit-scrollbar \{.*?display: none;.*?\}",
        )
        self.assertRegex(
            css,
            r"(?s)\.rx2-ylabels \{.*?height: var\(--rx2-chart-plot-height\);.*?\}.*?"
            r"\.rx2-chart \{.*?height: calc\(var\(--rx2-chart-plot-height\) \+ "
            r"var\(--rx2-chart-label-gutter\)\);.*?background: none;.*?\}.*?"
            r"\.rx2-chart-plot \{.*?align-items: flex-end;.*?"
            r"height: var\(--rx2-chart-plot-height\);.*?"
            r"border-bottom: 1px solid #cad6e4;.*?repeating-linear-gradient",
        )
        self.assertIn(
            '<div class="rx2-chart-wrap" role="region" tabindex="0" '
            'aria-labelledby="python-pyo3-chart-title">',
            landing,
        )
        plot = re.search(
            r'<div class="rx2-chart-plot">(.*?)</div></div>\s*</div>\s*'
            r'<div class="rx2-chart-range">',
            landing,
            flags=re.S,
        )
        self.assertIsNotNone(plot)
        self.assertEqual(31, plot.group(1).count('class="rx2-bar"'))
        self.assertRegex(
            css,
            r"(?s)\.rx2-bar-label \{.*?right: 50%;.*?transform: rotate\(-52deg\);.*?"
            r"transform-origin: top right;",
        )

    def test_publication_inputs_must_match_active_manifest(self) -> None:
        data = Path("docs/benchmarks/data")
        manifest = data / "active-snapshots.properties"
        active = {
            "corpus": data / "java-benchmark-corpora-2026-09-11.csv",
            "quality": data / "stemming-quality-2026-09-11.csv",
        }

        MODULE.verify_active_inputs(manifest, active)
        with self.assertRaisesRegex(ValueError, "is not active"):
            MODULE.verify_active_inputs(
                manifest,
                {"corpus": data / "java-benchmark-corpora-2026-08-25.csv"},
            )

    def test_active_generalization_summary_is_complete_and_dynamic(self) -> None:
        infos = MODULE.load_model_infos()
        defaults = {info.language for info in infos if info.role != "optional"}

        scenarios, languages = MODULE.read_active_generalization_summary(
            Path("docs/benchmarks/data/active-snapshots.properties"), defaults
        )

        self.assertEqual(7_150, scenarios)
        self.assertEqual(143, languages)

    def test_homepage_language_section_replacement_is_idempotent(self) -> None:
        original = """<main>
    <section class="rx2-languages-strip rx2-languages-strip--active">old</section>
</main>
"""
        generated = """<!-- JAVA-LANGUAGE-PAGES:START -->
<section class="rx2-languages-strip rx2-languages-strip--active">new</section>
<!-- JAVA-LANGUAGE-PAGES:END -->"""

        updated = MODULE.replace_homepage_language_section(original, generated)

        self.assertEqual(
            updated,
            MODULE.replace_homepage_language_section(updated, generated),
        )
        self.assertEqual(1, updated.count("JAVA-LANGUAGE-PAGES:START"))
        self.assertEqual(1, updated.count("JAVA-LANGUAGE-PAGES:END"))

    def test_environment_provenance_uses_exact_publication_date(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            docs_root = Path(directory)
            data = docs_root / "benchmarks" / "data"
            reference = docs_root / "benchmarks" / "reference"
            data.mkdir(parents=True)
            reference.mkdir(parents=True)
            (data / "measured-source-2030-01-02.patch").write_bytes(b"patch\n")
            (data / "measured-untracked-2030-01-02.sha256").write_bytes(b"hashes\n")
            environment = reference / "environment.md"
            environment.write_text(
                "| Release identity | Radixor/Java `old`; retained as "
                "`measured-source-2026-09-10.patch` and "
                "`measured-untracked-2026-09-10.sha256` |\n",
                encoding="utf-8",
            )

            MODULE.update_environment_provenance(
                docs_root,
                "2030-01-02",
                "test-release",
                MODULE.Publication("update"),
            )
            updated = environment.read_text(encoding="utf-8")
            self.assertIn("measured-source-2030-01-02.patch", updated)
            self.assertIn("measured-untracked-2030-01-02.sha256", updated)
            self.assertNotIn("2026-09-10", updated)
            MODULE.update_environment_provenance(
                docs_root,
                "2030-01-02",
                "test-release",
                MODULE.Publication("verify"),
            )

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

    def test_empty_changed_population_renders_na(self) -> None:
        corpus = {
            "all_exact": 2,
            "total": 2,
            "changed_exact": 0,
            "changed": 0,
            "root_exact": 2,
            "roots": 2,
        }

        values = MODULE.rounded_accuracy(MODULE.corpus_accuracy(corpus))

        self.assertEqual(("100.000%", "n/a", "100.000%"), values)
        self.assertEqual(
            ("100.000%", "n/a", "100.000%"),
            MODULE.rounded_accuracy(
                (
                    MODULE.percentage(2, 2),
                    MODULE.percentage(0, 0),
                    MODULE.percentage(2, 2),
                )
            ),
        )

    def test_new_page_transitions_to_measured_and_preserves_markers(self) -> None:
        info = MODULE.replace(self.model_info(7, 700), stars=2)
        language = info.language
        key = MODULE.Key("example.radixor", (("modelId", info.model_id),))
        corpus = {
            "model": info.model_id,
            "version": info.version,
            "rows": 1,
            "distinct": info.distinct_forms,
            "total": 1,
            "roots": 1,
            "changed": 0,
            "timing_basis": "root-only tokens",
            "timing": 5_000,
            "all_exact": 1,
            "changed_exact": 0,
            "root_exact": 1,
            "commands": [("PreserveCommand", 1)],
        }
        old_languages = MODULE.LANGUAGES
        old_identity_words = MODULE.LANGUAGE_IDENTITY_WORDS
        try:
            MODULE.LANGUAGES = {info.page_name: language}
            MODULE.LANGUAGE_IDENTITY_WORDS = {language: {"LANGUAGE"}}
            with tempfile.TemporaryDirectory() as directory:
                docs_root = Path(directory)
                page = docs_root / "benchmarks" / "languages" / info.page_name
                page.parent.mkdir(parents=True)
                pending_shell = MODULE._new_page(info).replace(
                    "Speed uses JMH average time, 3 warmup iterations, 5 measurement "
                    "iterations, 3 independent forks, and 1 thread.\n\n",
                    "",
                )
                page.write_text(pending_shell, encoding="utf-8")

                MODULE.update_language_pages(
                    docs_root,
                    {language: corpus},
                    MODULE.JmhData(primary={}, auxiliary={}),
                    MODULE.JmhData(primary={key: speed_row(250_000.0)}, auxiliary={}),
                    MODULE.SpeedProtocol(15, 3, 3, 5),
                    MODULE.Publication("update"),
                    "2026-09-10",
                    "test-release",
                    {},
                )

                updated = page.read_text(encoding="utf-8")
                self.assertIn("root-only tokens", updated)
                self.assertIn(
                    "3 warmup iterations, 5 measurement iterations, 3 independent forks, and 1 thread",
                    updated,
                )
                self.assertIn("| 100.000% | n/a | 100.000% |", updated)
                self.assertIn("| 0.250 | 0.001 | 50.0 | 1.000 |", updated)
                self.assertIn("<!-- DICTIONARY-GENERALIZATION:START -->", updated)
                self.assertIn("<!-- EDIT-COST-GENERALIZATION:START -->", updated)
                self.assertIn("<!-- STEMMING-QUALITY:START -->", updated)
                self.assertNotIn(
                    "#dictionary-family-generalization-conclusion", updated
                )
                self.assertNotIn(
                    "#edit-costs-and-dictionary-knowledge-generalization", updated
                )
                self.assertNotIn("#stemming-quality", updated)
                self.assertNotIn("Their 10–90% curves", updated)
                self.assertNotIn("pending", updated.lower())
                self.assertEqual(
                    1,
                    updated.count("Speed uses JMH average time"),
                )
                self.assertIn(
                    "The canonical timing workload prefers changed dictionary tokens",
                    updated,
                )
        finally:
            MODULE.LANGUAGES = old_languages
            MODULE.LANGUAGE_IDENTITY_WORDS = old_identity_words

    def test_published_english_suite_note_matches_active_main_speed(self) -> None:
        corpus = MODULE.read_corpora(
            Path("docs/benchmarks/data/java-benchmark-corpora-2026-09-11.csv")
        )
        speed = MODULE.read_jmh(
            Path("docs/benchmarks/data/java-stemmer-speed-2026-09-11.csv")
        )
        key = MODULE.select_speed_key("radixor[us-uk-default]", speed, "US_UK")
        self.assertIsNotNone(key)
        assert key is not None
        expected = (
            float(speed.primary[key]["Score"])
            / int(corpus["us-uk-default"]["timing"])
        )
        for path in (
            Path("docs/benchmarks/languages/english.md"),
            Path("docs/benchmarks/reference/english-coverage.md"),
        ):
            content = path.read_text(encoding="utf-8")
            marker = re.search(
                r"<!-- ENGLISH-SPEED-SUITES:START -->(.*?)"
                r"<!-- ENGLISH-SPEED-SUITES:END -->",
                content,
                flags=re.DOTALL,
            )
            self.assertIsNotNone(marker)
            self.assertIn(f"`{expected:.1f} ns/token`", marker.group(1))
            self.assertNotIn("91.3 ns/token", marker.group(1))

    def test_published_pages_have_one_speed_protocol_sentence(self) -> None:
        infos = MODULE.load_model_infos()
        defaults = [info for info in infos if info.role != "optional"]

        self.assertEqual(143, len(defaults))
        for info in defaults:
            content = Path("docs/benchmarks/languages", info.page_name).read_text(
                encoding="utf-8"
            )
            self.assertEqual(
                1,
                content.count("Speed uses JMH average time"),
                info.model_id,
            )
            self.assertNotIn(
                "Speed uses JMH average time over the canonical timing workload",
                content,
                info.model_id,
            )

    def test_evidence_map_rejects_missing_internal_anchor(self) -> None:
        text = """# Example

<!-- BENCHMARK-EVIDENCE-MAP:START -->
[Missing section](#missing-section)
<!-- BENCHMARK-EVIDENCE-MAP:END -->

## Existing Section
"""

        with self.assertRaisesRegex(ValueError, "missing-section"):
            MODULE.validate_evidence_fragment_links(text, Path("example.md"))

    def test_polimorf_section_consumes_exact_optional_reports(self) -> None:
        info = MODULE.ModelInfo(
            model_id="pl-pl-polimorf",
            role="optional",
            language="PL_PL",
            display_name="Polish — PoliMorf",
            language_name="Polish",
            version="1.0.0",
            right_to_left=False,
            dictionary_rows=2,
            distinct_forms=4_668_685,
            sha256="0" * 64,
            stars=5,
        )
        counters = {
            "correctMatches": 2.0,
            "evaluatedTokens": 2.0,
            "changedCorrectMatches": 1.0,
            "changedEvaluatedTokens": 1.0,
            "rootPreservedMatches": 1.0,
            "rootEvaluatedTokens": 1.0,
        }
        accuracy_data = MODULE.JmhData(
            primary={},
            auxiliary={
                MODULE.Key("example.exactRootAgreement", (("candidateName", "POLISH_POLIMORF_RADIXOR"),)): counters,
                MODULE.Key("example.exactRootAgreement", (("candidateName", "POLISH_POLIMORF_LUCENE_MORFOLOGIK_FILTER"),)): counters,
                MODULE.Key("example.exactRootAgreement", (("candidateName", "POLISH_POLIMORF_SNOWBALL_DIRECT"),)): counters,
            },
        )
        radixor = MODULE.Key("example.radixor", (("modelId", info.model_id),))
        morfologik = MODULE.Key("example.polishPolimorfLuceneMorfologikFilter", ())
        speed_data = MODULE.JmhData(
            primary={radixor: speed_row(500_000.0), morfologik: speed_row(1_000_000.0)},
            auxiliary={},
        )
        corpus = {
            "version": "1.0.0",
            "rows": 2,
            "distinct": 4_668_685,
            "total": 2,
            "roots": 1,
            "changed": 1,
            "timing_basis": "changed tokens",
            "timing": 5_000,
            "all_exact": 2,
            "changed_exact": 1,
            "root_exact": 1,
        }

        section = MODULE._polimorf_section(
            info, corpus, accuracy_data, speed_data
        )

        self.assertIn("★★★★★", section)
        self.assertIn("4,668,685 distinct usable word forms", section)
        self.assertIn("POLIMORF", repr(accuracy_data.auxiliary))
        self.assertIn("| 0.500 | 0.001 | 100.0 | 1.000 |", section)
        self.assertIn("| 1.000 | 0.001 | 200.0 | 2.000 |", section)
        self.assertNotIn("pending", section)

    def test_speed_protocol_is_inferred_from_report_samples(self) -> None:
        key = MODULE.Key("example.speed", ())

        historical = MODULE.infer_speed_protocol(
            MODULE.JmhData(primary={key: protocol_row(21)}, auxiliary={}),
            "historical",
        )
        canonical = MODULE.infer_speed_protocol(
            MODULE.JmhData(primary={key: protocol_row(15)}, auxiliary={}),
            "canonical",
        )

        self.assertEqual(
            "Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, "
            "3 independent forks, and 1 thread.",
            historical.page_sentence(),
        )
        self.assertEqual(
            "Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, "
            "3 independent forks, and 1 thread.",
            canonical.page_sentence(),
        )

    def test_speed_protocol_rejects_wrong_thread_count(self) -> None:
        key = MODULE.Key("example.speed", ())

        with self.assertRaisesRegex(ValueError, "exactly one thread"):
            MODULE.infer_speed_protocol(
                MODULE.JmhData(primary={key: protocol_row(15, 2)}, auxiliary={}),
                "wrong threads",
            )

    def test_speed_protocol_rejects_inconsistent_samples(self) -> None:
        first = MODULE.Key("example.first", ())
        second = MODULE.Key("example.second", ())

        with self.assertRaisesRegex(ValueError, "inconsistent sample counts"):
            MODULE.infer_speed_protocol(
                MODULE.JmhData(
                    primary={first: protocol_row(15), second: protocol_row(21)},
                    auxiliary={},
                ),
                "mixed",
            )

    def test_speed_protocol_rejects_unsupported_samples(self) -> None:
        key = MODULE.Key("example.speed", ())

        with self.assertRaisesRegex(ValueError, "unsupported sample count"):
            MODULE.infer_speed_protocol(
                MODULE.JmhData(primary={key: protocol_row(12)}, auxiliary={}),
                "unsupported",
            )

    def test_verify_mode_rejects_stale_document_without_writing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "page.md"
            path.write_text("reviewed\n", encoding="utf-8")

            with self.assertRaisesRegex(SystemExit, "documentation is stale"):
                MODULE.Publication("verify").write(path, "generated\n")

            self.assertEqual("reviewed\n", path.read_text(encoding="utf-8"))

    def test_update_mode_replaces_stale_document(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "page.md"
            path.write_text("old\n", encoding="utf-8")

            MODULE.Publication("update").write(path, "current\n")

            self.assertEqual("current\n", path.read_text(encoding="utf-8"))

    def test_active_navigation_preserves_prohibited_benchmark_pages(self) -> None:
        info = self.model_info(1, 10)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            docs = root / "docs"
            docs.mkdir()
            navigation = root / "mkdocs.yml"
            navigation.write_text(
                "nav:\n"
                "  - Benchmarks:\n"
                "     - Language Comparisons:\n"
                "        - Overview: benchmarks/languages/index.md\n"
                "        - Old active entry: benchmarks/languages/old.md\n"
                "        - Benchmark-only prohibited models:\n"
                "           - Private: benchmarks/languages/private.md\n"
                "     - Methods and Reproduction:\n"
                "        - Methodology: benchmarks/reference/methodology.md\n",
                encoding="utf-8",
            )

            MODULE.update_mkdocs_language_nav(
                docs, {info.language: info}, MODULE.Publication("update")
            )

            updated = navigation.read_text(encoding="utf-8")
            self.assertIn(
                f"        - {info.language_name}: benchmarks/languages/{info.page_name}",
                updated,
            )
            self.assertIn(
                "        - Benchmark-only prohibited models:\n"
                "           - Private: benchmarks/languages/private.md\n",
                updated,
            )
            self.assertNotIn("Old active entry", updated)

    def test_public_ratings_include_documented_prohibited_population(self) -> None:
        infos = [self.model_info(index, index + 1) for index in range(4)]
        with tempfile.TemporaryDirectory() as directory:
            docs = Path(directory)
            data = docs / "benchmarks/data"
            data.mkdir(parents=True)
            (data / "prohibited-active-snapshots.properties").write_text(
                "corpus=prohibited.csv\n", encoding="utf-8"
            )
            (data / "prohibited.csv").write_text(
                "Model ID,Distinct usable forms\n"
                "private-a,5\n"
                "private-b,6\n",
                encoding="utf-8",
            )

            ranked = MODULE.include_prohibited_rating_population(docs, infos)

        self.assertEqual([1, 1, 2, 3], [info.stars for info in ranked])


if __name__ == "__main__":
    unittest.main()
