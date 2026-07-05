# French Stemmer Benchmarks

This page reports same-language stemming benchmarks for French. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `FR_FR` | 59,240 | 474,110 | 108,141 | 365,969 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **474,110**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 5,370 | 1.133% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 185,263 | 39.076% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 153,886 | 32.458% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 116,519 | 24.576% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 13,072 | 2.757% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 94.831% | 94.859% | 94.734% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 68.923% | 63.617% | 86.876% | Benchmark-only French Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene FrenchMinimalStemFilter | 11.472% | 6.236% | 29.192% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SnowballFilter | 8.551% | 5.183% | 19.952% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 8.462% | 5.067% | 19.952% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene FrenchLightStemFilter | 6.377% | 3.965% | 14.540% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `frenchRadixor` | 47.033 | 4.146 | 128.5 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 1664.935 | 65.928 | 4549.4 | 35.399 | Benchmark-only French Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene FrenchMinimalStemFilter | `frenchLuceneFrenchMinimalStemFilter` | 19.234 | 2.098 | 52.6 | 0.409 | Minimal French suffix reducer; narrow baseline. |
| Lucene FrenchLightStemFilter | `frenchLuceneFrenchLightStemFilter` | 30.560 | 3.680 | 83.5 | 0.650 | Light French suffix stemmer. |
| Official Snowball direct | `snowballDirect[FRENCH]` | 111.057 | 8.172 | 303.5 | 2.361 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[FRENCH]` | 123.648 | 3.500 | 337.9 | 2.629 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.
