# Performance (Python Runtimes)

This page reports **runtime stemming throughput** of Python (PyO3) against
common Python stemmers, and — crucially — documents exactly how the comparison
is made fair. The scripts are in the repository (`python/benchmarks/`); anyone
can reproduce the numbers.

`radixor-c` belongs in this same benchmark rather than in a separate table,
because both packages expose the same workload and models. The published run
measures both native runtimes from version 4.2.0: `radixor` through Rust/PyO3
and `radixor-c` through the CPython C API.

The matching patch number in this run does not couple their release streams.
Radixor runtimes share project-wide major and minor versions, while each
runtime advances its patch version independently for local fixes and
improvements.

!!! info "Published single-machine measurement"
    These results were regenerated on 2026-08-23 on the current benchmark
    workstation: AMD Ryzen 5 7600 6-Core Processor (6 cores / 12 threads),
    Fedora Linux 44 with kernel `7.1.8-200.fc44.x86_64`, CPython 3.14.7,
    Rust 1.97.1, GCC 16.2.1, and locally built release-mode wheels for both
    Radixor 4.2.0 Python runtimes. All logical CPUs used the `performance`
    governor and `performance` energy preference. Absolute timings remain
    machine-specific; compare ratios only within this run.

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
| CPU | AMD Ryzen 5 7600 6-Core Processor |
| CPU topology | 6 physical cores / 12 logical CPUs |
| OS | Fedora Linux 44, kernel `7.1.8-200.fc44.x86_64`, glibc 2.43 |
| CPU policy | `amd-pstate-epp` active; `performance` governor and energy preference on all 12 logical CPUs; boost enabled |
| Python | CPython 3.14.7 |
| Python (PyO3) | Radixor 4.2.0, locally built release-mode ABI3 wheel, cache disabled |
| Python-C | Radixor 4.2.0, locally built CPython 3.14 C-extension wheel, cache disabled |
| Native toolchains | Rust 1.97.1; GCC 16.2.1 |
| Source identity | Radixor 4.2.0 release source based on Git commit `31e3b9d`; the Java benchmark provenance retains the exact measured source patch and untracked-source checksums |
| PyStemmer | 3.1.0 (`libstemmer_c` 3.1.0), cache disabled |
| snowballstemmer | 3.1.1, forced pure-Python backend |
| NLTK | 3.10.3 |
| Workload | 5,000 changed tokens per language and measurement |
| Batch sizes | 100 |
| Timing | median of 3 calibrated ~250 ms samples after a warm-up of at least 3 complete-corpus passes and 500 ms |

The checkout represents the 4.2.0 Python runtimes. The repository's local
benchmark build deliberately leaves wheel metadata at its `0.0.0` packaging
placeholder, which is why the raw report records `engine_version=0.0.0` even
though the measured source version is 4.2.0.

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
| Czech (`cs`) | 105.2 | **95.5** | 147.7 | — | 3385.8 | — |
| Danish (`da`) | **76.3** | 84.6 | 171.5 | — | 5991.8 | — |
| German (`de`) | **118.4** | 134.7 | 428.8 | 2305.5 | 23667.1 | — |
| English (`en`) | 91.2 | **90.3** | 228.3 | — | 12724.9 | 5307.5 |
| Spanish (`es`) | 91.4 | **84.9** | 194.9 | — | 13100.4 | — |
| Persian (`fa`) | 109.6 | **95.1** | 325.3 | — | 20884.6 | — |
| Finnish (`fi`) | 136.0 | **115.2** | 177.5 | — | 7951.3 | — |
| French (`fr`) | 127.5 | **122.3** | 324.0 | — | 24327.4 | — |
| Hebrew (`he`) | 131.5 | **124.6** | — | — | — | — |
| Hungarian (`hu`) | **87.8** | 89.5 | 166.4 | — | 9059.7 | — |
| Italian (`it`) | 97.0 | **77.7** | 339.4 | — | 23162.9 | — |
| Norwegian Bokmål (`nb`) | **80.6** | 86.7 | 145.1 | — | 5169.2 | — |
| Dutch (`nl`) | 83.4 | **77.9** | 240.4 | — | 11800.1 | — |
| Norwegian Nynorsk (`nn`) | **68.9** | 74.3 | 141.3 | — | 4969.1 | — |
| Polish (`pl`) | **84.2** | 84.8 | 131.2 | — | 3667.3 | — |
| Portuguese (`pt`) | 73.4 | **67.3** | 180.4 | — | 14282.9 | — |
| Russian (`ru`) | **143.7** | 144.6 | 257.7 | — | 10633.2 | — |
| Swedish (`sv`) | **84.1** | 92.4 | 132.6 | — | 3800.7 | — |
| Ukrainian (`uk`) | **98.8** | 106.0 | — | — | — | — |
| Yiddish (`yi`) | **91.5** | 95.9 | 419.0 | — | 21410.5 | — |

Both Radixor runtimes recorded lower median processing time in **18 / 18**
direct PyStemmer comparisons. At `N=100`,
Python (PyO3) achieved a **2.25×** geometric-mean speedup, with a largest direct
advantage of **4.58×** for Yiddish and throughput of **6.96–14.52 million
words/s** across its 20 languages. Python-C achieved a **2.29×** geometric-mean
speedup, with a largest direct advantage of **4.37×** for Yiddish and throughput
of **6.92–14.85 million words/s**. Python-C was faster than PyO3 in 10 languages
and PyO3 was faster in 10; Python-C's geometric-mean advantage over PyO3 was
**1.02×**, so workload and language remain more useful selection criteria than
a universal ranking.

### CISTEM comparison for German

The German row also provides a direct comparison with CISTEM:

| Engine | Implementation | N=100 | vs Python (PyO3) |
|---|---|---|---|
| **Python (PyO3)** | Rust trie | **118.4 ns/word** | — |
| Python-C | CPython C trie | 134.7 ns/word | 1.14× slower |
| PyStemmer (de) | Snowball C | 428.8 ns/word | 3.62× slower |
| **CISTEM** | pure Python (`nltk`) | **2,305.5 ns/word** | **19.48× slower** |

CISTEM has no batch entry point, so the harness invokes it through a per-word
Python loop and cannot amortize work through a native batch API. This run
measures only `N=100` and does not claim a CISTEM scaling curve across batch
sizes. CISTEM is a compact ~40-rule German heuristic with no dictionary — a
different design point that trades coverage for simplicity. Because CISTEM's
unavoidable normalization work modestly biases the measurement in Radixor's
favour (point 2 above), the 19.48× result is not a perfectly
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
