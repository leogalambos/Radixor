# Benchmarking

Radixor contains internal trie microbenchmarks, a separate stemmer comparison suite, and a dictionary coverage benchmark for Radixor itself. Published stemmer comparison results must come only from benchmark classes matching `.*StemmerComparisonBenchmark.*`; internal `FrequencyTrie*` microbenchmarks are not part of those results.

This page is the entry point for benchmark interpretation. Detailed tables and long reference material are split into focused subpages so that important points do not get buried.

## Key Takeaways

- Speed and accuracy must be read together. A faster row is not necessarily a better stemmer.
- Radixor is the quality-oriented baseline in same-language comparisons. Its exact-root accuracy is often close to 100%, while many faster competitors are light, minimal, possessive, or aggressive rule-based stemmers with much lower root agreement.
- The measured Radixor cost buys dictionary-trained stemming precision. That precision improves search quality by mapping inflected forms to intended dictionary roots instead of approximate or over-reduced stems.
- Speed benchmarks process changed dictionary tokens where the surface form differs from the expected root. Accuracy benchmarks process the complete dictionary.
- Accuracy-only benchmarks intentionally use one deterministic JMH measurement iteration without warmup because repeated precision passes would duplicate the same counters.
- The historical Porter performance badge is retired. Benchmark reporting now uses speed and quality tables rather than a single Porter ratio.

## Benchmark Documentation Map

| Page | Purpose |
| --- | --- |
| [Benchmark methodology](benchmarks/reference/methodology.md) | Workload design, speed pass, quality pass, normalization policy, and exact-root metrics. |
| [Benchmark corpora](benchmarks/reference/corpora.md) | Dictionary row counts, complete quality tokens, already-root tokens, changed speed tokens, and timing token counts. |
| [Benchmark environment and reports](benchmarks/reference/environment.md) | Hardware, OS, JVM, JMH settings, report files, and current badge/report policy. |
| [English dictionary coverage benchmark](benchmarks/reference/english-coverage.md) | The quality/speed operating curve for contracted Radixor tries built from 100% down to 10% of English dictionary rows. |
| [Candidate evaluation](benchmarks/reference/candidates.md) | Included benchmark families and evaluated candidates that were skipped. |
| [Language benchmark pages](benchmarks/languages/index.md) | Per-language accuracy tables, speed tables, and implementation notes. |

## How To Read Results

Start with the [language benchmark pages](benchmarks/languages/index.md). Each language page lists accuracy first and speed second because throughput without root agreement is not enough to interpret stemmer quality.

When Radixor is slower than a narrow competitor, check the accuracy table before drawing a conclusion. Many Lucene light/minimal filters and possessive filters intentionally do less work. They can be fast precisely because they are not trying to match the dictionary root with the same precision.

The [English dictionary coverage benchmark](benchmarks/reference/english-coverage.md) shows the central operating curve explicitly: contracted tries preserve high quality even at reduced dictionary coverage, while changed-form exactness still reflects how much language knowledge was available during training. This is why Radixor performance should be discussed as a configurable quality/speed point, not as a single fixed ratio against Porter.

## Current Result Locations

The current measured language results are published in [Language Benchmark Pages](benchmarks/languages/index.md). Generated local report files for this benchmark update are listed in [Benchmark environment and reports](benchmarks/reference/environment.md).

JMH TXT and CSV reports are still published as benchmark artifacts. They are no longer converted into a Shields endpoint benchmark badge.
