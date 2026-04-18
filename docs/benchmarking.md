# Benchmarking

Radixor includes a JMH benchmark suite for both the internal algorithmic core and a side-by-side English comparison against the Snowball Porter stemmer family.

This document explains what is benchmarked, how to run the suite, and how benchmark results should be interpreted.

## Scope

The benchmark suite currently covers two categories:

- Radixor core operations,
- English stemmer comparison on the same token workload.

The comparison benchmark processes the same deterministic English token stream through:

- Radixor with bundled `US_UK_PROFI`,
- Snowball original Porter,
- Snowball English, commonly referred to as Porter2.

The purpose of the comparison is throughput measurement on identical input. It is not intended to demonstrate linguistic equivalence between the compared stemmers.

## How to read the published numbers

Two kinds of benchmark numbers are relevant in the project.

### Reference measurements

The detailed benchmark snapshot documented on this page comes from a controlled run on a Ryzen 5 system. Those numbers are the best reference point for understanding absolute throughput under a known local benchmark environment.

### Published badge figures

The benchmark badge metadata published through GitHub Pages is generated in the GitHub-hosted container environment. That environment is convenient for continuous publication, but it is not the right place to treat absolute throughput values as stable across time. CPU scheduling, shared-host variability, and container-level noise can materially affect raw numbers from run to run.

For that reason, the published badge values should be treated primarily as a compact status surface. They are useful for observing broad trends and relative positioning, but not as the authoritative source for precise absolute throughput claims.

## Current snapshot

A recent JMH run on JDK 21.0.10 with JMH 1.37, one thread, three warmup iterations, and five measurement iterations produced the following approximate throughput ranges:

| Workload | Radixor `US_UK_PROFI` | Snowball Porter | Snowball English |
| --- | ---: | ---: | ---: |
| About 12,000 generated tokens | 30.99 M tokens/s | 8.21 M tokens/s | 5.46 M tokens/s |
| About 60,000 generated tokens | 32.25 M tokens/s | 8.02 M tokens/s | 5.11 M tokens/s |

On that workload, Radixor measured approximately:

- 4 times the throughput of Snowball original Porter,
- 6 times the throughput of Snowball English.

These values are workload-dependent and environment-dependent. They should be read as measured results for the documented setup, not as universal constants.

## Interpreting the relative result

Although the absolute numbers can move across environments, the throughput relationship between Radixor and the compared Porter-family stemmers has remained broadly stable in practical measurements. In particular, the comparison against Snowball original Porter is consistently in the rough range of about four to one in Radixor’s favor.

That relative behavior is more informative than any single absolute figure. It reflects a real architectural difference rather than a cosmetic benchmark artifact.

Radixor is built around a compiled patch-command trie that resolves the result through a direct lookup and patch application path. In contrast, classic rule-based stemmers such as the Porter family follow a different operational model. The result is that Radixor combines two properties that do not often appear together:

- dictionary-driven compiled lookup performance,
- the ability to generalize beyond explicitly listed word forms instead of behaving like a pure closed-form dictionary lookup table.

Within that design space, the measured throughput profile is strong enough to place Radixor among the fastest known practical implementations of this kind, while still supporting stemming of previously unseen forms. That should still be read as a carefully bounded engineering statement, not as an absolute claim over every possible stemmer architecture or benchmark scenario.

## Benchmark classes

The main benchmark classes are under `src/jmh/java/org/egothor/stemmer/benchmark`.

Relevant classes include:

- `FrequencyTrieLookupBenchmark`,
- `FrequencyTrieCompilationBenchmark`,
- `EnglishStemmerComparisonBenchmark`.

The English comparison benchmark uses the bundled Radixor English resource and the official Snowball Java distribution integrated into the JMH source set.

## Workload design

The English comparison benchmark uses a deterministic generated corpus rather than an uncontrolled ad hoc text sample.

The workload intentionally mixes:

- simple inflections,
- common derivational forms,
- US and UK spelling families,
- lexical forms appropriate for `US_UK_PROFI`.

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

- `build/reports/jmh/jmh-results.txt`,
- `build/reports/jmh/jmh-results.csv`.

The text report is convenient for human review. The CSV report is more useful for CI archiving, historical tracking, and external processing.

## Interpreting results responsibly

Benchmark numbers should always be read with care.

Important factors include:

- CPU model and frequency behavior,
- thermal throttling,
- JVM vendor and version,
- system background load,
- operating-system scheduling noise,
- benchmark parameter changes.

For meaningful comparison, keep these stable:

- hardware or VM class,
- JDK version,
- benchmark parameters,
- thread count,
- benchmark source revision.

If a regression is suspected, repeat the run and compare against previous CSV output rather than relying on a single measurement.

## Regression tracking

The recommended regression workflow is:

1. archive `jmh-results.csv`,
2. compare the same benchmark names across runs,
3. compare only like-for-like environments,
4. investigate sustained regressions rather than one-off noise.

For public reporting, the README should keep only the condensed benchmark summary, while detailed benchmark methodology and interpretation should remain in this document.

## Notes on comparison fairness

Radixor, Snowball Porter, and Snowball English are not the same kind of stemmer.

Radixor uses a compiled patch-command trie driven by dictionary data. Snowball Porter and Snowball English are rule-based English stemmers.

Because of that, the comparison should be understood as:

- equal input workload,
- different stemming strategies,
- measured throughput rather than semantic identity.

That distinction matters whenever performance claims are discussed in documentation, release notes, or badge summaries.
