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

"""Coverage tests for the active Python benchmark documentation publisher."""

from __future__ import annotations

import csv
import importlib.util
import json
import math
import re
import shlex
from pathlib import Path

import pytest


REPOSITORY = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY / "tools/update-python-benchmark-documentation.py"
SPEC = importlib.util.spec_from_file_location("python_benchmark_docs", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def _write_complete_report(path: Path) -> None:
    rows: list[dict[str, str]] = []
    for language, model_id in MODULE.MODEL_IDS.items():
        for engine in ("radixor", "radixor-c"):
            rows.append({
                "language": language,
                "model": model_id,
                "engine": engine,
                "batch_size": "100",
                "per_word_ns": "100.0",
            })
        if language in MODULE.PYSTEMMER_LANGUAGES:
            for engine in ("PyStemmer", "snowballstemmer-pure"):
                rows.append({
                    "language": language,
                    "model": model_id,
                    "engine": engine,
                    "batch_size": "100",
                    "per_word_ns": "200.0",
                })
    rows.extend([
        {"language": "de", "model": MODULE.MODEL_IDS["de"], "engine": "cistem",
         "batch_size": "100", "per_word_ns": "300.0"},
        {"language": "en", "model": MODULE.MODEL_IDS["en"], "engine": "nltk-porter",
         "batch_size": "100", "per_word_ns": "300.0"},
    ])
    with path.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def test_standard_authority_and_competitor_cohort_are_exact() -> None:
    memberships = MODULE.read_standard_membership(
        REPOSITORY / "models/standard-model-projects.properties"
    )

    assert len(memberships) == 31
    assert len(MODULE.PYSTEMMER_LANGUAGES) == 29
    assert "he" not in MODULE.PYSTEMMER_LANGUAGES
    assert "uk" not in MODULE.PYSTEMMER_LANGUAGES


def test_chart_geography_uses_authoritative_standard_model_mapping() -> None:
    geography = MODULE.read_chart_geography(
        REPOSITORY / "docs/benchmarks/data/homepage-language-geography.csv"
    )

    assert len(geography) == 31
    assert sum(icon == "globe" for icon, _label in geography.values()) == 4
    assert sum(icon != "globe" for icon, _label in geography.values()) == 27
    assert geography["fi"] == ("FI", "Finland")
    assert geography["ar"] == ("globe", "Transnational language")
    assert geography["en"] == ("globe", "Transnational language")


def test_finnish_case_study_inputs_are_active_quality_rows() -> None:
    quality = MODULE.read_finnish_quality(
        REPOSITORY / "docs/benchmarks/data/stemming-quality-2026-09-11.csv"
    )

    assert quality == {
        "radixor_balanced": 0.984837631475,
        "snowball_balanced": 0.739869822067,
        "radixor_under": 3.032474,
        "snowball_under": 52.025976,
    }


def test_publication_inputs_must_be_active_and_geography_canonical() -> None:
    data = REPOSITORY / "docs/benchmarks/data"
    active_csv = data / "python-all-languages-batch-2026-09-11.csv"
    active_json = data / "python-all-languages-batch-2026-09-11.json"
    active_quality = data / "stemming-quality-2026-09-11.csv"
    geography = data / "homepage-language-geography.csv"

    MODULE.verify_publication_inputs(
        REPOSITORY / "docs", active_csv, active_json, active_quality, geography
    )

    with pytest.raises(ValueError, match="python_csv is not active"):
        MODULE.verify_publication_inputs(
            REPOSITORY / "docs",
            data / "python-all-languages-batch-2026-08-25.csv",
            active_json,
            active_quality,
            geography,
        )
    with pytest.raises(ValueError, match="python_json is not active"):
        MODULE.verify_publication_inputs(
            REPOSITORY / "docs",
            active_csv,
            data / "python-all-languages-batch-2026-08-25.json",
            active_quality,
            geography,
        )
    with pytest.raises(ValueError, match="quality is not active"):
        MODULE.verify_publication_inputs(
            REPOSITORY / "docs",
            active_csv,
            active_json,
            data / "stemming-quality.csv",
            geography,
        )
    with pytest.raises(ValueError, match="not the canonical homepage catalog"):
        MODULE.verify_publication_inputs(
            REPOSITORY / "docs",
            active_csv,
            active_json,
            active_quality,
            REPOSITORY / "README.md",
        )


def test_documented_python_publisher_command_tracks_required_inputs() -> None:
    reproducibility = (
        REPOSITORY / "docs/benchmarks/reference/reproducibility.md"
    ).read_text(encoding="utf-8")
    match = re.search(
        r"python3 tools/update-python-benchmark-documentation\.py \\\n"
        r"(.*?)(?=\n\./gradlew)",
        reproducibility,
        flags=re.S,
    )
    assert match is not None
    command = "python3 tools/update-python-benchmark-documentation.py " + match.group(1)
    arguments = shlex.split(command.replace("\\\n", " "))

    expected = {
        "--csv": "docs/benchmarks/data/python-all-languages-batch-2026-09-11.csv",
        "--json": "docs/benchmarks/data/python-all-languages-batch-2026-09-11.json",
        "--quality": "docs/benchmarks/data/stemming-quality-2026-09-11.csv",
        "--geography": "docs/benchmarks/data/homepage-language-geography.csv",
    }
    for option, value in expected.items():
        assert arguments[arguments.index(option) + 1] == value
    assert arguments[arguments.index("--mode") + 1] == "update"


def test_complete_31_model_report_is_accepted(tmp_path: Path) -> None:
    report = tmp_path / "python.csv"
    _write_complete_report(report)

    rows, values = MODULE.read_benchmark_rows(report)

    assert len(rows) == 122
    assert len(values) == 122


def test_report_rejects_missing_or_wrong_model_rows(tmp_path: Path) -> None:
    report = tmp_path / "python.csv"
    _write_complete_report(report)
    rows = list(csv.DictReader(report.open(encoding="utf-8", newline="")))
    rows[0]["model"] = "wrong-default"
    with report.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)

    with pytest.raises(ValueError, match="model identity differs"):
        MODULE.read_benchmark_rows(report)

    _write_complete_report(report)
    rows = list(csv.DictReader(report.open(encoding="utf-8", newline="")))[:-1]
    with report.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    with pytest.raises(ValueError, match="coverage differs"):
        MODULE.read_benchmark_rows(report)


