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

"""Measure internal Radixor Python/Rust runtime stages.

This diagnostic benchmark is intended to explain cross-CPU scaling. It uses the
same changed-token corpus and calibrated timing methodology as run_benchmark.py,
but calls the native diagnostic entry points that progressively add work:

    _len_batch          PyO3 input marshalling with minimal native work
    _echo_batch         input marshalling + Python output-object construction
    _encode_batch       normalization/UTF-8 decode + UTF-16 key encoding
    _encodefind_batch      encoding + trie traversal
    _encodefindpatch_batch encoding + trie traversal + preferred-patch lookup
    _stem_lengths_batch    encoding + trie traversal + UTF-16 patch application
    _stem_utf8_lengths_batch production UTF-8 result generation, no Python objects
    stem_batch          complete public native batch path including outputs

Differences between independently timed stages are descriptive only; use them to
locate a bottleneck, not as exact additive accounting.
"""

from __future__ import annotations

import argparse
import gc
import importlib.metadata as metadata
import json
import platform
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import corpus as corpus_mod  # noqa: E402
import engines as engines_mod  # noqa: E402
import timing as timing_mod  # noqa: E402
from run_benchmark import (  # noqa: E402
    _load_language_aliases,
    _performance_environment,
    _processor_name,
)

_STAGE_NAMES: tuple[str, ...] = (
    "_len_batch",
    "_echo_batch",
    "_encode_batch",
    "_encodefind_batch",
    "_encodefindpatch_batch",
    "_stem_lengths_batch",
    "_stem_utf8_lengths_batch",
    "stem_batch",
)

_STAGE_LABELS: dict[str, str] = {
    "_len_batch": "marshal",
    "_echo_batch": "marshal+output",
    "_encode_batch": "encode",
    "_encodefind_batch": "encode+find",
    "_encodefindpatch_batch": "encode+find+patch",
    "_stem_lengths_batch": "native-stem",
    "_stem_utf8_lengths_batch": "stem-utf8",
    "stem_batch": "full-output",
}


def _chunks(seq: list[str], size: int) -> list[list[str]]:
    """Split a corpus into stable batches."""
    return [seq[index : index + size] for index in range(0, len(seq), size)]


def _dictionary_path(model_id: str, explicit_path: str | None) -> Path:
    """Resolve the canonical benchmark dictionary for one model."""
    if explicit_path:
        return Path(explicit_path)
    return (
        HERE.parents[1]
        / "models"
        / model_id
        / "src"
        / "modelInput"
        / "stemmer.gz"
    )


