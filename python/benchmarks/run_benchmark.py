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

"""Runtime stemming benchmark for the radixor Python extension vs alternatives.

Measures ONLY runtime stemming throughput — model construction / dictionary
compilation happens once in setup and is excluded from all timings.

Batch sizes are swept (default 10/20/50/100) and an unconstrained descriptive
line is fitted for each engine: per_call_time(N) = intercept + slope * N. The
fit summarizes scaling across the measured sizes; timing noise can make its
intercept negative, so it must not be read as a physical overhead measurement.

Data is the same as the Java JMH benchmarks: the changed-token corpus derived
from the bundled UniMorph gold-standard dictionaries (see corpus.py).

Examples
--------
    python run_benchmark.py --language en
    python run_benchmark.py --all-languages --engines radixor
    python run_benchmark.py --language en de ru --repeats 15 --csv results.csv
    python run_benchmark.py --language en --sizes 10 20 50 100 200 --json out.json
"""

from __future__ import annotations

import argparse
import csv as csvmod
import gc
import json
import platform
import statistics
import sys
import time
from pathlib import Path
from typing import Optional

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))  # allow running as a plain script

import corpus as corpus_mod  # noqa: E402
import engines as engines_mod  # noqa: E402


def _chunks(seq: list[str], n: int) -> list[list[str]]:
    return [seq[i : i + n] for i in range(0, len(seq), n)]


def _time_sequence_ns(batch_fn, batches: list[list[str]]) -> int:
    """Time one full pass over all batches (nanoseconds)."""
    start = time.perf_counter_ns()
    for b in batches:
        batch_fn(b)
    return time.perf_counter_ns() - start


def _linfit(xs: list[float], ys: list[float]) -> tuple[float, float]:
    """Ordinary least squares: returns (intercept, slope)."""
    n = len(xs)
    mean_x = sum(xs) / n
    mean_y = sum(ys) / n
    sxx = sum((x - mean_x) ** 2 for x in xs)
    sxy = sum((x - mean_x) * (y - mean_y) for x, y in zip(xs, ys))
    slope = sxy / sxx if sxx else 0.0
    intercept = mean_y - slope * mean_x
    return intercept, slope


_DIST_NAMES = {
    "radixor": "radixor",
    "PyStemmer": "PyStemmer",
    "snowballstemmer-pure": "snowballstemmer",
    "nltk-porter": "nltk",
}


def _engine_version(name: str) -> Optional[str]:
    import importlib.metadata as md

    dist = _DIST_NAMES.get(name)
    if not dist:
        return None
    try:
        return md.version(dist)
    except Exception:
        return "editable" if name == "radixor" else None


def _processor_name() -> str:
    """Return a useful CPU model name without adding a platform dependency."""
    name = platform.processor().strip()
    if name:
        return name

    cpuinfo = Path("/proc/cpuinfo")
    if cpuinfo.is_file():
        for line in cpuinfo.read_text(encoding="utf-8", errors="replace").splitlines():
            key, separator, value = line.partition(":")
            if separator and key.strip() in {"model name", "Hardware"}:
                name = value.strip()
                if name:
                    return name

    return "unknown"


