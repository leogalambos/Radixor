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

"""Publish the Python batch benchmark CSV into documentation summaries."""

from __future__ import annotations

import argparse
import csv
import hashlib
import html
import json
import math
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LANGUAGES = {
    "ar": "Arabic", "hy": "Armenian", "ca": "Catalan",
    "cs": "Czech", "da": "Danish", "de": "German", "en": "English",
    "es": "Spanish", "et": "Estonian", "fa": "Persian", "fi": "Finnish",
    "fr": "French", "el": "Greek", "he": "Hebrew", "hu": "Hungarian",
    "id": "Indonesian", "ga": "Irish", "it": "Italian", "lt": "Lithuanian",
    "nb": "Norwegian Bokmål", "nl": "Dutch", "nn": "Norwegian Nynorsk",
    "pl": "Polish", "pt": "Portuguese", "ro": "Romanian", "ru": "Russian",
    "st": "Southern Sotho", "sv": "Swedish", "tr": "Turkish",
    "uk": "Ukrainian", "yi": "Yiddish",
}
MODEL_IDS = {
    "ar": "ar-default", "hy": "hy-am-default", "ca": "ca-es-default",
    "cs": "cs-cz-default", "da": "da-dk-default", "de": "de-de-default",
    "en": "us-uk-default", "es": "es-es-default", "et": "et-ee-default",
    "fa": "fa-ir-default", "fi": "fi-fi-default", "fr": "fr-fr-default",
    "el": "el-gr-default", "he": "he-il-default", "hu": "hu-hu-default",
    "id": "id-id-default", "ga": "ga-ie-default", "it": "it-it-default",
    "lt": "lt-lt-default", "nb": "nb-no-default", "nl": "nl-nl-default",
    "nn": "nn-no-default", "pl": "pl-pl-unimorph", "pt": "pt-pt-default",
    "ro": "ro-ro-default", "ru": "ru-ru-default", "st": "st-za-default",
    "sv": "sv-se-default", "tr": "tr-tr-default", "uk": "uk-ua-default",
    "yi": "yi-default",
}
PYSTEMMER_LANGUAGES = frozenset(LANGUAGES) - {"he", "uk"}
ENGINES = ("radixor", "radixor-c", "PyStemmer", "cistem", "snowballstemmer-pure", "nltk-porter")
FINNISH_CASE_START = "<!-- FINNISH-CASE-STUDY:START -->"
FINNISH_CASE_END = "<!-- FINNISH-CASE-STUDY:END -->"
PYTHON_HIGHLIGHTS_START = "<!-- PYTHON-HIGHLIGHTS:START -->"
PYTHON_HIGHLIGHTS_END = "<!-- PYTHON-HIGHLIGHTS:END -->"
CHART_TITLE_ID = "python-pyo3-chart-title"


def geometric_mean(values: list[float]) -> float:
    return math.exp(sum(math.log(value) for value in values) / len(values))


def replace_once(text: str, pattern: str, replacement: str, *, flags: int = 0) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise ValueError(f"Expected exactly one match for {pattern!r}, found {count}")
    return updated


def publish(path: Path, text: str, mode: str) -> None:
    """Writes one generated file or rejects a stale checked-in publication."""
    current = path.read_text(encoding="utf-8") if path.is_file() else None
    if current == text:
        return
    if mode == "verify":
        raise SystemExit(f"Generated Python benchmark documentation is stale: {path}")
    path.write_text(text, encoding="utf-8")


def verify_publication_inputs(
        docs_root: Path, csv_path: Path, json_path: Path, quality_path: Path,
        geography_path: Path) -> None:
    """Reject stale snapshots and a non-canonical geography catalog."""

    data_directory = docs_root / "benchmarks" / "data"
    manifest = data_directory / "active-snapshots.properties"
    pointers: dict[str, str] = {}
    for raw_line in manifest.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or not key or not value or key in pointers:
            raise ValueError("Active snapshot manifest contains an invalid or duplicate entry")
        pointers[key] = value
    for key, source in {
        "python_csv": csv_path,
        "python_json": json_path,
        "quality": quality_path,
    }.items():
        selected = pointers.get(key)
        if selected is None:
            raise ValueError(f"Active snapshot manifest does not select {key}")
        expected = (data_directory / selected).resolve()
        if source.resolve() != expected:
            raise ValueError(
                f"Publication input {key} is not active: {source} (expected {expected})"
            )

    canonical_geography = data_directory / "homepage-language-geography.csv"
    if geography_path.resolve() != canonical_geography.resolve():
        raise ValueError(
            "Publication geography is not the canonical homepage catalog: "
            f"{geography_path} (expected {canonical_geography.resolve()})"
        )
    checksum_path = data_directory / "measured-untracked-2026-09-11.sha256"
    expected_digest: str | None = None
    expected_name = "docs/benchmarks/data/homepage-language-geography.csv"
    for line in checksum_path.read_text(encoding="utf-8").splitlines():
        digest, separator, name = line.partition("  ")
        if separator and name == expected_name:
            if expected_digest is not None:
                raise ValueError("Canonical geography has duplicate checksum entries")
            expected_digest = digest
    actual_digest = hashlib.sha256(canonical_geography.read_bytes()).hexdigest()
    if expected_digest is None or not re.fullmatch(r"[0-9a-f]{64}", expected_digest):
        raise ValueError("Canonical geography checksum is missing or malformed")
    if actual_digest != expected_digest:
        raise ValueError("Canonical geography checksum is stale")


