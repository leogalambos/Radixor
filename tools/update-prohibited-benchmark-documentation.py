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

"""Publish measured, documentation-only evidence for prohibited model sources."""

from __future__ import annotations

import argparse
import csv
import hashlib
import html
import math
import re
import statistics
from dataclasses import dataclass, replace
from pathlib import Path


START = "<!-- PROHIBITED-BENCHMARK-MODELS:START -->"
END = "<!-- PROHIBITED-BENCHMARK-MODELS:END -->"
LANDING_START = "<!-- PROHIBITED-LANGUAGE-PAGES:START -->"
LANDING_END = "<!-- PROHIBITED-LANGUAGE-PAGES:END -->"
PUBLIC_RELEASE_ALIASES = {"4.3.0.dirty": "4.4.0"}


@dataclass(frozen=True)
class Model:
    """Documentation identity and measured dictionary size for one model."""

    model_id: str
    language: str
    name: str
    page: str
    icon: str
    icon_label: str
    forms: int
    rows: int
    version: str
    sha256: str
    stars: int = 0


@dataclass(frozen=True)
class Publication:
    """Apply generated content or reject stale checked-in output."""

    mode: str

    def write(self, path: Path, content: str) -> None:
        """Write or verify one UTF-8 file."""

        current = path.read_text(encoding="utf-8") if path.is_file() else None
        if current == content:
            return
        if self.mode == "verify":
            raise ValueError(f"Generated prohibited benchmark documentation is stale: {path}")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


def read_csv(path: Path) -> list[dict[str, str]]:
    """Read one nonempty CSV report."""

    with path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if not rows:
        raise ValueError(f"Report is empty: {path}")
    return rows


def sha256(path: Path) -> str:
    """Hash one file with bounded auxiliary memory."""

    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def validate_run_provenance(arguments: argparse.Namespace) -> None:
    """Bind publication identity to source and runtime-classpath manifests."""

    fields: dict[str, str] = {}
    for line in arguments.provenance.read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition(": ")
        if not separator or key in fields:
            raise ValueError("Invalid prohibited benchmark provenance")
        fields[key] = value
    expected = {
        "Benchmark class": "prohibited documentation-only models",
        "Benchmark date": arguments.date,
        "Source identity": arguments.release_version,
        "Protocol": "3 warmup iterations, 5 measurement iterations, 3 forks, 1 thread, 1 s iterations",
    }
    for key, value in expected.items():
        if fields.get(key) != value:
            raise ValueError(f"Prohibited benchmark provenance differs for {key}")

    source_records: dict[str, str] = {}
    for line in arguments.source_hashes.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  (\S.*)", line)
        if match is None or match.group(2) in source_records:
            raise ValueError("Invalid prohibited source-input hash manifest")
        source_records[match.group(2)] = match.group(1)
    required_suffixes = (
        "models/model-quarantine.properties",
        "models/prohibited-model-sources.sha256",
        "tools/prohibited-model-lifecycle.py",
        "tools/run-prohibited-model-benchmarks.sh",
        "/models.tsv",
    )
    records_by_suffix: dict[str, tuple[str, str]] = {}
    for suffix in required_suffixes:
        matching = [(path, digest) for path, digest in source_records.items() if path.endswith(suffix)]
        if len(matching) != 1:
            raise ValueError(f"Prohibited source manifest does not bind exactly one {suffix}")
        records_by_suffix[suffix] = matching[0]
    if sha256(arguments.model_manifest) != records_by_suffix["/models.tsv"][1]:
        raise ValueError("Published prohibited model manifest differs from measured provenance")
    if fields.get("Quarantine manifest SHA-256") \
            != records_by_suffix["models/model-quarantine.properties"][1]:
        raise ValueError("Prohibited quarantine provenance differs")
    if fields.get("Source hash manifest SHA-256") \
            != records_by_suffix["models/prohibited-model-sources.sha256"][1]:
        raise ValueError("Prohibited source-hash provenance differs")
    if fields.get("Runtime classpath manifest SHA-256") \
            != sha256(arguments.runtime_classpath):
        raise ValueError("Prohibited runtime classpath provenance differs")


