# Runtime stemming benchmarks

These scripts measure **runtime stemming throughput only** — model construction
and dictionary compilation happen once during setup and are **excluded** from
every timing. Anyone can reproduce the numbers on their own machine.

## What is measured, and why batch sizes

Each engine is driven through its batch entry point over a fixed word budget
(default 5 000 tokens), at a single batch size of **100** words. We report the
*median* of calibrated repeats as the benchmark convention used for this suite.

## Data — identical to the Java JMH benchmarks

The workload is the **changed-token corpus** built from the repository's
canonical dictionaries (`models/<model>/src/modelInput/stemmer.gz`),
mirroring `LanguageBenchmarkCorpus` in the Java project: each dictionary field
is paired with its line's root, normalized `trim().lower()`, and only tokens
that **differ** from their root are kept, in dictionary order, padded to ≥ 5 000
tokens. See `corpus.py`.

## Fairness — three things that quietly break stemmer comparisons

Getting a *fair* comparison turned out to matter more than any micro-optimization.
Three asymmetries, if left in, make the numbers meaningless:

1. **Result caching.** PyStemmer caches results by default (`maxCacheSize=10000`).
   Because a benchmark stems the same corpus every repeat, that cache turns
   measured passes into dict lookups rather than stemming. **The harness
   explicitly disables all native caches**: PyStemmer uses `maxCacheSize=0`,
   while radixor and radixor-c use `cache_size=0`, so every engine does real stemming.
   snowballstemmer-pure, nltk-porter, and cistem have no cache.

2. **Lowercasing.** Snowball/PyStemmer do no case handling — they assume the
   caller pre-lowercased the input (our corpus is pre-lowercased for everyone).
   radixor normally lowercases internally; for a same-work comparison the
   harness runs radixor and radixor-c with **`lowercase=False`**
   (assume-already-lowercased), so all native engines receive identical input
   without redundant normalization.
   CISTEM always performs its own lowercasing and German umlaut normalization;
   that unavoidable extra work modestly biases its comparison in radixor's
   favour.

3. **Delegation.** `snowballstemmer` delegates to PyStemmer when it is installed
   (they become the same C code). The harness bypasses that and uses
   snowballstemmer's genuine pure-Python backend, and records each engine's
   backing module + whether it is a compiled extension (`--json`) as proof.

## Engines compared

| Engine | Implementation | Batch API |
|---|---|---|
| `radixor` | Rust patch-command trie (cache disabled) | `stem_batch` — one FFI call per batch |
| `radixor-c` | CPython C patch-command trie (cache disabled) | `stem_batch` — one C call per batch |
| `PyStemmer` | Snowball C `libstemmer` (cache disabled) | `stemWords(list)` — one C call per batch |
| `snowballstemmer-pure` | Official **pure-Python** Snowball | `stemWords(list)` — Python loop |
| `nltk-porter` | Porter (English), pure Python | scalar loop |
| `cistem` | CISTEM (German), pure Python (`nltk`) | scalar loop |

## Reproduce

```bash
cd python/
maturin develop --release             # build the radixor extension into your env
cd ..
./gradlew pythonBuildStandardModels    # generate and package standard models
pip install --no-deps build/python/dist/standard/radixor_models_standard-0.0.0-py3-none-any.whl
cd python/
pip install -r benchmarks/requirements-bench.txt
taskset -c 2 python -u benchmarks/run_benchmark.py \
    --all-languages --sizes 100 \
    --words 5000 --repeats 3 --sample-ms 250 --warmup-ms 500 \
    --json ../build/reports/python-benchmarks/published-benchmark.json \
    --csv ../build/reports/python-benchmarks/published-benchmark.csv
```

From the repository root, the Gradle integration now runs each configured engine
in its own virtual environment (only one stemmer package per run) and merges the
results, over all supported languages with fixed batch size 100:

```bash
./gradlew pythonBenchmarkAllLanguagesBatch \
    -PpythonBenchmarkEngines=radixor,radixor-c,PyStemmer,snowballstemmer-pure
```

The generated CSV and JSON reports are placed in
`build/reports/python-benchmarks/`.

Comparison engines are now intentionally isolated per task by benchmark task
configuration (`pythonBenchmarkEngines`) instead of depending on what is
installed in the global environment of `pythonExecutable`.

The run prints machine/Python/engine versions and each engine's backing module,
and writes per-point rows (CSV) plus the full report incl. environment and
provenance (JSON), so results are self-describing and verifiable.

## Published results

The canonical, current single-machine results and complete environment metadata
are published on the documentation site's [Python performance
page](../../docs/python/performance.md). Keeping the measured table in one place
prevents results from different CPUs or benchmark runs from being mixed.

## Interpretation

- In the published 2026-08-17 run, **radixor is the fastest stemmer measured in
  Python** in all 18 languages directly shared with PyStemmer.
- The benchmark intentionally disables caches. Cached-operation performance is
  outside this suite and must not be inferred from its results.
- radixor and Snowball remain different *classes* of stemmer: radixor is
  dictionary-based (UniMorph gold coverage), Snowball is rule-based. radixor
  gives dictionary-quality stems *and* the best measured throughput.