def run(args) -> dict:
    from radixor import _LANGUAGE_ALIASES

    engine_filter = set(args.engines) if args.engines else None
    engines = engines_mod.available_engines(engine_filter)
    if not engines:
        print(
            "No stemmer engines available. Install PyStemmer / snowballstemmer / nltk.",
            file=sys.stderr,
        )
        sys.exit(2)

    results: list[dict] = []
    strict_engine_names = (
        (engine_filter or {"radixor"}) if args.all_languages else set()
    )
    failures: list[str] = []
    available_engine_names = {engine.name for engine in engines}
    for missing_engine in sorted(strict_engine_names - available_engine_names):
        failures.append(f"engine unavailable: {missing_engine}")

    for code in args.language:
        model_id = _LANGUAGE_ALIASES.get(code, code)
        if args.model_path:
            dict_path = Path(args.model_path)
        else:
            # Corpus construction deliberately uses the canonical repository
            # source. Runtime distributions contain only compiled model data.
            dict_path = (
                HERE.parents[1]
                / "models"
                / model_id
                / "src"
                / "modelInput"
                / "stemmer.gz"
            )
        if not dict_path.is_file():
            print(
                f"[{code}] canonical benchmark dictionary not found: {dict_path}",
                file=sys.stderr,
            )
            if args.all_languages:
                failures.append(f"{code}: dictionary not found: {dict_path}")
            continue

        full = corpus_mod.build_timing_corpus(dict_path)
        budget = min(args.words, len(full)) if args.words > 0 else len(full)
        pool = full[:budget]
        print(
            f"\n=== language={code}  model={model_id}  "
            f"corpus={len(pool)} changed tokens ==="
        )

        for engine in engines:
            if not engine.supports(code):
                if engine.name in strict_engine_names:
                    failures.append(f"{code}: engine does not support {engine.name}")
                continue
            try:
                batch_fn = engine.make(code)
            except Exception as exc:  # pragma: no cover - engine setup failure
                print(f"  [{engine.name}] setup failed: {exc}", file=sys.stderr)
                if engine.name in strict_engine_names:
                    failures.append(f"{code}: {engine.name} setup failed: {exc}")
                continue

            prov = engine.provenance(code)
            results.append(
                {
                    "language": code,
                    "model": model_id,
                    "engine": engine.name,
                    "kind": engine.kind,
                    "batch_size": "PROVENANCE",
                    **prov,
                }
            )
            print(
                f"  {engine.name:<16} backing={prov.get('backing_module')} "
                f"compiled={prov.get('compiled_extension')} "
                f"algo={prov.get('algorithm')}"
            )

            # sanity: output length must equal input length
            probe = batch_fn(pool[: min(8, len(pool))])
            if len(probe) != min(8, len(pool)):
                print(
                    f"  [{engine.name}] unexpected output shape; skipping",
                    file=sys.stderr,
                )
                if engine.name in strict_engine_names:
                    failures.append(
                        f"{code}: {engine.name} returned an unexpected output shape"
                    )
                continue

            per_call_best: list[float] = []
            for size in args.sizes:
                batches = _chunks(pool, size)
                n_calls = len(batches)
                n_words = len(pool)

                # warmup
                for _ in range(args.warmup):
                    _time_sequence_ns(batch_fn, batches)

                gc_was_enabled = gc.isenabled()
                gc.disable()
                try:
                    totals = [
                        _time_sequence_ns(batch_fn, batches)
                        for _ in range(args.repeats)
                    ]
                finally:
                    if gc_was_enabled:
                        gc.enable()

                med_total = statistics.median(totals)
                min_total = min(totals)
                # Per-word/per-call reported from the best (min) pass — the
                # microbenchmark convention that suppresses OS/GC scheduling
                # noise. The later OLS fit is descriptive and unconstrained.
                per_word_ns = min_total / n_words
                per_call_ns = min_total / n_calls
                per_call_best.append(per_call_ns)
                throughput = n_words / (min_total / 1e9)

                row = {
                    "language": code,
                    "model": model_id,
                    "engine": engine.name,
                    "kind": engine.kind,
                    "batch_size": size,
                    "calls": n_calls,
                    "words": n_words,
                    "repeats": args.repeats,
                    "median_total_ms": med_total / 1e6,
                    "min_total_ms": min_total / 1e6,
                    "per_word_ns": per_word_ns,
                    "per_call_us": per_call_ns / 1e3,
                    "throughput_words_per_s": throughput,
                }
                results.append(row)
                print(
                    f"  {engine.name:<16} [{engine.kind:<12}] "
                    f"N={size:<4} {per_word_ns:8.1f} ns/word  "
                    f"{per_call_ns / 1e3:8.2f} us/call  "
                    f"{throughput / 1e6:6.2f} M words/s"
                )

            # Unconstrained descriptive OLS fit across batch sizes. Keep the
            # historical JSON key for report compatibility.
            if len(args.sizes) >= 2:
                intercept_ns, slope_ns = _linfit(
                    [float(s) for s in args.sizes], per_call_best
                )
                results.append(
                    {
                        "language": code,
                        "model": model_id,
                        "engine": engine.name,
                        "kind": engine.kind,
                        "batch_size": "FIT",
                        "regie_ns_per_call": intercept_ns,
                        "real_ns_per_word": slope_ns,
                    }
                )
                print(
                    f"  {engine.name:<16} -> estimated intercept/call = "
                    f"{intercept_ns / 1e3:7.2f} us   "
                    f"estimated slope = {slope_ns:7.1f} ns/word"
                )

    if args.all_languages:
        expected_measurements = {
            (code, engine_name, size)
            for code in args.language
            for engine_name in strict_engine_names
            for size in args.sizes
        }
        actual_measurements = {
            (row["language"], row["engine"], row["batch_size"])
            for row in results
            if isinstance(row.get("batch_size"), int)
        }
        missing_measurements = sorted(expected_measurements - actual_measurements)
        if missing_measurements:
            failures.append(f"missing measurement rows: {missing_measurements}")
        if failures:
            raise RuntimeError(
                "Incomplete all-language benchmark: " + "; ".join(failures)
            )

    return {
        "environment": {
            "platform": platform.platform(),
            "processor": _processor_name(),
            "python": sys.version.split()[0],
            "python_impl": platform.python_implementation(),
            "engine_versions": {e.name: _engine_version(e.name) for e in engines},
        },
        "parameters": {
            "languages": args.language,
            "sizes": args.sizes,
            "words_budget": args.words,
            "repeats": args.repeats,
            "warmup": args.warmup,
        },
        "results": results,
    }


