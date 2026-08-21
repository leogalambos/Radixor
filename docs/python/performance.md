# Performance (Python Runtimes)

This page reports **runtime stemming throughput** of Python (PyO3) against
common Python stemmers, and — crucially — documents exactly how the comparison
is made fair. The scripts are in the repository (`python/benchmarks/`); anyone
can reproduce the numbers.

`radixor-c` belongs in this same benchmark rather than in a separate table,
because both packages expose the same workload and models. It has not yet been
measured by the published harness, so its column is explicitly `N/A`. No
performance claim on this page should be inferred from those placeholders.

!!! info "Published single-machine measurement"
    These results were regenerated on 2026-08-18 on the current benchmark
    workstation: AMD Ryzen 5 8600G with Radeon 760M Graphics (6 cores / 12
    threads), Linux-7.1.8-200.fc44.x86_64-x86_64-with-glibc2.43,
    CPython 3.14.6, Rust 1.97.1, and a release wheel.
    All logical CPUs used the `performance` governor. Absolute timings remain
    machine-specific; compare ratios only within this run.

## What is measured

- **Runtime stemming only.** Model construction / dictionary compilation happens
  once in setup and is excluded from every timing.
- **Workload = the Java JMH corpus.** The *changed-token* corpus derived from
  the bundled UniMorph gold-standard dictionaries: every dictionary field paired
  with its line's root, normalized `trim().lower()`, keeping only tokens that
  differ from their root (the forms a stemmer must actually rewrite), padded to
  ≥ 5 000 tokens. This is identical to the Java `LanguageBenchmarkCorpus`.
- The benchmark is now run at a single fixed batch size **N = 100**.
  This avoids fitting a scaling model and reports the measured batch performance
  directly.

## Fairness: making the comparison apples-to-apples

Three asymmetries silently distort stemmer comparisons. Each is neutralized, and
where it **cannot** be neutralized the effect is described.

1. **Result caching — neutralized.** PyStemmer caches results by default
   (`maxCacheSize=10000`). Since a benchmark stems the same corpus repeatedly,
   that cache would turn measured passes into dictionary lookups rather than
   stemming. The harness explicitly disables **both** PyStemmer's cache
   (`maxCacheSize=0`) and radixor's default cache (`cache_size=0`). The other
   engines have no cache.
2. **Lowercasing — neutralized.** Snowball (PyStemmer, snowballstemmer) and
   CISTEM differ in whether they case-fold. Snowball does **no** case handling;
   it assumes pre-lowercased input. The corpus is pre-lowercased for every
   engine, and radixor is therefore run with **`lowercase=False`** so it does
   the same work. On already-lowercased input radixor returns identical results
   either way. **Exception — CISTEM:** it always performs its own lowercasing
   and German umlaut normalization internally and cannot be told to skip it, so
   CISTEM does *slightly more* normalization work than the others. This
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
| CPU | AMD Ryzen 5 8600G with Radeon 760M Graphics |
| CPU topology | 6 physical cores / 12 logical CPUs |
| OS | Linux-7.1.8-200.fc44.x86_64-x86_64-with-glibc2.43 |
| CPU governor | `performance` on all 12 logical CPUs; boost enabled |
| Python | CPython 3.14.6 |
| Radixor | 4.1.2, release-mode ABI3 wheel, cache disabled |
| PyStemmer | 3.1.0 (`libstemmer_c` 3.1.0), cache disabled |
| snowballstemmer | 3.1.1, forced pure-Python backend |
| NLTK | 3.10.3 |
| Workload | 5,000 changed tokens per language and measurement |
| Batch sizes | 100 |
| Timing | median of 3 measured passes after 3 warm-up passes |

The authoritative command was:

