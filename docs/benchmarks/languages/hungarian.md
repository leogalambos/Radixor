# Hungarian Stemmer Benchmarks

This page reports same-language stemming benchmarks for Hungarian. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `hu-hu-default` | `1.0.0` | `HU_HU` | 19,406 | 935,713 | 38,775 | 896,938 | 896,938 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **935,713**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 15 | 0.002% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 149,173 | 15.942% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 750,282 | 80.183% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 36,139 | 3.862% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 104 | 0.011% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.222% | 99.537% | 91.948% | Radixor dictionary-trained patch-command stemmer. |
| Lucene SnowballFilter | 66.445% | 66.938% | 55.043% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 66.445% | 66.938% | 55.043% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene HungarianLightStemFilter | 14.748% | 14.777% | 14.086% | Light suffix stemmer; intentionally narrower than Radixor's dictionary-trained transformation model. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `hungarianRadixor` | 51.864 | 1.193 | 57.8 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene HungarianLightStemFilter | `hungarianLuceneHungarianLightStemFilter` | 90.099 | 5.756 | 100.5 | 1.737 | Light Hungarian suffix stemmer. |
| Official Snowball direct | `snowballDirect[HUNGARIAN]` | 161.436 | 10.993 | 180.0 | 3.113 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[HUNGARIAN]` | 179.732 | 7.331 | 200.4 | 3.465 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-trained patch-command stemmer. Its learned transformations can generalize beyond the word forms listed in the training resource.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `HU_HU` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `hu-hu-default`, loaded from classpath resource `org/egothor/stemmer/models/hu-hu-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.995555** among 4 deterministic stemmers. The runner-up is `SNOWBALL HUNGARIAN LUCENE FILTER` at 0.822963, a difference of 0.172592. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.996227** among 4 deterministic stemmers. The runner-up is `SNOWBALL HUNGARIAN DIRECT` at 0.822077, a difference of 0.174151. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.995555|&lt;0.000001%|0.889037%|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|0.822963|0.000378%|35.407050%|
|3|SNOWBALL HUNGARIAN DIRECT|0.822704|0.000309%|35.458800%|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|0.816967|0.000915%|36.605593%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999998|0.991110|1.000000|0.995555|1.000000|0.000000|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.901236|0.645929|0.999996|0.822963|0.999977|0.000023|
|3|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.917622|0.645412|0.999997|0.822704|0.999978|0.000022|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.786953|0.633944|0.999991|0.816967|0.999971|0.000029|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998208|0.995534|0.992875|0.991108|0.995544|0.995544|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.835212|0.752518|0.684724|0.603229|0.762978|0.762967|
|3|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.846240|0.757814|0.686119|0.610064|0.769574|0.769564|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.750715|0.702210|0.659593|0.541082|0.706318|0.706304|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|21921219|39|196636|414653743434|39 / 414653743473|196636 / 22117855|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|14286575|1565633|7831280|414652177840|1565633 / 414653743473|7831280 / 22117855|
|3|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|14275129|1281527|7842726|414652461946|1281527 / 414653743473|7842726 / 22117855|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|14021483|3795942|8096372|414649947531|3795942 / 414653743473|8096372 / 22117855|

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
|Radixor|0 / 414653743473|0 / 22117855|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|&lt;0.000001%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999991|1.000000|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999993|0.999996|0.999998|0.999991|0.999996|0.999996|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|22117855|192|0|414653743281|192 / 414653743473|0 / 22117855|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|196636|39|153|6664|0.731754%|5|917595|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.996227|&lt;0.000001%|0.754564%|
|2|SNOWBALL HUNGARIAN DIRECT|0.822077|0.000334%|35.584346%|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|0.822077|0.000334%|35.584346%|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|0.815385|0.000869%|36.922109%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999998|0.992454|1.000000|0.996227|1.000000|0.000000|
|2|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.915319|0.644157|0.999997|0.822077|0.999977|0.000023|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.915319|0.644157|0.999997|0.822077|0.999977|0.000023|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.802756|0.630779|0.999991|0.815385|0.999971|0.000029|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998480|0.996212|0.993954|0.992453|0.996219|0.996219|
|2|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.844241|0.756163|0.684726|0.607928|0.767860|0.767849|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.844241|0.756163|0.684726|0.607928|0.767860|0.767849|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.761246|0.706452|0.659016|0.546135|0.711591|0.711577|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|21206087|39|161230|380936197647|39 / 380936197686|161230 / 21367317|
|2|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|13763897|1273370|7603420|380934924316|1273370 / 380936197686|7603420 / 21367317|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|13763897|1273370|7603420|380934924316|1273370 / 380936197686|7603420 / 21367317|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|13478053|3311675|7889264|380932886011|3311675 / 380936197686|7889264 / 21367317|

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
|Radixor|0 / 380936197686|0 / 21367317|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|&lt;0.000001%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999991|1.000000|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999993|0.999996|0.999998|0.999991|0.999996|0.999996|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|21367317|192|0|380936197494|192 / 380936197686|0 / 21367317|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|161230|39|153|5518|0.632162%|5|878574|

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
- Dictionary language: `HU_HU`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
