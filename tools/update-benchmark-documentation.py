#!/usr/bin/env python3
"""Update published benchmark tables from deterministic corpus and JMH CSV reports."""

from __future__ import annotations

import argparse
import csv
import math
import re
from collections import defaultdict
from dataclasses import dataclass
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


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--docs-root", type=Path, default=Path("docs"))
    parser.add_argument("--readme", type=Path, default=Path("README.md"))
    parser.add_argument("--corpus", type=Path, required=True)
    parser.add_argument("--accuracy", type=Path, required=True)
    parser.add_argument("--speed", type=Path, required=True)
    parser.add_argument("--coverage-accuracy", type=Path, required=True)
    parser.add_argument("--coverage-speed", type=Path, required=True)
    return parser.parse_args()


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


def accuracy(data: JmhData, key: Key) -> tuple[float, float, float]:
    counters = data.auxiliary[key]
    return (
        100.0 * counters["correctMatches"] / counters["evaluatedTokens"],
        100.0 * counters["changedCorrectMatches"] / counters["changedEvaluatedTokens"],
        100.0 * counters["rootPreservedMatches"] / counters["rootEvaluatedTokens"],
    )


def read_corpora(path: Path) -> dict[str, dict[str, object]]:
    corpora: dict[str, dict[str, object]] = {}
    with path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            language = row["Language"]
            entry = corpora.setdefault(
                language,
                {
                    "model": row["Model ID"],
                    "version": row["Model version"],
                    "sha256": row["Model SHA-256"],
                    "rows": int(row["Dictionary rows"]),
                    "total": int(row["Total tokens"]),
                    "roots": int(row["Already-root tokens"]),
                    "changed": int(row["Changed tokens"]),
                    "timing": int(row["Speed timing tokens"]),
                    "all_exact": int(row["All exact matches"]),
                    "changed_exact": int(row["Changed exact matches"]),
                    "root_exact": int(row["Root preserved matches"]),
                    "commands": [],
                },
            )
            entry["commands"].append((row["Command class"], int(row["Command count"])))
    if set(corpora) != set(LANGUAGES.values()):
        raise ValueError(
            f"Corpus report languages differ from documentation languages: {sorted(corpora)}"
        )
    if any(entry["model"] == "pl-pl-polimorf" for entry in corpora.values()):
        raise ValueError(
            "The default-model corpus report must not contain pl-pl-polimorf."
        )
    return corpora


def format_integer(value: int) -> str:
    return f"{value:,}"