def read_standard_membership(path: Path) -> set[str]:
    """Read the exact Python/Java standard-package membership authority."""

    model_ids: set[str] = set()
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        model_id, separator, value = line.partition("=")
        if not separator or not model_id or value != "true" or model_id in model_ids:
            raise ValueError(f"Invalid standard-model membership line: {raw_line!r}")
        model_ids.add(model_id)
    if model_ids != set(MODEL_IDS.values()) or set(MODEL_IDS) != set(LANGUAGES):
        raise ValueError("Python benchmark language mapping differs from the standard-model authority")
    return model_ids


def flag_glyph(icon: str) -> str:
    """Return the Unicode flag for one validated ISO alpha-2 code."""

    if not re.fullmatch(r"[A-Z]{2}", icon):
        raise ValueError(f"Invalid homepage country icon: {icon!r}")
    return "".join(chr(0x1F1E6 + ord(character) - ord("A")) for character in icon)


def read_chart_geography(path: Path) -> dict[str, tuple[str, str]]:
    """Map every Python benchmark language through the docs geography authority."""

    by_language: dict[str, tuple[str, str]] = {}
    with path.open(encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames != ["Language", "Icon", "Label"]:
            raise ValueError("Homepage geography catalog schema is invalid")
        for row in reader:
            language = row["Language"]
            icon = row["Icon"]
            label = row["Label"]
            if not language or language in by_language or not label:
                raise ValueError("Homepage geography catalog has a duplicate or blank entry")
            if icon != "globe" and re.fullmatch(r"[A-Z]{2}", icon) is None:
                raise ValueError(f"Invalid homepage geography icon for {language}: {icon}")
            by_language[language] = (icon, label)

    result: dict[str, tuple[str, str]] = {}
    for benchmark_language, model_id in MODEL_IDS.items():
        build_path = ROOT / "models" / model_id / "build.gradle"
        match = re.search(
            r"^\s*language\s*=\s*'([^']+)'\s*$",
            build_path.read_text(encoding="utf-8"),
            flags=re.MULTILINE,
        )
        if match is None:
            raise ValueError(f"Model has no unambiguous language metadata: {model_id}")
        language = match.group(1)
        if language not in by_language:
            raise ValueError(f"Homepage geography catalog omits {language}")
        result[benchmark_language] = by_language[language]
    if len(result) != len(LANGUAGES):
        raise ValueError("Python chart geography coverage is incomplete")
    return result


def read_finnish_quality(path: Path) -> dict[str, float]:
    """Read the two authoritative Finnish shared model-quality rows."""

    expected = {"FINNISH_RADIXOR", "SNOWBALL_FINNISH_DIRECT"}
    rows: dict[str, dict[str, str]] = {}
    with path.open(encoding="utf-8", newline="") as source:
        for row in csv.DictReader(source):
            stemmer = row.get("Stemmer", "")
            if (
                stemmer in expected
                and row.get("Language") == "FI_FI"
                and row.get("Dictionary mode") == "ALL_WORDS"
                and row.get("Output policy") == "PRIMARY_OUTPUT"
            ):
                if stemmer in rows:
                    raise ValueError(f"Duplicate Finnish quality row: {stemmer}")
                rows[stemmer] = row
    if set(rows) != expected:
        raise ValueError("Finnish quality comparison is incomplete")
    values = {
        "radixor_balanced": float(rows["FINNISH_RADIXOR"]["Balanced accuracy"]),
        "snowball_balanced": float(
            rows["SNOWBALL_FINNISH_DIRECT"]["Balanced accuracy"]
        ),
        "radixor_under": float(
            rows["FINNISH_RADIXOR"]["Under-stemming percentage"]
        ),
        "snowball_under": float(
            rows["SNOWBALL_FINNISH_DIRECT"]["Under-stemming percentage"]
        ),
    }
    if any(not math.isfinite(value) or value < 0.0 for value in values.values()):
        raise ValueError("Finnish quality comparison contains an invalid value")
    return values


def read_benchmark_rows(path: Path) -> tuple[list[dict[str, str]], dict[tuple[str, str], float]]:
    """Validate and return the complete 31-model benchmark matrix."""

    with path.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source))
    expected_keys = {
        (language, engine)
        for language in LANGUAGES
        for engine in ("radixor", "radixor-c")
    }
    expected_keys.update(
        (language, engine)
        for language in PYSTEMMER_LANGUAGES
        for engine in ("PyStemmer", "snowballstemmer-pure")
    )
    expected_keys.update({("de", "cistem"), ("en", "nltk-porter")})
    values: dict[tuple[str, str], float] = {}
    for row in rows:
        key = (row.get("language", ""), row.get("engine", ""))
        if key in values:
            raise ValueError(f"Duplicate Python benchmark row: {key}")
        if key not in expected_keys:
            raise ValueError(f"Unexpected Python benchmark row: {key}")
        if row.get("model") != MODEL_IDS[key[0]]:
            raise ValueError(f"Python benchmark model identity differs for {key}")
        if row.get("batch_size") != "100":
            raise ValueError(f"Python benchmark row does not use batch size 100: {key}")
        value = float(row["per_word_ns"])
        if not math.isfinite(value) or value <= 0.0:
            raise ValueError(f"Python benchmark row has invalid per-word timing: {key}")
        values[key] = value
    actual_keys = set(values)
    if actual_keys != expected_keys:
        missing = sorted(expected_keys - actual_keys)
        unexpected = sorted(actual_keys - expected_keys)
        raise ValueError(
            f"Python benchmark coverage differs: missing={missing}, unexpected={unexpected}"
        )
    return rows, values


