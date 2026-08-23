# Italian Stemmer Benchmarks

This page reports same-language stemming benchmarks for Italian. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `it-it-default` | `1.0.0` | `IT_IT` | 10,009 | 337,546 | 20,004 | 317,542 | 317,542 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **337,546**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 302,089 | 89.496% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 12,348 | 3.658% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 20,013 | 5.929% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 3,096 | 0.917% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.056% | 98.997% | 100.000% | Radixor dictionary-trained patch-command stemmer. |
| Lucene ItalianLightStemFilter | 0.466% | 0.479% | 0.270% | Light suffix stemmer; intentionally narrower than Radixor's dictionary-trained transformation model. |
| Lucene SnowballFilter | 0.041% | 0.043% | 0.010% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 0.041% | 0.043% | 0.010% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `italianRadixor` | 22.089 | 0.429 | 69.6 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene ItalianLightStemFilter | `italianLuceneItalianLightStemFilter` | 14.913 | 0.384 | 47.0 | 0.675 | Light Italian suffix stemmer. |
| Official Snowball direct | `snowballDirect[ITALIAN]` | 108.166 | 3.729 | 340.6 | 4.897 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[ITALIAN]` | 115.064 | 4.496 | 362.4 | 5.209 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-trained patch-command stemmer. Its learned transformations can generalize beyond the word forms listed in the training resource.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `IT_IT` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `it-it-default`, loaded from classpath resource `org/egothor/stemmer/models/it-it-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.996651** among 4 deterministic stemmers. The runner-up is `SNOWBALL ITALIAN DIRECT` at 0.866290, a difference of 0.130361. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.996656** among 4 deterministic stemmers. The runner-up is `SNOWBALL ITALIAN DIRECT` at 0.866307, a difference of 0.130350. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.996651|0.000000%|0.669827%|
|2|SNOWBALL ITALIAN DIRECT|0.866290|0.000738%|26.741219%|
|3|SNOWBALL ITALIAN LUCENE FILTER|0.866290|0.000738%|26.741219%|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|0.508920|0.000005%|98.216094%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.993302|1.000000|0.996651|0.999999|0.000001|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.920474|0.732588|0.999993|0.866290|0.999961|0.000039|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.920474|0.732588|0.999993|0.866290|0.999961|0.000039|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.975468|0.017839|1.000000|0.508920|0.999885|0.000115|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998653|0.996640|0.994634|0.993302|0.996645|0.996645|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.875563|0.815854|0.763768|0.688980|0.821175|0.821157|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.875563|0.815854|0.763768|0.688980|0.821175|0.821157|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.083115|0.035037|0.022197|0.017831|0.131914|0.131907|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|6093034|0|41088|52600354673|0 / 52600354673|41088 / 6134122|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|4493783|388246|1640339|52599966427|388246 / 52600354673|1640339 / 6134122|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|4493783|388246|1640339|52599966427|388246 / 52600354673|1640339 / 6134122|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|109427|2752|6024695|52600351921|2752 / 52600354673|6024695 / 6134122|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.001304%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 52600354673|80 / 6134122|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.999993|0.000000%|0.001304%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|0.999987|1.000000|0.999993|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999997|0.999993|0.999990|0.999987|0.999993|0.999993|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|6134042|0|80|52600354673|0 / 52600354673|80 / 6134122|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|41008|0|0|3069|0.946153%|4|327552|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.996656|0.000000%|0.668702%|
|2|SNOWBALL ITALIAN DIRECT|0.866307|0.000738%|26.737902%|
|3|SNOWBALL ITALIAN LUCENE FILTER|0.866307|0.000738%|26.737902%|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|0.508920|0.000005%|98.216040%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.993313|1.000000|0.996656|0.999999|0.000001|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.920458|0.732621|0.999993|0.866307|0.999961|0.000039|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.920458|0.732621|0.999993|0.866307|0.999961|0.000039|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.975462|0.017840|1.000000|0.508920|0.999885|0.000115|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998655|0.996645|0.994643|0.993313|0.996651|0.996650|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.875561|0.815868|0.763794|0.689001|0.821186|0.821168|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.875561|0.815868|0.763794|0.689001|0.821186|0.821168|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.083118|0.035038|0.022198|0.017832|0.131916|0.131908|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|6091474|0|41008|52574085988|0 / 52574085988|41008 / 6132482|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|4492785|388246|1639697|52573697742|388246 / 52574085988|1639697 / 6132482|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|4492785|388246|1639697|52573697742|388246 / 52574085988|1639697 / 6132482|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|109401|2752|6023081|52574083236|2752 / 52574085988|6023081 / 6132482|

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
|Radixor|0 / 52574085988|0 / 6132482|

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
|1|Radixor|ALL_CANDIDATES|6132482|0|0|52574085988|0 / 52574085988|0 / 6132482|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|41008|0|0|3068|0.946081%|4|327469|

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
- Source SHA-256: `f15f8e653022e0333955b8b82f42944aa1c5a14a5ce54e628bb1a9c9aed42132`
- Evaluation command: `./gradlew stemmingQuality --no-daemon`
- Dictionary language: `IT_IT`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