```bash
./gradlew pythonBenchmarkAllLanguagesBatch \
  -PpythonBenchmarkEngines=radixor,PyStemmer,snowballstemmer-pure,cistem,nltk-porter \
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
means that the engine has no implementation for that language. Every available
competitor was measured in the same process, with the same corpus and batch
partitioning.

| Language | Python (PyO3) | Python-C | PyStemmer (Snowball C) | CISTEM (pure Py) | snowballstemmer (pure Py) | NLTK Porter (pure Py) |
|---|---:|---:|---:|---:|---:|---:|
| Czech (`cs`) | **151.6** | N/A | 158.7 | — | 3194.4 | — |
| Danish (`da`) | **115.8** | N/A | 181.9 | — | 5643.1 | — |
| German (`de`) | **156.1** | N/A | 453.3 | 2306.4 | 22351.7 | — |
| English (`en`) | **127.4** | N/A | 251.2 | — | 12806.1 | 5335.1 |
| Spanish (`es`) | **134.3** | N/A | 209.9 | — | 13053.4 | — |
| Persian (`fa`) | **147.4** | N/A | 324.2 | — | 20816.9 | — |
| Finnish (`fi`) | **171.7** | N/A | 188.3 | — | 7790.9 | — |
| French (`fr`) | **167.4** | N/A | 349.8 | — | 22760.1 | — |
| Hebrew (`he`) | **161.6** | N/A | — | — | — | — |
| Hungarian (`hu`) | **133.2** | N/A | 190.7 | — | 9040.2 | — |
| Italian (`it`) | **137.6** | N/A | 363.8 | — | 22083.6 | — |
| Norwegian Bokmål (`nb`) | **115.1** | N/A | 166.4 | — | 4926.5 | — |
| Dutch (`nl`) | **120.4** | N/A | 246.3 | — | 11720.3 | — |
| Norwegian Nynorsk (`nn`) | **106.5** | N/A | 164.3 | — | 4828.7 | — |
| Polish (`pl`) | **125.6** | N/A | 137.2 | — | 3489.3 | — |
| Portuguese (`pt`) | **114.5** | N/A | 198.7 | — | 14330.2 | — |
| Russian (`ru`) | **192.3** | N/A | 278.5 | — | 10333.2 | — |
| Swedish (`sv`) | **122.6** | N/A | 138.0 | — | 3576.5 | — |
| Ukrainian (`uk`) | **149.6** | N/A | — | — | — | — |
| Yiddish (`yi`) | **159.5** | N/A | 434.1 | — | 20835.4 | — |

Radixor won **18 / 18** direct PyStemmer comparisons. At `N=100`, its
geometric-mean speedup was **1.68×**; the largest direct advantage was **2.90×**
for German. Across all 20 Radixor languages, throughput ranged from **5.20 to
9.39 million words/s**.

### CISTEM comparison for German

The German row also provides a direct comparison with CISTEM:

| Engine | Implementation | N=100 | vs radixor |
|---|---|---|---|
| **radixor** | Rust trie | **156.1 ns/word** | — |
| PyStemmer (de) | Snowball C | 453.3 ns/word | 2.90× slower |
| **CISTEM** | pure Python (`nltk`) | **2,306.4 ns/word** | **14.79× slower** |

CISTEM has no batch entry point (it is a per-word Python loop), so its per-word
cost is flat across batch sizes and batching cannot amortize it. It is a compact
~40-rule German heuristic with no dictionary — a different design point that
trades coverage for simplicity. Because CISTEM's unavoidable normalization work
modestly biases the measurement in radixor's favour (point 2 above), the 14.82×
result is not a perfectly normalization-matched ratio.

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
  -PpythonBenchmarkEngines=radixor,PyStemmer,snowballstemmer-pure,cistem,nltk-porter \
  -PpythonBenchmarkWords=5000 \
  -PpythonBenchmarkRepeats=3 \
  -PpythonBenchmarkWarmup=3 \
  --rerun-tasks
```

The run prints the machine/Python/engine versions and each engine's backing
module (provenance), and writes per-point rows (CSV) plus the full report
including environment (JSON). Methodology and fairness notes live in
`python/benchmarks/README.md`.
