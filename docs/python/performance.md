# Performance (Python Runtimes)

This page reports **runtime stemming throughput** of Python (PyO3) against
common Python stemmers, and — crucially — documents exactly how the comparison
is made fair. The scripts are in the repository (`python/benchmarks/`); anyone
can reproduce the numbers.

`radixor-c` belongs in this same benchmark rather than in a separate table,
because both packages expose the same workload and models. The published run
measures both native runtimes from version 4.2.1: `radixor` through Rust/PyO3
and `radixor-c` through the CPython C API.

The matching patch number in this run does not couple their release streams.
Radixor runtimes share project-wide major and minor versions, while each
runtime advances its patch version independently for local fixes and
improvements.

!!! info "Published single-machine measurement"
    These results were regenerated on 2026-08-25 on `AMD Ryzen 5 8600G w/ Radeon 760M Graphics` with 12 logical CPUs available,
    `Linux-7.1.8-200.fc44.x86_64-x86_64-with-glibc2.43`, and CPython 3.14.7. Both Radixor 4.2.1 native
    runtimes were built locally in release mode. The recorded CPU governor was `performance`
    and the energy preference was `performance`. Absolute timings remain machine-specific;
    compare ratios only within this run.

## What is measured

- **Runtime stemming only.** Model construction / dictionary compilation happens
  once in setup and is excluded from every timing.
- **Same corpus construction as Java JMH.** The *changed-token* corpus is derived from
  the bundled UniMorph gold-standard dictionaries: every dictionary field paired
  with its line's root, normalized `trim().lower()`, keeping only tokens that
  differ from their root (the forms a stemmer must actually rewrite), padded to
  ≥ 5 000 tokens. Python measures the fixed first 5,000-token prefix for every
  language; Java measures the complete constructed corpus when it is larger.
- The benchmark is now run at a single fixed batch size **N = 100**.
  This avoids fitting a scaling model and reports the measured batch performance
  directly.

## Fairness: making the comparison apples-to-apples

Three asymmetries silently distort stemmer comparisons. Each is neutralized, and
where it **cannot** be neutralized the effect is described.

1. **Result caching — neutralized.** PyStemmer caches results by default
   (`maxCacheSize=10000`). Since a benchmark stems the same corpus repeatedly,
   that cache would turn measured passes into dictionary lookups rather than
   stemming. The harness explicitly disables PyStemmer's cache
   (`maxCacheSize=0`) and both Radixor runtimes' default caches
   (`cache_size=0`). The other engines have no cache.
2. **Lowercasing — neutralized.** Snowball (PyStemmer, snowballstemmer) and
   CISTEM differ in whether they case-fold. Snowball does **no** case handling;
   it assumes pre-lowercased input. The corpus is pre-lowercased for every
   engine, and both Radixor runtimes are therefore run with
   **`lowercase=False`** so they do the same work. On already-lowercased input
   they return identical results either way. **Exception — CISTEM:** it always
   performs its own lowercasing and German umlaut normalization internally and
   cannot be told to skip it, so CISTEM does *slightly more* normalization work
   than the others. This
   unavoidable extra work biases the comparison modestly **in radixor's
   favour**, not CISTEM's.
3. **Hidden delegation — neutralized.** `snowballstemmer` delegates to PyStemmer
   when PyStemmer is installed (they become the same C code). The harness
   bypasses that and uses snowballstemmer's genuine pure-Python backend, and
   records each engine's backing module + whether it is a compiled extension so
   the provenance is verifiable.

## Environment and parameters

| Item | Published value |
|---|---|
| CPU | AMD Ryzen 5 8600G w/ Radeon 760M Graphics |
| CPU topology | 12 logical CPUs in the recorded affinity; physical topology not recorded by the harness |
| OS | `Linux-7.1.8-200.fc44.x86_64-x86_64-with-glibc2.43` |
| CPU policy | `amd-pstate-epp`; `performance` governor; `performance` energy preference |
| Python | CPython 3.14.7 |
| Python (PyO3) | Radixor 4.2.1, locally built release-mode ABI3 wheel, cache disabled |
| Python-C | Radixor 4.2.1, locally built CPython 3.14 C-extension wheel, cache disabled |
| Native toolchains | Not part of the timed runtime report; wheels were built locally in release mode |
| Source identity | Radixor 4.2.1 release source based on Git commit `84e57fb`; the Java benchmark provenance retains the exact measured source patch and untracked-source checksums |
| PyStemmer | 3.1.0 (`libstemmer_c` 3.1.0), cache disabled |
| snowballstemmer | 3.1.1, forced pure-Python backend |
| NLTK | 3.10.3 |
| Workload | 5,000 changed tokens per language and measurement |
| Batch sizes | 100 |
| Timing | median of 3 calibrated ~250 ms samples after at least 3 complete-corpus warm-ups and 500 ms |

The checkout represents the 4.2.1 Python runtimes. The repository's local
benchmark build deliberately leaves wheel metadata at its `0.0.0` packaging
placeholder, which is why the raw report records `engine_version=0.0.0` even
though the measured source version is 4.2.1.

The authoritative command was:

```bash
./gradlew pythonBenchmarkAllLanguagesBatch \
  -PpythonBenchmarkEngines=radixor,radixor-c,PyStemmer,snowballstemmer-pure,cistem,nltk-porter \
  -PpythonBenchmarkWords=5000 \
  -PpythonBenchmarkRepeats=3 \
  -PpythonBenchmarkWarmup=3 \
  --rerun-tasks
```

The run completed successfully and emitted
the full per-size CSV and JSON reports under
`build/reports/python-benchmarks/`.

## Results — batch size 100, cache disabled

