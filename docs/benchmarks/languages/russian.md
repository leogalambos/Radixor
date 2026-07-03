# Russian Stemmer Benchmarks

This page reports same-language stemming benchmarks for Russian. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `RU_RU` | 37,410 | 806,279 | 74,808 | 731,471 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **806,279**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 9,260 | 1.148% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 584,785 | 72.529% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 82,864 | 10.277% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 75,646 | 9.382% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 53,724 | 6.663% |

## Accuracy

Accuracy is computed from one deterministic JMH measurement iteration without warmup. The benchmark may execute the full dictionary pass more than once inside that single timed iteration; percentages divide matching counters by evaluated counters from the same iteration.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 98.807% | 98.696% | 99.896% | Full Radixor dictionary patch-command stemmer. |
| Lucene RussianLightStemFilter | 9.658% | 8.452% | 21.447% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |
| Lucene SnowballFilter | 9.162% | 8.162% | 18.936% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 9.162% | 8.162% | 18.936% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `russianRadixor` | 72.970 | 15.642 | 99.8 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene RussianLightStemFilter | `russianLuceneRussianLightStemFilter` | 57.900 | 4.404 | 79.2 | 0.793 | Light Russian suffix stemmer. |
| Official Snowball direct | `snowballDirect[RUSSIAN]` | 99.019 | 14.688 | 135.4 | 1.357 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[RUSSIAN]` | 128.272 | 7.201 | 175.4 | 1.758 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.
