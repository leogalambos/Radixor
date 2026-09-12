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

"""Update published benchmark tables from deterministic corpus and JMH CSV reports."""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import html
import math
import re
from collections import defaultdict
from dataclasses import dataclass, replace
from pathlib import Path


LANGUAGES = {
    "czech.md": "CS_CZ",
    "danish.md": "DA_DK",
    "dutch.md": "NL_NL",
    "english.md": "US_UK",
    "finnish.md": "FI_FI",
    "french.md": "FR_FR",
    "german.md": "DE_DE",
    "hebrew.md": "HE_IL",
    "hungarian.md": "HU_HU",
    "italian.md": "IT_IT",
    "norwegian-bokmal.md": "NB_NO",
    "norwegian-nynorsk.md": "NN_NO",
    "persian.md": "FA_IR",
    "polish.md": "PL_PL",
    "portuguese.md": "PT_PT",
    "russian.md": "RU_RU",
    "spanish.md": "ES_ES",
    "swedish.md": "SV_SE",
    "ukrainian.md": "UK_UA",
    "yiddish.md": "YI",
}

REPOSITORY = Path(__file__).resolve().parents[1]
MODELS_ROOT = REPOSITORY / "models"
TOPOLOGY_ROLES = {"default", "standalone", "optional"}
LEGACY_PAGE_NAMES = {language: file_name for file_name, language in LANGUAGES.items()}
SNOWBALL_CASES: dict[str, tuple[str, bool]] = {}
POLIMORF_START = "<!-- POLIMORF-BENCHMARK:START -->"
POLIMORF_END = "<!-- POLIMORF-BENCHMARK:END -->"


@dataclass(frozen=True)
class ModelInfo:
    """Authoritative documentation metadata for one user-facing model."""

    model_id: str
    role: str
    language: str
    display_name: str
    language_name: str
    version: str
    right_to_left: bool
    dictionary_rows: int
    distinct_forms: int
    sha256: str
    stars: int = 0

    @property
    def page_name(self) -> str:
        """Return the stable page file for this language default."""

        if self.language in LEGACY_PAGE_NAMES:
            return LEGACY_PAGE_NAMES[self.language]
        suffix = "-default"
        stem = self.model_id[: -len(suffix)] if self.model_id.endswith(suffix) else self.model_id
        return f"{stem}.md"


@dataclass(frozen=True)
class SnowballCase:
    """One exact-language case generated from the Java benchmark authority."""

    case_name: str
    language: str
    model_id: str
    display_language: str
    speed_method: str
    quality_candidate: str
    direct_available: bool
    lucene_available: bool
    homepage_chart: bool
    homepage_aggregate: bool


@dataclass(frozen=True)
class HomepageGeography:
    """Documentation-only homepage icon policy for one language."""

    icon: str
    label: str


def _required_gradle_string(text: str, field: str, path: Path) -> str:
    match = re.search(rf"^\s*{re.escape(field)}\s*=\s*'([^']+)'\s*$", text, re.MULTILINE)
    if match is None:
        raise ValueError(f"Missing {field} in {path}")
    return match.group(1)


def _strip_dictionary_remark(line: str) -> str:
    positions = [position for position in (line.find("#"), line.find("//")) if position >= 0]
    return line if not positions else line[: min(positions)]


def _contains_whitespace(value: str) -> bool:
    return any(character.isspace() for character in value)


def dictionary_size(path: Path) -> tuple[int, int]:
    """Count parser-compatible rows and exact case-preserved distinct forms.

    Runtime is O(total fields); auxiliary memory is O(distinct forms in the
    current dictionary). No data from an earlier dictionary is retained.
    """

    rows = 0
    forms: set[str] = set()
    with gzip.open(path, mode="rt", encoding="utf-8", errors="strict", newline="") as source:
        for physical_line in source:
            logical_line = _strip_dictionary_remark(physical_line).strip()
            if not logical_line:
                continue
            fields = logical_line.split("\t")
            stem = fields[0].strip()
            if not stem or _contains_whitespace(stem):
                continue
            rows += 1
            forms.add(stem)
            for field in fields[1:]:
                variant = field.strip()
                if variant and not _contains_whitespace(variant):
                    forms.add(variant)
    return rows, len(forms)


def load_model_infos(models_root: Path = MODELS_ROOT) -> list[ModelInfo]:
    """Load and rank the complete deterministic user-facing model universe."""

    topology = models_root / "model-projects.properties"
    infos: list[ModelInfo] = []
    for raw_line in topology.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        model_id, separator, role = line.partition("=")
        if not separator or not model_id or role not in TOPOLOGY_ROLES:
            raise ValueError(f"Invalid model topology line: {raw_line!r}")
        project = models_root / model_id
        build_file = project / "build.gradle"
        build_text = build_file.read_text(encoding="utf-8")
        language = _required_gradle_string(build_text, "language", build_file)
        display_name = _required_gradle_string(build_text, "displayName", build_file)
        language_name = display_name.split(" — ", 1)[0].removesuffix(" default model")
        version = (project / "model-version.txt").read_text(encoding="utf-8").strip()
        dictionary = project / "src" / "modelInput" / "stemmer.gz"
        rows, distinct_forms = dictionary_size(dictionary)
        digest = hashlib.sha256()
        with dictionary.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
        infos.append(
            ModelInfo(
                model_id=model_id,
                role=role,
                language=language,
                display_name=display_name,
                language_name=language_name,
                version=version,
                right_to_left=bool(
                    re.search(r"^\s*rightToLeft\s*=\s*true\s*$", build_text, re.MULTILINE)
                ),
                dictionary_rows=rows,
                distinct_forms=distinct_forms,
                sha256=digest.hexdigest(),
            )
        )
    if not infos:
        raise ValueError("The active topology contains no user-facing models")
    return assign_star_tiers(infos)


def assign_star_tiers(infos: list[ModelInfo]) -> list[ModelInfo]:
    """Assign rank-based quintiles without splitting equal-count boundaries."""

    if not infos:
        raise ValueError("At least one model is required for star-tier assignment")
    ranked = sorted(infos, key=lambda info: (info.distinct_forms, info.model_id))
    for index in range(1, len(ranked)):
        previous_tier = (index - 1) * 5 // len(ranked) + 1
        current_tier = index * 5 // len(ranked) + 1
        if (
            previous_tier != current_tier
            and ranked[index - 1].distinct_forms == ranked[index].distinct_forms
        ):
            raise ValueError(
                "Equal distinct-form counts would make a rank boundary ambiguous: "
                f"{ranked[index].distinct_forms}"
            )
    stars_by_id = {
        info.model_id: rank * 5 // len(ranked) + 1 for rank, info in enumerate(ranked)
    }
    result = [replace(info, stars=stars_by_id[info.model_id]) for info in infos]
    return result


def include_prohibited_rating_population(
    docs_root: Path, infos: list[ModelInfo]
) -> list[ModelInfo]:
    """Rank public models with documented private models when that snapshot exists."""

    pointer = docs_root / "benchmarks/data/prohibited-active-snapshots.properties"
    if not pointer.is_file():
        return infos
    entries: dict[str, str] = {}
    for line in pointer.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        key, separator, value = stripped.partition("=")
        if not separator or not key or not value or key in entries:
            raise ValueError("Prohibited snapshot pointer is malformed")
        entries[key] = value
    corpus_name = entries.get("corpus")
    if corpus_name is None or Path(corpus_name).name != corpus_name:
        raise ValueError("Prohibited snapshot pointer has no safe corpus entry")
    private_sizes: dict[str, int] = {}
    with (pointer.parent / corpus_name).open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            model_id = row["Model ID"]
            distinct = int(row["Distinct usable forms"])
            if model_id in private_sizes and private_sizes[model_id] != distinct:
                raise ValueError(f"Prohibited corpus conflicts for {model_id}")
            private_sizes[model_id] = distinct
    public_ids = {info.model_id for info in infos}
    overlap = public_ids.intersection(private_sizes)
    if overlap:
        raise ValueError(
            f"Prohibited rating population overlaps public models: {sorted(overlap)}"
        )
    ranked = sorted(
        [(info.distinct_forms, info.model_id) for info in infos]
        + [(forms, model_id) for model_id, forms in private_sizes.items()]
    )
    stars = {
        model_id: rank * 5 // len(ranked) + 1
        for rank, (_, model_id) in enumerate(ranked)
    }
    return [replace(info, stars=stars[info.model_id]) for info in infos]

LANGUAGE_IDENTITY_WORDS = {
    "CS_CZ": {"CZECH"},
    "DA_DK": {"DANISH"},
    "NL_NL": {"DUTCH"},
    "US_UK": {"ENGLISH"},
    "FI_FI": {"FINNISH"},
    "FR_FR": {"FRENCH"},
    "DE_DE": {"GERMAN"},
    "HE_IL": {"HEBREW"},
    "HU_HU": {"HUNGARIAN"},
    "IT_IT": {"ITALIAN"},
    "NB_NO": {"NORWEGIAN", "BOKMAL"},
    "NN_NO": {"NORWEGIAN", "NYNORSK"},
    "FA_IR": {"PERSIAN"},
    "PL_PL": {"POLISH"},
    "PT_PT": {"PORTUGUESE"},
    "RU_RU": {"RUSSIAN"},
    "ES_ES": {"SPANISH"},
    "SV_SE": {"SWEDISH"},
    "ST_ZA": {"SESOTHO"},
    "UK_UA": {"UKRAINIAN"},
    "YI": {"YIDDISH"},
}

COMMAND_MEANINGS = {
    "AppendCharacterCommand": "Appends one character to the end of the word form.",
    "BackwardCompoundCommand": "Applies a multi-step backward patch made from skip, delete, insert, and replace operations.",
    "DeletePrefixCommand": "Deletes one or more leading characters from the word form in forward traversal.",
    "DeleteSuffixCommand": "Deletes one or more trailing characters from the word form.",
    "ForwardCompoundCommand": "Applies a multi-step forward patch made from skip, delete, insert, and replace operations.",
    "PrependCharacterCommand": "Prepends one character to the beginning of the word form.",
    "PreserveCommand": "Returns the word form unchanged because it already matches the preferred root.",
    "ReplaceFirstCharacterCommand": "Replaces the first character of the word form in forward traversal.",
    "ReplaceLastCharacterCommand": "Replaces the final character of the word form.",
}

AUXILIARY_NAMES = {
    "changedCorrectMatches",
    "changedEvaluatedTokens",
    "correctMatches",
    "evaluatedTokens",
    "rootEvaluatedTokens",
    "rootPreservedMatches",
}


@dataclass(frozen=True)
class Key:
    benchmark: str
    parameters: tuple[tuple[str, str], ...]

    @property
    def method(self) -> str:
        return self.benchmark.rsplit(".", 1)[-1]

    def parameter(self, name: str) -> str:
        return dict(self.parameters).get(name, "")


@dataclass
class JmhData:
    primary: dict[Key, dict[str, str]]
    auxiliary: dict[Key, dict[str, float]]


@dataclass(frozen=True)
class SpeedProtocol:
    """Supported published JMH speed protocol inferred from report evidence."""

    samples: int
    forks: int
    warmup_iterations: int
    measurement_iterations: int
    threads: int = 1

    def page_sentence(self) -> str:
        """Return the protocol sentence rendered on language pages."""

        return (
            f"Speed uses JMH average time, {self.warmup_iterations} warmup iterations, "
            f"{self.measurement_iterations} measurement iterations, {self.forks} independent "
            f"forks, and {self.threads} thread."
        )

    def methodology_sentence(self) -> str:
        """Return the prose fragment rendered by the methodology page."""

        number_words = {1: "one", 3: "three", 5: "five", 7: "seven"}
        return (
            f"Published speed and coverage-speed methods use {number_words[self.forks]} "
            f"independent forks, {number_words[self.warmup_iterations]} one-second warmup "
            f"iterations and {number_words[self.measurement_iterations]} one-second measurement "
            f"iterations per fork, {number_words[self.threads]} benchmark thread, and a fixed 6 GiB heap."
        )


