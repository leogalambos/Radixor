# Polish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Polish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `PL_PL` | 9,990 | 132,308 | 19,957 | 112,351 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **132,308**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 1,719 | 1.299% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 53,303 | 40.287% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 37,051 | 28.004% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 20,415 | 15.430% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 19,820 | 14.980% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 98.837% | 98.744% | 99.359% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 89.545% | 88.272% | 96.713% | Benchmark-only Polish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene MorfologikFilter | 87.729% | 86.606% | 94.047% | Dictionary-based path; Morfologik can emit multiple terms. |
| Lucene StempelFilter | 70.009% | 69.262% | 74.220% | Lucene TokenFilter integration path for table-driven Polish Stempel. |
| Lucene StempelStemmer direct | 70.009% | 69.262% | 74.220% | Direct table-driven Polish Stempel stemmer API. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `polishRadixor` | 9.049 | 0.485 | 80.5 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 483.316 | 11.455 | 4301.8 | 53.408 | Benchmark-only Polish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene StempelStemmer direct | `polishLuceneStempelStemmerDirect` | 41.932 | 1.916 | 373.2 | 4.634 | Direct table-driven Polish Stempel stemmer API. |
| Lucene StempelFilter | `polishLuceneStempelFilter` | 45.277 | 13.693 | 403.0 | 5.003 | Lucene TokenFilter integration path for table-driven Polish Stempel. |
| Lucene MorfologikFilter | `polishLuceneMorfologikFilter` | 135.763 | 31.634 | 1208.4 | 15.002 | Dictionary-based Morfologik TokenFilter; may emit multiple terms. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.