def test_merged_protocol_requires_matching_numeric_summary_types() -> None:
    run_parameters = {
        "words_budget": 5000,
        "repeats": 3,
        "warmup": 3,
        "sizes": [100],
    }
    report = {
        "runs": [{"environment": {"python": "3.14.7"}, "parameters": run_parameters}],
        "parameters": {
            "word_budget": 5000,
            "repeats": 3,
            "warmup": 3,
            "sizes": [100],
        },
    }

    environment, actual_parameters = MODULE.validate_report_metadata(report)

    assert environment == {"python": "3.14.7"}
    assert actual_parameters == run_parameters
    for name in ("word_budget", "repeats", "warmup"):
        invalid = json.loads(json.dumps(report))
        invalid["parameters"][name] = str(invalid["parameters"][name])
        with pytest.raises(ValueError, match="matching numeric value"):
            MODULE.validate_report_metadata(invalid)


def test_published_snapshot_and_homepage_match_current_cohorts() -> None:
    data = REPOSITORY / "docs/benchmarks/data"
    json_path = data / "python-all-languages-batch-2026-09-11.json"
    json_text = json_path.read_text(encoding="utf-8")
    report = json.loads(json_text)
    MODULE.validate_report_metadata(report)
    rows, values = MODULE.read_benchmark_rows(
        data / "python-all-languages-batch-2026-09-11.csv"
    )
    shared = sorted(MODULE.PYSTEMMER_LANGUAGES)
    pyo3_ratios = [
        values[language, "PyStemmer"] / values[language, "radixor"]
        for language in shared
    ]
    c_ratios = [
        values[language, "PyStemmer"] / values[language, "radixor-c"]
        for language in shared
    ]
    landing = (REPOSITORY / "docs/overrides/landing.html").read_text(encoding="utf-8")

    assert len(rows) == 122
    pointers = dict(
        line.split("=", 1)
        for line in (data / "active-snapshots.properties").read_text(
            encoding="utf-8"
        ).splitlines()
        if line and not line.startswith("#")
    )
    assert pointers["python_csv"] == "python-all-languages-batch-2026-09-11.csv"
    assert pointers["python_json"] == json_path.name
    assert "/home/" not in json_text
    assert "<repository>/build/python/runtime/benchmark" in json_text
    assert sum(row["engine"] == "radixor" for row in rows) == 31
    assert sum(row["engine"] == "radixor-c" for row in rows) == 31
    assert sum(row["engine"] == "PyStemmer" for row in rows) == 29
    assert sum(row["engine"] == "snowballstemmer-pure" for row in rows) == 29
    assert sum(row["engine"] == "cistem" for row in rows) == 1
    assert sum(row["engine"] == "nltk-porter" for row in rows) == 1
    assert MODULE.geometric_mean(pyo3_ratios) == pytest.approx(2.1647, abs=0.00005)
    assert MODULE.geometric_mean(c_ratios) == pytest.approx(2.2783, abs=0.00005)
    assert landing.count('class="rx2-bar-label"') == 31
    assert landing.count('class="rx2-chart-icon"') == 31
    assert landing.count('class="rx2-chart-icon" aria-hidden="true" title="Transnational language">🌐</span>') == 4
    assert len(re.findall(r'class="rx2-chart-icon"[^>]*>[^🌐<]+</span>', landing)) == 27
    assert "2.165×" in landing
    assert "2.278×" in landing
    assert "29/29 comparisons through PyO3 and 29/29 through Python-C" in landing
    assert "4.2.1" not in landing
    assert landing.count('id="python-pyo3-chart-title"') == 1
    assert landing.count(
        '<div class="rx2-chart-wrap" role="region" tabindex="0" '
        'aria-labelledby="python-pyo3-chart-title">'
    ) == 1
    plot = re.search(
        r'<div class="rx2-chart-plot">(.*?)</div></div>\s*</div>\s*'
        r'<div class="rx2-chart-range">',
        landing,
        flags=re.S,
    )
    assert plot is not None
    assert plot.group(1).count('class="rx2-bar"') == 31
    css = (REPOSITORY / "docs/assets/stylesheets/landing-v2.css").read_text(
        encoding="utf-8"
    )
    assert ".rx2-chart-wrap:focus-visible {" in css
    assert ".rx2-bar:focus-visible" not in css
    assert re.search(
        r"(?s)\.rx2-chart-wrap \{.*?--rx2-chart-plot-height: 168px;.*?"
        r"--rx2-chart-label-gutter: 128px;.*?overflow-x: auto;.*?overflow-y: hidden;.*?"
        r"scrollbar-width: none;.*?-ms-overflow-style: none;.*?\}",
        css,
    )
    assert re.search(
        r"(?s)\.rx2-chart-wrap::\-webkit-scrollbar \{.*?display: none;.*?\}",
        css,
    )
    assert re.search(
        r"(?s)\.rx2-ylabels \{.*?height: var\(--rx2-chart-plot-height\);.*?\}.*?"
        r"\.rx2-chart \{.*?height: calc\(var\(--rx2-chart-plot-height\) \+ "
        r"var\(--rx2-chart-label-gutter\)\);.*?padding: 0;.*?background: none;.*?\}.*?"
        r"\.rx2-chart-plot \{.*?align-items: flex-end;.*?"
        r"height: var\(--rx2-chart-plot-height\);.*?"
        r"border-bottom: 1px solid #cad6e4;.*?repeating-linear-gradient",
        css,
    )

    highlights = re.search(
        r"<!-- PYTHON-HIGHLIGHTS:START -->(.*?)<!-- PYTHON-HIGHLIGHTS:END -->",
        landing,
        flags=re.S,
    )
    assert highlights is not None
    assert highlights.group(1).count('class="rx2-highlight-stat ') == 4
    assert "31 / 31" in highlights.group(1)
    assert "29 / 29" in highlights.group(1)
    assert "both Python<br>runtimes beat PyStemmer" in highlights.group(1)

    case_study = re.search(
        r"<!-- FINNISH-CASE-STUDY:START -->(.*?)<!-- FINNISH-CASE-STUDY:END -->",
        landing,
        flags=re.S,
    )
    assert case_study is not None
    case = case_study.group(1)
    assert "Quality and speed you can trust — Finnish case study" in case
    assert "0.98" in case
    assert "0.74" in case
    assert "+33.11%" in case
    assert "3.03%" in case
    assert "52.03%" in case
    assert "94.17%" in case
    assert "6.73M words/s" in case
    assert "5.36M words/s" in case
    assert "1.26×" in case
    assert "1.49×" in case
    assert "1.00×" in case
    assert re.search(r"\d+\.\d{3,}", case) is None
    assert "no Python quality evaluation is implied" in case
    assert landing.index(MODULE.FINNISH_CASE_END) < landing.index(
        "<!-- JAVA-LANGUAGE-PAGES:START -->"
    )


