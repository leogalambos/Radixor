# Swedish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Swedish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `sv-se-default` | `1.0.0` | `SV_SE` | 12,371 | 110,468 | 24,731 | 85,737 | 85,737 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **110,468**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 711 | 0.644% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 14,126 | 12.787% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 68,749 | 62.234% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 23,583 | 21.348% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 3,299 | 2.986% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 96.713% | 97.407% | 94.307% | Radixor dictionary-trained patch-command stemmer. |
| Lucene SwedishMinimalStemFilter | 49.532% | 49.186% | 50.730% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SwedishLightStemFilter | 45.672% | 46.383% | 43.209% | Light suffix stemmer; intentionally narrower than Radixor's dictionary-trained transformation model. |
| Official Snowball direct | 40.068% | 37.512% | 48.926% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene SnowballFilter | 38.785% | 35.839% | 48.999% | Lucene TokenFilter integration path around the Snowball algorithm. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 10 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `swedishRadixor` | 5.078 | 0.104 | 59.2 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene SwedishMinimalStemFilter | `swedishLuceneSwedishMinimalStemFilter` | 4.417 | 0.061 | 51.5 | 0.870 | Minimal Swedish suffix reducer. |
| Lucene SwedishLightStemFilter | `swedishLuceneSwedishLightStemFilter` | 5.090 | 0.373 | 59.4 | 1.002 | Light Swedish suffix stemmer. |
| Official Snowball direct | `snowballDirect[SWEDISH]` | 7.497 | 0.653 | 87.4 | 1.476 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[SWEDISH]` | 9.831 | 0.648 | 114.7 | 1.936 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-trained patch-command stemmer. Its learned transformations can generalize beyond the word forms listed in the training resource.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `SV_SE` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `sv-se-default`, loaded from classpath resource `org/egothor/stemmer/models/sv-se-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.977619** among 5 deterministic stemmers. The runner-up is `SNOWBALL SWEDISH DIRECT` at 0.808543, a difference of 0.169076. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.977573** among 5 deterministic stemmers. The runner-up is `SNOWBALL SWEDISH DIRECT` at 0.808611, a difference of 0.168961. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **7 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.977619|0.000000%|4.476263%|
|2|SNOWBALL SWEDISH DIRECT|0.808543|0.000821%|38.290570%|
|3|SNOWBALL SWEDISH LUCENE FILTER|0.800222|0.000775%|39.954747%|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|0.797907|0.000439%|40.418073%|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|0.785227|0.000534%|42.954113%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.955237|1.000000|0.977619|0.999996|0.000004|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.863080|0.617094|0.999992|0.808543|0.999960|0.000040|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.866630|0.600453|0.999992|0.800222|0.999959|0.000041|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.919176|0.595819|0.999996|0.797907|0.999962|0.000038|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.899588|0.570459|0.999995|0.785227|0.999959|0.000041|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990715|0.977106|0.963866|0.955237|0.977362|0.977361|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.799353|0.719647|0.654396|0.562070|0.729796|0.729777|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.796053|0.709394|0.639751|0.549660|0.721367|0.721348|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.829176|0.722989|0.640913|0.566158|0.740043|0.740026|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.806522|0.698179|0.615497|0.536309|0.716364|0.716347|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|362653|0|16994|4529284143|0 / 4529284143|16994 / 379647|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|234278|37166|145369|4529246977|37166 / 4529284143|145369 / 379647|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|227960|35082|151687|4529249061|35082 / 4529284143|151687 / 379647|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|226201|19890|153446|4529264253|19890 / 4529284143|153446 / 379647|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|216573|24174|163074|4529259969|24174 / 4529284143|163074 / 379647|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 4529284143|0 / 379647|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|379647|0|0|4529284143|0 / 4529284143|0 / 379647|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|16994|0|0|2840|2.983789%|5|98108|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **7 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.977573|0.000000%|4.485467%|
|2|SNOWBALL SWEDISH DIRECT|0.808611|0.000824%|38.276920%|
|3|SNOWBALL SWEDISH LUCENE FILTER|0.800274|0.000778%|39.944519%|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|0.797785|0.000441%|40.442582%|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|0.785141|0.000536%|42.971167%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.955145|1.000000|0.977573|0.999996|0.000004|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.862864|0.617231|0.999992|0.808611|0.999960|0.000040|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.866412|0.600555|0.999992|0.800274|0.999959|0.000041|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.918993|0.595574|0.999996|0.797785|0.999962|0.000038|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.899375|0.570288|0.999995|0.785141|0.999959|0.000041|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990695|0.977058|0.963791|0.955145|0.977315|0.977314|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.799250|0.719665|0.654494|0.562091|0.729785|0.729766|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.795941|0.709393|0.639820|0.549658|0.721337|0.721319|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.828962|0.722752|0.640668|0.565867|0.739816|0.739800|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.806317|0.697987|0.615318|0.536083|0.716172|0.716155|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|361874|0|16994|4507704713|0 / 4507704713|16994 / 378868|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|233849|37166|145019|4507667547|37166 / 4507704713|145019 / 378868|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|227531|35082|151337|4507669631|35082 / 4507704713|151337 / 378868|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|225644|19890|153224|4507684823|19890 / 4507704713|153224 / 378868|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|216064|24174|162804|4507680539|24174 / 4507704713|162804 / 378868|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 4507704713|0 / 378868|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|378868|0|0|4507704713|0 / 4507704713|0 / 378868|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|16994|0|0|2840|2.990922%|5|97881|

