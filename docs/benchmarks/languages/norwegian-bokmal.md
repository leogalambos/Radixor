# Norwegian Bokmal Stemmer Benchmarks

This page reports same-language stemming benchmarks for Norwegian Bokmal. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `nb-no-default` | `1.0.0` | `NB_NO` | 17,929 | 90,757 | 33,376 | 57,381 | 57,381 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **90,757**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 2,528 | 2.785% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 4,258 | 4.692% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 48,925 | 53.908% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 32,086 | 35.354% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 2,960 | 3.261% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 96.852% | 97.637% | 95.503% | Radixor dictionary-trained patch-command stemmer. |
| Lucene NorwegianMinimalStemFilter | 57.107% | 53.913% | 62.599% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Official Snowball direct | 54.824% | 51.791% | 60.040% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene SnowballFilter | 54.803% | 51.780% | 60.001% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Lucene NorwegianLightStemFilter | 52.136% | 50.616% | 54.749% | Light suffix stemmer; intentionally narrower than Radixor's dictionary-trained transformation model. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `norwegianBokmalRadixor` | 3.237 | 0.059 | 56.4 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene NorwegianMinimalStemFilter | `norwegianBokmalLuceneNorwegianMinimalStemFilter` | 2.763 | 0.054 | 48.2 | 0.854 | Minimal Norwegian suffix reducer. |
| Lucene NorwegianLightStemFilter | `norwegianBokmalLuceneNorwegianLightStemFilter` | 3.139 | 0.050 | 54.7 | 0.970 | Light Norwegian suffix stemmer. |
| Official Snowball direct | `snowballDirect[NORWEGIAN_BOKMAL]` | 4.788 | 0.521 | 83.4 | 1.479 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[NORWEGIAN_BOKMAL]` | 5.721 | 0.314 | 99.7 | 1.767 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-trained patch-command stemmer. Its learned transformations can generalize beyond the word forms listed in the training resource.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `NB_NO` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `nb-no-default`, loaded from classpath resource `org/egothor/stemmer/models/nb-no-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.976021** among 5 deterministic stemmers. The runner-up is `SNOWBALL NORWEGIAN BOKMAL DIRECT` at 0.874259, a difference of 0.101762. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.976240** among 5 deterministic stemmers. The runner-up is `SNOWBALL NORWEGIAN BOKMAL DIRECT` at 0.874286, a difference of 0.101954. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **7 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.976021|0.000000%|4.795770%|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|0.874259|0.000386%|25.147805%|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|0.874138|0.000389%|25.171937%|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|0.849389|0.000416%|30.121722%|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|0.831282|0.000110%|33.743568%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.952042|1.000000|0.976021|0.999997|0.000003|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.910734|0.748522|0.999996|0.874259|0.999983|0.000017|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.910189|0.748281|0.999996|0.874138|0.999983|0.000017|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.898501|0.698783|0.999996|0.849389|0.999980|0.000020|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.969387|0.662564|0.999999|0.831282|0.999981|0.000019|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990026|0.975432|0.961262|0.952042|0.975727|0.975725|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.872901|0.821699|0.776171|0.697359|0.825654|0.825646|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.872435|0.821332|0.775884|0.696830|0.825274|0.825266|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.849918|0.786156|0.731293|0.647658|0.792374|0.792365|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.887216|0.787133|0.707341|0.648985|0.801425|0.801417|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|134138|0|6757|2676746970|0 / 2676746970|6757 / 140895|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|105463|10337|35432|2676736633|10337 / 2676746970|35432 / 140895|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|105429|10403|35466|2676736567|10403 / 2676746970|35466 / 140895|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|98455|11122|42440|2676735848|11122 / 2676746970|42440 / 140895|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|93352|2948|47543|2676744022|2948 / 2676746970|47543 / 140895|

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
|Radixor|0 / 2676746970|0 / 140895|

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
|1|Radixor|ALL_CANDIDATES|140895|0|0|2676746970|0 / 2676746970|0 / 140895|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|6757|0|0|2097|2.865929%|9|75343|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **7 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.976240|0.000000%|4.751928%|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|0.874286|0.000387%|25.142395%|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|0.874101|0.000387%|25.179325%|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|0.849330|0.000414%|30.133659%|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|0.831210|0.000108%|33.757794%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.952481|1.000000|0.976240|0.999997|0.000003|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.910689|0.748576|0.999996|0.874286|0.999983|0.000017|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.910546|0.748207|0.999996|0.874101|0.999983|0.000017|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.898862|0.698663|0.999996|0.849330|0.999980|0.000020|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.969896|0.662422|0.999999|0.831210|0.999981|0.000019|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990121|0.975662|0.961620|0.952481|0.975951|0.975950|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.872882|0.821713|0.776211|0.697379|0.825663|0.825655|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.872677|0.821432|0.775872|0.696975|0.825395|0.825387|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.850142|0.786219|0.731236|0.647743|0.792466|0.792457|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.887506|0.787200|0.707265|0.649077|0.801549|0.801541|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|134115|0|6691|2672431799|0 / 2672431799|6691 / 140806|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|105404|10337|35402|2672421462|10337 / 2672431799|35402 / 140806|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|105352|10350|35454|2672421449|10350 / 2672431799|35454 / 140806|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|98376|11069|42430|2672420730|11069 / 2672431799|42430 / 140806|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|93273|2895|47533|2672428904|2895 / 2672431799|47533 / 140806|

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
|Radixor|0 / 2672431799|0 / 140806|

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
|1|Radixor|ALL_CANDIDATES|140806|0|0|2672431799|0 / 2672431799|0 / 140806|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|6691|0|0|2064|2.823105%|9|75251|

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
- Dictionary language: `NB_NO`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
