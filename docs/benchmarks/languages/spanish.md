# Spanish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Spanish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `ES_ES` | 65,059 | 926,393 | 120,121 | 806,272 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **926,393**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 5,367 | 0.579% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 524,682 | 56.637% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 240,872 | 26.001% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 130,089 | 14.043% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 25,383 | 2.740% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 97.459% | 97.544% | 96.891% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 49.074% | 42.656% | 92.154% | Benchmark-only Spanish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene SpanishMinimalStemFilter | 17.284% | 5.347% | 97.403% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SpanishPluralStemFilter | 15.140% | 5.802% | 77.820% | Plural-focused suffix reducer; narrow baseline. |
| Lucene SpanishLightStemFilter | 9.577% | 7.088% | 26.279% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |
| Lucene SnowballFilter | 4.889% | 4.287% | 8.932% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 4.889% | 4.287% | 8.930% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `spanishRadixor` | 78.919 | 7.253 | 97.9 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 2079.041 | 193.548 | 2578.6 | 26.344 | Benchmark-only Spanish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene SpanishMinimalStemFilter | `spanishLuceneSpanishMinimalStemFilter` | 45.596 | 4.639 | 56.6 | 0.578 | Minimal Spanish suffix reducer; narrow baseline. |
| Lucene SpanishLightStemFilter | `spanishLuceneSpanishLightStemFilter` | 42.003 | 1.683 | 52.1 | 0.532 | Light Spanish suffix stemmer. |
| Lucene SpanishPluralStemFilter | `spanishLuceneSpanishPluralStemFilter` | 93.734 | 6.247 | 116.3 | 1.188 | Plural-oriented Spanish suffix reducer. |
| Official Snowball direct | `snowballDirect[SPANISH]` | 171.995 | 11.035 | 213.3 | 2.179 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[SPANISH]` | 211.138 | 17.940 | 261.9 | 2.675 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.