def render_corpus_sections(language: str, entry: dict[str, object]) -> str:
    total = int(entry["total"])
    lines = [
        "## Dictionary Corpus",
        "",
        "| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |",
        "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |",
        f"| `{entry['model']}` | `{entry['version']}` | `{language}` | {format_integer(int(entry['rows']))} | "
        f"{format_integer(total)} | {format_integer(int(entry['roots']))} | "
        f"{format_integer(int(entry['changed']))} | {format_integer(int(entry['timing']))} |",
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


def rounded_accuracy(values: tuple[float, float, float]) -> tuple[str, str, str]:
    return tuple(f"{value:.3f}" for value in values)


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


def corpus_accuracy(entry: dict[str, object]) -> tuple[float, float, float]:
    return (
        100.0 * int(entry["all_exact"]) / int(entry["total"]),
        100.0 * int(entry["changed_exact"]) / int(entry["changed"]),
        100.0 * int(entry["root_exact"]) / int(entry["roots"]),
    )


def update_accuracy_table(
    text: str,
    new_data: JmhData,
    language: str,
    corpus: dict[str, object],
) -> str:
    start = text.index("## Accuracy")
    end = text.index("## Speed", start)
    section = text[start:end].rstrip()
    output: list[str] = []
    for line in section.splitlines():
        cells = [cell.strip() for cell in line.split("|")[1:-1]]
        measured = len(cells) == 5 and all(
            re.fullmatch(r"\d+\.\d{3}%", cell) for cell in cells[1:4]
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
            else:
                key = select_accuracy_key(cells[0], language, new_data)
                values = rounded_accuracy(accuracy(new_data, key))
            cells[1:4] = [f"{value}%" for value in values]
            line = "| " + " | ".join(cells) + " |"
        output.append(line)
    replacement = "\n".join(output).rstrip() + "\n\n"
    return text[:start] + replacement + text[end:]


def method_and_parameter(display: str) -> tuple[str, str]:
    match = re.fullmatch(r"([A-Za-z0-9]+)(?:\[([A-Z_]+)])?", display)
    if not match:
        raise ValueError(f"Unsupported benchmark method display: {display}")
    return match.group(1), match.group(2) or ""


def speed_matches(display: str, data: JmhData, language: str) -> list[Key]:
    method, language_case = method_and_parameter(display)
    matches = [
        key
        for key, row in data.primary.items()
        if key.method == method
        and (not language_case or key.parameter("languageCaseName") == language_case)
        and key not in data.auxiliary
        and row["Unit"] == "ns/op"
    ]
    if language_case or len(matches) <= 1:
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
            parsed.append((line, cells, key))
        else:
            parsed.append((line, None, None))
    if math.isnan(radixor_score):
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
            line = "| " + " | ".join(cells) + " |"
        output.append(line)
    replacement = "\n".join(output).rstrip() + "\n\n"
    return text[:start] + replacement + text[end:]


def update_language_pages(
    docs_root: Path,
    corpora: dict[str, dict[str, object]],
    accuracy_data: JmhData,
    speed_data: JmhData,
) -> None:
    directory = docs_root / "benchmarks" / "languages"
    for file_name, language in LANGUAGES.items():
        path = directory / file_name
        text = path.read_text(encoding="utf-8")
        corpus_start = text.index("## Dictionary Corpus")
        accuracy_start = text.index("## Accuracy", corpus_start)
        text = (
            text[:corpus_start]
            + render_corpus_sections(language, corpora[language])
            + text[accuracy_start:]
        )
        text = re.sub(
            r"Speed uses JMH average time, \d+ warmup iterations, \d+ measurement iterations, "
            r"\d+ forks?, and 1 thread\.",
            "Speed uses JMH average time, 5 warmup iterations, 10 measurement iterations, "
            "3 independent forks, and 1 thread.",
            text,
            count=1,
        )
        text = update_accuracy_table(text, accuracy_data, language, corpora[language])
        text = update_speed_table(
            text, speed_data, int(corpora[language]["timing"]), language
        )
        path.write_text(text, encoding="utf-8")


def update_corpora_reference(
    docs_root: Path, corpora: dict[str, dict[str, object]]
) -> None:
    path = docs_root / "benchmarks" / "reference" / "corpora.md"
    text = path.read_text(encoding="utf-8")
    original_header = "| Language resource |"
    current_header = "| Default model ID |"
    if original_header in text:
        table_start = text.index(original_header)
    elif current_header in text:
        table_start = text.index(current_header)
    else:
        raise ValueError(
            "The corpora reference contains no recognized corpus-table header."
        )
    table_end = text.index("\n\n", table_start)
    lines = [
        "| Default model ID | Version | SHA-256 | Language | Dictionary rows | Total tokens | Already-root tokens | Changed tokens | Speed timing tokens |",
        "| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for language in LANGUAGES.values():
        entry = corpora[language]
        lines.append(
            f"| `{entry['model']}` | `{entry['version']}` | `{entry['sha256']}` | `{language}` | "
            f"{format_integer(int(entry['rows']))} | "
            f"{format_integer(int(entry['total']))} | {format_integer(int(entry['roots']))} | "
            f"{format_integer(int(entry['changed']))} | {format_integer(int(entry['timing']))} |"
        )
    replacement = "\n".join(lines)
    path.write_text(
        text[:table_start] + replacement + text[table_end:], encoding="utf-8"
    )


def coverage_rows(accuracy_data: JmhData, speed_data: JmhData) -> list[str]:
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
            f"{speed / 1_000_000.0:.3f} | {error / 1_000_000.0:.3f} | {speed / 210_500:.1f} |"
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
) -> None:
    lines = coverage_rows(accuracy_data, speed_data)
    full = [cell.strip() for cell in lines[2].split("|")[1:-1]]
    reduced = [cell.strip() for cell in lines[-1].split("|")[1:-1]]
    reference = docs_root / "benchmarks" / "reference" / "english-coverage.md"
    reference.write_text(
        replace_coverage_table(reference.read_text(encoding="utf-8"), lines),
        encoding="utf-8",
    )
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
    readme.write_text(readme_text, encoding="utf-8")

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
    index.write_text(
        index_text[:key_start] + key_section + index_text[key_end:], encoding="utf-8"
    )


def main() -> None:
    arguments = parse_arguments()
    corpora = read_corpora(arguments.corpus)
    accuracy_data = read_jmh(arguments.accuracy)
    speed_data = read_jmh(arguments.speed)
    coverage_accuracy_data = read_jmh(arguments.coverage_accuracy)
    coverage_speed_data = read_jmh(arguments.coverage_speed)
    measured_keys = set(accuracy_data.primary) | set(speed_data.primary)
    if any("PolishPolimorf" in key.benchmark for key in measured_keys):
        raise ValueError(
            "A published report contains the excluded PolishPolimorf benchmark."
        )
    update_language_pages(arguments.docs_root, corpora, accuracy_data, speed_data)
    update_corpora_reference(arguments.docs_root, corpora)
    update_coverage(
        arguments.docs_root,
        arguments.readme,
        coverage_accuracy_data,
        coverage_speed_data,
    )


if __name__ == "__main__":
    main()
