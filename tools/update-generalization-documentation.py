#!/usr/bin/env python3
"""Publish or verify the deterministic dictionary-generalization report."""

from __future__ import annotations

import argparse
import csv
import hashlib
import re
import statistics
from collections import defaultdict
from pathlib import Path


LANGUAGES = {
    "CS_CZ": "Czech",
    "DA_DK": "Danish",
    "DE_DE": "German",
    "ES_ES": "Spanish",
    "FA_IR": "Persian",
    "FI_FI": "Finnish",
    "FR_FR": "French",
    "HE_IL": "Hebrew",
    "HU_HU": "Hungarian",
    "IT_IT": "Italian",
    "NB_NO": "Norwegian Bokmål",
    "NL_NL": "Dutch",
    "NN_NO": "Norwegian Nynorsk",
    "PL_PL": "Polish",
    "PT_PT": "Portuguese",
    "RU_RU": "Russian",
    "SV_SE": "Swedish",
    "UK_UA": "Ukrainian",
    "US_UK": "English",
    "YI": "Yiddish",
}
LANGUAGE_PAGES = {
    "CS_CZ": "czech.md", "DA_DK": "danish.md", "DE_DE": "german.md",
    "ES_ES": "spanish.md", "FA_IR": "persian.md", "FI_FI": "finnish.md",
    "FR_FR": "french.md", "HE_IL": "hebrew.md", "HU_HU": "hungarian.md",
    "IT_IT": "italian.md", "NB_NO": "norwegian-bokmal.md", "NL_NL": "dutch.md",
    "NN_NO": "norwegian-nynorsk.md", "PL_PL": "polish.md",
    "PT_PT": "portuguese.md", "RU_RU": "russian.md", "SV_SE": "swedish.md",
    "UK_UA": "ukrainian.md", "US_UK": "english.md", "YI": "yiddish.md",
}
PERCENTS = tuple(range(100, 0, -10))
EXPECTED_PROTOCOL = "radixor-generalization-v1"
EXPECTED_SEEDS = {
    "2654435761", "2611923443488327891", "7046029254386353131",
    "11400714819323198485", "15111065706836454659",
}
COUNT_SCOPES = ("whole", "withheld", "unseen")
COUNT_FAMILIES = ("", "changed_", "root_")
LANGUAGE_SECTION_START = "<!-- DICTIONARY-GENERALIZATION:START -->"
LANGUAGE_SECTION_END = "<!-- DICTIONARY-GENERALIZATION:END -->"


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("documentation_root", type=Path)
    parser.add_argument("mode", choices=("update", "verify"))
    return parser.parse_args()


def read_model_catalog(path: Path) -> dict[str, tuple[str, str, str]]:
    models: dict[str, tuple[str, str, str]] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|"):
            continue
        cells = [cell.strip().strip("`") for cell in line.split("|")[1:-1]]
        if len(cells) == 14 and cells[2] == "true":
            models[cells[1]] = (cells[0], cells[4], cells[12])
    if set(models) != set(LANGUAGES):
        raise ValueError("The checked-in model catalog does not define the expected 20 defaults.")
    return models


def read_corpus_catalog(path: Path) -> dict[str, tuple[str, str, str, int, int, int, int]]:
    corpora: dict[str, tuple[str, str, str, int, int, int, int]] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|"):
            continue
        cells = [cell.strip().strip("`") for cell in line.split("|")[1:-1]]
        if len(cells) == 9 and cells[3] in LANGUAGES:
            corpora[cells[3]] = (
                cells[0], cells[1], cells[2], int(cells[4].replace(",", "")),
                int(cells[5].replace(",", "")), int(cells[6].replace(",", "")),
                int(cells[7].replace(",", "")),
            )
    if set(corpora) != set(LANGUAGES):
        raise ValueError("The checked-in corpus table does not define the expected 20 defaults.")
    return corpora


