# Performance (Python)

This page reports **runtime stemming throughput** of the Python implementation against
common Python stemmers, and — crucially — documents exactly how the comparison
is made fair. The scripts are in the repository (`python/benchmarks/`); anyone
can reproduce the numbers.

!!! info "Published single-machine measurement"
    These results were regenerated on 2026-08-08 on the current benchmark
    workstation: Fedora Linux 44 (`7.1.6-201.fc44.x86_64`), AMD Ryzen 5 5625U
    (6 cores / 12 threads), CPython 3.14.6, Rust 1.97.1, and a release wheel.
    All logical CPUs used the `schedutil` governor. Absolute timings remain
    machine-specific; compare ratios only within this run.

## What is measured

- **Runtime stemming only.** Model construction / dictionary compilation happens
  once in setup and is excluded from every timing.
- **Workload = the Java JMH corpus.** The *changed-token* corpus derived from
  the bundled UniMorph gold-standard dictionaries: every dictionary field paired
  with its line's root, normalized `trim().lower()`, keeping only tokens that
  differ from their root (the forms a stemmer must actually rewrite), padded to
  ≥ 5 000 tokens. This is identical to the Java `LanguageBenchmarkCorpus`.
- **Batch sizes 10/20/50/100** are swept and a line is fit to `per_call(N) =
  intercept + N · slope` as a descriptive scaling summary. This is an
  unconstrained OLS fit, so noise may produce a negative intercept; it is not a
  physical decomposition of runtime. The *best* of many repeats is reported.

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
| CPU | AMD Ryzen 5 5625U with Radeon Graphics |
| CPU topology | 6 physical cores / 12 logical CPUs |
| OS | Fedora Linux 44, kernel `7.1.6-201.fc44.x86_64` |
| CPU governor | `schedutil` on all 12 logical CPUs; boost enabled |
| Python | CPython 3.14.6 |
| Radixor | 4.1.0, release-mode ABI3 wheel, cache disabled |
| PyStemmer | 3.1.0 (`libstemmer_c` 3.1.0), cache disabled |
| snowballstemmer | 3.1.1, forced pure-Python backend |
| NLTK | 3.10.2 |
| Workload | 5,000 changed tokens per language and measurement |
| Batch sizes | 10, 20, 50, 100 |
| Timing | best of 15 measured passes after 3 warm-up passes |

The authoritative command was:

```bash
./gradlew pythonBenchmarkAllLanguagesBatch --rerun-tasks
```

It completed successfully in 3 minutes 33 seconds and emitted
the full per-size CSV and JSON reports under
`build/reports/python-benchmarks/`.

## Results — batch size 100, cache disabled

The table reports nanoseconds per word at `N=100` (lower is better). A dash
means that the engine has no implementation for that language. Every available
competitor was measured in the same process, with the same corpus and batch
partitioning.

| Language | Radixor | PyStemmer (Snowball C) | CISTEM (pure Py) | snowballstemmer (pure Py) | NLTK Porter (pure Py) |
|---|---:|---:|---:|---:|---:|
| Czech (`cs`) | **224.3** | 236.6 | — | 4,835.2 | — |
| Danish (`da`) | **178.3** | 267.6 | — | 8,568.9 | — |
| German (`de`) | **230.9** | 635.5 | 3,341.9 | 33,654.1 | — |
| English (`en`) | **180.5** | 331.9 | — | 20,195.0 | 7,740.3 |
| Spanish (`es`) | **184.2** | 316.6 | — | 19,640.1 | — |
| Persian (`fa`) | **210.1** | 497.1 | — | 32,732.3 | — |
| Finnish (`fi`) | **227.8** | 258.8 | — | 12,339.5 | — |
| French (`fr`) | **234.2** | 503.7 | — | 36,161.9 | — |
| Hebrew (`he`) | **228.6** | — | — | — | — |
| Hungarian (`hu`) | **198.2** | 264.7 | — | 13,694.3 | — |
| Italian (`it`) | **170.8** | 517.0 | — | 34,504.6 | — |
| Norwegian Bokmål (`nb`) | **187.1** | 239.7 | — | 7,457.6 | — |
| Dutch (`nl`) | **187.1** | 354.8 | — | 18,148.2 | — |
| Norwegian Nynorsk (`nn`) | **168.7** | 231.2 | — | 7,489.4 | — |
| Polish (`pl`) | **194.6** | 214.5 | — | 5,282.9 | — |
| Portuguese (`pt`) | **166.9** | 293.2 | — | 21,157.2 | — |
| Russian (`ru`) | **273.4** | 414.4 | — | 15,703.8 | — |
| Swedish (`sv`) | **189.3** | 212.5 | — | 5,351.4 | — |
| Ukrainian (`uk`) | **221.5** | — | — | — | — |
| Yiddish (`yi`) | **227.5** | 624.2 | — | 33,251.6 | — |

Radixor won all **18 / 18** direct PyStemmer comparisons. At `N=100`, its
geometric-mean speedup was **1.67×**; the largest direct advantage was **3.03×**
for Italian. Across all 20 Radixor languages, throughput ranged from **3.66 to
5.99 million words/s**.

### CISTEM comparison for German

The German row also provides a direct comparison with CISTEM:

| Engine | Implementation | N=100 | vs radixor |
|---|---|---|---|
| **radixor** | Rust trie | **230.9 ns/word** | — |
| PyStemmer (de) | Snowball C | 635.5 ns/word | 2.75× slower |
| **CISTEM** | pure Python (`nltk`) | **3,341.9 ns/word** | **14.47× slower** |

CISTEM has no batch entry point (it is a per-word Python loop), so its per-word
cost is flat across batch sizes and batching cannot amortize it. It is a compact
~40-rule German heuristic with no dictionary — a different design point that
trades coverage for simplicity. Because CISTEM's unavoidable normalization work
modestly biases the measurement in radixor's favour (point 2 above), the 14.47×
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
./gradlew pythonBenchmarkAllLanguagesBatch --rerun-tasks
```

The run prints the machine/Python/engine versions and each engine's backing
module (provenance), and writes per-point rows (CSV) plus the full report
including environment (JSON). Methodology and fairness notes live in
`python/benchmarks/README.md`.
