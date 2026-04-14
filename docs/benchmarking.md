# Benchmarking

> ← Back to [README.md](../README.md)

Radixor includes a JMH benchmark suite for both the internal algorithmic core and a side-by-side English comparison against the Snowball Porter stemmer family.

This document explains what is benchmarked, how to run it, and how to interpret the results responsibly.

## Scope

The benchmark suite currently covers two categories:

- Radixor core operations
- English stemmer comparison on the same token workload

The comparison benchmark processes the same deterministic English token stream through:

- Radixor with bundled `US_UK_PROFI`
- Snowball original Porter
- Snowball English, commonly referred to as Porter2

The purpose of the comparison is throughput measurement on identical input. It is not intended to prove linguistic equivalence between the compared stemmers.

## Current snapshot

A recent JMH run on JDK 21.0.10 with JMH 1.37, one thread, three warmup iterations, and five measurement iterations produced the following approximate throughput ranges:

| Workload | Radixor `US_UK_PROFI` | Snowball Porter | Snowball English |
| --- | ---: | ---: | ---: |
| About 12,000 generated tokens | 30.99 M tokens/s | 8.21 M tokens/s | 5.46 M tokens/s |
| About 60,000 generated tokens | 32.25 M tokens/s | 8.02 M tokens/s | 5.11 M tokens/s |

On that workload, Radixor is approximately:

- 4 times faster than Snowball original Porter
- 6 times faster than Snowball English

These values are workload- and environment-dependent. Treat them as measured results for the documented benchmark setup, not as universal constants.

## Benchmark classes

The main benchmark classes are under `src/jmh/java/org/egothor/stemmer/benchmark`.

Relevant classes include:

- `FrequencyTrieLookupBenchmark`
- `FrequencyTrieCompilationBenchmark`
- `EnglishStemmerComparisonBenchmark`

The English comparison benchmark uses the bundled Radixor English resource and the official Snowball Java distribution integrated into the JMH source set.

## Workload design

The English comparison benchmark uses a deterministic generated corpus rather than an uncontrolled ad hoc text sample.

The workload intentionally mixes:

- simple inflections
- common derivational forms
- US and UK spelling families
- lexical forms appropriate for `US_UK_PROFI`

This design keeps runs reproducible across environments and avoids accidental drift caused by changing external corpora.

## Running benchmarks

Run the full benchmark suite:

```bash
./gradlew jmh
```

Run only the English comparison benchmark:

```bash
./gradlew jmh -Pjmh.includes=EnglishStemmerComparisonBenchmark
```

## Generated reports

JMH reports are written to:

- `build/reports/jmh/jmh-results.txt`
- `build/reports/jmh/jmh-results.csv`

The text report is convenient for human review. The CSV report is more useful for CI archiving, historical tracking, and external processing.

## Interpreting results

Benchmark numbers should be read with care.

Important factors include:

- CPU model and frequency behavior
- thermal throttling
- JVM vendor and version
- system background load
- operating-system scheduling noise
- benchmark parameter changes

For meaningful comparison, keep these stable:

- hardware or VM class
- JDK version
- benchmark parameters
- thread count
- benchmark source revision

If a regression is suspected, repeat the run and compare against the previous CSV output rather than relying on a single measurement.

## Regression tracking

The recommended regression workflow is:

1. archive `jmh-results.csv`
2. compare the same benchmark names across runs
3. compare only like-for-like environments
4. investigate sustained regressions rather than one-off noise

For public reporting, the README should keep only the condensed benchmark summary, while detailed benchmark methodology and interpretation should remain in this document.

## Notes on comparison fairness

Radixor, Snowball Porter, and Snowball English are not the same kind of stemmer.

Radixor uses a compiled patch-command trie driven by dictionary data. Snowball Porter and Snowball English are rule-based English stemmers.

Because of that, the comparison should be understood as:

- equal input workload
- different stemming strategies
- measured throughput, not semantic identity

That distinction matters whenever performance claims are discussed in documentation or release notes.