def read_and_validate(source: Path, documentation_root: Path) -> list[dict[str, str]]:
    with source.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        raise ValueError("The generalization CSV is empty.")
    required = {
        "protocol_version", "radixor_java_version", "source_revision", "source_state",
        "generator_sha256", "language", "model_id", "model_version", "model_sha256",
        "seed", "requested_percent",
        "selected_rows", "total_rows", "withheld_rows",
        "excluded_overlap_occurrences",
    }
    for scope in COUNT_SCOPES:
        for family in COUNT_FAMILIES:
            required.add(f"{scope}_{family}correct")
            required.add(f"{scope}_{family}total")
    missing = required.difference(rows[0])
    if missing:
        raise ValueError(f"Missing required columns: {sorted(missing)}")

    protocols = {row["protocol_version"] for row in rows}
    versions = {row["radixor_java_version"] for row in rows}
    seeds = {row["seed"] for row in rows}
    if protocols != {EXPECTED_PROTOCOL} or len(versions) != 1 or not next(iter(versions)).strip():
        raise ValueError("Unexpected split protocol or inconsistent Radixor/Java identity.")
    if seeds != EXPECTED_SEEDS:
        raise ValueError("The report does not use the five predeclared split seeds.")
    if len({row["source_revision"] for row in rows}) != 1 \
            or len({row["source_state"] for row in rows}) != 1 \
            or len({row["generator_sha256"] for row in rows}) != 1:
        raise ValueError("Source revision, state, and generator digest must be invariant.")
    first = rows[0]
    if len(first["source_revision"]) != 40 or len(first["generator_sha256"]) != 64 \
            or not first["source_state"].strip():
        raise ValueError("Source provenance is incomplete.")
    models = read_model_catalog(documentation_root / "stemmer-model-catalog.md")
    corpora = read_corpus_catalog(documentation_root / "benchmarks/reference/corpora.md")
    keys: set[tuple[str, str, int]] = set()
    by_language: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        language = row["language"]
        percent = int(row["requested_percent"])
        key = (language, row["seed"], percent)
        if key in keys:
            raise ValueError(f"Duplicate scenario: {key}")
        keys.add(key)
        by_language[language].append(row)
        if language not in LANGUAGES or percent not in PERCENTS:
            raise ValueError(f"Unexpected language or percentage: {key}")
        model = (row["model_id"], row["model_version"], row["model_sha256"])
        if model != models[language] or model != corpora[language][:3]:
            raise ValueError(f"Scenario does not use the published default model: {key}")
        total_rows = int(row["total_rows"])
        selected_rows = int(row["selected_rows"])
        expected_selected = total_rows if percent == 100 else max(
            1, (total_rows * percent + 50) // 100
        )
        if selected_rows != expected_selected:
            raise ValueError(f"Incorrect selected-row count: {key}")
        if int(row["withheld_rows"]) != total_rows - selected_rows:
            raise ValueError(f"Incorrect withheld-row count: {key}")
        for scope in COUNT_SCOPES:
            total = int(row[f"{scope}_total"])
            changed = int(row[f"{scope}_changed_total"])
            roots = int(row[f"{scope}_root_total"])
            if total != changed + roots:
                raise ValueError(f"Count partition is inconsistent: {key}, {scope}")
            if int(row[f"{scope}_correct"]) != (
                int(row[f"{scope}_changed_correct"]) + int(row[f"{scope}_root_correct"])
            ):
                raise ValueError(f"Correct-count partition is inconsistent: {key}, {scope}")
            for family in COUNT_FAMILIES:
                correct = int(row[f"{scope}_{family}correct"])
                denominator = int(row[f"{scope}_{family}total"])
                if not 0 <= correct <= denominator:
                    raise ValueError(f"Invalid numerator: {key}, {scope}, {family}")
        if int(row["withheld_total"]) != (
            int(row["unseen_total"]) + int(row["excluded_overlap_occurrences"])
        ):
            raise ValueError(f"Withheld/unseen overlap arithmetic is inconsistent: {key}")
        for family in COUNT_FAMILIES:
            if int(row[f"unseen_{family}total"]) > int(row[f"withheld_{family}total"]) \
                    or int(row[f"unseen_{family}correct"]) > int(row[f"withheld_{family}correct"]):
                raise ValueError(f"Unseen counters exceed withheld counters: {key}, {family}")
        if percent == 100 and any(int(row[column]) for column in (
            "withheld_total", "unseen_total", "excluded_overlap_occurrences"
        )):
            raise ValueError(f"The 100% scenario must have no held-out observations: {key}")

    if set(by_language) != set(LANGUAGES):
        raise ValueError("The report does not contain exactly the 20 default languages.")
    expected_per_language = len(seeds) * len(PERCENTS)
    for language, language_rows in by_language.items():
        if len(language_rows) != expected_per_language:
            raise ValueError(f"Incomplete scenario matrix for {language}.")
        if len({(row["model_id"], row["model_version"], row["model_sha256"])
                for row in language_rows}) != 1:
            raise ValueError(f"Model provenance changes within {language}.")
        corpus = corpora[language]
        denominators = {(int(row["total_rows"]), int(row["whole_total"]),
                         int(row["whole_root_total"]), int(row["whole_changed_total"]))
                        for row in language_rows}
        if denominators != {(corpus[3], corpus[4], corpus[5], corpus[6])}:
            raise ValueError(f"Scenario denominators differ from the published corpus: {language}")
        full_rows = [row for row in language_rows if int(row["requested_percent"]) == 100]
        full_counters = {tuple(row[f"whole_{family}{suffix}"]
                               for family in COUNT_FAMILIES for suffix in ("correct", "total"))
                         for row in full_rows}
        if len(full_counters) != 1:
            raise ValueError(f"Full-coverage counters differ across splits: {language}")
    return rows