### Output Policies and Metric Definitions

Each distinct surface form is one item and may belong to several gold groups. Two forms are gold-related when their membership sets intersect; a relation shared by several groups is counted once. `PRIMARY_OUTPUT` uses one deterministic stem per form. `ANY_CANDIDATE` is an optimistic oracle-assisted pairwise upper bound: a gold-related pair succeeds when candidates intersect, while a gold-negative pair succeeds when a non-colliding selection exists. Candidate choices may differ between pairs, so this is not deterministic runtime behaviour and does not define one confusion matrix. `ALL_CANDIDATES` activates every returned candidate; forms are related when candidate sets intersect.

For `PRIMARY_OUTPUT` and `ALL_CANDIDATES`, `TP = underPossiblePairs - underErrorPairs`, `FN = underErrorPairs`, `FP = overErrorPairs`, and `TN = overPossiblePairs - overErrorPairs`. `ANY_CANDIDATE` publishes only its separate oracle-assisted under/over bounds; confusion-derived metrics are mathematically inapplicable and are not presented in its language-page section. Their machine-readable CSV fields remain empty. Undefined metric denominators in otherwise applicable policies are rendered as `n/a`.

- Under-stemming rate (Paice UI): `FN / (TP + FN)`, the false-negative rate over gold-related pairs.
- Over-stemming rate (Paice OI): `FP / (TN + FP)`, the false-positive rate over gold-negative pairs.
- Pairwise precision: `TP / (TP + FP)`, the fraction of predicted conflations that are gold-standard positive pairs.
- Pairwise recall: `TP / (TP + FN)`, the fraction of gold-standard positive pairs successfully connected.
- Pairwise specificity: `TN / (TN + FP)`, the fraction of gold-negative pairs correctly separated.
- Balanced accuracy: `(recall + specificity) / 2`. It gives equal weight to positive and negative pair classes and is less dominated by the large true-negative class than ordinary accuracy. It does not replace the raw errors or other metrics.
- Pairwise F-beta: `((1 + betaSquared) * TP) / (((1 + betaSquared) * TP) + (betaSquared * FN) + FP)`. F0.5 emphasizes precision and penalizes over-stemming more; F1 weights precision and recall equally; F2 emphasizes recall and penalizes under-stemming more.
- MCC: `(TP * TN - FP * FN) / sqrt((TP + FP) * (TP + FN) * (TN + FP) * (TN + FN))`. It uses all confusion counts and remains useful under class imbalance, except when its denominator is degenerate.
- Jaccard index: `TP / (TP + FP + FN)`.
- Fowlkes–Mallows index: `sqrt(precision * recall)`.
- Pairwise accuracy: `(TP + TN) / (TP + TN + FP + FN)`. It can be dominated by true-negative cross-group pairs.
- Pairwise error rate: `(FP + FN) / (TP + TN + FP + FN)`.

Standard ARI, homogeneity, completeness, V-measure, and NMI are not calculated: their usual contingency-table definitions require an exclusive gold partition, while this gold standard is an overlapping cover.

### Provenance

- Authoritative source: `docs/benchmarks/data/stemming-quality.csv`
- Source SHA-256: `d34f325da320a2e040b54d8d8b5c216d70448f08cfb8659a423e99882aa1afb5`
- Evaluation command: `./gradlew stemmingQuality --no-daemon`
- Dictionary language: `SV_SE`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