def test_rotated_chart_labels_fit_the_explicit_gutter() -> None:
    css = (REPOSITORY / "docs/assets/stylesheets/landing-v2.css").read_text(
        encoding="utf-8"
    )
    landing = (REPOSITORY / "docs/overrides/landing.html").read_text(
        encoding="utf-8"
    )
    label_rule = re.search(r"(?s)\.rx2-bar-label \{(.*?)\}", css)
    assert label_rule is not None
    label_css = label_rule.group(1)
    gutter = int(re.search(r"--rx2-chart-label-gutter: (\d+)px", css).group(1))
    offset = int(re.search(r"top: calc\(100% \+ (\d+)px\)", label_css).group(1))
    width = int(re.search(r"max-width: (\d+)px", label_css).group(1))
    font_rem = float(
        re.search(r"font-size: (\d+(?:\.\d+)?|\.\d+)rem", label_css).group(1)
    )
    line_height = float(re.search(r"line-height: (\d+(?:\.\d+)?)", label_css).group(1))
    angle = int(re.search(r"transform: rotate\(-(\d+)deg\)", label_css).group(1))
    longest_labels = ("Norwegian Bokmål", "Norwegian Nynorsk", "Southern Sotho")
    label_font_px = font_rem * 16.0
    safety_margin = 8.0
    for label in longest_labels:
        # 0.65em per character conservatively bounds these Noto Sans labels;
        # the CSS max-width remains the final painted-box bound.
        noto_width = min(width, len(label) * label_font_px * 0.65)
        projected_height = (
            noto_width * math.sin(math.radians(angle))
            + label_font_px * line_height * math.cos(math.radians(angle))
        )
        assert gutter >= math.ceil(offset + projected_height + safety_margin)
    assert "--rx2-chart-plot-height: 168px" in css
    for label in longest_labels:
        assert f'>{label}</span>' in landing


