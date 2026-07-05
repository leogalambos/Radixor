# German Stemmer Benchmarks

This page reports same-language stemming benchmarks for German. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `DE_DE` | 39,315 | 213,440 | 73,799 | 139,641 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **213,440**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 3,627 | 1.699% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 48,605 | 22.772% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 80,443 | 37.689% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 75,717 | 35.475% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 5,048 | 2.365% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 92.725% | 92.847% | 92.396% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 47.064% | 29.661% | 93.678% | Benchmark-only German Hunspell dictionary compared via Lucene HunspellStemFilter. |
| CISTEM (German) | 24.675% | 23.724% | 27.222% | Benchmark-only CISTEM implementation. |
| Lucene GermanLightStemFilter | 37.434% | 35.465% | 42.707% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |
| Lucene GermanMinimalStemFilter | 27.640% | 24.951% | 34.844% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SnowballFilter | 30.956% | 28.853% | 36.589% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 30.481% | 29.027% | 34.376% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene GermanStemFilter | 21.559% | 19.312% | 27.576% | German Lucene stemming TokenFilter; broader than minimal/light variants. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `germanRadixor` | 41.166 | 2.396 | 294.8 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| CISTEM | `germanCistem` | 248.392 | 12.294 | 1778.8 | 6.034 | Benchmark-only CISTEM implementation. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 281.322 | 3.411 | 2014.6 | 6.834 | Benchmark-only German Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene GermanMinimalStemFilter | `germanLuceneGermanMinimalStemFilter` | 23.562 | 0.969 | 168.7 | 0.572 | Minimal German suffix reduction; narrow baseline. |
| Lucene GermanLightStemFilter | `germanLuceneGermanLightStemFilter` | 24.410 | 1.034 | 174.8 | 0.593 | Light German suffix stemmer; narrower than a dictionary stemmer. |
| Lucene GermanStemFilter | `germanLuceneGermanStemFilter` | 71.039 | 4.443 | 508.7 | 1.726 | Older German stemming TokenFilter with normalization requirements. |
| Lucene SnowballFilter | `luceneSnowballFilter[GERMAN]` | 105.771 | 9.617 | 757.4 | 2.569 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |
| Official Snowball direct | `snowballDirect[GERMAN]` | 100.688 | 9.018 | 721.0 | 2.446 | Official Snowball generated Java stemmer; direct API. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.