def validate_report_metadata(report: dict[str, object]) -> tuple[dict[str, object], dict[str, object]]:
    """Validate the merged report's numeric protocol and shared run provenance."""

    runs = report.get("runs")
    if not isinstance(runs, list) or not runs:
        raise ValueError("Python benchmark run provenance is missing")
    environments = [run.get("environment") for run in runs if isinstance(run, dict)]
    if len(environments) != len(runs) or any(
            environment != environments[0] for environment in environments[1:]):
        raise ValueError(
            "Python benchmark environments are missing or differ between isolated engine runs."
        )
    first_run = runs[0]
    if not isinstance(first_run, dict) or not isinstance(first_run.get("parameters"), dict):
        raise ValueError("Python benchmark run parameters are missing")
    run_parameters = first_run["parameters"]
    merged_parameters = report.get("parameters")
    if not isinstance(merged_parameters, dict):
        raise ValueError("Merged Python benchmark parameters are missing")
    expected = {
        "word_budget": run_parameters.get("words_budget"),
        "repeats": run_parameters.get("repeats"),
        "warmup": run_parameters.get("warmup"),
        "sizes": run_parameters.get("sizes"),
    }
    for name, value in expected.items():
        actual = merged_parameters.get(name)
        valid_type = (
            isinstance(actual, list) and all(type(item) is int for item in actual)
            if name == "sizes"
            else type(actual) is int
        )
        if not valid_type or actual != value:
            raise ValueError(
                f"Merged Python benchmark parameter {name!r} is not a matching numeric value"
            )
    return environments[0], run_parameters


def replace_marked_or_before(
    text: str, start: str, end: str, content: str, before: str
) -> str:
    """Replace one generated block, or insert it immediately before an anchor."""

    block = f"{start}\n{content}\n{end}"
    if start in text or end in text:
        if text.count(start) != 1 or text.count(end) != 1:
            raise ValueError(f"Generated block markers are missing or ambiguous: {start}")
        return replace_once(
            text,
            re.escape(start) + r".*?" + re.escape(end),
            block,
            flags=re.S,
        )
    if text.count(before) != 1:
        raise ValueError(f"Generated block insertion anchor is missing or ambiguous: {before}")
    return text.replace(before, block + "\n\n" + before, 1)


def render_highlight_cards(
    summaries: dict[str, dict[str, object]], joint_wins: int
) -> str:
    """Render four Python-only, data-derived homepage summary cards."""

    py = summaries["radixor"]
    c = summaries["radixor-c"]
    speed_icon = (
        '<i><svg viewBox="0 0 32 32" aria-hidden="true"><path d="M5 23a12 12 0 1 1 22 0" '
        'fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>'
        '<path d="m16 20 6-8" stroke="currentColor" stroke-width="2.2" '
        'stroke-linecap="round"/><circle cx="16" cy="20" r="2" fill="currentColor"/></svg></i>'
    )
    cohort_icon = (
        '<i><svg viewBox="0 0 32 32" aria-hidden="true"><path d="m7 9 9-5 9 5v14l-9 5-9-5Z" '
        'fill="none" stroke="currentColor" stroke-width="2.1"/><path d="m7 9 9 5 9-5M16 14v14" '
        'fill="none" stroke="currentColor" stroke-width="2.1"/></svg></i>'
    )
    comparison_icon = (
        '<i><svg viewBox="0 0 32 32" aria-hidden="true"><path d="M10 4h12v6c0 7-3 10-6 10s-6-3-6-10V4Z" '
        'fill="none" stroke="currentColor" stroke-width="2.2"/><path d="M10 7H5c0 6 2 9 7 9M22 7h5c0 6-2 9-7 9M16 20v5M11 27h10" '
        'fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg></i>'
    )
    comparisons = int(py["comparisons"])
    return (
        '<div class="rx2-highlight-stats">'
        f'<div class="rx2-highlight-stat teal">{cohort_icon}<strong>{len(LANGUAGES)} / {len(LANGUAGES)}</strong>'
        '<span>Python standard models<br>benchmarked</span></div>'
        f'<div class="rx2-highlight-stat purple">{comparison_icon}<strong>{joint_wins} / {comparisons}</strong>'
        '<span>Comparisons where both Python<br>runtimes beat PyStemmer</span></div>'
        f'<div class="rx2-highlight-stat blue">{speed_icon}<strong>{py["geomean"]:.3f}×</strong>'
        '<span>PyO3 geometric-mean<br>speedup vs PyStemmer</span></div>'
        f'<div class="rx2-highlight-stat blue">{speed_icon}<strong>{c["geomean"]:.3f}×</strong>'
        '<span>Python-C geometric-mean<br>speedup vs PyStemmer</span></div></div>'
    )