def ratio(row: dict[str, str], prefix: str) -> float | None:
    denominator = int(row[f"{prefix}_total"])
    if denominator == 0:
        return None
    return 100.0 * int(row[f"{prefix}_correct"]) / denominator


def median_range(values: list[float | None]) -> str:
    measured = [value for value in values if value is not None]
    if not measured:
        return "n/a"
    middle = statistics.median(measured)
    if max(measured) - min(measured) < 0.0005:
        return f"{middle:.3f}%"
    return f"{middle:.3f}% ({min(measured):.3f}–{max(measured):.3f})"


def macro(rows: list[dict[str, str]], percent: int, prefix: str) -> float | None:
    medians: list[float] = []
    for language in LANGUAGES:
        values = [ratio(row, prefix) for row in rows
                  if row["language"] == language
                  and int(row["requested_percent"]) == percent]
        measured = [value for value in values if value is not None]
        if measured:
            medians.append(statistics.median(measured))
    return statistics.mean(medians) if medians else None


def percent(value: float | None) -> str:
    return "n/a" if value is None else f"{value:.2f}%"


def median_ratio(rows: list[dict[str, str]], prefix: str) -> float | None:
    """Returns the median percentage for one language/coverage slice."""
    measured = [value for value in (ratio(row, prefix) for row in rows) if value is not None]
    return statistics.median(measured) if measured else None


def replace_language_section(document: str, section: str) -> str:
    """Replaces one generated language conclusion or inserts it before cost analysis."""
    block = f"{LANGUAGE_SECTION_START}\n\n{section.rstrip()}\n\n{LANGUAGE_SECTION_END}"
    start_count = document.count(LANGUAGE_SECTION_START)
    end_count = document.count(LANGUAGE_SECTION_END)
    if start_count == 1 and end_count == 1:
        return re.sub(
            re.escape(LANGUAGE_SECTION_START) + r".*?" + re.escape(LANGUAGE_SECTION_END),
            block,
            document,
            count=1,
            flags=re.S,
        )
    if start_count != 0 or end_count != 0:
        raise ValueError("A language page contains incomplete or duplicate generalization markers.")
    anchors = ("<!-- EDIT-COST-GENERALIZATION:START -->", "<!-- STEMMING-QUALITY:START -->")
    anchor = next((candidate for candidate in anchors if document.count(candidate) == 1), None)
    if anchor is None:
        raise ValueError("A language page has no unique experimental-section insertion anchor.")
    return document.replace(anchor, f"{block}\n\n{anchor}", 1)


