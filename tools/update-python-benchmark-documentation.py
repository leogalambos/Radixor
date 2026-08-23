#!/usr/bin/env python3
"""Publish the Python batch benchmark CSV into documentation summaries."""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LANGUAGES = {
    "cs": "Czech", "da": "Danish", "de": "German", "en": "English",
    "es": "Spanish", "fa": "Persian", "fi": "Finnish", "fr": "French",
    "he": "Hebrew", "hu": "Hungarian", "it": "Italian",
    "nb": "Norwegian Bokmål", "nl": "Dutch", "nn": "Norwegian Nynorsk",
    "pl": "Polish", "pt": "Portuguese", "ru": "Russian", "sv": "Swedish",
    "uk": "Ukrainian", "yi": "Yiddish",
}
ENGINES = ("radixor", "radixor-c", "PyStemmer", "cistem", "snowballstemmer-pure", "nltk-porter")


def geometric_mean(values: list[float]) -> float:
    return math.exp(sum(math.log(value) for value in values) / len(values))


def replace_once(text: str, pattern: str, replacement: str, *, flags: int = 0) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise ValueError(f"Expected exactly one match for {pattern!r}, found {count}")
    return updated


def write_if_changed(path: Path, text: str) -> None:
    if path.read_text() != text:
        path.write_text(text)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", required=True, type=Path)
    parser.add_argument("--date", required=True)
    parser.add_argument("--release-version", required=True)
    parser.add_argument("--base-commit")
    args = parser.parse_args()
    base_commit = args.base_commit or subprocess.check_output(
        ["git", "rev-parse", "--short=7", "HEAD"], cwd=ROOT, text=True
    ).strip()

    rows = list(csv.DictReader(args.csv.open()))
    values = {(row["language"], row["engine"]): float(row["per_word_ns"]) for row in rows}
    expected = 20 + 20 + 18 + 18 + 1 + 1
    if len(rows) != expected or len(values) != expected:
        raise ValueError(f"Expected {expected} unique benchmark rows, found {len(rows)} / {len(values)}")

    shared = [language for language in LANGUAGES if (language, "PyStemmer") in values]
    summaries = {}
    for engine in ("radixor", "radixor-c"):
        ratios = {language: values[language, "PyStemmer"] / values[language, engine] for language in shared}
        throughput = {language: 1000.0 / values[language, engine] for language in LANGUAGES}
        summaries[engine] = {
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

    p = ROOT / "docs/python/performance.md"
    text = p.read_text()
    text = text.replace("version 4.1.4", f"version {args.release_version}")
    text = text.replace("both\n    Radixor 4.1.4 Python runtimes", f"both\n    Radixor {args.release_version} Python runtimes")
    text = text.replace("Radixor 4.1.4, locally built", f"Radixor {args.release_version}, locally built")
    text = text.replace("represents the 4.1.4 Python runtimes", f"represents the {args.release_version} Python runtimes")
    text = text.replace("measured source version is 4.1.4", f"measured source version is {args.release_version}")
    text = text.replace("2026-08-22", args.date)
    text = re.sub(
        r"\| (?:Source state|Source identity) \|.*?\|",
        f"| Source identity | Radixor {args.release_version} release source based on Git commit `{base_commit}`; the Java benchmark provenance retains the exact measured source patch and untracked-source checksums |",
        text,
        count=1,
    )
    text = replace_once(
        text,
        r"\| Language \| Python \(PyO3\).*?\n\| Yiddish \(`yi`\)[^\n]*",
        table_text,
        flags=re.S,
    )
    py = summaries["radixor"]
    c = summaries["radixor-c"]
    summary = (
        f"Both Radixor runtimes recorded lower median processing time in **{py['wins']} / {len(shared)}**\n"
        f"direct PyStemmer comparisons. At `N=100`,\n"
        f"Python (PyO3) achieved a **{py['geomean']:.2f}×** geometric-mean speedup, with a largest direct\n"
        f"advantage of **{py['maximum'][1]:.2f}×** for {LANGUAGES[py['maximum'][0]]} and throughput of "
        f"**{py['minimum_throughput'][1]:.2f}–{py['maximum_throughput'][1]:.2f} million\n"
        f"words/s** across its 20 languages. Python-C achieved a **{c['geomean']:.2f}×** geometric-mean\n"
        f"speedup, with a largest direct advantage of **{c['maximum'][1]:.2f}×** for {LANGUAGES[c['maximum'][0]]} and throughput\n"
        f"of **{c['minimum_throughput'][1]:.2f}–{c['maximum_throughput'][1]:.2f} million words/s**. Python-C was faster than PyO3 in {c_wins} languages\n"
        f"and PyO3 was faster in {pyo3_wins}; Python-C's geometric-mean advantage over PyO3 was\n"
        f"**{c_geomean:.2f}×**, so workload and language remain more useful selection criteria than\n"
        "a universal ranking."
    )
    text = replace_once(text, r"Both Radixor runtimes recorded.*?a universal ranking\.", summary, flags=re.S)

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
    write_if_changed(p, text)

    homepage = {
        "source": f"Python all-language batch benchmark, {args.date}",
        "environment": {
            "processor": "AMD Ryzen 5 7600 6-Core Processor",
            "platform": "Fedora Linux 44, Linux-7.1.8-200.fc44.x86_64, glibc 2.43",
            "python": "CPython 3.14.7", "rust": "1.97.1", "gcc": "16.2.1",
            "cpu_governor": "performance", "energy_performance_preference": "performance",
        },
        "versions": {"radixor": args.release_version, "radixor-c": args.release_version,
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
    write_if_changed(ROOT / "docs/assets/data/homepage-performance.json", json.dumps(homepage, indent=2, ensure_ascii=False) + "\n")

    print(f"Published {len(rows)} Python benchmark rows for {args.release_version} ({args.date}).")


if __name__ == "__main__":
    main()
