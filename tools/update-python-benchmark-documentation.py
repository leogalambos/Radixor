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


def publish(path: Path, text: str, mode: str) -> None:
    """Writes one generated file or rejects a stale checked-in publication."""
    current = path.read_text(encoding="utf-8") if path.is_file() else None
    if current == text:
        return
    if mode == "verify":
        raise SystemExit(f"Generated Python benchmark documentation is stale: {path}")
    path.write_text(text, encoding="utf-8")


def publish_landing_summary(
        values: dict[tuple[str, str], float], summaries: dict[str, dict[str, object]],
        *, date: str, release_version: str, docs_root: Path, mode: str) -> None:
    """Updates every Python measurement embedded in the landing-page template."""
    py = summaries["radixor"]
    c = summaries["radixor-c"]
    landing = docs_root / "overrides/landing.html"
    text = landing.read_text(encoding="utf-8")
    text = replace_once(
        text,
        r'(<div class="rx2-highlight-stat blue">.*?<strong>)[^<]+(</strong><span>PyO3 geometric-)',
        rf"\g<1>{py['geomean']:.2f}×\g<2>",
    )
    text = replace_once(
        text,
        r'(<div class="rx2-highlight-stat blue">.*?<strong>)[^<]+(</strong><span>Python-C geometric-)',
        rf"\g<1>{c['geomean']:.2f}×\g<2>",
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
        chart_lines.append(
            f'<span class="rx2-bar" style="--h:{100.0 * value / axis_maximum:.1f}%" '
            f'role="img" aria-label="{name}: {value:.2f} million words per second" '
            f'title="{name}: {value:.2f}M words/s"><img '
            f'src="{{{{ base_url }}}}/assets/images/flags/{language}.svg" alt=""></span>'
        )
    chart = ('<div class="rx2-chart" aria-label="Python PyO3 throughput by language at batch size 100">'
             + "\n".join(chart_lines) + "</div>")
    text = replace_once(
        text,
        r'<div class="rx2-chart" aria-label="Python PyO3 throughput by language at batch size 100">.*?</div>',
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
        f'{py["comparisons"]} direct PyStemmer 3.1.0 comparisons. Both Radixor '
        f'{release_version} runtimes record lower median processing time in all {py["wins"]}: '
        f'PyO3 records a {py["geomean"]:.2f}× geometric-mean speedup and Python-C records '
        f'{c["geomean"]:.2f}×. PyStemmer has no direct {missing_text} comparator. The chart shows '
        f'PyO3 throughput; Python-C spans {c_minimum:.2f}M–{c_maximum:.2f}M words/s. '
        '<a href="{{ base_url }}/python/performance/">View the complete Python benchmarks →</a></p>'
    )
    text = replace_once(text, r'<p class="rx2-note"><b>\*</b>.*?</p>', note)
    finnish_pyo3 = values["fi", "PyStemmer"] / values["fi", "radixor"]
    finnish_c = values["fi", "PyStemmer"] / values["fi", "radixor-c"]
    text = replace_once(
        text,
        r'(<b>PyO3 speed</b><small>\(vs\. PyStemmer\)</small></span><strong>)[^<]+'
        r'(<small>faster</small></strong><span>1\.00× <small>\(baseline\)</small></span>'
        r'<b class="green">)[^<]+',
        rf"\g<1>{finnish_pyo3:.2f}× \g<2>{finnish_pyo3:.2f}× ",
    )
    text = replace_once(
        text,
        r'(The displayed speed comparison is Python \(PyO3\) versus PyStemmer 3\.1\.0 at batch '
        r'size N=100; Python-C is )[^ ]+( faster than the same baseline\.)',
        rf"\g<1>{finnish_c:.2f}×\g<2>",
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
        f"- each Radixor {release_version} Python runtime records lower median processing time than\n"
        f"  PyStemmer 3.1.0 in all **{py['wins']} / {py['comparisons']}** direct language comparisons;\n"
        f"- Python (PyO3) has a **{py['geomean']:.2f}×** geometric-mean speedup and a largest measured\n"
        f"  direct advantage of **{py['maximum'][1]:.2f}×** ({LANGUAGES[py['maximum'][0]]});\n"
        f"- Python-C has a **{c['geomean']:.2f}×** geometric-mean speedup and a largest measured direct\n"
        f"  advantage of **{c['maximum'][1]:.2f}×** ({LANGUAGES[c['maximum'][0]]});\n"
        f"- Python (PyO3) spans **{py['minimum_throughput'][1]:.2f}–{py['maximum_throughput'][1]:.2f} million words/s**, "
        f"while Python-C spans\n  **{c['minimum_throughput'][1]:.2f}–{c['maximum_throughput'][1]:.2f} million words/s**, "
        "across all 20 languages at batch size `N=100`."
    )
    text = replace_once(
        text,
        r'At `N=100`, the current Python batch benchmark.*?across all 20 languages at batch size `N=100`\.',
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
    parser.add_argument("--date", required=True)
    parser.add_argument("--release-version", required=True)
    parser.add_argument("--base-commit")
    parser.add_argument("--docs-root", type=Path, default=ROOT / "docs")
    parser.add_argument("--mode", choices=("update", "verify"), default="update")
    args = parser.parse_args()
    base_commit = args.base_commit or subprocess.check_output(
        ["git", "rev-parse", "--short=7", "HEAD"], cwd=ROOT, text=True
    ).strip()

    report = json.loads(args.json.read_text(encoding="utf-8"))
    run_environments = [run["environment"] for run in report.get("runs", [])]
    if not run_environments or any(environment != run_environments[0] for environment in run_environments[1:]):
        raise ValueError("Python benchmark environments are missing or differ between isolated engine runs.")
    environment = run_environments[0]
    run_parameters = report["runs"][0]["parameters"]

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
    text = re.sub(r"version \d+\.\d+\.\d+", f"version {args.release_version}", text)
    text = re.sub(r"Radixor \d+\.\d+\.\d+ Python runtimes",
                  f"Radixor {args.release_version} Python runtimes", text)
    text = re.sub(r"Radixor \d+\.\d+\.\d+, locally built",
                  f"Radixor {args.release_version}, locally built", text)
    text = re.sub(r"represents the \d+\.\d+\.\d+ Python runtimes",
                  f"represents the {args.release_version} Python runtimes", text)
    text = re.sub(r"measured source version is \d+\.\d+\.\d+",
                  f"measured source version is {args.release_version}", text)
    text = re.sub(r"20\d{2}-\d{2}-\d{2}", args.date, text)
    text = re.sub(
        r"\| (?:Source state|Source identity) \|.*?\|",
        f"| Source identity | Radixor {args.release_version} release source based on Git commit `{base_commit}`; the Java benchmark provenance retains the exact measured source patch and untracked-source checksums |",
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
    publish(p, text, args.mode)
    publish_landing_summary(
        values,
        summaries,
        date=args.date,
        release_version=args.release_version,
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