def run(args: argparse.Namespace) -> dict:
    """Execute diagnostics and return a machine-readable report."""
    from radixor import Stemmer

    aliases = _load_language_aliases()
    results: list[dict] = []
    provenance: list[dict] = []

    for code in args.language:
        model_id = aliases.get(code, code)
        dict_path = _dictionary_path(model_id, args.model_path)
        if not dict_path.is_file():
            raise FileNotFoundError(
                f"Canonical benchmark dictionary not found for {code}: {dict_path}"
            )

        full = corpus_mod.build_timing_corpus(dict_path)
        budget = min(args.words, len(full)) if args.words > 0 else len(full)
        pool = full[:budget]

        stemmer = Stemmer(code, lowercase=False, cache_size=0)
        core = stemmer._core
        info = engines_mod._module_info(core)
        required_phase5 = (
            "_optimization_tag",
            "_value_layout_stats",
            "_patch_stats_batch",
            "_backward_compound_patterns_batch",
            "_stem_utf8_lengths_batch",
        )
        missing_phase5 = [name for name in required_phase5 if not hasattr(core, name)]
        if missing_phase5:
            raise RuntimeError(
                "Installed Radixor native extension is not the Phase 5 build; "
                f"missing diagnostic entry points: {missing_phase5}"
            )

        optimization_tag = core._optimization_tag()
        value_layout = core._value_layout_stats()
        patch_stats = dict(core._patch_stats_batch(pool))
        backward_compound_patterns = list(core._backward_compound_patterns_batch(pool))
        provenance.append(
            {
                "language": code,
                "model": model_id,
                "radixor_version": metadata.version("radixor"),
                "optimization_tag": optimization_tag,
                "value_layout": {
                    "nodes": value_layout[0],
                    "value_references": value_layout[1],
                    "distinct_patches": value_layout[2],
                },
                "patch_stats": patch_stats,
                "backward_compound_patterns": [
                    {"pattern": pattern, "count": count}
                    for pattern, count in backward_compound_patterns
                ],
                **info,
            }
        )

        missing = [name for name in _STAGE_NAMES if not hasattr(core, name)]
        if missing:
            raise RuntimeError(
                "Installed Radixor native extension lacks required diagnostic "
                f"entry points: {missing}"
            )

        print(
            f"\n=== Radixor diagnostics language={code} model={model_id} "
            f"corpus={len(pool)} ==="
        )
        print(
            f"  native={info.get('backing_file')} sha256={info.get('backing_sha256')}"
        )
        print(f"  optimization={optimization_tag}")
        print(
            "  value layout: "
            f"nodes={value_layout[0]} "
            f"refs={value_layout[1]} "
            f"distinct_patches={value_layout[2]}"
        )
        print(
            "  patch stats: "
            + ", ".join(f"{name}={count}" for name, count in patch_stats.items())
        )
        if backward_compound_patterns:
            print("  backward compound patterns (top 12):")
            for pattern, count in backward_compound_patterns[:12]:
                print(f"    {count:5d}  {pattern}")

        for size in args.sizes:
            batches = _chunks(pool, size)
            n_words = len(pool)
            n_calls = len(batches)
            stage_per_word: dict[str, float] = {}

            print(f"\n  batch size N={size}")
            for stage_name in _STAGE_NAMES:
                fn = getattr(core, stage_name)
                gc_was_enabled = gc.isenabled()
                gc.disable()
                try:
                    timing = timing_mod.measure(
                        fn,
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
                per_call_ns = timing.median_ns / measured_calls
                stage_per_word[stage_name] = per_word_ns
                results.append(
                    {
                        "language": code,
                        "model": model_id,
                        "stage": stage_name,
                        "stage_label": _STAGE_LABELS[stage_name],
                        "batch_size": size,
                        "words": n_words,
                        "calls": n_calls,
                        "sample_passes": timing.passes_per_sample,
                        "sample_words": measured_words,
                        "sample_calls": measured_calls,
                        "repeats": args.repeats,
                        "median_total_ms": timing.median_ns / 1e6,
                        "min_total_ms": timing.minimum_ns / 1e6,
                        "max_total_ms": timing.maximum_ns / 1e6,
                        "relative_mad_pct": timing.relative_mad_percent,
                        "per_word_ns": per_word_ns,
                        "per_call_us": per_call_ns / 1e3,
                    }
                )
                print(
                    f"    {_STAGE_LABELS[stage_name]:<15} "
                    f"{per_word_ns:9.2f} ns/word  "
                    f"{per_call_ns / 1e3:9.3f} us/call  "
                    f"passes={timing.passes_per_sample:<6} "
                    f"MAD={timing.relative_mad_percent:5.2f}%"
                )

            encode = stage_per_word["_encode_batch"]
            find = stage_per_word["_encodefind_batch"]
            find_patch = stage_per_word["_encodefindpatch_batch"]
            native_stem = stage_per_word["_stem_lengths_batch"]
            stem_utf8 = stage_per_word["_stem_utf8_lengths_batch"]
            full = stage_per_word["stem_batch"]
            print("    approximate incremental medians (descriptive):")
            print(f"      trie traversal       {find - encode:9.2f} ns/word")
            print(f"      preferred patch      {find_patch - find:9.2f} ns/word")
            print(f"      UTF-16 patch apply   {native_stem - find_patch:9.2f} ns/word")
            print(f"      UTF-8 render path    {stem_utf8 - find_patch:9.2f} ns/word")
            print(f"      Python output        {full - stem_utf8:9.2f} ns/word")

    return {
        "environment": {
            "platform": platform.platform(),
            "processor": _processor_name(),
            "python": sys.version.split()[0],
            "python_impl": platform.python_implementation(),
            **_performance_environment(),
        },
        "parameters": {
            "languages": args.language,
            "sizes": args.sizes,
            "words_budget": args.words,
            "repeats": args.repeats,
            "sample_ms": args.sample_ms,
            "warmup": args.warmup,
            "warmup_ms": args.warmup_ms,
        },
        "provenance": provenance,
        "results": results,
    }


def main() -> None:
    """Parse command-line arguments and execute the diagnostic benchmark."""
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--language",
        "-l",
        nargs="+",
        default=["pl", "cs", "fi", "ru", "sv"],
        help="Languages to diagnose. Default: pl cs fi ru sv",
    )
    parser.add_argument(
        "--sizes",
        "-s",
        type=int,
        nargs="+",
        default=[10, 20, 50, 100],
        help="Batch sizes. Default: 10 20 50 100",
    )
    parser.add_argument(
        "--words",
        "-w",
        type=int,
        default=5000,
        help="Corpus words per traversal. Default: 5000",
    )
    parser.add_argument(
        "--repeats",
        "-r",
        type=int,
        default=9,
        help="Timed samples per point. Default: 9",
    )
    parser.add_argument(
        "--sample-ms",
        type=float,
        default=250.0,
        help="Approximate duration of each calibrated sample. Default: 250 ms",
    )
    parser.add_argument(
        "--warmup",
        type=int,
        default=3,
        help="Minimum warmup corpus passes. Default: 3",
    )
    parser.add_argument(
        "--warmup-ms",
        type=float,
        default=500.0,
        help="Minimum warmup duration per point. Default: 500 ms",
    )
    parser.add_argument(
        "--model-path",
        default=None,
        help="Explicit dictionary path; intended for a single-language run",
    )
    parser.add_argument("--json", default=None, help="Write full report to JSON")
    args = parser.parse_args()

    if args.sample_ms <= 0 or args.warmup_ms < 0:
        parser.error("--sample-ms must be positive and --warmup-ms non-negative")
    if args.repeats <= 0:
        parser.error("--repeats must be positive")
    if any(size <= 0 for size in args.sizes):
        parser.error("all --sizes values must be positive")
    if args.model_path and len(args.language) != 1:
        parser.error("--model-path requires exactly one language")

    report = run(args)
    if args.json:
        Path(args.json).write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(f"\nwrote {args.json}")

    print("\nEnvironment:")
    for key, value in report["environment"].items():
        print(f"  {key}: {value}")


if __name__ == "__main__":
    main()
