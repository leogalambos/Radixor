# Runtime stemming benchmarks

These scripts measure **runtime stemming throughput only** — model construction
and dictionary compilation happen once during setup and are **excluded** from
every timing. Anyone can reproduce the numbers on their own machine.

## What is measured, and why batch sizes

Each engine is driven through its batch entry point over a fixed word budget
(default 5 000 tokens), split into batches of **10, 20, 50, 100** words. The
harness fits the descriptive line `per_call(N) ≈ intercept + N · slope` across
those sizes. The fit is unconstrained and timing noise can make its intercept
negative, so it describes observed scaling rather than physically separating
overhead from word work. We report the *best* (minimum) of many repeats — the
microbenchmark convention that suppresses OS/GC noise.

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
   explicitly disables both caches**: PyStemmer uses `maxCacheSize=0` and
   radixor uses `cache_size=0`, so both engines do real stemming.
   snowballstemmer-pure, nltk-porter, and cistem have no cache.

2. **Lowercasing.** Snowball/PyStemmer do no case handling — they assume the
   caller pre-lowercased the input (our corpus is pre-lowercased for everyone).
   radixor normally lowercases internally; for a same-work comparison the
   harness runs radixor with **`lowercase=False`** (assume-already-lowercased),
   so Snowball and radixor do identical normalization work on identical input.
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
python benchmarks/run_benchmark.py --language en de fr ru fi \
    --sizes 10 20 50 100 --repeats 21 \
    --json benchmarks/results.json --csv benchmarks/results.csv
```

From the repository root, the Gradle integration builds an isolated host wheel
and benchmarks Radixor plus every available comparison engine over all
supported languages with the fixed 10/20/50/100 size sweep:

```bash
./gradlew pythonBenchmarkAllLanguagesBatch
```

The generated CSV and JSON reports are placed in
`build/reports/python-benchmarks/`.

Comparison engines are auto-detected in the environment selected by the Gradle
`pythonExecutable` property. Install `requirements-bench.txt` in that environment
to enable the complete comparison set.

The run prints machine/Python/engine versions and each engine's backing module,
and writes per-point rows (CSV) plus the full report incl. environment and
provenance (JSON), so results are self-describing and verifiable.

## Published results

The canonical, current single-machine results and complete environment metadata
are published on the documentation site's [Python performance
page](../../docs/python/performance.md). Keeping the measured table in one place
prevents results from different CPUs or benchmark runs from being mixed.

## Interpretation

- In the published 2026-08-08 run, **radixor is the fastest stemmer measured in
  Python** in all 18 languages directly shared with PyStemmer.
- The benchmark intentionally disables caches. Cached-operation performance is
  outside this suite and must not be inferred from its results.
- radixor and Snowball remain different *classes* of stemmer: radixor is
  dictionary-based (UniMorph gold coverage), Snowball is rule-based. radixor
  gives dictionary-quality stems *and* the best measured throughput.