def render_finnish_case_study(
    values: dict[tuple[str, str], float], quality: dict[str, float]
) -> str:
    """Render the Finnish Java-quality and Python-runtime case study."""

    radixor_balanced = quality["radixor_balanced"]
    snowball_balanced = quality["snowball_balanced"]
    radixor_under = quality["radixor_under"]
    snowball_under = quality["snowball_under"]
    balanced_advantage = 100.0 * (radixor_balanced / snowball_balanced - 1.0)
    under_reduction = 100.0 * (1.0 - radixor_under / snowball_under)
    pyo3_throughput = 1000.0 / values["fi", "radixor"]
    pystemmer_throughput = 1000.0 / values["fi", "PyStemmer"]
    pyo3_ratio = values["fi", "PyStemmer"] / values["fi", "radixor"]
    c_ratio = values["fi", "PyStemmer"] / values["fi", "radixor-c"]
    return f'''    <h2 class="rx2-case-heading">Quality and speed you can trust — Finnish case study</h2>
    <section class="rx2-case-table">
      <div class="rx2-case-head"><span></span><span><img src="{{{{ base_url }}}}/assets/images/radixor-logo.png" alt=""> Radixor</span><span class="snow">❄ Snowball Finnish</span><span class="adv"><svg viewBox="0 0 32 32" aria-hidden="true"><circle cx="16" cy="16" r="12" fill="none" stroke="currentColor" stroke-width="2"/><path d="m10 16 4 4 8-9" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg> Radixor advantage</span></div>
      <div class="rx2-case-row"><span><i><svg viewBox="0 0 32 32" aria-hidden="true"><path d="M5 23a12 12 0 1 1 22 0" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/><path d="m16 20 6-8" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/><circle cx="16" cy="20" r="2" fill="currentColor"/></svg></i><b>Balanced accuracy</b><small>Java quality evaluation · higher is better</small></span><strong>{radixor_balanced:.2f}</strong><span>{snowball_balanced:.2f}</span><b class="green">+{balanced_advantage:.2f}% <small>higher</small></b></div>
      <div class="rx2-case-row"><span><i class="dots">◌</i><b>Under-stemming</b><small>Java quality evaluation · lower is better</small></span><strong>{radixor_under:.2f}%</strong><b class="red">{snowball_under:.2f}%</b><b class="green">{under_reduction:.2f}% <small>lower</small></b></div>
      <div class="rx2-case-row"><span><i><svg viewBox="0 0 32 32" aria-hidden="true"><path d="M6 26V16M13 26V11M20 26V6M27 26V3" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/></svg></i><b>Python PyO3 speed</b><small>batch N=100 · higher ratio is faster</small></span><strong>{pyo3_ratio:.2f}× <small>vs PyStemmer</small></strong><span>1.00× <small>PyStemmer baseline</small></span><b class="green">{pyo3_ratio:.2f}× <small>faster</small></b></div>
    </section>
    <p class="rx2-case-note"><strong>Java quality evaluation:</strong> balanced accuracy and under-stemming use the shared Finnish model-quality evidence; no Python quality evaluation is implied. <strong>Python runtime:</strong> PyO3 measured {pyo3_throughput:.2f}M words/s versus PyStemmer 3.1.0 at {pystemmer_throughput:.2f}M words/s ({pyo3_ratio:.2f}×), and Python-C measured {c_ratio:.2f}×, all at batch size N=100.</p>'''


