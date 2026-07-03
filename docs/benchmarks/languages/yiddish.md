# Yiddish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Yiddish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `YI` | 802 | 4,300 | 1,524 | 2,776 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **4,300**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `DeletePrefixCommand` | Deletes one or more leading characters from the word form in forward traversal. | 25 | 0.581% |
| `ForwardCompoundCommand` | Applies a multi-step forward patch made from skip, delete, insert, and replace operations. | 2,721 | 63.279% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 1,551 | 36.070% |
| `ReplaceFirstCharacterCommand` | Replaces the first character of the word form in forward traversal. | 3 | 0.070% |

## Accuracy

Accuracy is computed from one deterministic JMH measurement iteration without warmup. The benchmark may execute the full dictionary pass more than once inside that single timed iteration; percentages divide matching counters by evaluated counters from the same iteration.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 98.930% | 98.343% | 100.000% | Radixor baseline in the Snowball-language comparison family. |
| Lucene SnowballFilter | 2.837% | 2.558% | 3.346% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 2.837% | 2.558% | 3.346% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `radixor[YIDDISH]` | 0.236 | 0.004 | 85.1 | 1.000 | Radixor baseline for the Snowball-language comparison family. |
| Official Snowball direct | `snowballDirect[YIDDISH]` | 1.432 | 0.193 | 515.7 | 6.058 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[YIDDISH]` | 1.595 | 0.068 | 574.6 | 6.749 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.
