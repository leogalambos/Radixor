# Benchmarking

Radixor contains internal trie microbenchmarks, a separate stemmer comparison suite, and a dictionary coverage benchmark for Radixor itself. The current default-model publication uses the same-language speed and exact-root methods selected by the command recorded on the [environment page](benchmarks/reference/environment.md). Internal `FrequencyTrie*` microbenchmarks, the optional `PolishPolimorfStemmerComparisonBenchmark`, and the separate German CISTEM gold-standard experiment are not part of these language tables.

Every current default Radixor benchmark scenario uses the model ID declared by its `Language.defaultModelId()`. The root JMH runtime configuration depends directly on all default model projects plus optional `pl-pl-polimorf`; no benchmark-pack project or artifact exists. These dependencies are benchmark-only and never enter the root published POM. A PoliMorf comparison must be labeled with model ID `pl-pl-polimorf`, while the default Polish row remains `pl-pl-unimorph`.

The optional model now has a verified complete compiled loading path. It is not included in the 2026-07-23 corpus, accuracy, speed, coverage, or stemming-quality measurements. Any future full PoliMorf benchmark must provision its documented startup heap independently and record the exact model artifact version and checksum.

This page is the entry point for benchmark interpretation. Detailed tables and long reference material are split into focused subpages so that important points do not get buried.

## Key Takeaways

- Speed and accuracy must be read together. A faster row is not necessarily a better stemmer.
- Radixor is the quality-oriented baseline in same-language comparisons. Its exact-root accuracy is often close to 100%, while many faster competitors are light, minimal, possessive, or aggressive rule-based stemmers with much lower root agreement.
- The measured Radixor cost buys dictionary-trained stemming precision. That precision improves search quality by mapping inflected forms to intended dictionary roots instead of approximate or over-reduced stems.
- Speed benchmarks process changed dictionary tokens where the surface form differs from the expected root. Accuracy benchmarks process the complete dictionary.
- Accuracy tables use deterministic auxiliary counters from a single non-timed JMH evaluation, while Radixor counters are independently cross-checked by the default-model corpus report. Runtime scores from accuracy methods are not interpreted.
- The historical Porter performance badge is retired. Benchmark reporting now uses speed and quality tables rather than a single Porter ratio.

## Benchmark Documentation Map

| Page | Purpose |
| --- | --- |
| [Benchmark methodology](benchmarks/reference/methodology.md) | Workload design, speed pass, quality pass, normalization policy, and exact-root metrics. |
| [Linguistic quality methodology](benchmarks/reference/linguistic-quality.md) | Pairwise gold standard, over/under-stemming, candidate policies, metrics, and ranking rules. |
| [Tested stemmers](benchmarks/reference/tested-stemmers.md) | Upstream attribution, tested versions, language coverage, adapter behaviour, and limitations. |
| [Reproducibility and raw data](benchmarks/reference/reproducibility.md) | Versioned quality snapshot, checksum, commands, reports, and provenance limitations. |
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
Model IDs, independent artifact versions, and descriptor checksums identify the inputs in the checked corpus snapshot. The optional PoliMorf model must not be attributed to the default Polish results. See [Model Selection and Loading](model-selection-and-loading.md) and [Reproducibility](benchmarks/reference/reproducibility.md).
