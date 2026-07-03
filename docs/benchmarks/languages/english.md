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

Accuracy is computed from one deterministic JMH measurement iteration without warmup. The benchmark may execute the full dictionary pass more than once inside that single timed iteration; percentages divide matching counters by evaluated counters from the same iteration.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 97.478% | 97.197% | 97.552% | Full Radixor dictionary patch-command stemmer. |
| Lucene EnglishMinimalStemFilter | 90.981% | 65.189% | 97.820% | Minimal English plural reduction, not a full stemmer. |
| Lucene KStemFilter | 80.076% | 76.608% | 80.996% | Krovetz-style English stemming TokenFilter; broader than minimal suffix reducers. |
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
| Radixor | `radixorUsUkProfiPreferredStem` | 16.621 | 8.532 | 79.0 | 1.000 | Full dictionary patch-command stemmer using compiled patch commands. |
| Lucene EnglishPossessiveFilter | `luceneEnglishPossessiveFilter` | 23.845 | 0.833 | 113.3 | 1.435 | Possessive-ending remover only; not a full stemmer. |
| Lucene EnglishMinimalStemFilter | `luceneEnglishMinimalStemFilter` | 17.091 | 0.198 | 81.2 | 1.028 | Narrow plural reduction filter; not a full stemmer. |
| Lucene PorterStemmer direct copy | `lucenePorterStemmerCopied` | 18.598 | 10.954 | 88.4 | 1.119 | Benchmark-only generated copy of Lucene package-private Porter implementation. |
| OpenNLP PorterStemmer | `opennlpPorterStemmer` | 18.213 | 10.674 | 86.5 | 1.096 | Apache OpenNLP Porter implementation. |
| Snowball original Porter | `snowballOriginalPorter` | 32.921 | 11.520 | 156.4 | 1.981 | Classic Porter suffix-rule stemmer; historical English baseline, not a dictionary-equivalent stemmer. |
| Lucene PorterStemFilter | `lucenePorterStemFilter` | 42.874 | 1.321 | 203.7 | 2.579 | Lucene TokenFilter integration path for Porter; includes TokenStream overhead. |
| Lucene KStemFilter | `luceneKStemFilter` | 50.483 | 3.624 | 239.8 | 3.037 | Krovetz-style English TokenFilter; broader than minimal suffix filters. |
| Snowball English / Porter2 | `snowballEnglishPorter2` | 47.844 | 1.887 | 227.3 | 2.878 | Porter2 suffix-rule stemmer, distinct from original Porter. |
| Paice/Husk Lancaster | `paiceHuskLancaster` | 135.050 | 11.088 | 641.6 | 8.125 | Aggressive rule-based English stemmer. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.
