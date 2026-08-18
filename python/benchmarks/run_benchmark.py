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

The default batch size is fixed to **100**. Each measurement point is
automatically calibrated so one timed sample lasts approximately 250 ms by
default. The median of repeated samples is the primary throughput estimator;
the minimum is retained only as a diagnostic statistic.

Data is the same as the Java JMH benchmarks: the changed-token corpus derived
from the bundled UniMorph gold-standard dictionaries (see corpus.py).

Examples
--------
    python run_benchmark.py --language en
    python run_benchmark.py --all-languages --engines radixor
    python run_benchmark.py --language en de ru --repeats 3 --csv results.csv
    python run_benchmark.py --language en --sizes 100 --json out.json
"""
from __future__ import annotations

import argparse
import csv as csvmod
import gc
import json
import os
import platform
import sys
from pathlib import Path
from typing import Optional

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))  # allow running as a plain script

import corpus as corpus_mod  # noqa: E402
import engines as engines_mod  # noqa: E402
import timing as timing_mod  # noqa: E402

_FALLBACK_LANGUAGE_MODEL_IDS: tuple[str, ...] = (
    "cs-cz-default",
    "da-dk-default",
    "de-de-default",
    "us-uk-default",
    "es-es-default",
    "fa-ir-default",
    "fi-fi-default",
    "fr-fr-default",
    "hu-hu-default",
    "it-it-default",
    "nb-no-default",
    "nn-no-default",
    "nl-nl-default",
    "pl-pl-unimorph",
    "pt-pt-default",
    "ru-ru-default",
    "sv-se-default",
    "yi-default",
)

_FALLBACK_LANGUAGE_ALIASES: dict[str, str] = {
    **{model_id: model_id for model_id in _FALLBACK_LANGUAGE_MODEL_IDS},
    "cs": "cs-cz-default",
    "dan": "da-dk-default",
    "da": "da-dk-default",
    "dutch": "nl-nl-default",
    "nl": "nl-nl-default",
    "nld": "nl-nl-default",
    "eng": "us-uk-default",
    "en": "us-uk-default",
    "english": "us-uk-default",
    "fi": "fi-fi-default",
    "finnish": "fi-fi-default",
    "fr": "fr-fr-default",
    "french": "fr-fr-default",
    "de": "de-de-default",
    "german": "de-de-default",
    "hu": "hu-hu-default",
    "hungarian": "hu-hu-default",
    "it": "it-it-default",
    "italian": "it-it-default",
    "nb": "nb-no-default",
    "nn": "nn-no-default",
    "nor": "nb-no-default",
    "no": "nb-no-default",
    "norwegian": "nb-no-default",
    "fa": "fa-ir-default",
    "persian": "fa-ir-default",
    "pl": "pl-pl-unimorph",
    "polish": "pl-pl-unimorph",
    "pt": "pt-pt-default",
    "portuguese": "pt-pt-default",
    "ru": "ru-ru-default",
    "russian": "ru-ru-default",
    "es": "es-es-default",
    "spanish": "es-es-default",
    "sv": "sv-se-default",
    "swedish": "sv-se-default",
    "yi": "yi-default",
    "yiddish": "yi-default",
    "ces": "cs-cz-default",
    "cze": "cs-cz-default",
    "fra": "fr-fr-default",
    "fre": "fr-fr-default",
    "ger": "de-de-default",
    "deu": "de-de-default",
    "hun": "hu-hu-default",
    "ita": "it-it-default",
    "fin": "fi-fi-default",
    "swe": "sv-se-default",
    "pol": "pl-pl-unimorph",
    "por": "pt-pt-default",
    "rus": "ru-ru-default",
    "spa": "es-es-default",
    "esl": "es-es-default",
    "yid": "yi-default",
}


def _load_language_aliases() -> dict[str, str]:
    """Return language aliases to model IDs with optional Radixor fallback.

    PyStemmer-only environments cannot import ``radixor`` yet still need stable
    language-to-model mapping for benchmark corpus selection. The fallback table
    stays focused on languages supported by the benchmark-compatible engines.
    """
    try:
        from radixor import _LANGUAGE_ALIASES

        return dict(_LANGUAGE_ALIASES)
    except Exception:
        return _FALLBACK_LANGUAGE_ALIASES.copy()


def _index_language_aliases(
    aliases: dict[str, str]
) -> dict[str, list[str]]:
    by_model: dict[str, list[str]] = {}
    for alias, model_id in aliases.items():
        by_model.setdefault(model_id, []).append(alias)
    return {model_id: sorted(set(values)) for model_id, values in by_model.items()}


def _normalize_language_requests(
    requested: list[str], aliases: dict[str, str]
) -> list[tuple[str, str]]:
    seen_models: set[str] = set()
    normalized: list[tuple[str, str]] = []
    for requested_code in requested:
        model_id = aliases.get(requested_code, requested_code)
        if model_id in seen_models:
            continue
        seen_models.add(model_id)
        normalized.append((requested_code, model_id))
    return normalized


def _primary_code_for_model(model_id: str, aliases: list[str]) -> str:
    if not aliases:
        return model_id
    for alias in aliases:
        if len(alias) == 2:
            return alias
    return aliases[0]


def _all_language_requests(
    aliases: dict[str, str]
) -> list[tuple[str, str]]:
    by_model = _index_language_aliases(aliases)
    requests: list[tuple[str, str]] = []
    for model_id in sorted(by_model):
        request_code = _primary_code_for_model(model_id, by_model[model_id])
        requests.append((request_code, model_id))
    return requests


def _resolve_supported_language_code(
    engine,
    requested_code: str,
    model_id: str,
    aliases_by_model: dict[str, list[str]],
) -> str | None:
    if engine.supports(requested_code):
        return requested_code

    for candidate in aliases_by_model.get(model_id, ()):
        if candidate == requested_code:
            continue
        if engine.supports(candidate):
            return candidate

    if engine.supports(model_id):
        return model_id

    return None

def _chunks(seq: list[str], n: int) -> list[list[str]]:
    return [seq[i : i + n] for i in range(0, len(seq), n)]


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
    if name == "radixor":
        try:
            import radixor

            getter = getattr(radixor, "version", None)
            if callable(getter):
                return str(getter())
            value = getattr(radixor, "__version__", None)
            return str(value) if value is not None else None
        except Exception:
            return None

    elif name == "PyStemmer":
        try:
            import importlib.metadata as md

            return md.version("PyStemmer")
        except Exception:
            return None

    else:
        import importlib.metadata as md
        dist = _DIST_NAMES.get(name)
        if not dist:
            return None
        try:
            return md.version(dist)
        except Exception:
            return None


def _assert_expected_provenance(
    engine_name: str, prov: dict, language_code: str, model_id: str
) -> None:
    def _fail(msg: str) -> None:
        raise RuntimeError(f"[{language_code}/{model_id}:{engine_name}] {msg}")

    dist = prov.get("distribution")
    dist_verified = bool(prov.get("distribution_verified"))
    backing_module = prov.get("backing_module")
    backing_file = str(prov.get("backing_file", "")).lower()

    if engine_name == "PyStemmer":
        if dist != "PyStemmer" or not dist_verified:
            _fail(
                f"expected PyStemmer distribution-backed backend, got distribution={dist!r} "
                f"verified={dist_verified} (backing_module={backing_module}, file={prov.get('backing_file')})"
            )
        if not prov.get("independent_of_snowballstemmer", False):
            _fail(
                f"PyStemmer is unexpectedly sourced from snowballstemmer or a shimmed module "
                f"(backing_file={prov.get('backing_file')})"
            )
        if not prov.get("compiled_extension", False):
            _fail(
                "PyStemmer is expected to be a native C extension for benchmark fairness"
            )
        return

    if engine_name == "snowballstemmer-pure":
        if dist != "snowballstemmer" or not dist_verified:
            _fail(
                f"expected pure snowballstemmer module, got distribution={dist!r} "
                f"verified={dist_verified} (backing_module={backing_module}, file={prov.get('backing_file')})"
            )
        if not backing_module or not str(backing_module).startswith("snowballstemmer."):
            _fail(
                f"expected snowballstemmer language module, got backing_module={backing_module}"
            )
        if prov.get("compiled_extension", False):
            _fail(
                f"snowballstemmer-pure must not use compiled extensions (backing_file={prov.get('backing_file')})"
            )


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




def _read_sysfs_value(path: str) -> Optional[str]:
    """Return a stripped sysfs value when available on the current platform."""
    file_path = Path(path)
    try:
        return file_path.read_text(encoding="utf-8").strip()
    except OSError:
        return None


def _cpu_affinity() -> Optional[list[int]]:
    """Return the process CPU-affinity set where the operating system exposes it."""
    try:
        return sorted(os.sched_getaffinity(0))
    except (AttributeError, OSError):
        return None


def _performance_environment() -> dict:
    """Return CPU power-policy metadata relevant to reproducible benchmarking."""
    return {
        "cpu_affinity": _cpu_affinity(),
        "scaling_driver": _read_sysfs_value(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_driver"
        ),
        "scaling_governor": _read_sysfs_value(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
        ),
        "energy_performance_preference": _read_sysfs_value(
            "/sys/devices/system/cpu/cpu0/cpufreq/energy_performance_preference"
        ),
        "amd_pstate_status": _read_sysfs_value(
            "/sys/devices/system/cpu/amd_pstate/status"
        ),
    }


def run(args) -> dict:
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

    language_aliases = _load_language_aliases()
    aliases_by_model = _index_language_aliases(language_aliases)
    language_requests = (
        _all_language_requests(language_aliases)
        if args.all_languages
        else _normalize_language_requests(args.language or ["en"], language_aliases)
    )

    for code, model_id in language_requests:
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
            engine_code = _resolve_supported_language_code(
                engine, code, model_id, aliases_by_model
            )
            if engine_code is None:
                continue
            try:
                batch_fn = engine.make(engine_code)
            except Exception as exc:  # pragma: no cover - engine setup failure
                print(f"  [{engine.name}] setup failed: {exc}", file=sys.stderr)
                if engine.name in strict_engine_names:
                    failures.append(f"{code}: {engine.name} setup failed: {exc}")
                continue

            engine_version = _engine_version(engine.name)
            prov = engine.provenance(engine_code)
            _assert_expected_provenance(engine.name, prov, code, model_id)
            results.append(
                {
                    "language": code,
                    "model": model_id,
                    "engine": engine.name,
                    "kind": engine.kind,
                    "batch_size": "PROVENANCE",
                    "engine_version": engine_version,
                    **prov,
                }
            )
            print(
                f"  {engine.name:<16} backing={prov.get('backing_module')} "
                f"compiled={prov.get('compiled_extension')} "
                f"algo={prov.get('algorithm')} version={engine_version or 'unknown'}"
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

            per_call_medians: list[float] = []
            for size in args.sizes:
                batches = _chunks(pool, size)
                n_calls = len(batches)
                n_words = len(pool)

                gc_was_enabled = gc.isenabled()
                gc.disable()
                try:
                    timing = timing_mod.measure(
                        batch_fn,
                        batches,
                        repeats=args.repeats,
                        target_sample_ns=int(args.sample_ms * 1_000_000),
                        warmup_passes=args.warmup,
                        warmup_duration_ns=int(args.warmup_ms * 1_000_000),
                    )
                finally:
                    if gc_was_enabled:
                        gc.enable()

                measured_words = n_words * timing.passes_per_sample
                measured_calls = n_calls * timing.passes_per_sample
                per_word_ns = timing.median_ns / measured_words
                min_per_word_ns = timing.minimum_ns / measured_words
                per_call_ns = timing.median_ns / measured_calls
                min_per_call_ns = timing.minimum_ns / measured_calls
                per_call_medians.append(per_call_ns)
                throughput = measured_words / (timing.median_ns / 1e9)
                min_throughput = measured_words / (timing.minimum_ns / 1e9)

                row = {
                    "language": code,
                    "model": model_id,
                    "engine": engine.name,
                    "kind": engine.kind,
                    "batch_size": size,
                    "engine_version": engine_version,
                    "calls": n_calls,
                    "words": n_words,
                    "sample_passes": timing.passes_per_sample,
                    "sample_calls": measured_calls,
                    "sample_words": measured_words,
                    "repeats": args.repeats,
                    "median_total_ms": timing.median_ns / 1e6,
                    "min_total_ms": timing.minimum_ns / 1e6,
                    "max_total_ms": timing.maximum_ns / 1e6,
                    "relative_mad_pct": timing.relative_mad_percent,
                    "per_word_ns": per_word_ns,
                    "min_per_word_ns": min_per_word_ns,
                    "per_call_us": per_call_ns / 1e3,
                    "min_per_call_us": min_per_call_ns / 1e3,
                    "throughput_words_per_s": throughput,
                    "min_throughput_words_per_s": min_throughput,
                }
                results.append(row)
                print(
                    f"  {engine.name:<16} [{engine.kind:<12}] "
                    f"N={size:<4} {per_word_ns:8.1f} ns/word  "
                    f"{per_call_ns / 1e3:8.2f} us/call  "
                    f"{throughput / 1e6:6.2f} M words/s  "
                    f"passes={timing.passes_per_sample:<5} "
                    f"MAD={timing.relative_mad_percent:5.2f}% "
                    f"min={min_per_word_ns:7.1f} ns/word"
                )

            # Unconstrained descriptive OLS fit across batch sizes. The fit is
            # now based on the median measurement for each size. Keep the
            # historical JSON keys for report compatibility.
            if len(args.sizes) >= 2:
                intercept_ns, slope_ns = _linfit(
                    [float(s) for s in args.sizes], per_call_medians
                )
                results.append(
                    {
                        "language": code,
                        "model": model_id,
                        "engine": engine.name,
                        "kind": engine.kind,
                        "batch_size": "FIT",
                        "engine_version": engine_version,
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
        language_codes = [code for code, _ in language_requests]
        expected_measurements = {
            (code, engine_name, size)
            for code, model_id in language_requests
            for engine_name in strict_engine_names
            for size in args.sizes
            for engine in engines
            if (
                    engine.name == engine_name
                    and _resolve_supported_language_code(
                        engine, code, model_id, aliases_by_model
                    )
                    is not None
            )
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
            **_performance_environment(),
        },
        "parameters": {
            "languages": [code for code, _ in language_requests],
            "sizes": args.sizes,
            "words_budget": args.words,
            "repeats": args.repeats,
            "sample_ms": args.sample_ms,
            "warmup": args.warmup,
            "warmup_ms": args.warmup_ms,
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
        default=[100],
        help="Batch sizes to benchmark. Default: 100",
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
        default=3,
        help="Timed repeats per point (median reported). Default: 3",
    )
    p.add_argument(
        "--sample-ms",
        type=float,
        default=250.0,
        help="Approximate duration of each calibrated timed sample. Default: 250 ms",
    )
    p.add_argument(
        "--warmup",
        type=int,
        default=3,
        help="Minimum warmup corpus passes; retained for CLI compatibility. Default: 3",
    )
    p.add_argument(
        "--warmup-ms",
        type=float,
        default=500.0,
        help="Minimum warmup duration for each measurement point. Default: 500 ms",
    )
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

    if args.sample_ms <= 0:
        p.error("--sample-ms must be positive")
    if args.warmup_ms < 0:
        p.error("--warmup-ms must be non-negative")
    if args.warmup < 0:
        p.error("--warmup must be non-negative")
    if args.repeats <= 0:
        p.error("--repeats must be positive")
    if any(size <= 0 for size in args.sizes):
        p.error("all --sizes values must be positive")

    if args.language is None:
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