def render_language_conclusion(language: str, rows: list[dict[str, str]]) -> str:
    """Renders the independent baseline-generalization evidence for one language."""
    by_percent = {
        coverage: [row for row in rows if int(row["requested_percent"]) == coverage]
        for coverage in range(10, 100, 10)
    }
    if any(len(scenarios) != len(EXPECTED_SEEDS) for scenarios in by_percent.values()):
        raise ValueError(f"Incomplete language conclusion matrix for {language}.")
    first = by_percent[10]
    last = by_percent[90]
    first_changed = median_ratio(first, "unseen_changed")
    last_changed = median_ratio(last, "unseen_changed")
    first_all = median_ratio(first, "unseen")
    last_all = median_ratio(last, "unseen")
    first_root = median_ratio(first, "unseen_root")
    last_root = median_ratio(last, "unseen_root")
    if None in (first_changed, last_changed, first_all, last_all, first_root, last_root):
        raise ValueError(f"Undefined endpoint generalization metric for {language}.")
    provenance = rows[0]
    lines = [
        "## Dictionary-Family Generalization Conclusion", "",
        f"This is the language-specific conclusion from the independent `{EXPECTED_PROTOCOL}` baseline",
        "experiment. It is intentionally separate from the wider edit-cost protocol below; values from",
        "the two frozen snapshots are not substituted for one another.", "",
        "### Evidence", "",
        f"Model `{provenance['model_id']}` version `{provenance['model_version']}` is evaluated over five",
        "predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface",
        "also appeared in training. Parentheses show the observed split minimum–maximum.", "",
        "| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |",
        "| ---: | ---: | ---: | ---: | ---: |",
    ]
    for coverage, scenarios in by_percent.items():
        lines.append(
            f"| {coverage}% | {int(statistics.median(int(row['unseen_total']) for row in scenarios)):,} | "
            f"{median_range([ratio(row, 'unseen') for row in scenarios])} | "
            f"{median_range([ratio(row, 'unseen_changed') for row in scenarios])} | "
            f"{median_range([ratio(row, 'unseen_root') for row in scenarios])} |"
        )
    changed_gain = float(last_changed) - float(first_changed)
    all_gain = float(last_all) - float(first_all)
    root_gain = float(last_root) - float(first_root)
    lines.extend([
        "", "### Generalization conclusion", "",
        f"- Median exactness on genuinely unseen changed forms moves from **{float(first_changed):.3f}%**",
        f"  at 10% training knowledge to **{float(last_changed):.3f}%** at 90%, a measured",
        f"  **{changed_gain:+.3f} percentage-point** change for this dictionary.",
        f"- Over the same endpoints, unseen all-form exactness changes by **{all_gain:+.3f} pp** and",
        f"  preservation of unseen already-root forms changes by **{root_gain:+.3f} pp**. These separate",
        "  outcomes show whether the changed-form result coexists with preservation behavior.",
        "- The evidence establishes within-resource transfer across withheld dictionary families. It",
        "  does not estimate unrelated domains, misspellings, arbitrary compounds, or external corpora.",
        "", "The complete ten-level table and split ranges remain in the",
        "[independent generalization report](../generalization.md); raw counters and provenance are in",
        "[`dictionary-generalization.csv`](../data/dictionary-generalization.csv). The",
        "[frozen methodology](../reference/generalization-methodology.md) defines family-level",
        "splitting, unseen-surface leakage control, aggregation, and the limits of the claim.",
    ])
    return "\n".join(lines)


