# Portuguese Stemmer Benchmarks

This page reports same-language stemming benchmarks for Portuguese. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `PT_PT` | 4,001 | 215,490 | 8,002 | 207,488 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **215,490**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 3,806 | 1.766% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 120,691 | 56.008% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 71,284 | 33.080% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 8,003 | 3.714% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 11,706 | 5.432% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.815% | 99.808% | 100.000% | Full Radixor dictionary patch-command stemmer. |
| Lucene PortugueseLightStemFilter | 8.966% | 5.558% | 97.326% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |
| Lucene PortugueseMinimalStemFilter | 5.539% | 1.896% | 100.000% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SnowballFilter | 0.625% | 0.558% | 2.374% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 0.625% | 0.558% | 2.374% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene PortugueseStemFilter | 0.312% | 0.308% | 0.425% | Portuguese RSLP-style Lucene TokenFilter stemmer. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `portugueseRadixor` | 12.109 | 0.698 | 58.4 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene PortugueseLightStemFilter | `portugueseLucenePortugueseLightStemFilter` | 11.172 | 1.870 | 53.8 | 0.923 | Light Portuguese suffix stemmer. |
| Lucene PortugueseMinimalStemFilter | `portugueseLucenePortugueseMinimalStemFilter` | 16.038 | 1.752 | 77.3 | 1.325 | Minimal Portuguese suffix reducer. |
| Official Snowball direct | `snowballDirect[PORTUGUESE]` | 53.725 | 5.356 | 258.9 | 4.437 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[PORTUGUESE]` | 57.457 | 1.182 | 276.9 | 4.745 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |
| Lucene PortugueseStemFilter | `portugueseLucenePortugueseStemFilter` | 165.447 | 40.334 | 797.4 | 13.663 | Portuguese RSLP-style Lucene TokenFilter. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.
