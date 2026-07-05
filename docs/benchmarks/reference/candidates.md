# Benchmark Candidate Evaluation

Implemented benchmark methods are documented on the per-language pages under [Language Benchmark Pages](../languages/index.md). This keeps the exact method names, implementation descriptions, accuracy tables, and speed tables close to the language where they are valid.

## Included Candidate Families

The current benchmark pages include Radixor baselines, Lucene language filters where the language matches a bundled Radixor resource, Lucene Stempel and Morfologik paths where applicable, official Snowball Java stemmers where same-language comparison is available, benchmark-only CISTEM German stemmer evaluation, benchmark-only Hunspell comparisons, and selected English-specific non-Lucene baselines such as OpenNLP Porter and Paice/Husk Lancaster.

Benchmark-only Hunspell comparisons use bundled benchmark dictionaries and the Lucene HunspellStemFilter adapter over the selected language token streams.
The CISTEM candidate is implemented in `src/jmh/java/org/egothor/stemmer/benchmark/Cistem.java` and follows the original MIT-licensed upstream implementation from Leonie Weissweiler's CISTEM project.
CISTEM German gold-standard files are not vendored in this repository. The Gradle JMH resource preparation tasks download `goldstandard1.txt` and `goldstandard2.txt` from the upstream CISTEM repository into generated build resources.

Direct stemmer APIs and Lucene TokenFilter paths are documented separately on language pages. TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.

For the benchmark refresh used in this documentation build:

- Hunspell families are included in `HunspellStemmerComparisonBenchmark` (speed) and `HunspellStemmerComparisonBenchmarkQuality` (quality for all benchmark languages in this corpus). The legacy
  `EnglishHunspellStemmerComparisonBenchmarkQuality` result is retained for continuity.
- CISTEM quality is present in the published per-language results under `GERMAN_CISTEM`. CISTEM speed is present as `germanCistem` in `MultiLanguageStemmerComparisonBenchmark`.

## Evaluated But Skipped Candidates

| Candidate | Language | Link/source | Reason skipped |
| --- | --- | --- | --- |
| Lucene Arabic, Bulgarian, Bengali, Sorani, Greek, Galician, Hindi, Indonesian, Latvian, Telugu filters | Various | `lucene-analysis-common` | No bundled same-language Radixor resource in this repository snapshot. |
| Lucene analyzer-only paths | Multiple | Lucene analyzers | Full analyzers mix tokenization, stop-word handling, and other behavior; direct filters are used where available. |
| Lucene StemmerOverrideFilter | Multiple | `lucene-analysis-common` | Override map facility, not a stemmer algorithm. |
| Additional Snowball Lovins | English | Official Snowball Java distribution | No Lovins Java stemmer was present in the selected Snowball Java distribution. |
| Lemur Project Krovetz Stemmer | English | Lemur Project | Lucene KStem represents the Krovetz-style path without adding separate dependency and license risk. |
| Smile Lancaster / Paice-Husk | English | Smile NLP | Smile is large for one stemmer; Paice/Husk is included through a smaller benchmark-only generated path. |
| `stemmerEval` reference repository | Multiple | `https://github.com/endredy/stemmerEval` | Used only as a candidate reference; no code or data copied. |
