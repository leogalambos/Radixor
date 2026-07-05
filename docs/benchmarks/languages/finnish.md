# Finnish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Finnish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `FI_FI` | 57,027 | 1,865,215 | 110,525 | 1,754,690 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **1,865,215**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 745 | 0.040% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 1,176,003 | 63.049% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 565,585 | 30.323% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 116,946 | 6.270% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 5,936 | 0.318% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 98.661% | 98.803% | 96.408% | Full Radixor dictionary patch-command stemmer. |
| Lucene SnowballFilter | 10.991% | 10.268% | 22.471% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 10.991% | 10.268% | 22.471% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene FinnishLightStemFilter | 4.351% | 4.294% | 5.264% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `finnishRadixor` | 308.076 | 15.529 | 175.6 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene FinnishLightStemFilter | `finnishLuceneFinnishLightStemFilter` | 175.250 | 46.995 | 99.9 | 0.569 | Light Finnish suffix stemmer. |
| Official Snowball direct | `snowballDirect[FINNISH]` | 264.652 | 63.054 | 150.8 | 0.859 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[FINNISH]` | 374.883 | 238.157 | 213.6 | 1.217 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.
