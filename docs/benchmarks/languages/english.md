# English Stemmer Benchmarks

This page reports same-language stemming benchmarks for English. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `US_UK` | 396,939 | 1,004,374 | 793,874 | 210,500 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **1,004,374**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 28 | 0.003% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 22,493 | 2.240% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 186,764 | 18.595% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 795,024 | 79.156% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 65 | 0.006% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 97.478% | 97.197% | 97.552% | Full Radixor dictionary patch-command stemmer. |
| Lucene EnglishMinimalStemFilter | 90.981% | 65.189% | 97.820% | Minimal English plural reduction, not a full stemmer. |
| Lucene KStemFilter | 80.076% | 76.608% | 80.996% | Krovetz-style English stemming TokenFilter; broader than minimal suffix reducers. |
| Lucene HunspellStemFilter | 80.243% | 12.750% | 98.139% | Benchmark-only English Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene EnglishPossessiveFilter | 79.032% | 0.003% | 99.987% | Possessive-ending remover only, not a full stemmer. |
| Snowball English / Porter2 | 40.342% | 46.296% | 38.763% | Porter2 rule-based suffix stemmer, distinct from original Porter. |
| Lucene PorterStemFilter | 39.538% | 46.201% | 37.772% | Lucene TokenFilter path for Porter suffix rules; not dictionary-root equivalent. |
| Lucene PorterStemmer direct copy | 39.538% | 46.201% | 37.772% | Direct Porter suffix-rule implementation generated under build for benchmark-only use. |
| OpenNLP PorterStemmer | 39.538% | 46.201% | 37.772% | Apache OpenNLP Porter suffix-rule implementation. |
| Snowball original Porter | 39.529% | 46.179% | 37.766% | Classic Porter rule-based suffix stemmer. |
| Paice/Husk Lancaster | 28.055% | 37.039% | 25.673% | Aggressive Paice/Husk rule stemmer that often produces shorter stems. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `radixorUsUkProfiPreferredStem` | 21.987 | 8.707 | 104.5 | 1.000 | Full dictionary patch-command stemmer using compiled patch commands. |
| Lucene EnglishPossessiveFilter | `luceneEnglishPossessiveFilter` | 24.539 | 1.515 | 116.6 | 1.116 | Possessive-ending remover only; not a full stemmer. |
| Lucene EnglishMinimalStemFilter | `luceneEnglishMinimalStemFilter` | 22.702 | 1.195 | 107.8 | 1.032 | Narrow plural reduction filter; not a full stemmer. |
| Lucene PorterStemmer direct copy | `lucenePorterStemmerCopied` | 24.696 | 13.235 | 117.3 | 1.123 | Benchmark-only generated copy of Lucene package-private Porter implementation. |
| OpenNLP PorterStemmer | `opennlpPorterStemmer` | 23.121 | 12.528 | 109.8 | 1.052 | Apache OpenNLP Porter implementation. |
| Snowball original Porter | `snowballOriginalPorter` | 38.904 | 10.353 | 184.8 | 1.769 | Classic Porter suffix-rule stemmer; historical English baseline, not a dictionary-equivalent stemmer. |
| Lucene PorterStemFilter | `lucenePorterStemFilter` | 37.021 | 1.196 | 175.9 | 1.684 | Lucene TokenFilter integration path for Porter; includes TokenStream overhead. |
| Lucene KStemFilter | `luceneKStemFilter` | 51.640 | 2.591 | 245.3 | 2.349 | Krovetz-style English TokenFilter; broader than minimal suffix filters. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 79.785 | 1.347 | 379.0 | 3.629 | Benchmark-only English Hunspell comparison using the benchmark Hunspell corpus. |
| Snowball English / Porter2 | `snowballEnglishPorter2` | 52.437 | 0.773 | 249.1 | 2.385 | Porter2 suffix-rule stemmer, distinct from original Porter. |
| Paice/Husk Lancaster | `paiceHuskLancaster` | 141.556 | 12.324 | 672.5 | 6.438 | Aggressive rule-based English stemmer. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.