def render(rows: list[dict[str, str]], checksum: str) -> str:
    first = rows[0]
    endpoints: dict[str, tuple[float, float]] = {}
    for language in LANGUAGES:
        low_rows = [row for row in rows
                    if row["language"] == language and int(row["requested_percent"]) == 10]
        high_rows = [row for row in rows
                     if row["language"] == language and int(row["requested_percent"]) == 90]
        low = median_ratio(low_rows, "unseen_changed")
        high = median_ratio(high_rows, "unseen_changed")
        if low is None or high is None:
            raise ValueError(f"Undefined generalization endpoint for {language}.")
        endpoints[language] = (low, high)
    lowest_at_ten = min(endpoints, key=lambda language: endpoints[language][0])
    highest_at_ten = max(endpoints, key=lambda language: endpoints[language][0])
    lowest_at_ninety = min(endpoints, key=lambda language: endpoints[language][1])
    highest_at_ninety = max(endpoints, key=lambda language: endpoints[language][1])
    largest_gain = max(
        endpoints,
        key=lambda language: endpoints[language][1] - endpoints[language][0],
    )
    lines = [
        "# Dictionary-Family Generalization",
        "",
        "Radixor learns transformations from lexical families rather than storing a closed",
        "word-to-root answer list. This experiment measures how those transformations transfer",
        "to dictionary families that were not used to build the Java trie.",
        "",
        "For every one of the 20 default models, complete dictionary rows are placed in a",
        "frozen pseudorandom order. Exact-size, nested prefixes retain 10% through 100% of",
        "the rows for training. Five predeclared splits are evaluated against the complete",
        "dictionary; the primary `Unseen` columns exclude a held-out occurrence whenever its",
        "normalized surface form also appeared in training. This prevents a duplicated form",
        "from being presented as unseen evidence.",
        "",
        "The experiment measures **within-resource dictionary-family generalization**. It does",
        "not claim performance on new domains, misspellings, arbitrary compounds, or other",
        "out-of-distribution text. See the [methodology and limitations](reference/generalization-methodology.md).",
        "",
        "## All-Language Summary",
        "",
        "Each cell is the language-macro mean of 20 per-language split medians, so large",
        "dictionaries do not dominate small ones. Changed-form exactness is the most demanding",
        "measure because it excludes words whose expected root is already the input token.",
        "",
        "| Training rows | Unseen all exact | Unseen changed exact | Unseen root preserved |",
        "| ---: | ---: | ---: | ---: |",
    ]
    for coverage in PERCENTS:
        lines.append("| {}% | {} | {} | {} |".format(
            coverage,
            percent(macro(rows, coverage, "unseen")),
            percent(macro(rows, coverage, "unseen_changed")),
            percent(macro(rows, coverage, "unseen_root")),
        ))
    lines += [
        "",
        "The language range is material and is therefore not replaced by the macro mean. At 10%",
        f"knowledge, median unseen changed-form exactness ranges from **{endpoints[lowest_at_ten][0]:.3f}%**",
        f"for {LANGUAGES[lowest_at_ten]} to **{endpoints[highest_at_ten][0]:.3f}%** for",
        f"{LANGUAGES[highest_at_ten]}. At 90%, it ranges from **{endpoints[lowest_at_ninety][1]:.3f}%**",
        f"for {LANGUAGES[lowest_at_ninety]} to **{endpoints[highest_at_ninety][1]:.3f}%** for",
        f"{LANGUAGES[highest_at_ninety]}. The largest 10%–90% change is",
        f"**{endpoints[largest_gain][1] - endpoints[largest_gain][0]:+.3f} pp** for",
        f"{LANGUAGES[largest_gain]}. These are descriptive within-resource results, not a causal",
        "ranking of language, script, dictionary size, or regularity.",
        "Every language's evidence-derived endpoint conclusion is also published on its separate",
        "[language benchmark page](languages/index.md), rather than inferred from this macro mean.",
        "",
        "## Per-Language Curves",
        "",
        "Values are the median across five frozen splits. Parentheses show the minimum–maximum",
        "split range. `Whole all exact` retains comparability with the earlier English coverage",
        "curve but mixes trained and held-out rows; the unseen columns are the generalization",
        "evidence. At 100% there is no held-out set, so unseen metrics are `n/a`.",
        "",
    ]
    for language, display_name in LANGUAGES.items():
        language_rows = [row for row in rows if row["language"] == language]
        provenance = language_rows[0]
        lines += [
            '<details markdown="block">',
            f"<summary><strong>{display_name}</strong> — <code>{provenance['model_id']}</code> "
            f"{provenance['model_version']}</summary>",
            '<div markdown="1">',
            "",
            "| Training | Selected / total rows | Median unseen occurrences | Overlap excluded | Unseen all exact | Unseen changed exact | Unseen root preserved | Whole all exact |",
            "| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
        ]
        for coverage in PERCENTS:
            scenarios = [row for row in language_rows
                         if int(row["requested_percent"]) == coverage]
            selected = scenarios[0]["selected_rows"]
            total = scenarios[0]["total_rows"]
            unseen_forms = [int(row["unseen_total"]) for row in scenarios]
            excluded = [int(row["excluded_overlap_occurrences"]) for row in scenarios]
            median_unseen = int(statistics.median(unseen_forms))
            median_excluded = int(statistics.median(excluded))
            lines.append("| {}% | {:,} / {:,} | {:,} | {:,} | {} | {} | {} | {} |".format(
                coverage, int(selected), int(total), median_unseen, median_excluded,
                median_range([ratio(row, "unseen") for row in scenarios]),
                median_range([ratio(row, "unseen_changed") for row in scenarios]),
                median_range([ratio(row, "unseen_root") for row in scenarios]),
                median_range([ratio(row, "whole") for row in scenarios]),
            ))
        lines += ["", "</div>", "</details>", ""]
    lines += [
        "## Provenance",
        "",
        f"- Radixor/Java: `{first['radixor_java_version']}`",
        f"- Core source revision: `{first['source_revision']}`",
        f"- Source state: `{first['source_state']}`",
        f"- Generalization generator SHA-256: `{first['generator_sha256']}`",
        "- Measured-source manifest: [`dictionary-generalization-sources.sha256`](data/dictionary-generalization-sources.sha256)",
        f"- Split protocol: `{first['protocol_version']}`",
        "- Splits per coverage level: 5",
        "- Authoritative raw counters: [`dictionary-generalization.csv`](data/dictionary-generalization.csv)",
        f"- CSV SHA-256: `{checksum}`",
        "- Model artifact IDs, independent versions, and SHA-256 values are recorded on every raw row.",
        "- Runtime speed is intentionally excluded: speed does not establish generalization. The",
        "  [English coverage deep dive](reference/english-coverage.md) retains its separately measured",
        "  JMH quality/speed curve.",
        "",
    ]
    return "\n".join(lines)


