# Ukrainian Stemmer Benchmarks

This page reports same-language stemming benchmarks for Ukrainian. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `UK_UA` | 1,493 | 15,737 | 2,985 | 12,752 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **15,737**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 249 | 1.582% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 4,160 | 26.435% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 5,859 | 37.231% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 3,004 | 19.089% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 2,465 | 15.664% |

## Accuracy

Accuracy is computed from one deterministic JMH measurement iteration without warmup. The benchmark may execute the full dictionary pass more than once inside that single timed iteration; percentages divide matching counters by evaluated counters from the same iteration.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.307% | 99.365% | 99.062% | Full Radixor dictionary patch-command stemmer. |
| Lucene MorfologikFilter | 92.362% | 90.637% | 99.732% | Dictionary-based path; Morfologik can emit multiple terms. |
| Morfologik direct | 92.362% | 90.637% | 99.732% | Direct dictionary lookup; first returned stem is used for quality when no ranking weight is exposed. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `ukrainianRadixor` | 0.605 | 0.056 | 47.4 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Morfologik direct | `ukrainianMorfologikDirect` | 8.106 | 0.040 | 635.7 | 13.408 | Direct Morfologik dictionary lookup; first returned stem is used for quality. |
| Lucene MorfologikFilter | `ukrainianLuceneMorfologikFilter` | 14.684 | 5.214 | 1151.5 | 24.287 | Dictionary-based Morfologik TokenFilter; may emit multiple terms. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.