def validate_snowball_provenance(arguments: argparse.Namespace) -> None:
    """Bind the separately measured prohibited Snowball comparison reports."""

    fields: dict[str, str] = {}
    for line in arguments.snowball_provenance.read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition(": ")
        if not separator or key in fields:
            raise ValueError("Invalid prohibited Snowball benchmark provenance")
        fields[key] = value
    expected = {
        "Benchmark class": "prohibited documentation-only Snowball comparisons",
        "Benchmark date": arguments.date,
        "Source identity": arguments.release_version,
        "Protocol": "3 warmup iterations, 5 measurement iterations, 3 forks, 1 thread, 1 s iterations",
    }
    for key, value in expected.items():
        if fields.get(key) != value:
            raise ValueError(f"Prohibited Snowball provenance differs for {key}")

    records: dict[str, str] = {}
    for line in arguments.snowball_source_hashes.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  (\S.*)", line)
        if match is None or match.group(2) in records:
            raise ValueError("Invalid prohibited Snowball source-input hash manifest")
        records[match.group(2)] = match.group(1)
    required_suffixes = (
        "models/model-quarantine.properties",
        "models/prohibited-model-sources.sha256",
        "/models.tsv",
        "/prohibited-snowball-cases-" + arguments.date + ".tsv",
    )
    by_suffix: dict[str, str] = {}
    for suffix in required_suffixes:
        matching = [digest for path, digest in records.items() if path.endswith(suffix)]
        if len(matching) != 1:
            raise ValueError(f"Prohibited Snowball source manifest does not bind exactly one {suffix}")
        by_suffix[suffix] = matching[0]
    if sha256(arguments.model_manifest) != by_suffix["/models.tsv"]:
        raise ValueError("Published prohibited model manifest differs from Snowball provenance")
    catalog_suffix = "/prohibited-snowball-cases-" + arguments.date + ".tsv"
    if sha256(arguments.comparator_catalog) != by_suffix[catalog_suffix]:
        raise ValueError("Published prohibited Snowball catalog differs from measured provenance")
    if fields.get("Quarantine manifest SHA-256") \
            != by_suffix["models/model-quarantine.properties"]:
        raise ValueError("Prohibited Snowball quarantine provenance differs")
    if fields.get("Source hash manifest SHA-256") \
            != by_suffix["models/prohibited-model-sources.sha256"]:
        raise ValueError("Prohibited Snowball source-hash provenance differs")
    if fields.get("Runtime classpath manifest SHA-256") \
            != sha256(arguments.snowball_runtime_classpath):
        raise ValueError("Prohibited Snowball runtime classpath provenance differs")


def read_metadata(path: Path) -> dict[str, dict[str, str]]:
    """Read the exact non-build documentation identity catalog."""

    rows = read_csv(path)
    expected_fields = ["Model ID", "Language", "Language name", "Page", "Icon", "Icon label"]
    if list(rows[0]) != expected_fields:
        raise ValueError("Prohibited documentation metadata schema is invalid")
    result = {row["Model ID"]: row for row in rows}
    if len(result) != len(rows):
        raise ValueError("Prohibited documentation metadata contains duplicate model IDs")
    return result


def read_quarantine(path: Path) -> dict[str, tuple[str, str]]:
    """Read evidence URI and reason from the authoritative quarantine manifest."""

    result: dict[str, tuple[str, str]] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        model_id, separator, value = line.partition("=")
        uri, evidence_separator, reason = value.partition("|")
        if not separator or not evidence_separator or model_id in result:
            raise ValueError("Invalid prohibited model quarantine manifest")
        result[model_id] = (uri, reason)
    return result


def aggregate_corpus(path: Path) -> dict[str, dict[str, str]]:
    """Collapse repeated command-class rows to one validated model record."""

    result: dict[str, dict[str, str]] = {}
    for row in read_csv(path):
        model_id = row["Model ID"]
        identity = {key: value for key, value in row.items() if key not in {"Command class", "Command count"}}
        if model_id in result and result[model_id] != identity:
            raise ValueError(f"Corpus identity fields differ for {model_id}")
        result[model_id] = identity
    return result


def load_models(metadata_path: Path, quarantine_path: Path, corpus_path: Path) -> list[Model]:
    """Join exact metadata, quarantine, and corpus identities."""

    metadata = read_metadata(metadata_path)
    quarantine = read_quarantine(quarantine_path)
    corpus = aggregate_corpus(corpus_path)
    if set(metadata) != set(quarantine) or set(metadata) != set(corpus):
        raise ValueError("Prohibited metadata, quarantine, and corpus coverage differ")
    models: list[Model] = []
    for model_id in sorted(metadata):
        identity = metadata[model_id]
        measured = corpus[model_id]
        if measured["Language"] != identity["Language"]:
            raise ValueError(f"Language identity differs for {model_id}")
        models.append(
            Model(
                model_id,
                identity["Language"],
                identity["Language name"],
                identity["Page"],
                identity["Icon"],
                identity["Icon label"],
                int(measured["Distinct usable forms"]),
                int(measured["Dictionary rows"]),
                measured["Model version"],
                measured["Model SHA-256"],
            )
        )
    return models