def test_public_documentation_uses_release_identity_not_raw_git_state() -> None:
    public_files = list((REPOSITORY / "docs").rglob("*.md"))
    public_files.extend((REPOSITORY / "docs").rglob("*.html"))
    stale = [
        str(path.relative_to(REPOSITORY))
        for path in public_files
        if "dirty" in path.read_text(encoding="utf-8").lower()
    ]

    assert stale == []
    current_pages = [
        REPOSITORY / "docs/overrides/landing.html",
        REPOSITORY / "docs/python/performance.md",
        REPOSITORY / "docs/technology-lineage.md",
        REPOSITORY / "docs/benchmarks/reference/environment.md",
        *(REPOSITORY / "docs/benchmarks/languages").glob("*.md"),
    ]
    for path in current_pages:
        text = path.read_text(encoding="utf-8")
        assert "4.3.0" not in text, path
    public_homepage_data = (
        REPOSITORY / "docs/assets/data/homepage-performance.json"
    ).read_text(encoding="utf-8")
    assert '"radixor": "4.4.0"' in public_homepage_data
    assert "dirty" not in public_homepage_data.lower()
    raw_provenance = (
        REPOSITORY / "docs/benchmarks/data/prohibited-provenance-2026-09-11.txt"
    ).read_text(encoding="utf-8")
    assert "4.3.0.dirty" in raw_provenance
