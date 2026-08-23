# Benchmarking

Radixor contains internal trie microbenchmarks, a separate stemmer comparison suite, and a dictionary coverage benchmark for Radixor itself. The current default-model publication uses the same-language speed and exact-root methods selected by the command recorded on the [environment page](benchmarks/reference/environment.md). Internal `FrequencyTrie*` microbenchmarks, the optional `PolishPolimorfStemmerComparisonBenchmark`, and the separate German CISTEM gold-standard experiment are not part of these language tables.

Every current default Radixor benchmark scenario uses the model ID declared by its `Language.defaultModelId()`. The root JMH runtime configuration depends directly on all default model projects plus optional `pl-pl-polimorf`; no benchmark-pack project or artifact exists. These dependencies are benchmark-only and never enter the root published POM. A PoliMorf comparison must be labeled with model ID `pl-pl-polimorf`, while the default Polish row remains `pl-pl-unimorph`.

The optional model now has a verified complete compiled loading path. It is not included in the 2026-08-23 corpus, accuracy, speed, coverage, or stemming-quality measurements. Any future full PoliMorf benchmark must provision its documented startup heap independently and record the exact model artifact version and checksum.

This page is the entry point for benchmark interpretation. Detailed tables and long reference material are split into focused subpages so that important points do not get buried.

## Key Takeaways

- Speed and accuracy must be read together. A faster row is not necessarily a better stemmer.
- Radixor ranks first in balanced accuracy in all 40 published deterministic
  language-mode matrices; 38 include a direct comparator and the two Hebrew
  modes report Radixor independently. In the Java speed matrix it records the lowest
  runtime point estimate in 9 of the 19 languages that have a direct comparator;
  Hebrew currently has no third-party Java adapter. In the other 10 languages,
  a narrower light or minimal filter is faster, and the adjacent accuracy table
  exposes the corresponding linguistic trade-off.
- The measured Radixor cost accompanies higher dictionary-root agreement in the published matrix. Search-system relevance is deployment-specific and is not measured by these stemming benchmarks.
- Speed benchmarks process changed dictionary tokens where the surface form differs from the expected root. Accuracy benchmarks process the complete dictionary.
- Accuracy tables use deterministic auxiliary counters from a single non-timed JMH evaluation, while Radixor counters are independently cross-checked by the default-model corpus report. Runtime scores from accuracy methods are not interpreted.
- The historical Porter performance badge is retired. Benchmark reporting now uses speed and quality tables rather than a single Porter ratio.

The nine Java runtime minima are Danish, Dutch, English, Hungarian, Norwegian
Nynorsk, Persian, Polish, Ukrainian, and Yiddish. Czech, Finnish, French, German,
Italian, Norwegian Bokmål, Portuguese, Russian, Spanish, and Swedish contain a
faster light or minimal point estimate. Hebrew publishes the Radixor measurement
without a direct third-party Java row. This count compares point estimates only;
the per-language tables retain JMH uncertainty and are the authoritative source.

## Benchmark Documentation Map

| Page | Purpose |
| --- | --- |
| [Benchmark methodology](benchmarks/reference/methodology.md) | Workload design, speed pass, quality pass, normalization policy, and exact-root metrics. |
| [Linguistic quality methodology](benchmarks/reference/linguistic-quality.md) | Pairwise gold standard, over/under-stemming, candidate policies, metrics, and ranking rules. |
| [Tested stemmers](benchmarks/reference/tested-stemmers.md) | Upstream attribution, tested versions, language coverage, adapter behaviour, and limitations. |
| [Reproducibility and raw data](benchmarks/reference/reproducibility.md) | Versioned quality snapshot, checksum, commands, reports, and provenance limitations. |
| [Benchmark corpora](benchmarks/reference/corpora.md) | Dictionary row counts, complete quality tokens, already-root tokens, changed speed tokens, and timing token counts. |
| [Benchmark environment and reports](benchmarks/reference/environment.md) | Hardware, OS, JVM, JMH settings, report files, and current badge/report policy. |
| [Dictionary-family generalization](benchmarks/generalization.md) | Five-split, all-language evaluation of transformations applied to families withheld from Java model training. |
| [Generalization methodology](benchmarks/reference/generalization-methodology.md) | Frozen sampling protocol, unseen-form leakage control, metrics, raw counters, and limitations. |
| [English dictionary coverage benchmark](benchmarks/reference/english-coverage.md) | The quality/speed operating curve for contracted Radixor tries built from 100% down to 10% of English dictionary rows. |
| [Candidate evaluation](benchmarks/reference/candidates.md) | Included benchmark families and evaluated candidates that were skipped. |
| [Language benchmark pages](benchmarks/languages/index.md) | Per-language accuracy tables, speed tables, and implementation notes. |

## How To Read Results

Start with the [language benchmark pages](benchmarks/languages/index.md). Each language page lists accuracy first and speed second because throughput without root agreement is not enough to interpret stemmer quality.

Read the accuracy and runtime tables together. Lucene light/minimal filters and
possessive filters intentionally perform narrower transformations; the
published tables preserve those rows rather than treating different linguistic
objectives as equivalent.

The [all-language generalization benchmark](benchmarks/generalization.md) isolates
forms from withheld dictionary families across five frozen splits. The separate
[English dictionary coverage benchmark](benchmarks/reference/english-coverage.md)
retains the original whole-dictionary quality/speed operating curve. Together
they distinguish transfer evidence from the model-size/runtime trade-off.

## Current Result Locations

The current measured language results are published in [Language Benchmark Pages](benchmarks/languages/index.md). Generated local report files for this benchmark update are listed in [Benchmark environment and reports](benchmarks/reference/environment.md).

JMH TXT and CSV reports are still published as benchmark artifacts. They are no longer converted into a Shields endpoint benchmark badge.
Model IDs, independent artifact versions, and descriptor checksums identify the inputs in the checked corpus snapshot. The optional PoliMorf model must not be attributed to the default Polish results. See [Model Selection and Loading](model-selection-and-loading.md) and [Reproducibility](benchmarks/reference/reproducibility.md).