def main() -> None:
    p = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    languages = p.add_mutually_exclusive_group()
    languages.add_argument(
        "--language",
        "-l",
        nargs="+",
        default=None,
        help="ISO-639-1 language code(s) or model id(s). Default: en",
    )
    languages.add_argument(
        "--all-languages",
        action="store_true",
        help="Benchmark every language alias bundled by radixor",
    )
    p.add_argument(
        "--sizes",
        "-s",
        type=int,
        nargs="+",
        default=[10, 20, 50, 100],
        help="Batch sizes to sweep. Default: 10 20 50 100",
    )
    p.add_argument(
        "--words",
        "-w",
        type=int,
        default=5000,
        help="Words processed per measurement (<=0 = whole corpus). Default: 5000",
    )
    p.add_argument(
        "--repeats",
        "-r",
        type=int,
        default=15,
        help="Timed repeats per point (best/min reported). Default: 15",
    )
    p.add_argument("--warmup", type=int, default=3, help="Warmup passes. Default: 3")
    p.add_argument(
        "--engines",
        nargs="+",
        default=None,
        help="Restrict to named engines (radixor PyStemmer snowballstemmer nltk-porter)",
    )
    p.add_argument(
        "--model-path",
        default=None,
        help="Explicit gzipped dictionary path (single-language runs)",
    )
    p.add_argument("--csv", default=None, help="Write per-point rows to this CSV file")
    p.add_argument(
        "--json", default=None, help="Write full results (incl. environment) to JSON"
    )
    args = p.parse_args()

    if args.all_languages:
        from radixor import _LANGUAGE_ALIASES

        args.language = sorted(_LANGUAGE_ALIASES)
    elif args.language is None:
        args.language = ["en"]

    report = run(args)

    if args.json:
        Path(args.json).write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(f"\nwrote {args.json}")
    if args.csv:
        rows = [
            r
            for r in report["results"]
            if r.get("batch_size") not in ("FIT", "PROVENANCE")
        ]
        if rows:
            with open(args.csv, "w", newline="", encoding="utf-8") as fh:
                w = csvmod.DictWriter(fh, fieldnames=list(rows[0].keys()))
                w.writeheader()
                w.writerows(rows)
            print(f"wrote {args.csv}")

    print("\nEnvironment:")
    for k, v in report["environment"].items():
        print(f"  {k}: {v}")


if __name__ == "__main__":
    main()
