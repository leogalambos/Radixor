# Benchmark Candidate Evaluation

Implemented benchmark methods are documented on the per-language pages under [Language Benchmark Pages](../languages/index.md). This keeps the exact method names, implementation descriptions, accuracy tables, and speed tables close to the language where they are valid.

## Included Candidate Families

The current benchmark pages include Radixor baselines, Lucene language filters where the language matches a bundled Radixor resource, Lucene Stempel and Morfologik paths where applicable, official Snowball Java stemmers where same-language comparison is available, and selected English-specific non-Lucene baselines such as OpenNLP Porter and Paice/Husk Lancaster.

Direct stemmer APIs and Lucene TokenFilter paths are documented separately on language pages. TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.

## Evaluated But Skipped Candidates

| Candidate | Language | Link/source | Reason skipped |
| --- | --- | --- | --- |
| Lucene Arabic, Bulgarian, Bengali, Sorani, Greek, Galician, Hindi, Indonesian, Latvian, Telugu filters | Various | `lucene-analysis-common` | No bundled same-language Radixor resource in this repository snapshot. |
| Lucene analyzer-only paths | Multiple | Lucene analyzers | Full analyzers mix tokenization, stop-word handling, and other behavior; direct filters are used where available. |
| Lucene HunspellStemFilter | Multiple | `lucene-analysis-common` | Requires external Hunspell dictionaries not resolved as benchmark-only resources here. |
| Lucene StemmerOverrideFilter | Multiple | `lucene-analysis-common` | Override map facility, not a stemmer algorithm. |
| Additional Snowball Lovins | English | Official Snowball Java distribution | No Lovins Java stemmer was present in the selected Snowball Java distribution. |
| Lemur Project Krovetz Stemmer | English | Lemur Project | Lucene KStem represents the Krovetz-style path without adding separate dependency and license risk. |
| Smile Lancaster / Paice-Husk | English | Smile NLP | Smile is large for one stemmer; Paice/Husk is included through a smaller benchmark-only generated path. |
| CISTEM German stemmer | German | `https://github.com/LeonieWeissweiler/CISTEM` | Clean benchmark-only Java integration was not completed in this phase. |
| `stemmerEval` reference repository | Multiple | `https://github.com/endredy/stemmerEval` | Used only as a candidate reference; no code or data copied. |