@dataclass(frozen=True)
class Publication:
    """Writes generated documentation or rejects a stale checked-in snapshot."""

    mode: str

    def write(self, path: Path, content: str) -> None:
        current = path.read_text(encoding="utf-8") if path.is_file() else None
        if current == content:
            return
        if self.mode == "verify":
            raise SystemExit(f"Generated benchmark documentation is stale: {path}")
        path.write_text(content, encoding="utf-8")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--docs-root", type=Path, default=Path("docs"))
    parser.add_argument("--readme", type=Path, default=Path("README.md"))
    parser.add_argument("--corpus", type=Path, required=True)
    parser.add_argument("--accuracy", type=Path, required=True)
    parser.add_argument("--speed", type=Path, required=True)
    parser.add_argument("--coverage-accuracy", type=Path, required=True)
    parser.add_argument("--coverage-speed", type=Path, required=True)
    parser.add_argument("--quality", type=Path, required=True)
    parser.add_argument("--snowball-catalog", type=Path, required=True)
    parser.add_argument("--date", required=True)
    parser.add_argument("--release-version", required=True)
    parser.add_argument("--mode", choices=("update", "verify"), default="update")
    return parser.parse_args()


def verify_active_inputs(manifest: Path, inputs: dict[str, Path]) -> None:
    """Reject publication inputs that are not selected by the active manifest."""

    pointers: dict[str, str] = {}
    for line in manifest.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or key in pointers:
            raise ValueError("Active snapshot manifest contains an invalid or duplicate entry")
        pointers[key] = value
    for key, source in inputs.items():
        selected = pointers.get(key)
        if selected is None:
            raise ValueError(f"Active snapshot manifest does not select {key}")
        expected = (manifest.parent / selected).resolve()
        if source.resolve() != expected:
            raise ValueError(
                f"Publication input {key} is not active: {source} (expected {expected})"
            )