def publish_landing_summary(
        values: dict[tuple[str, str], float], summaries: dict[str, dict[str, object]],
        quality: dict[str, float], geography: dict[str, tuple[str, str]],
        *, date: str, release_version: str, model_package_version: str,
        docs_root: Path, mode: str) -> None:
    """Updates every Python measurement embedded in the landing-page template."""
    py = summaries["radixor"]
    c = summaries["radixor-c"]
    landing = docs_root / "overrides/landing.html"
    text = landing.read_text(encoding="utf-8")
    text = replace_once(
        text,
        r'<div class="rx2-chart-title"(?: id="[^"]+")?>',
        f'<div class="rx2-chart-title" id="{CHART_TITLE_ID}">',
    )
    text = replace_once(
        text,
        r'<div class="rx2-chart-wrap"(?: role="region" tabindex="0" '
        r'aria-labelledby="[^"]+")?>',
        f'<div class="rx2-chart-wrap" role="region" tabindex="0" '
        f'aria-labelledby="{CHART_TITLE_ID}">',
    )
    shared = [language for language in LANGUAGES if (language, "PyStemmer") in values]
    joint_wins = sum(
        values[language, "radixor"] < values[language, "PyStemmer"]
        and values[language, "radixor-c"] < values[language, "PyStemmer"]
        for language in shared
    )
    cards = render_highlight_cards(summaries, joint_wins)
    if PYTHON_HIGHLIGHTS_START in text or PYTHON_HIGHLIGHTS_END in text:
        text = replace_marked_or_before(
            text,
            PYTHON_HIGHLIGHTS_START,
            PYTHON_HIGHLIGHTS_END,
            cards,
            '<div class="rx2-chart-title">',
        )
    else:
        text = replace_once(
            text,
            r'<div class="rx2-highlight-stats">.*?(?=\s*<div class="rx2-chart-title">)',
            f"{PYTHON_HIGHLIGHTS_START}\n{cards}\n{PYTHON_HIGHLIGHTS_END}\n",
            flags=re.S,
        )
    throughput = {language: 1000.0 / values[language, "radixor"] for language in LANGUAGES}
    minimum_language, minimum = min(throughput.items(), key=lambda item: item[1])
    maximum_language, maximum = max(throughput.items(), key=lambda item: item[1])
    axis_maximum = max(5, 5 * math.ceil(maximum / 5))
    y_labels = (
        f'<div class="rx2-ylabels"><span>{axis_maximum:g}M</span>'
        f'<span>{0.75 * axis_maximum:g}M</span><span>{0.5 * axis_maximum:g}M</span>'
        f'<span>{0.25 * axis_maximum:g}M</span><span>0</span></div>'
    )
    text = replace_once(text, r'<div class="rx2-ylabels">.*?</div>', y_labels)
    chart_lines = []
    for language, value in sorted(throughput.items(), key=lambda item: item[1]):
        name = LANGUAGES[language]
        icon_name, icon_label = geography[language]
        glyph = "🌐" if icon_name == "globe" else flag_glyph(icon_name)
        icon = (
            f'<span class="rx2-chart-icon" aria-hidden="true" '
            f'title="{html.escape(icon_label)}">{glyph}</span>'
        )
        chart_lines.append(
            f'<span class="rx2-bar" style="--h:{100.0 * value / axis_maximum:.1f}%" '
            f'role="img" aria-label="{html.escape(name)}, {html.escape(icon_label)}: '
            f'{value:.2f} million words per second" '
            f'title="{html.escape(name)}: {value:.2f}M words/s">{icon}'
            f'<span class="rx2-bar-label" aria-hidden="true">{html.escape(name)}</span></span>'
        )
    chart = (
        '<div class="rx2-chart" aria-label="Python PyO3 throughput by language at batch size 100">'
        '<div class="rx2-chart-plot">' + "\n".join(chart_lines) + "</div></div>"
    )
    text = replace_once(
        text,
        r'<div class="rx2-chart" aria-label="Python PyO3 throughput by language at batch size 100">'
        r'(?:<div class="rx2-chart-plot">)?.*?</div>(?:</div>)?',
        chart,
        flags=re.S,
    )
    text = replace_once(
        text,
        r'(<div class="rx2-throughput"><b>)[^<]+(</b> <span>words per second</span></div>)',
        rf"\g<1>{minimum:.2f}M – {maximum:.2f}M\g<2>",
    )
    text = replace_once(
        text,
        r'<div class="rx2-chart-range">.*?</div>',
        f'<div class="rx2-chart-range"><span><b>{minimum:.2f}M</b><small>Slowest</small></span>'
        f'<span><b>{maximum:.2f}M</b><small>Fastest</small></span></div>',
    )
    missing = [LANGUAGES[language] for language in LANGUAGES
               if (language, "PyStemmer") not in values]
    missing_text = " or ".join(missing)
    c_minimum = c["minimum_throughput"][1]
    c_maximum = c["maximum_throughput"][1]
    note = (
        f'<p class="rx2-note"><b>*</b> The {date} Python batch report contains '
        f'{py["comparisons"]} direct PyStemmer 3.1.0 (Snowball C) comparisons. Radixor '
        f'{release_version}, with the {len(LANGUAGES)}-model `radixor-models-standard` '
        f'{model_package_version} package, records lower median processing time in '
        f'{py["wins"]}/{py["comparisons"]} comparisons through PyO3 and '
        f'{c["wins"]}/{c["comparisons"]} through Python-C. The geometric-mean speedups are '
        f'{py["geomean"]:.3f}× and {c["geomean"]:.3f}× respectively. Each ratio is PyStemmer '
        f'ns/word divided by Radixor ns/word, so values above 1 favour Radixor. PyStemmer has no '
        f'direct {missing_text} comparator. The chart shows '
        f'PyO3 throughput; Python-C spans {c_minimum:.2f}M–{c_maximum:.2f}M words/s. '
        '<a href="{{ base_url }}/python/performance/">View the complete Python benchmarks →</a></p>'
    )
    text = replace_once(text, r'<p class="rx2-note"><b>\*</b>.*?</p>', note)
    case_study = render_finnish_case_study(values, quality)
    text = replace_marked_or_before(
        text,
        FINNISH_CASE_START,
        FINNISH_CASE_END,
        case_study,
        "    <!-- JAVA-LANGUAGE-PAGES:START -->",
    )
    publish(landing, text, mode)


