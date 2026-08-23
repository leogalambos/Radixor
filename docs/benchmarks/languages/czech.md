# Czech Stemmer Benchmarks

This page reports same-language stemming benchmarks for Czech. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `cs-cz-default` | `1.0.0` | `CS_CZ` | 5,113 | 56,612 | 10,049 | 46,563 | 46,563 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **56,612**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 711 | 1.256% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 22,643 | 39.997% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 15,007 | 26.509% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 10,046 | 17.745% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 8,205 | 14.493% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.465% | 99.439% | 99.582% | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | 84.850% | 82.269% | 96.806% | Benchmark-only Czech Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene CzechStemFilter | 16.784% | 15.538% | 22.559% | Lucene Czech suffix stemmer implemented as a TokenFilter. |
| Official Snowball direct | 19.865% | 18.186% | 27.645% | Official Snowball 3.1.0 generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `czechRadixor` | 3.194 | 0.059 | 68.6 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 365.700 | 30.976 | 7853.9 | 114.487 | Benchmark-only Czech Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene CzechStemFilter | `czechLuceneCzechStemFilter` | 2.896 | 0.042 | 62.2 | 0.907 | Czech suffix stemmer implemented as a Lucene TokenFilter. |
| Official Snowball direct | `snowballDirect[CZECH]` | 3.966 | 0.451 | 85.2 | 1.242 | Official Snowball 3.1.0 generated Java stemmer; direct API. |

## Interpretation Notes

- Radixor is a dictionary-trained patch-command stemmer. Its learned transformations can generalize beyond the word forms listed in the training resource.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `CS_CZ` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `cs-cz-default`, loaded from classpath resource `org/egothor/stemmer/models/cs-cz-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.996617** among 4 deterministic stemmers. The runner-up is `HUNSPELL CZECH LUCENE FILTER` at 0.854132, a difference of 0.142485. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.997195** among 4 deterministic stemmers. The runner-up is `HUNSPELL CZECH LUCENE FILTER` at 0.853150, a difference of 0.144045. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **8 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.996617|0.000000%|0.676519%|
|2|HUNSPELL CZECH LUCENE FILTER|0.854132|0.000691%|29.172837%|
|3|CZECH LUCENE CZECH STEM FILTER|0.794343|0.000928%|41.130549%|
|4|SNOWBALL CZECH DIRECT|0.786366|0.000904%|42.725842%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.993235|1.000000|0.996617|0.999998|0.000002|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.958877|0.708272|0.999993|0.854132|0.999927|0.000073|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.935210|0.588695|0.999991|0.794343|0.999897|0.000103|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|0.935153|0.572742|0.999991|0.786366|0.999894|0.000106|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998640|0.996606|0.994581|0.993235|0.996612|0.996611|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.895506|0.814739|0.747335|0.687392|0.824103|0.824070|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.836710|0.722556|0.635811|0.565626|0.741992|0.741949|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|0.830101|0.710396|0.620864|0.550864|0.731848|0.731804|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|298476|0|2033|1320705191|0 / 1320705191|2033 / 300509|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|212842|9128|87667|1320696063|9128 / 1320705191|87667 / 300509|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|176908|12256|123601|1320692935|12256 / 1320705191|123601 / 300509|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|172114|11935|128395|1320693256|11935 / 1320705191|128395 / 300509|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.000000%|
|HUNSPELL CZECH LUCENE FILTER|0.000650%|25.611213%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 1320705191|0 / 300509|
|HUNSPELL CZECH LUCENE FILTER|8582 / 1320705191|76964 / 300509|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|HUNSPELL CZECH LUCENE FILTER|0.871940|0.000816%|25.611213%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.954016|0.743888|0.999992|0.871940|0.999934|0.000066|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.903001|0.835949|0.778167|0.718138|0.842426|0.842395|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|300509|0|0|1320705191|0 / 1320705191|0 / 300509|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|223545|10775|76964|1320694416|10775 / 1320705191|76964 / 300509|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|2033|0|0|321|0.624501%|4|51739|
|HUNSPELL CZECH LUCENE FILTER|10703|546|1647|3194|6.213887%|5|55179|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **8 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.997195|0.000000%|0.561033%|
|2|HUNSPELL CZECH LUCENE FILTER|0.853150|0.000700%|29.369351%|
|3|CZECH LUCENE CZECH STEM FILTER|0.792522|0.000918%|41.494586%|
|4|SNOWBALL CZECH DIRECT|0.784821|0.000923%|43.034822%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.994390|1.000000|0.997195|0.999999|0.000001|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.958957|0.706306|0.999993|0.853150|0.999925|0.000075|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.936557|0.585054|0.999991|0.792522|0.999895|0.000105|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|0.934577|0.569652|0.999991|0.784821|0.999891|0.000109|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998873|0.997187|0.995507|0.994390|0.997191|0.997190|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.894932|0.813466|0.745594|0.685581|0.822993|0.822960|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.836092|0.720206|0.632534|0.562751|0.740227|0.740184|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|0.828436|0.707849|0.617907|0.547807|0.729646|0.729601|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|295818|0|1669|1284770069|0 / 1284770069|1669 / 297487|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|210117|8993|87370|1284761076|8993 / 1284770069|87370 / 297487|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|174046|11790|123441|1284758279|11790 / 1284770069|123441 / 297487|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|169464|11863|128023|1284758206|11863 / 1284770069|128023 / 297487|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.000000%|
|HUNSPELL CZECH LUCENE FILTER|0.000663%|25.840457%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 1284770069|0 / 297487|
|HUNSPELL CZECH LUCENE FILTER|8518 / 1284770069|76872 / 297487|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|HUNSPELL CZECH LUCENE FILTER|0.870794|0.000819%|25.840457%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.954473|0.741595|0.999992|0.870794|0.999932|0.000068|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.902651|0.834675|0.776220|0.716259|0.841328|0.841297|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|297487|0|0|1284770069|0 / 1284770069|0 / 297487|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|220615|10523|76872|1284759546|10523 / 1284770069|76872 / 297487|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|1669|0|0|269|0.530603%|4|50975|
|HUNSPELL CZECH LUCENE FILTER|10498|475|1530|3117|6.148293%|5|54394|

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
- Dictionary language: `CS_CZ`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