def read_active_generalization_summary(
    manifest: Path, expected_languages: set[str]
) -> tuple[int, int]:
    """Return active generalization scenario and language counts."""

    pointers: dict[str, str] = {}
    for raw_line in manifest.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or not key or not value or key in pointers:
            raise ValueError("Active snapshot manifest contains an invalid or duplicate entry")
        pointers[key] = value
    selected = pointers.get("generalization")
    if selected is None:
        raise ValueError("Active snapshot manifest does not select generalization")
    source_path = manifest.parent / selected
    languages: set[str] = set()
    scenario_count = 0
    with source_path.open(encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames is None or "language" not in reader.fieldnames:
            raise ValueError("Active generalization snapshot lacks the language column")
        for row in reader:
            languages.add(row["language"])
            scenario_count += 1
    if languages != expected_languages:
        raise ValueError("Active generalization snapshot does not cover every active default")
    return scenario_count, len(languages)


def read_snowball_catalog(path: Path) -> list[SnowballCase]:
    """Read and strictly validate the catalog emitted by the Java authority."""

    cases: list[SnowballCase] = []
    with path.open(newline="", encoding="utf-8") as source:
        reader = csv.DictReader(source)
        expected = [
            "Case", "Language", "Model ID", "Display language", "Direct available",
            "Lucene available", "Homepage chart", "Homepage aggregate",
        ]
        expected[4:4] = ["Speed method", "Quality candidate"]
        if reader.fieldnames != expected:
            raise ValueError("Snowball catalog schema differs from the Java authority")
        for row in reader:
            boolean_values = {
                name: row[name] == "true"
                for name in ("Direct available", "Lucene available", "Homepage chart", "Homepage aggregate")
            }
            if any(row[name] not in {"true", "false"} for name in boolean_values):
                raise ValueError("Snowball catalog contains a non-boolean availability value")
            cases.append(SnowballCase(
                row["Case"], row["Language"], row["Model ID"], row["Display language"],
                row["Speed method"], row["Quality candidate"],
                boolean_values["Direct available"], boolean_values["Lucene available"],
                boolean_values["Homepage chart"], boolean_values["Homepage aggregate"],
            ))
    if not cases or any(
        len({getattr(case, field) for case in cases}) != len(cases)
        for field in ("case_name", "model_id", "speed_method", "quality_candidate")
    ):
        raise ValueError(
            "Snowball catalog must contain unique cases, models, speed methods, and quality candidates"
        )
    if any(not case.speed_method or not case.quality_candidate for case in cases):
        raise ValueError("Every Snowball case must declare its exact speed method and quality candidate")
    chart = [case for case in cases if case.homepage_chart]
    aggregate = [case for case in cases if case.homepage_aggregate]
    if len(chart) != len(cases) or len(aggregate) != len(chart):
        raise ValueError(
            "Snowball homepage policy must chart and aggregate every direct case"
        )
    if not all(case.direct_available for case in chart):
        raise ValueError("Every homepage Snowball case must expose the official direct implementation")
    return cases


def read_homepage_geography(
    path: Path, expected_languages: set[str]
) -> dict[str, HomepageGeography]:
    """Read the complete docs-owned country/globe display policy."""

    entries: dict[str, HomepageGeography] = {}
    with path.open(newline="", encoding="utf-8") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames != ["Language", "Icon", "Label"]:
            raise ValueError("Homepage geography catalog schema is invalid")
        for row in reader:
            language = row["Language"]
            icon = row["Icon"]
            label = row["Label"]
            if language in entries or not label:
                raise ValueError("Homepage geography catalog has a duplicate or blank entry")
            if icon != "globe" and not re.fullmatch(r"[A-Z]{2}", icon):
                raise ValueError(f"Invalid homepage geography icon for {language}: {icon}")
            entries[language] = HomepageGeography(icon, label)
    if not expected_languages.issubset(entries):
        missing = sorted(expected_languages - set(entries))
        raise ValueError(
            f"Homepage geography coverage omits active defaults; missing={missing}"
        )
    return {language: entries[language] for language in expected_languages}


def geography_glyph(icon: str) -> str:
    """Return a country flag emoji or the single transnational globe glyph."""

    if icon == "globe":
        return "🌐"
    return "".join(chr(0x1F1E6 + ord(character) - ord("A")) for character in icon)


def read_jmh(path: Path) -> JmhData:
    primary: dict[Key, dict[str, str]] = {}
    auxiliary: dict[Key, dict[str, float]] = defaultdict(dict)
    with path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            benchmark_with_metric = row["Benchmark"]
            benchmark, separator, metric = benchmark_with_metric.partition(":")
            parameters = tuple(
                (name.removeprefix("Param: "), value)
                for name, value in row.items()
                if name.startswith("Param: ") and value
            )
            key = Key(benchmark, parameters)
            if separator:
                auxiliary[key][metric] = float(row["Score"])
            else:
                primary[key] = row
    return JmhData(primary, dict(auxiliary))


def infer_speed_protocol(data: JmhData, report_name: str) -> SpeedProtocol:
    """Validate report concurrency and map its sample count to a known protocol."""

    if not data.primary:
        raise ValueError(f"{report_name} contains no primary JMH speed rows")
    threads: set[int] = set()
    samples: set[int] = set()
    for row in data.primary.values():
        try:
            threads.add(int(row["Threads"]))
            samples.add(int(row["Samples"]))
        except (KeyError, ValueError) as error:
            raise ValueError(
                f"{report_name} contains invalid Threads or Samples fields"
            ) from error
    if threads != {1}:
        raise ValueError(f"{report_name} must use exactly one thread; found {sorted(threads)}")
    if len(samples) != 1:
        raise ValueError(f"{report_name} has inconsistent sample counts: {sorted(samples)}")
    sample_count = next(iter(samples))
    supported = {
        15: SpeedProtocol(15, 3, 3, 5),
        21: SpeedProtocol(21, 3, 5, 7),
    }
    if sample_count not in supported:
        raise ValueError(
            f"{report_name} uses unsupported sample count {sample_count}; expected 15 or 21"
        )
    return supported[sample_count]


def percentage(numerator: float, denominator: float) -> float | None:
    """Return a percentage, or ``None`` when the population is empty."""

    return None if denominator == 0 else 100.0 * numerator / denominator


def accuracy(data: JmhData, key: Key) -> tuple[float | None, float | None, float | None]:
    counters = data.auxiliary[key]
    return (
        percentage(counters["correctMatches"], counters["evaluatedTokens"]),
        percentage(
            counters["changedCorrectMatches"], counters["changedEvaluatedTokens"]
        ),
        percentage(
            counters["rootPreservedMatches"], counters["rootEvaluatedTokens"]
        ),
    )


def read_corpora(path: Path) -> dict[str, dict[str, object]]:
    corpora: dict[str, dict[str, object]] = {}
    with path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            model_id = row["Model ID"]
            entry = corpora.setdefault(
                model_id,
                {
                    "model": row["Model ID"],
                    "version": row["Model version"],
                    "sha256": row["Model SHA-256"],
                    "rows": int(row["Dictionary rows"]),
                    "distinct": int(row.get("Distinct usable forms") or 0),
                    "total": int(row["Total tokens"]),
                    "roots": int(row["Already-root tokens"]),
                    "changed": int(row["Changed tokens"]),
                    "timing_basis": row.get("Speed timing workload")
                    or (
                        "changed tokens"
                        if int(row["Changed tokens"]) > 0
                        else "root-only tokens"
                    ),
                    "timing": int(row["Speed timing tokens"]),
                    "all_exact": int(row["All exact matches"]),
                    "changed_exact": int(row["Changed exact matches"]),
                    "root_exact": int(row["Root preserved matches"]),
                    "commands": [],
                },
            )
            entry["commands"].append((row["Command class"], int(row["Command count"])))
    return corpora


def format_integer(value: int) -> str:
    return f"{value:,}"


def render_corpus_sections(language: str, entry: dict[str, object]) -> str:
    total = int(entry["total"])
    lines = [
        "## Dictionary Corpus",
        "",
        "| Model ID | Model version | Language | Dictionary rows | Distinct usable forms | Complete quality tokens | Already-root tokens | Changed tokens | Timing workload | JMH timing tokens |",
        "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |",
        f"| `{entry['model']}` | `{entry['version']}` | `{language}` | {format_integer(int(entry['rows']))} | "
        f"{format_integer(int(entry['distinct']))} | {format_integer(total)} | {format_integer(int(entry['roots']))} | "
        f"{format_integer(int(entry['changed']))} | {entry['timing_basis']} | "
        f"{format_integer(int(entry['timing']))} |",
        "",
        "## Radixor Patch Command Distribution",
        "",
        "Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. "
        "This distribution shows which runtime command class is selected by the trained trie for the complete default-model "
        f"dictionary. The total number of preferred patch commands analyzed for this language is **{format_integer(total)}**.",
        "",
        "| Command class | Meaning | Word forms | Share |",
        "| --- | --- | ---: | ---: |",
    ]
    command_total = 0
    for command, count in entry["commands"]:
        if command not in COMMAND_MEANINGS:
            raise ValueError(f"Undocumented patch command class: {command}")
        command_total += count
        lines.append(
            f"| `{command}` | {COMMAND_MEANINGS[command]} | {format_integer(count)} | "
            f"{100.0 * count / total:.3f}% |"
        )
    if command_total != total:
        raise ValueError(
            f"Patch command count {command_total} differs from corpus total {total} for {language}."
        )
    return "\n".join(lines) + "\n\n"


def rounded_accuracy(
    values: tuple[float | None, float | None, float | None]
) -> tuple[str, str, str]:
    """Format exact-root percentages without inventing empty-population values."""

    return tuple("n/a" if value is None else f"{value:.3f}%" for value in values)


def words(value: str) -> set[str]:
    value = value.replace("OpenNLP", "OPENNLP")
    value = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", value)
    return {
        {"COPIED": "COPY"}.get(word, word)
        for word in re.sub(r"[^A-Za-z0-9]+", " ", value).upper().split()
        if len(word) > 2
        and word
        not in {
            "ACCURACY",
            "AGREEMENT",
            "BENCHMARK",
            "CANDIDATE",
            "CASE",
            "COMPARISON",
            "EGOTHOR",
            "EXACT",
            "LANGUAGE",
            "NAME",
            "ORG",
            "QUALITY",
            "ROOT",
            "STEM",
            "STEMMER",
        }
    }


def select_accuracy_key(
    label: str,
    language: str,
    data: JmhData,
) -> Key:
    language_words = LANGUAGE_IDENTITY_WORDS[language]
    matches = [
        key
        for key, counters in data.auxiliary.items()
        if AUXILIARY_NAMES.issubset(counters)
        and language_words.issubset(
            words(
                key.benchmark
                + " "
                + " ".join(f"{name} {value}" for name, value in key.parameters)
            )
        )
    ]
    if not matches:
        raise ValueError(
            f"No current JMH accuracy row matches language {language} and label {label}."
        )

    label_words = words(label) - language_words

    def score(key: Key) -> tuple[int, int, int, int, int]:
        identity_words = (
            words(
                key.benchmark
                + " "
                + " ".join(f"{name} {value}" for name, value in key.parameters)
            )
            - language_words
        )
        return (
            len(label_words & identity_words),
            -len(label_words - identity_words),
            -len(identity_words - label_words),
            int(key.method != "exactRootAgreement"),
            int(language_words.issubset(words(key.benchmark))),
        )

    ranked = sorted(
        ((score(key), key) for key in matches), reverse=True, key=lambda item: item[0]
    )
    if ranked[0][0][0] == 0:
        raise ValueError(
            f"No implementation identity words match accuracy label {label} for {language}."
        )
    if len(ranked) > 1 and ranked[0][0] == ranked[1][0]:
        raise ValueError(
            f"Ambiguous current JMH accuracy identity for {label} in {language}: "
            f"{ranked[0][1]} and {ranked[1][1]}"
        )
    return ranked[0][1]


def select_accuracy_candidate(candidate: str, data: JmhData) -> Key:
    """Select one exact-root row by its authoritative candidate parameter."""

    matches = [
        key for key, counters in data.auxiliary.items()
        if AUXILIARY_NAMES.issubset(counters)
        and key.parameter("candidateName") == candidate
    ]
    if len(matches) != 1:
        raise ValueError(
            f"Expected one current JMH accuracy row for candidate {candidate}, found {len(matches)}."
        )
    return matches[0]


def corpus_accuracy(
    entry: dict[str, object]
) -> tuple[float | None, float | None, float | None]:
    return (
        percentage(int(entry["all_exact"]), int(entry["total"])),
        percentage(int(entry["changed_exact"]), int(entry["changed"])),
        percentage(int(entry["root_exact"]), int(entry["roots"])),
    )


def update_accuracy_table(
    text: str,
    new_data: JmhData,
    language: str,
    corpus: dict[str, object],
    snowball_case: SnowballCase | None = None,
) -> str:
    start = text.index("## Accuracy")
    end = text.index("## Speed", start)
    section = text[start:end].rstrip()
    output: list[str] = []
    for line in section.splitlines():
        cells = [cell.strip() for cell in line.split("|")[1:-1]]
        measured = len(cells) == 5 and all(
            cell == "n/a" or re.fullmatch(r"\d+\.\d{3}%", cell)
            for cell in cells[1:4]
        )
        pending = len(cells) == 5 and all(cell == "pending" for cell in cells[1:4])
        partial_pending = (
            len(cells) == 5
            and any(cell == "pending" for cell in cells[1:4])
            and not pending
        )
        if partial_pending:
            raise ValueError(
                f"Partially pending accuracy row for {cells[0]} in {language}."
            )
        if (
            len(cells) == 5
            and cells[0] not in {"Stemmer", "---"}
            and not measured
            and not pending
        ):
            raise ValueError(f"Malformed accuracy row for {cells[0]} in {language}.")
        if measured or pending:
            if cells[0] == "Radixor":
                values = rounded_accuracy(corpus_accuracy(corpus))
            elif snowball_case is not None and "Snowball" in cells[0] and (
                "direct" in cells[0].lower() or snowball_case.case_name == "ENGLISH"
            ):
                key = select_accuracy_candidate(snowball_case.quality_candidate, new_data)
                values = rounded_accuracy(accuracy(new_data, key))
            else:
                key = select_accuracy_key(cells[0], language, new_data)
                values = rounded_accuracy(accuracy(new_data, key))
            cells[1:4] = list(values)
            line = "| " + " | ".join(cells) + " |"
        output.append(line)
    replacement = "\n".join(output).rstrip() + "\n\n"
    return text[:start] + replacement + text[end:]


def normalize_exact_snowball_speed_table(text: str, case: SnowballCase) -> str:
    """Bind an exact-language page to generic Radixor and catalogued Snowball rows."""

    accuracy_start = text.index("## Accuracy")
    speed_start = text.index("## Speed", accuracy_start)
    accuracy_section = text[accuracy_start:speed_start]
    accuracy_lines: list[str] = []
    accuracy_count = 0
    exact_label = (
        "Official Snowball Porter2 (Java)"
        if case.case_name == "ENGLISH"
        else "Official Snowball direct (Java)"
    )
    for line in accuracy_section.splitlines(keepends=True):
        cells = [cell.strip() for cell in line.split("|")[1:-1]]
        if len(cells) == 5 and "Snowball" in cells[0] and (
            "direct" in cells[0].lower()
            or case.case_name == "ENGLISH" and "Porter2" in cells[0]
        ):
            cells[0] = exact_label
            newline = "\n" if line.endswith("\n") else ""
            line = "| " + " | ".join(cells) + " |" + newline
            accuracy_count += 1
        accuracy_lines.append(line)
    if accuracy_count != 1:
        raise ValueError(
            f"Expected one official direct Snowball accuracy row for {case.case_name}, found {accuracy_count}."
        )
    text = text[:accuracy_start] + "".join(accuracy_lines) + text[speed_start:]
    start = text.index("## Speed")
    end = text.index("## Interpretation Notes", start)
    section = text[start:end]
    section, radixor_count = re.subn(
        r'^(\| Radixor \| )`[^`]+`( \|)',
        rf'\g<1>`radixor[{case.model_id}]`\g<2>',
        section,
        count=1,
        flags=re.MULTILINE,
    )
    if radixor_count != 1:
        raise ValueError(f"No unique Radixor speed row for Snowball case {case.case_name}")
    lines: list[str] = []
    snowball_count = 0
    for line in section.splitlines(keepends=True):
        cells = [cell.strip() for cell in line.split("|")[1:-1]]
        if len(cells) == 7 and "Snowball" in cells[0] and (
            "direct" in cells[0].lower() or case.case_name == "ENGLISH" and "Porter2" in cells[0]
        ):
            cells[0] = exact_label
            cells[1] = f"`{case.speed_method}`"
            newline = "\n" if line.endswith("\n") else ""
            line = "| " + " | ".join(cells) + " |" + newline
            snowball_count += 1
        lines.append(line)
    if snowball_count != 1:
        raise ValueError(
            f"Expected one official direct Snowball speed row for {case.case_name}, found {snowball_count}."
        )
    return text[:start] + "".join(lines) + text[end:]


def method_and_parameter(display: str) -> tuple[str, str]:
    match = re.fullmatch(r"([A-Za-z0-9]+)(?:\[([A-Za-z0-9_-]+)])?", display)
    if not match:
        raise ValueError(f"Unsupported benchmark method display: {display}")
    return match.group(1), match.group(2) or ""


def speed_matches(display: str, data: JmhData, language: str) -> list[Key]:
    method, parameter = method_and_parameter(display)
    matches = [
        key
        for key, row in data.primary.items()
        if key.method == method
        and (
            not parameter
            or key.parameter(
                "modelId" if "-" in parameter else "languageCaseName"
            )
            == parameter
        )
        and key not in data.auxiliary
        and row["Unit"] == "ns/op"
    ]
    if parameter or len(matches) <= 1:
        return matches
    language_words = LANGUAGE_IDENTITY_WORDS[language]
    return [
        key
        for key in matches
        if language_words.issubset(
            words(" ".join(value for _, value in key.parameters))
        )
    ]


def select_speed_key(display: str, data: JmhData, language: str) -> Key:
    matches = speed_matches(display, data, language)
    if len(matches) != 1:
        raise ValueError(
            f"Expected one current JMH speed row for {display} in {language}, found {len(matches)}."
        )
    return matches[0]


def update_speed_table(
    text: str,
    new_data: JmhData,
    timing_tokens: int,
    language: str,
) -> str:
    start = text.index("## Speed")
    end = text.index("## Interpretation Notes", start)
    section = text[start:end].rstrip()
    parsed: list[tuple[str, list[str] | None, Key | None]] = []
    radixor_score = math.nan
    radixor_error = math.nan
    for line in section.splitlines():
        cells = [cell.strip() for cell in line.split("|")[1:-1]]
        if len(cells) == 7 and cells[1].startswith("`") and cells[1].endswith("`"):
            display = cells[1].strip("`")
            pending = all(cell == "pending" for cell in cells[2:6])
            measured = all(re.fullmatch(r"\d+\.\d+", cell) for cell in cells[2:6])
            partial_pending = (
                any(cell == "pending" for cell in cells[2:6]) and not pending
            )
            if partial_pending:
                raise ValueError(
                    f"Partially pending speed row for {cells[0]} in {language}."
                )
            if not pending and not measured:
                raise ValueError(f"Malformed speed row for {cells[0]} in {language}.")
            key = select_speed_key(display, new_data, language)
            if key not in new_data.primary:
                raise ValueError(f"New JMH report omits speed key {key}")
            score = float(new_data.primary[key]["Score"])
            if cells[0] == "Radixor":
                radixor_score = score
                radixor_error = float(new_data.primary[key]["Score Error (99.9%)"])
            parsed.append((line, cells, key))
        else:
            parsed.append((line, None, None))
    if math.isnan(radixor_score) or math.isnan(radixor_error):
        raise ValueError(f"No Radixor speed baseline found for {language}")

    output: list[str] = []
    for line, cells, key in parsed:
        if cells is not None and key is not None:
            row = new_data.primary[key]
            score = float(row["Score"])
            error = float(row["Score Error (99.9%)"])
            cells[2] = f"{score / 1_000_000.0:.3f}"
            cells[3] = f"{error / 1_000_000.0:.3f}"
            cells[4] = f"{score / timing_tokens:.1f}"
            cells[5] = f"{score / radixor_score:.3f}"
            uncertainty = (
                " The Radixor and Snowball 99.9% JMH intervals overlap; statistically "
                "tied, so the runtime ratio is a point estimate only."
            )
            cells[6] = cells[6].replace(uncertainty, "")
            if key.method in {"snowballDirect", "snowballEnglishPorter2"} and (
                score - error <= radixor_score + radixor_error
                and radixor_score - radixor_error <= score + error
            ):
                cells[6] += uncertainty
            line = "| " + " | ".join(cells) + " |"
        output.append(line)
    replacement = "\n".join(output).rstrip() + "\n\n"
    return text[:start] + replacement + text[end:]


def rating_markup(info: ModelInfo) -> str:
    """Render a five-position accessible relative dictionary-size rating."""

    glyphs = "★" * info.stars + "☆" * (5 - info.stars)
    count = format_integer(info.distinct_forms)
    label = (
        f"Relative dictionary size {info.stars} of 5; "
        f"{count} distinct usable word forms"
    )
    return (
        f'<span class="dictionary-rating" role="img" aria-label="{label}" '
        f'title="{label}">{glyphs}</span>'
    )


def _rating_legend(
    info: ModelInfo, population: str = "active user-facing models"
) -> str:
    return (
        "<!-- DICTIONARY-SIZE-RATING:START -->\n"
        f"Dictionary size: {rating_markup(info)}. The exact count is "
        f"**{format_integer(info.distinct_forms)} distinct usable word forms** after "
        "parser-compatible filtering and exact, case-preserved deduplication. Stars rank "
        f"dictionary size relative to all {population} in five nearly equal groups; "
        "they do **not** measure linguistic quality or benchmark accuracy.\n"
        "<!-- DICTIONARY-SIZE-RATING:END -->"
    )


def _with_rating(
    text: str, info: ModelInfo, population: str = "active user-facing models"
) -> str:
    lines = text.splitlines()
    if not lines or not lines[0].startswith("# "):
        raise ValueError(f"Benchmark page for {info.model_id} has no H1")
    heading = re.sub(r"\s*<span class=\"dictionary-rating\".*?</span>\s*$", "", lines[0])
    lines[0] = f"{heading} {rating_markup(info)}"
    text = "\n".join(lines) + ("\n" if text.endswith("\n") else "")
    legend = _rating_legend(info, population)
    if "<!-- DICTIONARY-SIZE-RATING:START -->" in text:
        return re.sub(
            r"<!-- DICTIONARY-SIZE-RATING:START -->.*?<!-- DICTIONARY-SIZE-RATING:END -->",
            legend,
            text,
            count=1,
            flags=re.DOTALL,
        )
    first_paragraph_end = text.find("\n\n", text.find("\n") + 1)
    if first_paragraph_end < 0:
        raise ValueError(f"Benchmark page for {info.model_id} has no introduction")
    return text[:first_paragraph_end] + "\n\n" + legend + text[first_paragraph_end:]


def _new_page(
    info: ModelInfo, population: str = "active user-facing models"
) -> str:
    snowball = SNOWBALL_CASES.get(info.language)
    accuracy_rows = [
        "| Radixor | pending | pending | pending | Exact model-ID benchmark; measurement pending. |"
    ]
    speed_rows = [
        f"| Radixor | `radixor[{info.model_id}]` | pending | pending | pending | pending | "
        "Canonical model timing workload; measurement pending. |"
    ]
    if snowball is not None:
        case_name, has_lucene = snowball
        accuracy_rows.append(
            "| Official Snowball direct | pending | pending | pending | "
            "Official Snowball 3.1.0 generated Java stemmer; measurement pending. |"
        )
        speed_rows.append(
            f"| Official Snowball direct | `snowballDirect[{case_name}]` | pending | pending | pending | pending | "
            "Official generated Java stemmer; measurement pending. |"
        )
        if has_lucene:
            accuracy_rows.append(
                "| Lucene SnowballFilter | pending | pending | pending | "
                "Lucene integration of the matching Snowball algorithm; measurement pending. |"
            )
            speed_rows.append(
                f"| Lucene SnowballFilter | `luceneSnowballFilter[{case_name}]` | pending | pending | pending | pending | "
                "Lucene TokenStream integration; measurement pending. |"
            )
    direction = "right-to-left" if info.right_to_left else "left-to-right"
    return f"""# {info.language_name} Stemmer Benchmarks

This page reports dictionary corpus, exact-root agreement, and runtime evidence for the independently available `{info.model_id}` {info.language_name} model.

{_rating_legend(info, population)}

The language metadata declares {direction} writing. New benchmark measurements are intentionally shown as pending until the canonical published runners produce complete reports; no values are inferred or fabricated.

All speed values are environment-specific and await publication on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations use the canonical dictionary-derived timing workload. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the exact dictionary size, then read exact-root agreement and runtime together after the canonical reports are published. Dictionary-size stars are contextual metadata, not an accuracy result.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Distinct usable word forms |
| --- | --- | --- | ---: | ---: |
| `{info.model_id}` | `{info.version}` | `{info.language}` | {format_integer(info.dictionary_rows)} | {format_integer(info.distinct_forms)} |

## Accuracy

Accuracy will use the complete dictionary and report exact agreement with the dictionary root. The pending cells remain until a canonical report identifies this exact model and candidate.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
{chr(10).join(accuracy_rows)}

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 3 independent forks, and 1 thread.

Speed will use JMH average time over the canonical timing workload. Changed dictionary tokens are preferred; a root-only dictionary uses its complete root-preservation corpus. The selected population is repeated only when needed to reach the timing minimum. Relative factors will use the Radixor row on this page as the baseline.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
{chr(10).join(speed_rows)}

## Interpretation Notes

- The star tier reflects only relative distinct-form count among all {population}.
- Pending values indicate that the canonical benchmark run has not yet published measurements for this page.
- Runtime and exact-root agreement describe different properties and must be interpreted together.

<!-- DICTIONARY-GENERALIZATION:START -->
<!-- Reserved for deterministic dictionary-family generalization publication. -->
<!-- DICTIONARY-GENERALIZATION:END -->

<!-- EDIT-COST-GENERALIZATION:START -->
<!-- Reserved for deterministic edit-cost publication. -->
<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->
<!-- Reserved for deterministic pairwise linguistic-quality publication. -->
<!-- STEMMING-QUALITY:END -->
"""


def _polimorf_section(
    info: ModelInfo,
    corpus: dict[str, object] | None,
    accuracy_data: JmhData | None,
    speed_data: JmhData | None,
) -> str:
    """Render the optional PoliMorf model without changing the Polish default."""

    if corpus is None:
        corpus_row = (
            f"| `{info.model_id}` | `{info.version}` | `PL_PL` | {format_integer(info.dictionary_rows)} | "
            f"{format_integer(info.distinct_forms)} | pending | pending | pending | pending | pending |"
        )
        accuracy_rows = [
            "| Radixor PoliMorf | pending | pending | pending | Exact optional model; measurement pending. |",
            "| Lucene MorfologikFilter | pending | pending | pending | Matching Polish dictionary adapter; measurement pending. |",
            "| Official Snowball direct | pending | pending | pending | Matching Polish Snowball algorithm; measurement pending. |",
        ]
        speed_rows = [
            f"| Radixor PoliMorf | `radixor[{info.model_id}]` | pending | pending | pending | pending | Exact optional-model baseline; measurement pending. |",
            "| Lucene MorfologikFilter | `polishPolimorfLuceneMorfologikFilter` | pending | pending | pending | pending | PoliMorf-derived corpus; measurement pending. |",
        ]
    else:
        if accuracy_data is None or speed_data is None:
            raise ValueError("Measured PoliMorf corpus requires accuracy and speed reports.")
        corpus_row = (
            f"| `{info.model_id}` | `{corpus['version']}` | `PL_PL` | {format_integer(int(corpus['rows']))} | "
            f"{format_integer(int(corpus['distinct']))} | {format_integer(int(corpus['total']))} | "
            f"{format_integer(int(corpus['roots']))} | {format_integer(int(corpus['changed']))} | "
            f"{corpus['timing_basis']} | {format_integer(int(corpus['timing']))} |"
        )
        accuracy_rows = []
        accuracy_specs = (
            ("Radixor PoliMorf", "PoliMorf Radixor", "Exact optional model."),
            ("PoliMorf Lucene MorfologikFilter", "PoliMorf Lucene MorfologikFilter", "Matching Polish dictionary adapter."),
            ("PoliMorf Official Snowball direct", "PoliMorf Official Snowball direct", "Matching Polish Snowball algorithm."),
        )
        for display, lookup, note in accuracy_specs:
            values = rounded_accuracy(
                accuracy(accuracy_data, select_accuracy_key(lookup, "PL_PL", accuracy_data))
            )
            accuracy_rows.append(
                f"| {display} | {values[0]} | {values[1]} | {values[2]} | {note} |"
            )

        timing_tokens = int(corpus["timing"])
        radixor_key = select_speed_key(f"radixor[{info.model_id}]", speed_data, "PL_PL")
        morfologik_key = select_speed_key("polishPolimorfLuceneMorfologikFilter", speed_data, "PL_PL")
        radixor_score = float(speed_data.primary[radixor_key]["Score"])
        speed_rows = []
        for display, method, key, note in (
            ("Radixor PoliMorf", f"radixor[{info.model_id}]", radixor_key, "Exact optional-model baseline."),
            ("Lucene MorfologikFilter", "polishPolimorfLuceneMorfologikFilter", morfologik_key, "PoliMorf-derived corpus."),
        ):
            row = speed_data.primary[key]
            score = float(row["Score"])
            error = float(row["Score Error (99.9%)"])
            speed_rows.append(
                f"| {display} | `{method}` | {score / 1_000_000.0:.3f} | "
                f"{error / 1_000_000.0:.3f} | {score / timing_tokens:.1f} | "
                f"{score / radixor_score:.3f} | {note} |"
            )

    return f"""{POLIMORF_START}

## Optional PoliMorf Model

PoliMorf is an independently available optional Polish model and is not the `PL_PL` default or a member of either standard aggregate. Its evidence is kept separate from the UniMorph default above.

Optional dictionary size: {rating_markup(info)}. The exact count is **{format_integer(info.distinct_forms)} distinct usable word forms**; the rating is relative size, not linguistic quality.

### PoliMorf Corpus

| Model ID | Model version | Language | Dictionary rows | Distinct usable forms | Complete quality tokens | Already-root tokens | Changed tokens | Timing workload | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
{corpus_row}

### PoliMorf Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
{chr(10).join(accuracy_rows)}

### PoliMorf Speed

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor PoliMorf | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
{chr(10).join(speed_rows)}

{POLIMORF_END}"""


def prepare_polimorf_section(
    docs_root: Path, info: ModelInfo, publication: Publication
) -> None:
    """Insert the pending optional section once while preserving measured content."""

    path = docs_root / "benchmarks" / "languages" / "polish.md"
    text = path.read_text(encoding="utf-8")
    if POLIMORF_START not in text:
        marker = "<!-- DICTIONARY-GENERALIZATION:START -->"
        text = text.replace(
            marker, _polimorf_section(info, None, None, None) + "\n\n" + marker, 1
        )
    publication.write(path, text)


def update_polimorf_section(
    docs_root: Path,
    info: ModelInfo,
    corpus: dict[str, object] | None,
    accuracy_data: JmhData,
    speed_data: JmhData,
    publication: Publication,
) -> None:
    """Update the optional section from exact-model reports when available."""

    path = docs_root / "benchmarks" / "languages" / "polish.md"
    text = path.read_text(encoding="utf-8")
    replacement = _polimorf_section(info, corpus, accuracy_data, speed_data)
    text, count = re.subn(
        re.escape(POLIMORF_START) + r".*?" + re.escape(POLIMORF_END),
        replacement,
        text,
        count=1,
        flags=re.DOTALL,
    )
    if count != 1:
        raise ValueError("The Polish page has no optional PoliMorf section.")
    publication.write(path, text)


def prepare_language_pages(
    docs_root: Path,
    model_infos: list[ModelInfo],
    publication: Publication,
    rating_population: str = "active user-facing models",
) -> dict[str, ModelInfo]:
    """Create deterministic shells and add ratings without replacing rich sections."""

    directory = docs_root / "benchmarks" / "languages"
    defaults = {
        info.language: info for info in model_infos if info.role != "optional"
    }
    if not defaults:
        raise ValueError("The active topology contains no language defaults")
    for info in defaults.values():
        path = directory / info.page_name
        if path.is_file():
            content = _with_rating(
                path.read_text(encoding="utf-8"), info, rating_population
            )
            if (
                "New benchmark measurements are intentionally shown as pending" in content
                and "All speed values are environment-specific" not in content
            ):
                anchor = (
                    "New benchmark measurements are intentionally shown as pending until the canonical "
                    "published runners produce complete reports; no values are inferred or fabricated."
                )
                identity = (
                    "All speed values are environment-specific and await measurement on the hardware and JVM "
                    "listed in the [benchmark overview](../index.md). Speed benchmark operations use the canonical "
                    "timing workload. Accuracy uses the complete Radixor dictionary for the language."
                )
                content = content.replace(anchor, anchor + "\n\n" + identity, 1)
            content = content.replace(
                "Speed will use JMH average time over changed dictionary tokens, repeated only when needed "
                "to reach the timing minimum. Relative factors will use the Radixor row on this page as the baseline.",
                "Speed will use JMH average time over the canonical timing workload. Changed tokens are preferred; "
                "a root-only dictionary uses its complete root-preservation corpus. Smaller populations are repeated "
                "deterministically to the timing minimum. Relative factors use the Radixor row as the baseline.",
            )
            content = content.replace(
                "Canonical changed-token model benchmark; measurement pending.",
                "Canonical model timing workload; measurement pending.",
            )
        else:
            content = _with_rating(
                _new_page(info, rating_population), info, rating_population
            )
        publication.write(path, content)
    return defaults


def mark_new_page_measured(text: str) -> str:
    """Replace new-page provisional language once canonical reports are present."""

    replacements = {
        " New benchmark measurements are intentionally shown as pending until the canonical "
        "published runners produce complete reports; no values are inferred or fabricated.": "",
        "Accuracy will use the complete dictionary and report exact agreement with the "
        "dictionary root. The pending cells remain until a canonical report identifies this "
        "exact model and candidate.": (
            "Accuracy uses the complete dictionary and reports exact agreement with the "
            "dictionary root for each identified model and candidate."
        ),
        "Speed will use JMH average time over the canonical timing workload. "
        "Changed dictionary tokens are preferred;": (
            "The canonical timing workload prefers changed dictionary tokens;"
        ),
        "measurement pending.": "measured in this snapshot.",
        "- Pending values indicate that the canonical benchmark run has not yet published "
        "measurements for this page.": (
            "- Values shown above come from the identified canonical benchmark snapshot."
        ),
    }
    for provisional, measured in replacements.items():
        text = text.replace(provisional, measured)
    if re.search(r"\bpending\b", text, flags=re.IGNORECASE):
        raise ValueError("A measured new language page still contains pending content.")
    return text


def validate_evidence_fragment_links(text: str, path: Path) -> None:
    """Reject fragment links in the evidence map that have no page heading."""

    match = re.search(
        r"<!-- BENCHMARK-EVIDENCE-MAP:START -->(.*?)"
        r"<!-- BENCHMARK-EVIDENCE-MAP:END -->",
        text,
        flags=re.DOTALL,
    )
    if match is None:
        raise ValueError(f"No benchmark evidence map found in {path}.")
    anchors = {
        re.sub(r"\s+", "-", re.sub(r"[^a-z0-9\s-]", "", heading.lower()).strip())
        for heading in re.findall(r"^#{1,6}\s+(.+?)\s*$", text, flags=re.MULTILINE)
    }
    missing = sorted(
        fragment
        for fragment in re.findall(r"\]\(#([^)]+)\)", match.group(1))
        if fragment not in anchors
    )
    if missing:
        raise ValueError(
            f"Benchmark evidence map in {path} references missing anchors: "
            + ", ".join(missing)
        )


def read_standard_model_ids() -> set[str]:
    """Read and validate the independent 31-model standard-pack authority."""

    path = MODELS_ROOT / "standard-model-projects.properties"
    model_ids: set[str] = set()
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        model_id, separator, value = line.partition("=")
        if not separator or not model_id or value != "true" or model_id in model_ids:
            raise ValueError(f"Invalid standard-model membership line: {raw_line!r}")
        model_ids.add(model_id)
    if len(model_ids) != 31:
        raise ValueError(f"Expected 31 standard models, found {len(model_ids)}")
    return model_ids


def update_model_indexes(
    docs_root: Path,
    model_infos: list[ModelInfo],
    defaults: dict[str, ModelInfo],
    publication: Publication,
) -> None:
    """Publish complete rated language and model indexes."""

    optional_by_language = {
        info.language: info for info in model_infos if info.role == "optional"
    }
    standard_ids = read_standard_model_ids()
    if not standard_ids.issubset({info.model_id for info in defaults.values()}):
        raise ValueError("Standard membership contains a non-default language model")
    language_index = docs_root / "benchmarks" / "languages" / "index.md"
    text = language_index.read_text(encoding="utf-8")
    introduction_start = text.index("This section ")
    introduction_end = text.index("\n\n", introduction_start)
    introduction = (
        f"This section covers all {len(defaults)} registered language defaults. The original 20 pages retain "
        "their complete measured and experimental sections; newly added pages publish corpus, "
        "accuracy, and speed evidence from the current checked-in canonical reports."
    )
    text = text[:introduction_start] + introduction + text[introduction_end:]
    start = text.index("| Language |", text.index("## Languages"))
    end = text.index("\n\n", start)
    lines = [
        "| Language | Java enum | Model ID | Relative dictionary size | Availability | Benchmark page |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for info in sorted(defaults.values(), key=lambda item: (item.language_name, item.model_id)):
        model_cell = f"`{info.model_id}`"
        availability = "Standard aggregate" if info.model_id in standard_ids else "Extended aggregate"
        optional = optional_by_language.get(info.language)
        if optional is not None:
            model_cell += f"; optional `{optional.model_id}` ({rating_markup(optional)})"
            availability += "; optional model is in the extended aggregate"
        lines.append(
            f"| {info.language_name} | `{info.language}` | {model_cell} | {rating_markup(info)} "
            f"({format_integer(info.distinct_forms)}) | {availability} | "
            f"[{info.language_name}]({info.page_name}) |"
        )
    publication.write(language_index, text[:start] + "\n".join(lines) + text[end:])

    built_in = docs_root / "built-in-languages.md"
    text = built_in.read_text(encoding="utf-8")
    heading = (
        "## Defaults and variants"
        if "## Defaults and variants" in text
        else "## Registered languages and models"
    )
    heading_start = text.index(heading)
    next_heading = text.index("## The Polish dual-model case", heading_start)
    lines = [
        "## Registered languages and models",
        "",
        "The Java and Python standard aggregates contain the 31 IDs in "
        "`models/standard-model-projects.properties`. Every other active model is individually "
        "published and belongs to the Java-only extended aggregate; filtered alternatives use "
        "their separate opt-in Java aggregate.",
        "",
        "| Language | Java enum | Model ID | Topology role | Package | Relative dictionary size |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for info in sorted(model_infos, key=lambda item: (item.language_name, item.model_id)):
        lines.append(
            f"| {info.language_name} | `{info.language}` | `{info.model_id}` | `{info.role}` | "
            f"`{'standard' if info.model_id in standard_ids else 'extended'}` | "
            f"{rating_markup(info)} ({format_integer(info.distinct_forms)} distinct forms) |"
        )
    lines.extend(
        [
            "",
            "The maintained table deliberately avoids duplicating mutable provenance fields. Those values come from model module metadata and the generated model catalog.",
            "",
        ]
    )
    publication.write(built_in, text[:heading_start] + "\n".join(lines) + text[next_heading:])


def update_mkdocs_language_nav(
    docs_root: Path, defaults: dict[str, ModelInfo], publication: Publication
) -> None:
    """Keep the MkDocs language navigation complete and deterministic."""

    path = docs_root.parent / "mkdocs.yml"
    text = path.read_text(encoding="utf-8")
    start_marker = "     - Language Comparisons:\n"
    start = text.index(start_marker)
    end = text.index("     - Methods and Reproduction:\n", start)
    prohibited_blocks = re.findall(
        r"        - Benchmark-only prohibited models:\n(?:           - .*\n)+",
        text[start:end],
    )
    if len(prohibited_blocks) > 1:
        raise ValueError("MkDocs navigation contains duplicate prohibited-model sections.")
    lines = [start_marker.rstrip("\n"), "        - Overview: benchmarks/languages/index.md"]
    for info in sorted(defaults.values(), key=lambda item: (item.language_name, item.model_id)):
        lines.append(
            f"        - {info.language_name}: benchmarks/languages/{info.page_name}"
        )
    block = "\n".join(lines) + "\n"
    if prohibited_blocks:
        block += prohibited_blocks[0]
    publication.write(path, text[:start] + block + text[end:])


def update_speed_protocol_documentation(
    docs_root: Path, protocol: SpeedProtocol, publication: Publication
) -> None:
    """Publish the protocol evidenced by the selected speed reports."""

    path = docs_root / "benchmarks" / "reference" / "methodology.md"
    text = path.read_text(encoding="utf-8")
    text, count = re.subn(
        r"Published speed and coverage-speed methods use .*?fixed 6 GiB heap\.",
        protocol.methodology_sentence(),
        text,
        count=1,
    )
    if count != 1:
        raise ValueError("No published speed protocol statement found in methodology.md")
    publication.write(path, text)


def update_environment_provenance(
    docs_root: Path, date: str, release_version: str, publication: Publication
) -> None:
    """Bind the environment page to the dated measured source artifacts."""

    data_directory = docs_root / "benchmarks" / "data"
    source_name = f"measured-source-{date}.patch"
    untracked_name = f"measured-untracked-{date}.sha256"
    source_path = data_directory / source_name
    untracked_path = data_directory / untracked_name
    missing = [str(path) for path in (source_path, untracked_path) if not path.is_file()]
    if missing:
        raise ValueError(
            "Published measured-source provenance is incomplete: " + ", ".join(missing)
        )

    source_digest = hashlib.sha256(source_path.read_bytes()).hexdigest()
    untracked_digest = hashlib.sha256(untracked_path.read_bytes()).hexdigest()
    replacement = (
        f"| Release identity | Radixor/Java `{release_version}`; exact measured tracked "
        f"changes are retained as [{source_name}](../data/{source_name}) "
        f"(SHA-256 `{source_digest}`), and untracked-source checksums as "
        f"[{untracked_name}](../data/{untracked_name}) "
        f"(SHA-256 `{untracked_digest}`) |"
    )
    path = docs_root / "benchmarks" / "reference" / "environment.md"
    text = path.read_text(encoding="utf-8")
    text = text.replace("4.3.0.dirty", release_version)
    text, count = re.subn(
        r"^\| Release identity \| Radixor/Java .*?\|$",
        replacement,
        text,
        count=1,
        flags=re.MULTILINE,
    )
    if count != 1:
        raise ValueError("Benchmark environment page has no unique release identity row")
    publication.write(path, text)


def update_reproducibility_release_identity(
    docs_root: Path, release_version: str, publication: Publication
) -> None:
    """Map the recorded development identity to its public release identity."""

    path = docs_root / "benchmarks" / "reference" / "reproducibility.md"
    text = path.read_text(encoding="utf-8")
    text = text.replace("4.3.0.dirty", release_version)
    text = text.replace("dirty continuation", "pre-release continuation")
    publication.write(path, text)


def update_language_pages(
    docs_root: Path,
    corpora: dict[str, dict[str, object]],
    accuracy_data: JmhData,
    speed_data: JmhData,
    speed_protocol: SpeedProtocol,
    publication: Publication,
    date: str,
    release_version: str,
    snowball_cases: dict[str, SnowballCase],
) -> None:
    directory = docs_root / "benchmarks" / "languages"
    for file_name, language in LANGUAGES.items():
        path = directory / file_name
        if language not in corpora:
            continue
        text = path.read_text(encoding="utf-8")
        snowball_case = snowball_cases.get(language)
        if snowball_case is not None:
            text = normalize_exact_snowball_speed_table(text, snowball_case)
        was_pending_shell = (
            "New benchmark measurements are intentionally shown as pending" in text
        )
        timing_basis = str(corpora[language]["timing_basis"])
        identity = (
            "All speed values are environment-specific and were measured on the hardware and JVM "
            "listed in the [benchmark overview](../index.md). The command distribution, exact-root "
            f"accuracy, and speed tables belong to the published {date} Radixor/Java "
            f"`{release_version}` snapshot. Speed benchmark operations process {timing_basis}. "
            "Accuracy uses the complete Radixor dictionary for the language."
        )
        text, count = re.subn(
            r"All speed values are environment-specific.*?Accuracy uses the complete Radixor "
            r"dictionary for the language\.",
            identity,
            text,
            count=1,
            flags=re.DOTALL,
        )
        if count != 1:
            raise ValueError(f"No benchmark identity paragraph found in {path}.")
        has_rich_experiments = all(
            heading in text
            for heading in (
                "## Dictionary-Family Generalization Conclusion",
                "## Edit Costs and Dictionary-Knowledge Generalization",
                "## Stemming Quality",
            )
        )
        if has_rich_experiments:
            guidance = (
                "    Start with the [corpus](#dictionary-corpus) and "
                "[patch-command distribution](#radixor-patch-command-distribution), then compare "
                "[exact-root agreement](#accuracy) with [runtime](#speed). The "
                "[dictionary-family experiment](#dictionary-family-generalization-conclusion), "
                "[edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and "
                "[pairwise linguistic evaluation](#stemming-quality) answer separate questions. "
                "Their 10–90% curves use independent frozen protocols and must not be substituted "
                "for one another."
            )
        else:
            guidance = (
                "    Start with the [corpus](#dictionary-corpus) and "
                "[patch-command distribution](#radixor-patch-command-distribution), then compare "
                "[exact-root agreement](#accuracy) with [runtime](#speed). Dictionary-size stars "
                "are contextual metadata, not an accuracy result."
            )
        evidence = (
            "<!-- BENCHMARK-EVIDENCE-MAP:START -->\n"
            "!!! info \"How to read this page\"\n"
            f"{guidance}\n"
            "<!-- BENCHMARK-EVIDENCE-MAP:END -->"
        )
        if "<!-- BENCHMARK-EVIDENCE-MAP:START -->" in text:
            text = re.sub(
                r"<!-- BENCHMARK-EVIDENCE-MAP:START -->.*?"
                r"<!-- BENCHMARK-EVIDENCE-MAP:END -->",
                evidence,
                text,
                count=1,
                flags=re.DOTALL,
            )
        else:
            text = text.replace(identity + "\n\n", identity + "\n\n" + evidence + "\n\n", 1)
        text = re.sub(
            r'Radixor must not be read as simply "slower".*?speed rows must be read together with the accuracy table above them\.',
            "Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.",
            text,
            count=1,
        )
        text = re.sub(
            r"- The star tier reflects only relative distinct-form count among (?:the )?"
            r"\d+ user-facing models\.",
            "- The star tier reflects only relative distinct-form count among the active "
            "user-facing models.",
            text,
            count=1,
        )
        corpus_start = text.index("## Dictionary Corpus")
        accuracy_start = text.index("## Accuracy", corpus_start)
        text = (
            text[:corpus_start]
            + render_corpus_sections(language, corpora[language])
            + text[accuracy_start:]
        )
        text, protocol_count = re.subn(
            r"Speed uses JMH average time, \d+ warmup iterations, \d+ measurement iterations, "
            r"\d+ (?:independent )?forks?, and 1 thread\.",
            speed_protocol.page_sentence(),
            text,
            count=1,
        )
        if protocol_count == 0:
            speed_heading_end = text.index("\n\n", text.index("## Speed")) + 2
            text = (
                text[:speed_heading_end]
                + speed_protocol.page_sentence()
                + "\n\n"
                + text[speed_heading_end:]
            )
        text = text.replace(
            "Speed uses JMH average time over the canonical timing workload. Changed ",
            "The canonical timing workload prefers changed ",
        )
        text = update_accuracy_table(
            text, accuracy_data, language, corpora[language], snowball_case
        )
        text = update_speed_table(
            text, speed_data, int(corpora[language]["timing"]), language
        )
        if language == "US_UK":
            key = select_speed_key("radixor[us-uk-default]", speed_data, language)
            if key is None or speed_data.primary[key]["Unit"] != "ns/op":
                raise ValueError("Expected one English exact-model Radixor speed row.")
            main_ns = float(speed_data.primary[key]["Score"]) / int(
                corpora[language]["timing"]
            )
            comparison = (
                "<!-- ENGLISH-SPEED-SUITES:START -->\n"
                "!!! note \"Separate English speed suites\"\n"
                f"    The `{main_ns:.1f} ns/token` Radixor value below is from the multilingual "
                "same-language comparison suite. The [coverage experiment](../reference/english-coverage.md) "
                "reports its own full-knowledge point from a separate benchmark method and run. "
                "Treat both as suite-specific estimates with their published uncertainty, not as "
                "interchangeable values.\n"
                "<!-- ENGLISH-SPEED-SUITES:END -->"
            )
            if "<!-- ENGLISH-SPEED-SUITES:START -->" in text:
                text = re.sub(
                    r"<!-- ENGLISH-SPEED-SUITES:START -->.*?"
                    r"<!-- ENGLISH-SPEED-SUITES:END -->",
                    comparison,
                    text,
                    count=1,
                    flags=re.DOTALL,
                )
            else:
                speed_start = text.index("## Speed")
                table_start = text.index("| Stemmer |", speed_start)
                text = text[:table_start] + comparison + "\n\n" + text[table_start:]
        if was_pending_shell:
            text = mark_new_page_measured(text)
        validate_evidence_fragment_links(text, path)
        publication.write(path, text)


def update_corpora_reference(
    docs_root: Path,
    model_infos: list[ModelInfo],
    corpora_by_model: dict[str, dict[str, object]],
    publication: Publication,
) -> None:
    path = docs_root / "benchmarks" / "reference" / "corpora.md"
    text = path.read_text(encoding="utf-8")
    original_header = "| Language resource |"
    current_header = "| Default model ID |"
    complete_header = "| Model ID | Role |"
    if original_header in text:
        table_start = text.index(original_header)
    elif current_header in text:
        table_start = text.index(current_header)
    elif complete_header in text:
        table_start = text.index(complete_header)
    else:
        raise ValueError(
            "The corpora reference contains no recognized corpus-table header."
        )
    table_end = text.index("\n\n", table_start)
    lines = [
        "| Model ID | Role | Version | SHA-256 | Language | Dictionary rows | Distinct usable forms | Total benchmark tokens | Changed benchmark tokens | Timing workload | Speed timing tokens |",
        "| --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- | ---: |",
    ]
    for info in sorted(model_infos, key=lambda item: item.model_id):
        entry = corpora_by_model.get(info.model_id)
        total = format_integer(int(entry["total"])) if entry is not None else "pending"
        changed = format_integer(int(entry["changed"])) if entry is not None else "pending"
        timing_basis = str(entry["timing_basis"]) if entry is not None else "pending"
        timing = format_integer(int(entry["timing"])) if entry is not None else "pending"
        lines.append(
            f"| `{info.model_id}` | `{info.role}` | `{info.version}` | `{info.sha256}` | `{info.language}` | "
            f"{format_integer(info.dictionary_rows)} | {format_integer(info.distinct_forms)} | "
            f"{total} | {changed} | {timing_basis} | {timing} |"
        )
    replacement = "\n".join(lines)
    publication.write(path, text[:table_start] + replacement + text[table_end:])


def coverage_rows(
    accuracy_data: JmhData, speed_data: JmhData, timing_tokens: int
) -> list[str]:
    lines = [
        "| Used rows | Actual row ratio | All exact | Changed exact | Root preserved | Speed ms/op | Error ms | ns/token |",
        "| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for percent in range(100, 0, -10):
        parameter = str(percent)
        accuracy_keys = [
            key
            for key, counters in accuracy_data.auxiliary.items()
            if key.method == "exactRootAgreement"
            and key.parameter("coveragePercent") == parameter
            and AUXILIARY_NAMES.issubset(counters)
        ]
        speed_keys = [
            key
            for key, row in speed_data.primary.items()
            if key.method == "changedTokenStemmingSpeed"
            and key.parameter("coveragePercent") == parameter
            and row["Unit"] == "ns/op"
        ]
        if len(accuracy_keys) != 1 or len(speed_keys) != 1:
            raise ValueError(f"Incomplete English coverage results for {percent}%.")
        accuracy_key = accuracy_keys[0]
        speed_key = speed_keys[0]
        counters = accuracy_data.auxiliary[accuracy_key]
        actual = 100.0 * counters["selectedRows"] / counters["totalRows"]
        values = accuracy(accuracy_data, accuracy_key)
        speed = float(speed_data.primary[speed_key]["Score"])
        error = float(speed_data.primary[speed_key]["Score Error (99.9%)"])
        lines.append(
            f"| {percent}% | {actual:.3f}% | {values[0]:.3f}% | {values[1]:.3f}% | {values[2]:.3f}% | "
            f"{speed / 1_000_000.0:.3f} | {error / 1_000_000.0:.3f} | {speed / timing_tokens:.1f} |"
        )
    return lines


def replace_coverage_table(text: str, lines: list[str]) -> str:
    start = text.index("| Used rows |")
    end = text.index("\n\n", start)
    return text[:start] + "\n".join(lines) + text[end:]


def update_coverage(
    docs_root: Path,
    readme: Path,
    accuracy_data: JmhData,
    speed_data: JmhData,
    timing_tokens: int,
    total_tokens: int,
    publication: Publication,
    main_english_ns: float,
) -> None:
    lines = coverage_rows(accuracy_data, speed_data, timing_tokens)
    full = [cell.strip() for cell in lines[2].split("|")[1:-1]]
    reduced = [cell.strip() for cell in lines[-1].split("|")[1:-1]]
    reference = docs_root / "benchmarks" / "reference" / "english-coverage.md"
    reference_text = replace_coverage_table(
        reference.read_text(encoding="utf-8"), lines
    )
    reference_text = re.sub(
        r"`Speed ms/op` divided by [\d,]+ changed English tokens\.",
        f"`Speed ms/op` divided by {format_integer(timing_tokens)} changed English tokens.",
        reference_text,
        count=1,
    )
    reference_text = re.sub(
        r"the speed workload processes [\d,]+ changed token/root pairs.*?"
        r"complete [\d,]+-token dictionary\.",
        f"the speed workload processes {format_integer(timing_tokens)} changed token/root pairs "
        "where the dictionary token differs from the expected root, and the quality workload "
        f"evaluates the complete {format_integer(total_tokens)}-token dictionary.",
        reference_text,
        count=1,
        flags=re.DOTALL,
    )
    comparison = (
        "<!-- ENGLISH-SPEED-SUITES:START -->\n"
        "!!! note \"Separate English speed suites\"\n"
        f"    The full-knowledge `{full[7]} ns/token` point on this page belongs to the coverage "
        "suite. The [English language comparison](../languages/english.md#speed) reports "
        f"`{main_english_ns:.1f} ns/token` from a separate JMH method and run. Their uncertainty "
        "intervals overlap; neither point estimate should replace the other.\n"
        "<!-- ENGLISH-SPEED-SUITES:END -->"
    )
    if "<!-- ENGLISH-SPEED-SUITES:START -->" in reference_text:
        reference_text = re.sub(
            r"<!-- ENGLISH-SPEED-SUITES:START -->.*?<!-- ENGLISH-SPEED-SUITES:END -->",
            comparison,
            reference_text,
            count=1,
            flags=re.DOTALL,
        )
    else:
        table_start = reference_text.index("| Used rows |")
        reference_text = reference_text[:table_start] + comparison + "\n\n" + reference_text[table_start:]
    publication.write(reference, reference_text)
    readme_text = replace_coverage_table(readme.read_text(encoding="utf-8"), lines)
    readme_text = re.sub(
        r"The contracted trie result is materially stronger than the older uncontracted profile: "
        r"full English coverage reaches .*?"
        r"This is why Radixor benchmark results are documented with both speed and quality instead of a single Porter speed badge\.",
        "The contracted trie result is materially stronger than the older uncontracted profile: "
        f"full English coverage reaches {full[2]} all-token exactness and {full[3]} changed-token exactness "
        f"at {full[7]} ns/token, while even a 10% deterministic dictionary slice remains at {reduced[2]} "
        f"all-token exactness and {reduced[3]} changed-token exactness at {reduced[7]} ns/token. "
        "This is why Radixor benchmark results are documented with both speed and quality instead of a single Porter speed badge.",
        readme_text,
        count=1,
        flags=re.DOTALL,
    )
    publication.write(readme, readme_text)

    index = docs_root / "benchmarks" / "index.md"
    index_text = index.read_text(encoding="utf-8")
    key_start = index_text.index("## Key Published Result")
    key_end = index_text.index("## Quality versus performance", key_start)
    key_section = (
        "## Key Published Result\n\n"
        "The English dictionary coverage benchmark shows the current contracted-trie operating curve. With\n"
        f"the full English dictionary, Radixor reaches `{full[2]}` all-token exactness and `{full[3]}`\n"
        f"changed-token exactness at `{full[7]} ns/token`. Even with a deterministic 10% dictionary slice, it\n"
        f"keeps `{reduced[2]}` all-token exactness and `{reduced[3]}` changed-token exactness at `{reduced[7]} ns/token`.\n\n"
        "Those figures should not be reduced to a single speed badge. The professional interpretation is a\n"
        "quality/speed envelope: the amount and quality of dictionary knowledge affect stemming precision,\n"
        "while contracted tries reduce lookup cost in uniform regions of the compiled graph.\n\n"
    )
    publication.write(index, index_text[:key_start] + key_section + index_text[key_end:])


def quality_homepage_counts(
    path: Path, expected_languages: set[str]
) -> tuple[int, int, int, int]:
    """Return defined matrices, Radixor wins, comparator coverage, and undefined matrices."""

    groups: dict[tuple[str, str], list[tuple[str, float]]] = defaultdict(list)
    all_groups: set[tuple[str, str]] = set()
    with path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            if row["Output policy"] != "PRIMARY_OUTPUT":
                continue
            key = (row["Language"], row["Dictionary mode"])
            all_groups.add(key)
            if not row["Balanced accuracy"]:
                continue
            groups[key].append(
                (row["Stemmer"], float(row["Balanced accuracy"]))
            )
    report_languages = {language for language, _ in all_groups}
    if report_languages != expected_languages or len(all_groups) != len(expected_languages) * 2:
        raise ValueError("Active quality snapshot must contain two matrices for every active default")
    wins = 0
    compared = 0
    for rows in groups.values():
        best = max(score for _, score in rows)
        leaders = [stemmer for stemmer, score in rows if score == best]
        if len(rows) > 1:
            compared += 1
        if len(leaders) == 1 and leaders[0].endswith("_RADIXOR"):
            wins += 1
    return len(groups), wins, compared, len(all_groups) - len(groups)


def speed_homepage_counts(
    docs_root: Path, defaults: dict[str, ModelInfo], speed_data: JmhData
) -> tuple[int, int]:
    """Derive Java language-page runtime minima from published table identities."""

    wins = 0
    comparisons = 0
    for info in defaults.values():
        text = (docs_root / "benchmarks" / "languages" / info.page_name).read_text(
            encoding="utf-8"
        )
        start = text.index("## Speed")
        end = text.index("## Interpretation Notes", start)
        scores: list[tuple[str, float]] = []
        for line in text[start:end].splitlines():
            cells = [cell.strip() for cell in line.split("|")[1:-1]]
            if len(cells) != 7 or not cells[1].startswith("`"):
                continue
            key = select_speed_key(cells[1].strip("`"), speed_data, info.language)
            scores.append((cells[0], float(speed_data.primary[key]["Score"])))
        if len(scores) < 2:
            continue
        radixor = [score for label, score in scores if label == "Radixor"]
        if len(radixor) != 1:
            raise ValueError(f"Expected one Radixor speed row on {info.page_name}")
        comparisons += 1
        if radixor[0] == min(score for _, score in scores):
            wins += 1
    return wins, comparisons


def render_snowball_homepage(
    cases: list[SnowballCase],
    corpora_by_model: dict[str, dict[str, object]],
    speed_data: JmhData,
    snapshot_date: str,
) -> str:
    """Render the complete direct Snowball comparison and accessible fallback."""

    rows: list[tuple[SnowballCase, float, float, float, float, float, str, bool]] = []
    for case in cases:
        if not case.homepage_chart:
            continue
        corpus = corpora_by_model.get(case.model_id)
        if corpus is None:
            raise ValueError(f"Snowball case {case.case_name} has no exact model corpus")
        timing_tokens = int(corpus["timing"])
        timing_basis = str(corpus["timing_basis"])
        if not case.homepage_aggregate and int(corpus["changed"]) > 0:
            raise ValueError(
                f"Snowball aggregate excludes changed-token corpus for {case.case_name}"
            )
        snowball_key = select_speed_key(case.speed_method, speed_data, case.language)
        radixor_key = select_speed_key(f"radixor[{case.model_id}]", speed_data, case.language)
        snowball_row = speed_data.primary[snowball_key]
        radixor_row = speed_data.primary[radixor_key]
        snowball_ns = float(snowball_row["Score"]) / timing_tokens
        snowball_error = float(snowball_row["Score Error (99.9%)"]) / timing_tokens
        radixor_ns = float(radixor_row["Score"]) / timing_tokens
        radixor_error = float(radixor_row["Score Error (99.9%)"]) / timing_tokens
        ratio = snowball_ns / radixor_ns
        if not all(math.isfinite(value) and value > 0.0 for value in (snowball_ns, snowball_error, radixor_ns, radixor_error, ratio)):
            raise ValueError(f"Snowball homepage comparison contains a non-positive or non-finite value for {case.case_name}")
        overlap = (
            snowball_ns - snowball_error <= radixor_ns + radixor_error
            and radixor_ns - radixor_error <= snowball_ns + snowball_error
        )
        rows.append((case, radixor_ns, radixor_error, snowball_ns, snowball_error, ratio, timing_basis, overlap))
    aggregate_ratios = [ratio for case, _, _, _, _, ratio, _, _ in rows if case.homepage_aggregate]
    chart_count = sum(case.homepage_chart for case in cases)
    aggregate_count = sum(case.homepage_aggregate for case in cases)
    if len(rows) != chart_count or len(aggregate_ratios) != aggregate_count:
        raise ValueError("Snowball homepage speed coverage differs from the authoritative catalog policy")
    if any(timing_basis != "changed tokens" for case, _, _, _, _, _, timing_basis, _ in rows
           if case.homepage_aggregate):
        raise ValueError("Every aggregated Snowball comparison must use a changed-token corpus")
    geomean = math.exp(math.fsum(math.log(value) for value in aggregate_ratios)
                       / len(aggregate_ratios))
    table = [
        "<table><thead><tr><th>Language</th><th>Model</th><th>Timing corpus</th><th>Radixor ns/token</th><th>Radixor JMH error</th><th>Snowball ns/token</th><th>Snowball JMH error</th><th>Snowball / Radixor</th><th>99.9% intervals</th><th>Aggregate</th></tr></thead><tbody>"
    ]
    bars: list[str] = []
    logarithmic_extent = max(
        math.log(1.1),
        max(abs(math.log(ratio)) for _, _, _, _, _, ratio, _, _ in rows),
    )
    tied_count = sum(overlap for _, _, _, _, _, _, _, overlap in rows)
    for case, radixor_ns, radixor_error, snowball_ns, snowball_error, ratio, timing_basis, overlap in rows:
        escaped = html.escape(case.display_language)
        aggregate = "yes" if case.homepage_aggregate else "no — catalog policy"
        interval_label = "overlap — statistically tied" if overlap else "do not overlap"
        table.append(
            f"<tr><td>{escaped}</td><td><code>{case.model_id}</code></td><td>{timing_basis}</td>"
            f"<td>{radixor_ns:.1f}</td><td>±{radixor_error:.1f}</td><td>{snowball_ns:.1f}</td><td>±{snowball_error:.1f}</td>"
            f"<td>{ratio:.3f}×</td><td>{interval_label}</td><td>{aggregate}</td></tr>"
        )
        displacement = 50.0 * math.log(ratio) / logarithmic_extent
        left = min(50.0, 50.0 + displacement)
        width = abs(displacement)
        bars.append(
            f'<div class="rx2-ratio-row"><span>{escaped}</span>'
            f'<span class="rx2-ratio-track" role="img" '
            f'aria-label="{escaped}: Snowball to Radixor runtime ratio {ratio:.3f}; '
            f'99.9 percent intervals {interval_label}" '
            f'title="{escaped}: {ratio:.3f}×"><i style="--left:{left:.2f}%;--width:{width:.2f}%"></i></span>'
            f'<b>{ratio:.3f}×</b></div>'
        )
    table.append("</tbody></table>")
    return (
        "<!-- JAVA-SNOWBALL-COMPARISON:START -->\n"
        '<div class="rx2-chart-title"><strong>Java Radixor and official direct Snowball</strong> '
        "<span>(exact-language cases)</span></div>\n"
        f'<div class="rx2-throughput"><b>{geomean:.3f}×</b> <span>Snowball / Radixor geometric-mean runtime ratio</span></div>\n'
        f'<div class="rx2-ratio-chart" aria-label="Log-scaled Snowball divided by Radixor nanoseconds per token for {chart_count} exact-language cases">\n'
        '<div class="rx2-ratio-axis"><span>Radixor slower</span><b>1×</b><span>Snowball slower</span></div>\n'
        + "\n".join(bars) + "\n</div>\n"
        f'<p class="rx2-note">The {snapshot_date} Java JMH snapshot charts all <b>{chart_count}</b> supported official direct Snowball exact-language cases against the exact generic Radixor model row. The geometric mean is <b>{geomean:.3f}× over all {aggregate_count} comparable changed-token cases</b>. A ratio above 1 means Snowball used more time per token; ratios are point estimates, not categorical winners. The 99.9% JMH intervals overlap in <b>{tied_count}</b> cases, which are labelled statistically tied. All rows use 3 warmup iterations, 5 measurement iterations, 3 forks, one thread, and the same per-language timing population.</p>\n'
        '<details class="quality-details"><summary>Accessible raw Java comparison table</summary>\n'
        + "".join(table) + "\n</details>\n"
        "<!-- JAVA-SNOWBALL-COMPARISON:END -->"
    )


def replace_homepage_language_section(text: str, language_section: str) -> str:
    """Replace the generated homepage language block without whitespace drift."""

    updated, replacements = re.subn(
        r'(?:[ \t]*\n)*[ \t]*(?:<!-- JAVA-LANGUAGE-PAGES:START -->\n)?'
        r'<section class="rx2-languages-strip(?: rx2-languages-strip--active)?">.*?</section>\n'
        r'(?:<!-- JAVA-LANGUAGE-PAGES:END -->)?(?:[ \t]*\n)*',
        "\n\n    " + language_section + "\n\n",
        text,
        count=1,
        flags=re.DOTALL,
    )
    if replacements != 1:
        raise ValueError("Homepage language-page section is missing or ambiguous")
    return updated


def update_snowball_overview(
    docs_root: Path,
    cases: list[SnowballCase],
    corpora_by_model: dict[str, dict[str, object]],
    speed_data: JmhData,
    snapshot_date: str,
    publication: Publication,
) -> None:
    """Publish the full Java exact-language comparison on the benchmark overview."""

    path = docs_root / "benchmarks/index.md"
    text = path.read_text(encoding="utf-8")
    section = render_snowball_homepage(cases, corpora_by_model, speed_data, snapshot_date)
    if "<!-- JAVA-SNOWBALL-COMPARISON:START -->" in text:
        text = re.sub(
            r"<!-- JAVA-SNOWBALL-COMPARISON:START -->.*?<!-- JAVA-SNOWBALL-COMPARISON:END -->",
            section,
            text,
            count=1,
            flags=re.DOTALL,
        )
    else:
        text = text.rstrip() + "\n\n## Java Radixor and Snowball runtime comparison\n\n" + section + "\n"
    publication.write(path, text)


def update_homepage(
    docs_root: Path,
    model_infos: list[ModelInfo],
    defaults: dict[str, ModelInfo],
    snowball_cases: list[SnowballCase],
    corpora_by_model: dict[str, dict[str, object]],
    speed_data: JmhData,
    quality_path: Path,
    publication: Publication,
    geography: dict[str, HomepageGeography],
    snapshot_date: str,
    generalization_scenarios: int,
    generalization_languages: int,
    speed_wins: int,
    speed_comparisons: int,
) -> None:
    """Publish lifecycle-safe Java evidence and every language-page link."""

    if not model_infos or not defaults:
        raise ValueError("Homepage publication requires active models and language defaults")
    page_names = {info.page_name for info in defaults.values()}
    if len(page_names) != len(defaults):
        raise ValueError("Homepage language pages are not unique")
    for page_name in page_names:
        if not (docs_root / "benchmarks" / "languages" / page_name).is_file():
            raise ValueError(f"Homepage language link target does not exist: {page_name}")
    landing = docs_root / "overrides" / "landing.html"
    text = landing.read_text(encoding="utf-8")
    if "<!-- JAVA-SNOWBALL-COMPARISON:START -->" in text:
        text = re.sub(
            r"\s*<!-- JAVA-SNOWBALL-COMPARISON:START -->.*?<!-- JAVA-SNOWBALL-COMPARISON:END -->\s*",
            "\n", text, count=1, flags=re.DOTALL,
        )
    links: list[str] = []
    for info in sorted(defaults.values(), key=lambda item: (item.language_name.casefold(), item.model_id)):
        place = geography[info.language]
        glyph = geography_glyph(place.icon)
        model_label = info.model_id
        rating_basis = ""
        if info.language == "PL_PL":
            model_label += " + pl-pl-polimorf"
            rating_basis = (
                "; size rating represents the page-primary pl-pl-unimorph model; "
                "the page also documents optional pl-pl-polimorf"
            )
        links.append(
            f'<a class="rx2-language" href="{{{{ base_url }}}}/benchmarks/languages/{info.page_name.removesuffix(".md")}/" '
            f'aria-label="Open {html.escape(info.language_name)} benchmark; {html.escape(place.label)}{rating_basis}">'
            f'<i class="rx2-language-icon" aria-hidden="true" title="{html.escape(place.label)}">{glyph}</i>'
            f'<span>{html.escape(info.language_name)}</span>'
            f'<small>{model_label}</small>{rating_markup(info)}</a>'
        )
    language_section = (
        '<!-- JAVA-LANGUAGE-PAGES:START -->\n<section class="rx2-languages-strip rx2-languages-strip--active">\n'
        f'<div class="rx2-language-label"><strong>{len(model_infos)} models · {len(defaults)} language pages</strong></div>\n'
        '<div class="rx2-language-list">' + "".join(links) + '</div>\n</section>\n'
        '<!-- JAVA-LANGUAGE-PAGES:END -->'
    )
    text = replace_homepage_language_section(text, language_section)
    text = re.sub(r"across \d+ language defaults", f"across {len(defaults)} language defaults", text)
    text = re.sub(r"<b>\d+ model artifacts</b>", f"<b>{len(model_infos)} model artifacts</b>", text)
    publication.write(landing, text)


def main() -> None:
    global LANGUAGES
    global LANGUAGE_IDENTITY_WORDS
    global SNOWBALL_CASES

    arguments = parse_arguments()
    publication = Publication(arguments.mode)
    verify_active_inputs(
        arguments.docs_root / "benchmarks" / "data" / "active-snapshots.properties",
        {
            "corpus": arguments.corpus,
            "accuracy": arguments.accuracy,
            "speed": arguments.speed,
            "coverage_accuracy": arguments.coverage_accuracy,
            "coverage_speed": arguments.coverage_speed,
            "quality": arguments.quality,
            "comparator": arguments.snowball_catalog,
        },
    )
    snowball_cases = read_snowball_catalog(arguments.snowball_catalog)
    SNOWBALL_CASES = {
        case.language: (case.case_name, case.lucene_available)
        for case in snowball_cases
    }
    prohibited_ratings = (
        arguments.docs_root
        / "benchmarks/data/prohibited-active-snapshots.properties"
    ).is_file()
    model_infos = include_prohibited_rating_population(
        arguments.docs_root, load_model_infos()
    )
    defaults = prepare_language_pages(
        arguments.docs_root,
        model_infos,
        publication,
        "benchmarked dictionaries" if prohibited_ratings else "active user-facing models",
    )
    generalization_scenarios, generalization_languages = read_active_generalization_summary(
        arguments.docs_root / "benchmarks" / "data" / "active-snapshots.properties",
        set(defaults),
    )
    geography = read_homepage_geography(
        arguments.docs_root / "benchmarks" / "data" / "homepage-language-geography.csv",
        set(defaults),
    )
    polimorf = next(info for info in model_infos if info.model_id == "pl-pl-polimorf")
    prepare_polimorf_section(arguments.docs_root, polimorf, publication)
    LANGUAGES = {
        info.page_name: info.language
        for info in sorted(defaults.values(), key=lambda item: item.page_name)
    }
    legacy_identity_words = LANGUAGE_IDENTITY_WORDS
    LANGUAGE_IDENTITY_WORDS = {
        info.language: legacy_identity_words.get(
            info.language, words(info.language_name)
        )
        for info in defaults.values()
    }
    corpora_by_model = read_corpora(arguments.corpus)
    infos_by_id = {info.model_id: info for info in model_infos}
    for model_id, entry in corpora_by_model.items():
        if model_id not in infos_by_id:
            raise ValueError(f"Corpus report contains unknown model {model_id}")
        expected_distinct = infos_by_id[model_id].distinct_forms
        reported_distinct = int(entry["distinct"])
        if reported_distinct not in {0, expected_distinct}:
            raise ValueError(
                f"Corpus distinct-form count for {model_id} is {reported_distinct}, "
                f"expected {expected_distinct}"
            )
        entry["distinct"] = expected_distinct
    corpora = {
        info.language: corpora_by_model[info.model_id]
        for info in defaults.values()
        if info.model_id in corpora_by_model
    }
    accuracy_data = read_jmh(arguments.accuracy)
    speed_data = read_jmh(arguments.speed)
    coverage_accuracy_data = read_jmh(arguments.coverage_accuracy)
    coverage_speed_data = read_jmh(arguments.coverage_speed)
    speed_protocol = infer_speed_protocol(speed_data, "stemmer speed report")
    coverage_speed_protocol = infer_speed_protocol(
        coverage_speed_data, "coverage speed report"
    )
    if speed_protocol != coverage_speed_protocol:
        raise ValueError(
            "Stemmer and coverage speed reports use inconsistent supported protocols: "
            f"{speed_protocol.samples} and {coverage_speed_protocol.samples} samples"
        )
    english_speed_key = select_speed_key(
        "radixor[us-uk-default]", speed_data, "US_UK"
    )
    if (
        english_speed_key is None
        or speed_data.primary[english_speed_key]["Unit"] != "ns/op"
    ):
        raise ValueError("Expected one English exact-model Radixor speed row.")
    main_english_ns = (
        float(speed_data.primary[english_speed_key]["Score"])
        / int(corpora["US_UK"]["timing"])
    )
    update_language_pages(
        arguments.docs_root,
        corpora,
        accuracy_data,
        speed_data,
        speed_protocol,
        publication,
        arguments.date,
        arguments.release_version,
        {case.language: case for case in snowball_cases},
    )
    update_polimorf_section(
        arguments.docs_root,
        polimorf,
        corpora_by_model.get(polimorf.model_id),
        accuracy_data,
        speed_data,
        publication,
    )
    update_corpora_reference(
        arguments.docs_root, model_infos, corpora_by_model, publication
    )
    update_model_indexes(arguments.docs_root, model_infos, defaults, publication)
    update_mkdocs_language_nav(arguments.docs_root, defaults, publication)
    update_speed_protocol_documentation(
        arguments.docs_root, speed_protocol, publication
    )
    update_environment_provenance(
        arguments.docs_root, arguments.date, arguments.release_version, publication
    )
    update_reproducibility_release_identity(
        arguments.docs_root, arguments.release_version, publication
    )
    update_coverage(
        arguments.docs_root,
        arguments.readme,
        coverage_accuracy_data,
        coverage_speed_data,
        int(corpora["US_UK"]["timing"]),
        int(corpora["US_UK"]["total"]),
        publication,
        main_english_ns,
    )
    update_snowball_overview(
        arguments.docs_root,
        snowball_cases,
        corpora_by_model,
        speed_data,
        arguments.date,
        publication,
    )
    update_homepage(
        arguments.docs_root,
        model_infos,
        defaults,
        snowball_cases,
        corpora_by_model,
        speed_data,
        arguments.quality,
        publication,
        geography,
        arguments.date,
        generalization_scenarios,
        generalization_languages,
        *speed_homepage_counts(arguments.docs_root, defaults, speed_data),
    )
    verb = "Verified" if arguments.mode == "verify" else "Updated"
    print(f"{verb} Java benchmark documentation for {arguments.release_version} ({arguments.date}).")


if __name__ == "__main__":
    main()