def active_model_sizes(path: Path) -> dict[str, int]:
    """Read active model distinct-form counts from the canonical corpus report."""

    return {
        model_id: int(row["Distinct usable forms"])
        for model_id, row in aggregate_corpus(path).items()
    }


def assign_stars(models: list[Model], active_sizes: dict[str, int]) -> tuple[list[Model], dict[str, int]]:
    """Assign stable rank quintiles across active and benchmark-only dictionaries."""

    sizes = dict(active_sizes)
    sizes.update({model.model_id: model.forms for model in models})
    ranked = sorted(sizes, key=lambda model_id: (sizes[model_id], model_id))
    for index in range(1, len(ranked)):
        if (index - 1) * 5 // len(ranked) != index * 5 // len(ranked) \
                and sizes[ranked[index - 1]] == sizes[ranked[index]]:
            raise ValueError("Equal dictionary sizes cross a prohibited publication tier boundary")
    stars = {model_id: index * 5 // len(ranked) + 1 for index, model_id in enumerate(ranked)}
    return [replace(model, stars=stars[model.model_id]) for model in models], stars


def rating(stars: int, forms: int) -> str:
    """Render a five-position accessible relative-size rating."""

    label = f"Relative dictionary size {stars} of 5; {forms:,} distinct usable word forms"
    return (
        f'<span class="dictionary-rating" role="img" aria-label="{label}" '
        f'title="{label}">{"★" * stars}{"☆" * (5 - stars)}</span>'
    )


def percent(numerator: int, denominator: int) -> str:
    """Render a percentage or mathematical n/a for a zero denominator."""

    return "n/a" if denominator == 0 else f"{100.0 * numerator / denominator:.3f}%"


def speed_by_model(path: Path, benchmark_suffix: str) -> dict[str, dict[str, str]]:
    """Read and validate one canonical speed row per selected model."""

    result: dict[str, dict[str, str]] = {}
    for row in read_csv(path):
        model_id = row.get("Param: modelId", row.get("Param: modelId ", ""))
        if not model_id or model_id in result or row["Threads"] != "1" or row["Samples"] != "15":
            raise ValueError("Invalid prohibited speed identity or protocol")
        if not row["Benchmark"].endswith(benchmark_suffix):
            raise ValueError(f"Unexpected prohibited speed benchmark: {row['Benchmark']}")
        if not math.isfinite(float(row["Score"])) or float(row["Score"]) <= 0:
            raise ValueError("Invalid prohibited speed score")
        result[model_id] = row
    return result


def snowball_cases(path: Path) -> dict[str, dict[str, str]]:
    """Read the exact documentation-only prohibited Snowball catalog."""

    with path.open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    expected_fields = [
        "model_id", "language", "display_language", "candidate", "speed_benchmark",
    ]
    if not rows or list(rows[0]) != expected_fields:
        raise ValueError("Invalid prohibited Snowball comparator catalog")
    result = {row["model_id"]: row for row in rows}
    if len(result) != len(rows) or any(
            not row["candidate"].startswith("SNOWBALL_")
            or row["speed_benchmark"] != "ProhibitedModelStemmerBenchmark.snowballDirect"
            for row in rows
    ):
        raise ValueError("Invalid prohibited Snowball comparator identity")
    return result


def accuracy_by_model(path: Path, cases: dict[str, dict[str, str]]) -> dict[str, dict[str, str]]:
    """Read one exact-root row per prohibited Snowball case."""

    result: dict[str, dict[str, str]] = {}
    for row in read_csv(path):
        model_id = row["Dictionary model ID"]
        if model_id in result or model_id not in cases or row["Candidate"] != cases[model_id]["candidate"]:
            raise ValueError("Invalid prohibited Snowball accuracy identity")
        total = int(row["Total tokens"])
        root = int(row["Already-root tokens"])
        changed = int(row["Changed tokens"])
        if total <= 0 or root + changed != total:
            raise ValueError("Invalid prohibited Snowball accuracy population")
        result[model_id] = row
    if set(result) != set(cases):
        raise ValueError("Prohibited Snowball accuracy coverage differs")
    return result


def quality_by_model(path: Path) -> dict[str, list[dict[str, str]]]:
    """Group exact raw quality rows by model."""

    result: dict[str, list[dict[str, str]]] = {}
    for row in read_csv(path):
        result.setdefault(row["Dictionary model ID"], []).append(row)
    if any(len(rows) != 2 for rows in result.values()):
        raise ValueError("Prohibited quality must contain exactly two processing modes per model")
    return result


def generalization_by_model(path: Path) -> dict[str, list[dict[str, str]]]:
    """Group complete generalization rows by model."""

    result: dict[str, list[dict[str, str]]] = {}
    for row in read_csv(path):
        result.setdefault(row["model_id"], []).append(row)
    if any(len(rows) != 50 for rows in result.values()):
        raise ValueError("Prohibited generalization must contain exactly 50 rows per model")
    return result


def render_generalization(rows: list[dict[str, str]]) -> str:
    """Render medians for all ten predeclared knowledge levels."""

    lines = [
        "| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |",
        "| ---: | ---: | ---: | ---: | ---: |",
    ]
    for requested in range(10, 101, 10):
        selected = [row for row in rows if int(row["requested_percent"]) == requested]
        if len(selected) != 5:
            raise ValueError("Each prohibited generalization level requires five seeded rows")
        unseen_total = statistics.median(int(row["unseen_total"]) for row in selected)
        all_values = [100.0 * int(row["unseen_correct"]) / int(row["unseen_total"])
                      for row in selected if int(row["unseen_total"])]
        changed_values = [100.0 * int(row["unseen_changed_correct"]) / int(row["unseen_changed_total"])
                          for row in selected if int(row["unseen_changed_total"])]
        root_values = [100.0 * int(row["unseen_root_correct"]) / int(row["unseen_root_total"])
                       for row in selected if int(row["unseen_root_total"])]
        format_values = lambda values: "n/a" if not values else f"{statistics.median(values):.3f}%"
        lines.append(
            f"| {requested}% | {unseen_total:,.0f} | {format_values(all_values)} | "
            f"{format_values(changed_values)} | {format_values(root_values)} |"
        )
    return "\n".join(lines)


def render_page(model: Model, quarantine: tuple[str, str], corpus_rows: list[dict[str, str]],
                speed: dict[str, str], quality: list[dict[str, str]], generalization: list[dict[str, str]],
                date: str, release: str, population: int,
                snowball_case: dict[str, str] | None = None,
                snowball_accuracy: dict[str, str] | None = None,
                snowball_quality: list[dict[str, str]] | None = None,
                snowball_speed: dict[str, str] | None = None) -> str:
    """Render one complete, conspicuously non-installable benchmark page."""

    release = PUBLIC_RELEASE_ALIASES.get(release, release)
    corpus = corpus_rows[0]
    commands = "\n".join(
        f"| `{row['Command class']}` | {int(row['Command count']):,} |"
        for row in sorted(corpus_rows, key=lambda row: row["Command class"])
    )
    timing = int(corpus["Speed timing tokens"])
    quality_lines = []
    for row in sorted(quality, key=lambda item: item["Dictionary mode"]):
        over = "n/a" if not row["Over-stemming percentage"] else f"{float(row['Over-stemming percentage']):.6f}%"
        under = "n/a" if not row["Under-stemming percentage"] else f"{float(row['Under-stemming percentage']):.6f}%"
        quality_lines.append(
            f"| Private Radixor filesystem model | `{row['Dictionary mode']}` | "
            f"{row['Over-stemming error pairs']} / "
            f"{row['Over-stemming possible pairs']} | {over} | "
            f"{row['Under-stemming error pairs']} / {row['Under-stemming possible pairs']} | "
            f"{under} |"
        )
    if snowball_case is not None:
        if snowball_accuracy is None or snowball_quality is None or snowball_speed is None:
            raise ValueError(f"Incomplete prohibited Snowball evidence for {model.model_id}")
        for row in sorted(snowball_quality, key=lambda item: item["Dictionary mode"]):
            over = "n/a" if not row["Over-stemming percentage"] else f"{float(row['Over-stemming percentage']):.6f}%"
            under = "n/a" if not row["Under-stemming percentage"] else f"{float(row['Under-stemming percentage']):.6f}%"
            quality_lines.append(
                f"| Official Snowball 3.1.0 direct | `{row['Dictionary mode']}` | "
                f"{row['Over-stemming error pairs']} / {row['Over-stemming possible pairs']} | {over} | "
                f"{row['Under-stemming error pairs']} / {row['Under-stemming possible pairs']} | {under} |"
            )
    accuracy_lines = [
        f"| Private Radixor filesystem model | "
        f"{percent(int(corpus['All exact matches']), int(corpus['Total tokens']))} | "
        f"{percent(int(corpus['Changed exact matches']), int(corpus['Changed tokens']))} | "
        f"{percent(int(corpus['Root preserved matches']), int(corpus['Already-root tokens']))} |"
    ]
    speed_lines = [
        f"| Private Radixor filesystem model | {float(speed['Score']) / 1_000_000:.3f} | "
        f"{float(speed['Score Error (99.9%)']) / 1_000_000:.3f} | {float(speed['Score']) / timing:.1f} |"
    ]
    if snowball_case is not None:
        assert snowball_accuracy is not None and snowball_speed is not None
        accuracy_lines.append(
            f"| Official Snowball 3.1.0 direct | "
            f"{percent(int(snowball_accuracy['All exact matches']), int(snowball_accuracy['Total tokens']))} | "
            f"{percent(int(snowball_accuracy['Changed exact matches']), int(snowball_accuracy['Changed tokens']))} | "
            f"{percent(int(snowball_accuracy['Root preserved matches']), int(snowball_accuracy['Already-root tokens']))} |"
        )
        speed_lines.append(
            f"| Official Snowball 3.1.0 direct | {float(snowball_speed['Score']) / 1_000_000:.3f} | "
            f"{float(snowball_speed['Score Error (99.9%)']) / 1_000_000:.3f} | "
            f"{float(snowball_speed['Score']) / timing:.1f} |"
        )
    return f"""# {model.name} Stemmer Benchmarks {rating(model.stars, model.forms)}

!!! warning "Benchmark-only dictionary — not distributed"
    `{model.model_id}` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [{quarantine[1]}]({quarantine[0]}). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: {rating(model.stars, model.forms)}. The exact count is **{model.forms:,} distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all {population} benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `{date}` Radixor/Java `{release}` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `{model.model_id}` | `{model.version}` | `{model.language}` | {model.rows:,} | {model.forms:,} | {int(corpus['Total tokens']):,} | {int(corpus['Already-root tokens']):,} | {int(corpus['Changed tokens']):,} | {corpus['Speed timing workload']} | {timing:,} |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
{commands}

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
{chr(10).join(accuracy_lines)}

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
{chr(10).join(quality_lines)}

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

{render_generalization(generalization)}

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
{chr(10).join(speed_lines)}
"""


def replace_marked(text: str, start: str, end: str, replacement: str) -> str:
    """Replace one marker block, or append it when absent."""

    pattern = re.escape(start) + r".*?" + re.escape(end)
    if re.search(pattern, text, flags=re.DOTALL):
        return re.sub(pattern, replacement, text, count=1, flags=re.DOTALL)
    return text.rstrip() + "\n\n" + replacement + "\n"


def replace_landing_language_block(text: str, replacement: str) -> str:
    """Place benchmark-only language links inside the rendered container block."""

    pattern = re.escape(LANDING_START) + r".*?" + re.escape(LANDING_END)
    without_existing = re.sub(
        pattern + r"(?:\r?\n){0,2}", "", text, count=1, flags=re.DOTALL
    )
    anchor = "  </div>\n</main>\n{% endblock %}"
    if without_existing.count(anchor) != 1:
        raise ValueError("Landing-page rendered container closing anchor is missing or ambiguous")
    return without_existing.replace(anchor, replacement + "\n\n" + anchor, 1)


def flag(icon: str) -> str:
    """Render a country flag or the common ambiguity globe."""

    if icon == "globe":
        return "🌐"
    return "".join(chr(0x1F1E6 + ord(character) - ord("A")) for character in icon)


def update_indexes(docs: Path, models: list[Model], publication: Publication) -> None:
    """Add separate benchmark-only tables and homepage links."""

    table = [
        START,
        "## Benchmark-only prohibited dictionaries",
        "",
        "These dictionaries are **not distributed, installable, registered, published, or supported**. "
        "They are listed separately because private, checksum-pinned bytes were benchmarked for transparency while file-applicable redistribution rights remain unresolved.",
        "",
        "| Language | Private model ID | Relative dictionary size | Status | Benchmark page |",
        "| --- | --- | --- | --- | --- |",
    ]
    for model in sorted(models, key=lambda item: (item.name.casefold(), item.model_id)):
        table.append(
            f"| {model.name} | `{model.model_id}` | {rating(model.stars, model.forms)} ({model.forms:,}) | "
            f"Benchmark only; not distributed | [{model.name}]({model.page}) |"
        )
    table.append(END)
    index = docs / "benchmarks/languages/index.md"
    publication.write(index, replace_marked(index.read_text(encoding="utf-8"), START, END, "\n".join(table)))

    links = []
    for model in sorted(models, key=lambda item: (item.name.casefold(), item.model_id)):
        links.append(
            f'<a class="rx2-language rx2-language--prohibited" href="{{{{ base_url }}}}/benchmarks/languages/{model.page.removesuffix(".md")}/" '
            f'aria-label="Open benchmark-only {html.escape(model.name)} evidence; not distributed; {html.escape(model.icon_label)}">'
            f'<i class="rx2-language-icon" aria-hidden="true" title="{html.escape(model.icon_label)}">{flag(model.icon)}</i>'
            f'<span>{html.escape(model.name)}</span><small>{model.model_id} · benchmark only</small>'
            f'{rating(model.stars, model.forms)}</a>'
        )
    landing_block = (
        f'{LANDING_START}\n<section class="rx2-languages-strip rx2-languages-strip--prohibited">\n'
        f'<div class="rx2-language-label"><div><strong>{len(models)} benchmark-only dictionaries</strong>'
        '<span>Not distributed or installable; license evidence unresolved</span></div></div>\n'
        '<div class="rx2-language-list">' + "".join(links) + '</div>\n</section>\n' + LANDING_END
    )
    landing = docs / "overrides/landing.html"
    publication.write(
        landing,
        replace_landing_language_block(landing.read_text(encoding="utf-8"), landing_block),
    )

    navigation = docs.parent / "mkdocs.yml"
    text = navigation.read_text(encoding="utf-8")
    nav_lines = ["        - Benchmark-only prohibited models:"]
    nav_lines.extend(
        f"           - {model.name}: benchmarks/languages/{model.page}"
        for model in sorted(models, key=lambda item: (item.name.casefold(), item.model_id))
    )
    nav_block = "\n".join(nav_lines) + "\n"
    marker = "     - Methods and Reproduction:\n"
    text = re.sub(r"        - Benchmark-only prohibited models:\n(?:           - .*\n)+", "", text)
    if marker not in text:
        raise ValueError("MkDocs language navigation marker is missing")
    publication.write(navigation, text.replace(marker, nav_block + marker, 1))


def update_active_ratings(docs: Path, active_sizes: dict[str, int], stars: dict[str, int],
                          publication: Publication) -> None:
    """Rebase existing active ratings onto the complete benchmarked population."""

    pages = [path for path in (docs / "benchmarks/languages").glob("*.md") if path.name != "index.md"]
    for model_id, forms in active_sizes.items():
        candidates = [
            path for path in pages
            if f"| `{model_id}` |" in path.read_text(encoding="utf-8")
        ]
        if len(candidates) != 1:
            raise ValueError(f"Expected one active benchmark page for {model_id}, found {candidates}")
        page = candidates[0]
        text = page.read_text(encoding="utf-8")
        old = re.compile(
            rf'<span class="dictionary-rating" role="img" aria-label="Relative dictionary size \d of 5; '
            rf'{forms:,} distinct usable word forms" title="Relative dictionary size \d of 5; '
            rf'{forms:,} distinct usable word forms">[★☆]{{5}}</span>'
        )
        text, count = old.subn(rating(stars[model_id], forms), text)
        if count == 0:
            raise ValueError(f"No rating for active model {model_id} on {page}")
        text = text.replace("relative to all active user-facing models", "relative to all benchmarked dictionaries")
        text = text.replace("among the active user-facing models", "among all benchmarked dictionaries")
        publication.write(page, text)


def publish_snapshots(arguments: argparse.Namespace, publication: Publication) -> None:
    """Copy dated private reports and write a checksum-bound active pointer."""

    data = arguments.docs_root / "benchmarks/data"
    inputs = {
        "corpus": arguments.corpus,
        "quality": arguments.quality,
        "generalization": arguments.generalization,
        "speed": arguments.speed,
        "provenance": arguments.provenance,
        "source_hashes": arguments.source_hashes,
        "runtime_classpath": arguments.runtime_classpath,
        "model_manifest": arguments.model_manifest,
        "comparator_catalog": arguments.comparator_catalog,
        "snowball_accuracy": arguments.snowball_accuracy,
        "snowball_quality": arguments.snowball_quality,
        "snowball_speed": arguments.snowball_speed,
        "snowball_provenance": arguments.snowball_provenance,
        "snowball_source_hashes": arguments.snowball_source_hashes,
        "snowball_runtime_classpath": arguments.snowball_runtime_classpath,
    }
    names = {
        key: f"prohibited-{key.replace('_', '-')}-{arguments.date}{path.suffix}"
        for key, path in inputs.items()
    }
    for key, source in inputs.items():
        target = data / names[key]
        content = source.read_bytes()
        if publication.mode == "verify":
            if not target.is_file() or target.read_bytes() != content:
                raise ValueError(f"Published prohibited snapshot differs: {target}")
        else:
            target.write_bytes(content)
    checksum_lines = []
    for name in sorted(names.values()):
        digest = hashlib.sha256((data / name).read_bytes()).hexdigest()
        checksum_lines.append(f"{digest}  {name}")
    publication.write(data / "prohibited-snapshots.sha256", "\n".join(checksum_lines) + "\n")
    pointer = "# Active documentation-only prohibited-model benchmark snapshots.\n" + "".join(
        f"{key}={names[key]}\n" for key in sorted(names)
    )
    publication.write(data / "prohibited-active-snapshots.properties", pointer)


def read_snapshot_pointer(path: Path) -> dict[str, str]:
    """Read a simple active-snapshot pointer with safe local file names."""

    entries: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        key, separator, value = stripped.partition("=")
        if not separator or not key or not value or key in entries \
                or Path(value).name != value:
            raise ValueError(f"Invalid active snapshot pointer: {path}")
        entries[key] = value
    return entries


def resolve_active_snapshots(arguments: argparse.Namespace) -> argparse.Namespace:
    """Resolve a self-contained published verification from active metadata."""

    source_names = (
        "active_corpus", "corpus", "quality", "generalization", "speed",
        "provenance", "source_hashes", "runtime_classpath", "model_manifest",
        "comparator_catalog", "snowball_accuracy", "snowball_quality", "snowball_speed",
        "snowball_provenance", "snowball_source_hashes", "snowball_runtime_classpath",
    )
    if arguments.active_snapshots is None:
        missing = [name for name in source_names if getattr(arguments, name) is None]
        if missing or arguments.date is None or arguments.release_version is None:
            raise ValueError(f"Missing prohibited publication arguments: {missing}")
        return arguments
    if any(getattr(arguments, name) is not None for name in source_names) \
            or arguments.date is not None or arguments.release_version is not None:
        raise ValueError("Active-snapshot verification cannot be mixed with explicit report inputs")
    private_entries = read_snapshot_pointer(arguments.active_snapshots)
    expected = {
        "corpus", "generalization", "model_manifest", "provenance", "quality",
        "runtime_classpath", "source_hashes", "speed",
        "comparator_catalog", "snowball_accuracy", "snowball_quality", "snowball_speed",
        "snowball_provenance", "snowball_source_hashes", "snowball_runtime_classpath",
    }
    if set(private_entries) != expected:
        raise ValueError("Prohibited active snapshot pointer has unexpected coverage")
    data = arguments.active_snapshots.parent
    for name in expected:
        setattr(arguments, name, data / private_entries[name])
    public_entries = read_snapshot_pointer(data / "active-snapshots.properties")
    corpus_name = public_entries.get("corpus")
    if corpus_name is None:
        raise ValueError("Public active snapshot pointer does not select a corpus")
    arguments.active_corpus = data / corpus_name
    provenance_fields: dict[str, str] = {}
    for line in arguments.provenance.read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition(": ")
        if separator and key not in provenance_fields:
            provenance_fields[key] = value
    arguments.date = provenance_fields.get("Benchmark date")
    arguments.release_version = provenance_fields.get("Source identity")
    if arguments.date is None or arguments.release_version is None:
        raise ValueError("Published prohibited provenance has no date or source identity")
    return arguments


def parse_arguments() -> argparse.Namespace:
    """Parse publication inputs."""

    parser = argparse.ArgumentParser()
    parser.add_argument("--docs-root", type=Path, default=Path("docs"))
    parser.add_argument("--metadata", type=Path, default=Path("models/prohibited-model-documentation.csv"))
    parser.add_argument("--quarantine", type=Path, default=Path("models/model-quarantine.properties"))
    parser.add_argument("--active-snapshots", type=Path)
    parser.add_argument("--active-corpus", type=Path)
    parser.add_argument("--corpus", type=Path)
    parser.add_argument("--quality", type=Path)
    parser.add_argument("--generalization", type=Path)
    parser.add_argument("--speed", type=Path)
    parser.add_argument("--provenance", type=Path)
    parser.add_argument("--source-hashes", type=Path)
    parser.add_argument("--runtime-classpath", type=Path)
    parser.add_argument("--model-manifest", type=Path)
    parser.add_argument("--comparator-catalog", type=Path)
    parser.add_argument("--snowball-accuracy", type=Path)
    parser.add_argument("--snowball-quality", type=Path)
    parser.add_argument("--snowball-speed", type=Path)
    parser.add_argument("--snowball-provenance", type=Path)
    parser.add_argument("--snowball-source-hashes", type=Path)
    parser.add_argument("--snowball-runtime-classpath", type=Path)
    parser.add_argument("--date")
    parser.add_argument("--release-version")
    parser.add_argument("--mode", choices=("update", "verify"), default="update")
    return parser.parse_args()


def main() -> None:
    """Validate, publish, and render the complete prohibited evidence set."""

    arguments = resolve_active_snapshots(parse_arguments())
    publication = Publication(arguments.mode)
    validate_run_provenance(arguments)
    validate_snowball_provenance(arguments)
    models = load_models(arguments.metadata, arguments.quarantine, arguments.corpus)
    active_sizes = active_model_sizes(arguments.active_corpus)
    if set(active_sizes) & {model.model_id for model in models}:
        raise ValueError("Prohibited model IDs overlap the active corpus")
    models, stars = assign_stars(models, active_sizes)
    expected = {model.model_id for model in models}
    corpus_rows = read_csv(arguments.corpus)
    corpora = {model_id: [row for row in corpus_rows if row["Model ID"] == model_id] for model_id in expected}
    speeds = speed_by_model(arguments.speed, "ProhibitedModelStemmerBenchmark.radixor")
    qualities = quality_by_model(arguments.quality)
    generalizations = generalization_by_model(arguments.generalization)
    if set(speeds) != expected or set(qualities) != expected or set(generalizations) != expected:
        raise ValueError("Prohibited report coverage differs from the exact model cohort")
    cases = snowball_cases(arguments.comparator_catalog)
    if not set(cases) <= expected:
        raise ValueError("Prohibited Snowball catalog contains a model outside the exact cohort")
    snowball_accuracies = accuracy_by_model(arguments.snowball_accuracy, cases)
    snowball_qualities = quality_by_model(arguments.snowball_quality)
    snowball_speeds = speed_by_model(
        arguments.snowball_speed, "ProhibitedModelStemmerBenchmark.snowballDirect",
    )
    if set(snowball_qualities) != set(cases) or set(snowball_speeds) != set(cases):
        raise ValueError("Prohibited Snowball report coverage differs from its exact catalog")
    for model_id, rows in snowball_qualities.items():
        if any(row["Stemmer"] != cases[model_id]["candidate"] for row in rows):
            raise ValueError(f"Prohibited Snowball quality candidate differs for {model_id}")
    quarantine = read_quarantine(arguments.quarantine)
    population = len(active_sizes) + len(models)
    for model in models:
        page = arguments.docs_root / "benchmarks/languages" / model.page
        publication.write(
            page,
            render_page(model, quarantine[model.model_id], corpora[model.model_id], speeds[model.model_id],
                        qualities[model.model_id], generalizations[model.model_id], arguments.date,
                        arguments.release_version, population, cases.get(model.model_id),
                        snowball_accuracies.get(model.model_id), snowball_qualities.get(model.model_id),
                        snowball_speeds.get(model.model_id)),
        )
    update_active_ratings(arguments.docs_root, active_sizes, stars, publication)
    update_indexes(arguments.docs_root, models, publication)
    publish_snapshots(arguments, publication)
    print(f"{'Verified' if arguments.mode == 'verify' else 'Published'} {len(models)} prohibited model pages.")


if __name__ == "__main__":
    main()