def publish_technology_summary(
        values: dict[tuple[str, str], float], summaries: dict[str, dict[str, object]],
        *, release_version: str, docs_root: Path, mode: str) -> None:
    """Keeps cross-cutting technology claims on the current Python snapshot."""
    py = summaries["radixor"]
    c = summaries["radixor-c"]
    page = docs_root / "technology-lineage.md"
    text = page.read_text(encoding="utf-8")
    block = (
        "At `N=100`, the current Python batch benchmark shows that rule-based\n"
        "generalization does not require accepting a runtime advantage over Radixor:\n\n"
        f"- Radixor {release_version} Python (PyO3) records lower median processing time than\n"
        f"  PyStemmer 3.1.0 in **{py['wins']} / {py['comparisons']}** direct language comparisons;\n"
        f"- Python-C records lower median processing time in **{c['wins']} / {c['comparisons']}** comparisons;\n"
        f"- Python (PyO3) has a **{py['geomean']:.3f}×** geometric-mean speedup and a largest measured\n"
        f"  direct advantage of **{py['maximum'][1]:.2f}×** ({LANGUAGES[py['maximum'][0]]});\n"
        f"- Python-C has a **{c['geomean']:.3f}×** geometric-mean speedup and a largest measured direct\n"
        f"  advantage of **{c['maximum'][1]:.2f}×** ({LANGUAGES[c['maximum'][0]]});\n"
        f"- Python (PyO3) spans **{py['minimum_throughput'][1]:.2f}–{py['maximum_throughput'][1]:.2f} million words/s**, "
        f"while Python-C spans\n  **{c['minimum_throughput'][1]:.2f}–{c['maximum_throughput'][1]:.2f} million words/s**, "
        f"across all {len(LANGUAGES)} standard languages at batch size `N=100`."
    )
    text = replace_once(
        text,
        r'At `N=100`, the current Python batch benchmark.*?across all \d+(?: standard)? languages at batch size `N=100`\.',
        block,
        flags=re.S,
    )
    finnish_pyo3 = values["fi", "PyStemmer"] / values["fi", "radixor"]
    finnish_c = values["fi", "PyStemmer"] / values["fi", "radixor-c"]
    text = replace_once(
        text,
        r'(At `N=100` in the current Python batch run,\nPython \(PyO3\) is )\*\*[^*]+\*\*'
        r'( faster and Python-C is )\*\*[^*]+\*\*( faster than\nPyStemmer\'s Finnish implementation\.)',
        rf"\g<1>**{finnish_pyo3:.2f}×**\g<2>**{finnish_c:.2f}×**\g<3>",
    )
    publish(page, text, mode)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", required=True, type=Path)
    parser.add_argument("--json", required=True, type=Path)
    parser.add_argument("--quality", required=True, type=Path)
    parser.add_argument("--geography", required=True, type=Path)
    parser.add_argument("--date", required=True)
    parser.add_argument("--release-version", required=True)
    parser.add_argument(
        "--model-package-version",
        default=(ROOT / "python/models-standard-version.txt").read_text(
            encoding="utf-8"
        ).strip(),
    )
    parser.add_argument("--base-commit")
    parser.add_argument(
        "--standard-models",
        type=Path,
        default=ROOT / "models/standard-model-projects.properties",
    )
    parser.add_argument("--docs-root", type=Path, default=ROOT / "docs")
    parser.add_argument("--mode", choices=("update", "verify"), default="update")
    args = parser.parse_args()
    verify_publication_inputs(
        args.docs_root, args.csv, args.json, args.quality, args.geography
    )
    read_standard_membership(args.standard_models)
    base_commit = args.base_commit or subprocess.check_output(
        ["git", "rev-parse", "--short=7", "HEAD"], cwd=ROOT, text=True
    ).strip()

    report = json.loads(args.json.read_text(encoding="utf-8"))
    environment, run_parameters = validate_report_metadata(report)

    rows, values = read_benchmark_rows(args.csv)
    quality = read_finnish_quality(args.quality)
    geography = read_chart_geography(args.geography)

    shared = [language for language in LANGUAGES if (language, "PyStemmer") in values]
    summaries = {}
    for engine in ("radixor", "radixor-c"):
        ratios = {language: values[language, "PyStemmer"] / values[language, engine] for language in shared}
        throughput = {language: 1000.0 / values[language, engine] for language in LANGUAGES}
        summaries[engine] = {
            "comparisons": len(shared),
            "wins": sum(values[language, engine] < values[language, "PyStemmer"] for language in shared),
            "geomean": geometric_mean(list(ratios.values())),
            "maximum": max(ratios.items(), key=lambda item: item[1]),
            "minimum_throughput": min(throughput.items(), key=lambda item: item[1]),
            "maximum_throughput": max(throughput.items(), key=lambda item: item[1]),
        }

    c_over_pyo3 = {language: values[language, "radixor"] / values[language, "radixor-c"] for language in LANGUAGES}
    c_wins = sum(value > 1 for value in c_over_pyo3.values())
    pyo3_wins = sum(value < 1 for value in c_over_pyo3.values())
    c_geomean = geometric_mean(list(c_over_pyo3.values()))

    table = [
        "| Language | Python (PyO3) | Python-C | PyStemmer (Snowball C) | CISTEM (pure Py) | snowballstemmer (pure Py) | NLTK Porter (pure Py) |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for language, name in LANGUAGES.items():
        present = [(engine, values[language, engine]) for engine in ENGINES if (language, engine) in values]
        fastest = min(value for _, value in present)
        cells = []
        for engine in ENGINES:
            value = values.get((language, engine))
            if value is None:
                cells.append("—")
            else:
                rendered = f"{value:.1f}"
                cells.append(f"**{rendered}**" if value == fastest else rendered)
        table.append(f"| {name} (`{language}`) | " + " | ".join(cells) + " |")
    table_text = "\n".join(table)

    p = args.docs_root / "python/performance.md"
    text = p.read_text(encoding="utf-8")
    text = re.sub(r"version \d+\.\d+\.\d+(?:\.dirty)?", f"version {args.release_version}", text)
    text = re.sub(r"Radixor \d+\.\d+\.\d+(?:\.dirty)? Python runtimes",
                  f"Radixor {args.release_version} Python runtimes", text)
    text = re.sub(r"Radixor \d+\.\d+\.\d+(?:\.dirty)?, locally built",
                  f"Radixor {args.release_version}, locally built", text)
    text = re.sub(r"represents the \d+\.\d+\.\d+(?:\.dirty)? Python runtimes",
                  f"represents the {args.release_version} Python runtimes", text)
    text = re.sub(r"measured source version is \d+\.\d+\.\d+(?:\.dirty)?",
                  f"measured source version is {args.release_version}", text)
    text = re.sub(
        r"The published standard model package is version \S+ and supplies \d+ languages\.",
        f"The published standard model package is version {args.model_package_version} and "
        f"supplies {len(LANGUAGES)} languages.",
        text,
    )
    text = re.sub(r"20\d{2}-\d{2}-\d{2}", args.date, text)
    text = re.sub(
        r"\| (?:Source state|Source identity) \|.*?\|",
        f"| Source identity | Radixor {args.release_version} measured source state based on Git commit `{base_commit}`; the Java benchmark provenance retains the exact measured source patch and untracked-source checksums |",
        text,
        count=1,
    )
    logical_cpus = len(environment.get("cpu_affinity", []))
    platform_identity = environment.get("platform", "not recorded")
    python_identity = f"{environment.get('python_impl', '')} {environment.get('python', '')}".strip()
    processor_identity = environment.get("processor", "not recorded")
    governor = environment.get("scaling_governor", "not recorded")
    preference = environment.get("energy_performance_preference", "not recorded")
    info = (
        f'!!! info "Published single-machine measurement"\n'
        f"    These results were regenerated on {args.date} on `{processor_identity}` with "
        f"{logical_cpus} logical CPUs available,\n"
        f"    `{platform_identity}`, and {python_identity}. Both Radixor {args.release_version} native\n"
        f"    runtimes were built locally in release mode. The recorded CPU governor was `{governor}`\n"
        f"    and the energy preference was `{preference}`. Absolute timings remain machine-specific;\n"
        "    compare ratios only within this run."
    )
    text = replace_once(
        text,
        r'!!! info "Published single-machine measurement"\n(?: {4}[^\n]*\n)*?'
        r' {4}compare ratios only within this run\.',
        info,
    )
    table_values = {
        "CPU": processor_identity,
        "CPU topology": f"{logical_cpus} logical CPUs in the recorded affinity; physical topology not recorded by the harness",
        "OS": f"`{platform_identity}`",
        "CPU policy": f"`{environment.get('scaling_driver', 'not recorded')}`; `{governor}` governor; `{preference}` energy preference",
        "Python": python_identity,
        "Standard model package": (
            f"`radixor-models-standard` {args.model_package_version}; "
            f"{len(LANGUAGES)} models"
        ),
        "Native toolchains": "Not part of the timed runtime report; wheels were built locally in release mode",
        "Workload": f"{run_parameters['words_budget']:,} changed tokens per language and measurement",
        "Batch sizes": ", ".join(str(value) for value in run_parameters["sizes"]),
        "Timing": (f"median of {run_parameters['repeats']} calibrated ~{run_parameters['sample_ms']:.0f} ms samples "
                   f"after at least {run_parameters['warmup']} complete-corpus warm-ups and "
                   f"{run_parameters['warmup_ms']:.0f} ms"),
    }
    for label, value in table_values.items():
        text = replace_once(text, rf"\| {re.escape(label)} \|.*?\|", f"| {label} | {value} |")
    text = replace_once(
        text,
        r"\| Language \| Python \(PyO3\).*?\n\| Yiddish \(`yi`\)[^\n]*",
        table_text,
        flags=re.S,
    )
    py = summaries["radixor"]
    c = summaries["radixor-c"]
    summary = (
        f"Python (PyO3) recorded lower median processing time in **{py['wins']} / {len(shared)}** direct\n"
        f"PyStemmer comparisons; Python-C did so in **{c['wins']} / {len(shared)}**. At `N=100`,\n"
        f"Python (PyO3) achieved a **{py['geomean']:.3f}×** geometric-mean speedup, with a largest direct\n"
        f"advantage of **{py['maximum'][1]:.2f}×** for {LANGUAGES[py['maximum'][0]]} and throughput of "
        f"**{py['minimum_throughput'][1]:.2f}–{py['maximum_throughput'][1]:.2f} million\n"
        f"words/s** across its {len(LANGUAGES)} standard languages. Python-C achieved a **{c['geomean']:.3f}×** geometric-mean\n"
        f"speedup, with a largest direct advantage of **{c['maximum'][1]:.2f}×** for {LANGUAGES[c['maximum'][0]]} and throughput\n"
        f"of **{c['minimum_throughput'][1]:.2f}–{c['maximum_throughput'][1]:.2f} million words/s**. Python-C was faster than PyO3 in {c_wins} languages\n"
        f"and PyO3 was faster in {pyo3_wins}; Python-C's geometric-mean advantage over PyO3 was\n"
        f"**{c_geomean:.2f}×**, so workload and language remain more useful selection criteria than\n"
        "a universal ranking."
    )
    text = replace_once(
        text,
        r"(?:Both Radixor runtimes|Python \(PyO3\)) recorded.*?a universal ranking\.",
        summary,
        flags=re.S,
    )

    de_py = values["de", "radixor"]
    de_table = "\n".join([
        "| Engine | Implementation | N=100 | vs Python (PyO3) |",
        "|---|---|---|---|",
        f"| **Python (PyO3)** | Rust trie | **{de_py:.1f} ns/word** | — |",
        f"| Python-C | CPython C trie | {values['de', 'radixor-c']:.1f} ns/word | {values['de', 'radixor-c'] / de_py:.2f}× slower |",
        f"| PyStemmer (de) | Snowball C | {values['de', 'PyStemmer']:.1f} ns/word | {values['de', 'PyStemmer'] / de_py:.2f}× slower |",
        f"| **CISTEM** | pure Python (`nltk`) | **{values['de', 'cistem']:,.1f} ns/word** | **{values['de', 'cistem'] / de_py:.2f}× slower** |",
    ])
    text = replace_once(text, r"\| Engine \| Implementation \| N=100.*?\n\| \*\*CISTEM\*\*[^\n]*", de_table, flags=re.S)
    text = re.sub(r"the \d+\.\d+× result", f"the {values['de', 'cistem'] / de_py:.2f}× result", text)
    publish(p, text, args.mode)
    publish_landing_summary(
        values,
        summaries,
        quality,
        geography,
        date=args.date,
        release_version=args.release_version,
        model_package_version=args.model_package_version,
        docs_root=args.docs_root,
        mode=args.mode,
    )
    publish_technology_summary(
        values,
        summaries,
        release_version=args.release_version,
        docs_root=args.docs_root,
        mode=args.mode,
    )

    homepage = {
        "source": f"Python all-language batch benchmark, {args.date}",
        "environment": {
            "processor": environment.get("processor", ""),
            "platform": environment.get("platform", ""),
            "python": f"{environment.get('python_impl', '')} {environment.get('python', '')}".strip(),
            "cpu_affinity": environment.get("cpu_affinity", []),
            "scaling_driver": environment.get("scaling_driver", ""),
            "cpu_governor": environment.get("scaling_governor", ""),
            "energy_performance_preference": environment.get("energy_performance_preference", ""),
            "amd_pstate_status": environment.get("amd_pstate_status", ""),
        },
        "versions": {"radixor": args.release_version, "radixor-c": args.release_version,
                     "radixor-models-standard": args.model_package_version,
                     "PyStemmer": "3.1.0", "snowballstemmer": "3.1.1", "NLTK": "3.10.3"},
        "batch_size": 100,
        "direct_pystemmer_comparisons": len(shared), "direct_pystemmer_wins": py["wins"],
        "geometric_mean_speedup_vs_pystemmer": round(py["geomean"], 4),
        "maximum_speedup_vs_pystemmer": {"language": py["maximum"][0], "speedup": round(py["maximum"][1], 4)},
        "radixor_throughput_mwords_per_second": {
            "minimum": {"language": py["minimum_throughput"][0], "value": round(py["minimum_throughput"][1], 4)},
            "maximum": {"language": py["maximum_throughput"][0], "value": round(py["maximum_throughput"][1], 4)}},
        "python_c": {
            "direct_pystemmer_comparisons": len(shared), "direct_pystemmer_wins": c["wins"],
            "geometric_mean_speedup_vs_pystemmer": round(c["geomean"], 4),
            "maximum_speedup_vs_pystemmer": {"language": c["maximum"][0], "speedup": round(c["maximum"][1], 4)},
            "throughput_mwords_per_second": {
                "minimum": {"language": c["minimum_throughput"][0], "value": round(c["minimum_throughput"][1], 4)},
                "maximum": {"language": c["maximum_throughput"][0], "value": round(c["maximum_throughput"][1], 4)}},
            "head_to_head_vs_pyo3": {"wins": c_wins, "losses": pyo3_wins, "geometric_mean_speedup": round(c_geomean, 4)}},
        "languages": {},
        "pystemmer_missing_for_radixor_languages": [language for language in LANGUAGES if (language, "PyStemmer") not in values],
    }
    for language in LANGUAGES:
        entry = {
            "radixor_ns_per_word": round(values[language, "radixor"], 4),
            "radixor_mwords_per_second": round(1000 / values[language, "radixor"], 4),
            "radixor_c_ns_per_word": round(values[language, "radixor-c"], 4),
            "radixor_c_mwords_per_second": round(1000 / values[language, "radixor-c"], 4),
        }
        if (language, "PyStemmer") in values:
            entry.update({
                "pystemmer_ns_per_word": round(values[language, "PyStemmer"], 4),
                "speedup_vs_pystemmer": round(values[language, "PyStemmer"] / values[language, "radixor"], 4),
                "radixor_c_speedup_vs_pystemmer": round(values[language, "PyStemmer"] / values[language, "radixor-c"], 4),
            })
        else:
            entry.update({"pystemmer_ns_per_word": None, "speedup_vs_pystemmer": None,
                          "radixor_c_speedup_vs_pystemmer": None})
        homepage["languages"][language] = entry
    publish(
        args.docs_root / "assets/data/homepage-performance.json",
        json.dumps(homepage, indent=2, ensure_ascii=False) + "\n",
        args.mode,
    )

    verb = "Verified" if args.mode == "verify" else "Published"
    print(f"{verb} {len(rows)} Python benchmark rows for {args.release_version} ({args.date}).")


if __name__ == "__main__":
    main()