The table reports nanoseconds per word at `N=100` (lower is better). A dash
means that the engine has no implementation for that language. Each engine ran
in an isolated virtual environment and process during the same controlled
benchmark session, with the same corpus construction and batch partitioning.
Bold values identify the fastest measured engine for that language.

These are point estimates from the documented single-machine session, not
formal confidence intervals. Small differences—especially the close PyO3 and
Python-C rows—should be treated as workload-specific unless they reproduce in
the deployment environment. The all-language geometric means are more useful
for the broad comparison than an isolated near-tie.

| Language | Python (PyO3) | Python-C | PyStemmer (Snowball C) | CISTEM (pure Py) | snowballstemmer (pure Py) | NLTK Porter (pure Py) |
|---|---:|---:|---:|---:|---:|---:|
| Czech (`cs`) | 108.6 | **97.7** | 154.3 | — | 3185.0 | — |
| Danish (`da`) | **82.4** | 85.5 | 176.7 | — | 5677.0 | — |
| German (`de`) | **120.5** | 135.0 | 442.1 | 2287.7 | 21308.5 | — |
| English (`en`) | 94.6 | **91.6** | 233.4 | — | 12644.0 | 5666.9 |
| Spanish (`es`) | 96.6 | **87.5** | 201.1 | — | 13115.4 | — |
| Persian (`fa`) | 112.3 | **97.5** | 329.0 | — | 21819.8 | — |
| Finnish (`fi`) | 137.6 | **118.3** | 179.1 | — | 8000.8 | — |
| French (`fr`) | 133.5 | **124.1** | 330.3 | — | 23448.1 | — |
| Hebrew (`he`) | **136.8** | 142.0 | — | — | — | — |
| Hungarian (`hu`) | **91.2** | 92.0 | 164.5 | — | 9144.7 | — |
| Italian (`it`) | 103.0 | **79.8** | 347.4 | — | 22847.0 | — |
| Norwegian Bokmål (`nb`) | **83.0** | 88.3 | 148.3 | — | 4945.1 | — |
| Dutch (`nl`) | 89.3 | **80.1** | 240.8 | — | 11852.8 | — |
| Norwegian Nynorsk (`nn`) | **72.1** | 76.7 | 145.1 | — | 4814.1 | — |
| Polish (`pl`) | 89.0 | **87.3** | 130.5 | — | 3632.7 | — |
| Portuguese (`pt`) | 77.4 | **68.3** | 183.1 | — | 13864.8 | — |
| Russian (`ru`) | **146.0** | 147.9 | 263.4 | — | 10372.7 | — |
| Swedish (`sv`) | **86.9** | 93.1 | 130.2 | — | 3681.2 | — |
| Ukrainian (`uk`) | **103.8** | 110.3 | — | — | — | — |
| Yiddish (`yi`) | **95.7** | 98.8 | 432.6 | — | 21625.9 | — |

Both Radixor runtimes recorded lower median processing time in **18 / 18**
direct PyStemmer comparisons. At `N=100`,
Python (PyO3) achieved a **2.19×** geometric-mean speedup, with a largest direct
advantage of **4.52×** for Yiddish and throughput of **6.85–13.87 million
words/s** across its 20 languages. Python-C achieved a **2.28×** geometric-mean
speedup, with a largest direct advantage of **4.38×** for Yiddish and throughput
of **6.76–14.63 million words/s**. Python-C was faster than PyO3 in 10 languages
and PyO3 was faster in 10; Python-C's geometric-mean advantage over PyO3 was
**1.03×**, so workload and language remain more useful selection criteria than
a universal ranking.

### CISTEM comparison for German

The German row also provides a direct comparison with CISTEM:

| Engine | Implementation | N=100 | vs Python (PyO3) |
|---|---|---|---|
| **Python (PyO3)** | Rust trie | **120.5 ns/word** | — |
| Python-C | CPython C trie | 135.0 ns/word | 1.12× slower |
| PyStemmer (de) | Snowball C | 442.1 ns/word | 3.67× slower |
| **CISTEM** | pure Python (`nltk`) | **2,287.7 ns/word** | **18.98× slower** |

CISTEM has no batch entry point, so the harness invokes it through a per-word
Python loop and cannot amortize work through a native batch API. This run
measures only `N=100` and does not claim a CISTEM scaling curve across batch
sizes. CISTEM is a compact ~40-rule German heuristic with no dictionary — a
different design point that trades coverage for simplicity. Because CISTEM's
unavoidable normalization work modestly biases the measurement in Radixor's
favour (point 2 above), the 18.98× result is not a perfectly
normalization-matched ratio.

The all-language Gradle task does not measure stage-level profiling or cached
lookup performance. This page therefore does not mix such figures from an older
workstation into the published run.

## A note on comparability of *quality*

These are **speed** comparisons. Radixor is a **lexicon-trained transformation
stemmer**: it learns patch commands from UniMorph-grounded word–stem evidence
and can generalize those transformations beyond exact training entries.
Snowball, Porter, and CISTEM use hand-written rule systems. They produce
different stems and are not directly comparable on output; see the shared
[linguistic quality
methodology](../benchmarks/reference/linguistic-quality.md) for how stemming
quality is assessed separately from throughput.

## Reproduce

```bash
pip install -r python/benchmarks/requirements-bench.txt
./gradlew pythonBenchmarkAllLanguagesBatch \
  -PpythonBenchmarkEngines=radixor,radixor-c,PyStemmer,snowballstemmer-pure,cistem,nltk-porter \
  -PpythonBenchmarkWords=5000 \
  -PpythonBenchmarkRepeats=3 \
  -PpythonBenchmarkWarmup=3 \
  --rerun-tasks
```

The run prints the machine/Python/engine versions and each engine's backing
module (provenance), and writes per-point rows (CSV) plus the full report
including environment (JSON). Methodology and fairness notes live in
`python/benchmarks/README.md`.