def main() -> None:
    arguments = parse_arguments()
    rows = read_and_validate(arguments.source, arguments.documentation_root)
    checksum = hashlib.sha256(arguments.source.read_bytes()).hexdigest()
    expected = render(rows, checksum)
    target = arguments.documentation_root / "benchmarks/generalization.md"
    data_directory = arguments.documentation_root / "benchmarks/data"
    published_csv = data_directory / "dictionary-generalization.csv"
    published_checksum = data_directory / "dictionary-generalization.sha256"
    checksum_text = f"{checksum}  dictionary-generalization.csv\n"
    language_documents: dict[Path, str] = {}
    for language, page_name in LANGUAGE_PAGES.items():
        page = arguments.documentation_root / "benchmarks/languages" / page_name
        if not page.is_file():
            raise ValueError(f"Missing language benchmark page: {page}")
        language_rows = [row for row in rows if row["language"] == language]
        section = render_language_conclusion(language, language_rows)
        language_documents[page] = replace_language_section(
            page.read_text(encoding="utf-8"), section)
    if arguments.mode == "update":
        target.write_text(expected, encoding="utf-8")
        for page, content in language_documents.items():
            page.write_text(content, encoding="utf-8")
        data_directory.mkdir(parents=True, exist_ok=True)
        published_csv.write_bytes(arguments.source.read_bytes())
        published_checksum.write_text(checksum_text, encoding="utf-8")
        print(f"Updated {target} from {len(rows)} validated scenarios.")
        return
    if target.read_text(encoding="utf-8") != expected:
        raise SystemExit(f"Generated generalization documentation is stale: {target}")
    for page, content in language_documents.items():
        if page.read_text(encoding="utf-8") != content:
            raise SystemExit(f"Generated language generalization conclusion is stale: {page}")
    if published_checksum.read_text(encoding="utf-8") != checksum_text:
        raise SystemExit("The checked-in generalization checksum is stale.")
    print(f"Verified {target} from {len(rows)} validated scenarios.")


if __name__ == "__main__":
    main()
