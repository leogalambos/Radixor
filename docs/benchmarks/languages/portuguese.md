# Portuguese Stemmer Benchmarks

This page reports same-language stemming benchmarks for Portuguese. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| `pt-pt-default` | `1.0.0` | `PT_PT` | 4,001 | 215,490 | 8,002 | 207,488 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **215,490**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 3,806 | 1.766% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 120,535 | 55.935% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 71,284 | 33.080% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 8,003 | 3.714% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 11,862 | 5.505% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.815% | 99.808% | 100.000% | Full Radixor dictionary patch-command stemmer. |
| Lucene PortugueseLightStemFilter | 8.966% | 5.558% | 97.326% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |
| Lucene PortugueseMinimalStemFilter | 5.539% | 1.896% | 100.000% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SnowballFilter | 0.625% | 0.558% | 2.374% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 0.625% | 0.558% | 2.374% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene PortugueseStemFilter | 0.312% | 0.308% | 0.425% | Portuguese RSLP-style Lucene TokenFilter stemmer. |




## Speed

Speed uses JMH average time, 5 warmup iterations, 10 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `portugueseRadixor` | 12.301 | 0.252 | 59.3 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene PortugueseLightStemFilter | `portugueseLucenePortugueseLightStemFilter` | 11.409 | 0.151 | 55.0 | 0.927 | Light Portuguese suffix stemmer. |
| Lucene PortugueseMinimalStemFilter | `portugueseLucenePortugueseMinimalStemFilter` | 15.619 | 0.084 | 75.3 | 1.270 | Minimal Portuguese suffix reducer. |
| Official Snowball direct | `snowballDirect[PORTUGUESE]` | 57.577 | 1.591 | 277.5 | 4.681 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[PORTUGUESE]` | 63.403 | 2.720 | 305.6 | 5.154 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |
| Lucene PortugueseStemFilter | `portugueseLucenePortugueseStemFilter` | 158.014 | 5.150 | 761.6 | 12.845 | Portuguese RSLP-style Lucene TokenFilter. |




## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `PT_PT` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `pt-pt-default`, loaded from classpath resource `org/egothor/stemmer/models/pt-pt-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.998542** among 6 deterministic stemmers. The runner-up is `SNOWBALL PORTUGUESE DIRECT` at 0.938922, a difference of 0.059620. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.998542** among 6 deterministic stemmers. The runner-up is `SNOWBALL PORTUGUESE DIRECT` at 0.938922, a difference of 0.059620. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **8 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.998542|0.000000%|0.291615%|
|2|SNOWBALL PORTUGUESE DIRECT|0.938922|0.000656%|12.214929%|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|0.938922|0.000656%|12.214929%|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|0.846554|0.000364%|30.688771%|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|0.513632|0.000006%|97.273598%|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|0.503949|&lt;0.000001%|99.210240%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.997084|1.000000|0.998542|0.999999|0.000001|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.970538|0.877851|0.999993|0.938922|0.999963|0.000037|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.970538|0.877851|0.999993|0.938922|0.999963|0.000037|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.979145|0.693112|0.999996|0.846554|0.999921|0.000079|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.991719|0.027264|1.000000|0.513632|0.999760|0.000240|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.999608|0.007898|1.000000|0.503949|0.999756|0.000244|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999415|0.998540|0.997666|0.997084|0.998541|0.998541|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.950467|0.921871|0.894944|0.855065|0.923032|0.923014|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.950467|0.921871|0.894944|0.855065|0.923032|0.923014|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.904492|0.811666|0.736120|0.683029|0.823807|0.823773|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.122815|0.053069|0.033847|0.027258|0.164433|0.164413|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.038278|0.015671|0.009853|0.007898|0.088851|0.088840|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|5470353|0|15999|22274113243|0 / 22274113243|15999 / 5486352|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|4816198|146201|670154|22273967042|146201 / 22274113243|670154 / 5486352|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|4816198|146201|670154|22273967042|146201 / 22274113243|670154 / 5486352|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|3802658|80995|1683694|22274032248|80995 / 22274113243|1683694 / 5486352|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|149580|1249|5336772|22274111994|1249 / 22274113243|5336772 / 5486352|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|43329|17|5443023|22274113226|17 / 22274113243|5443023 / 5486352|

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
|Radixor|0 / 22274113243|0 / 5486352|

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
|1|Radixor|ALL_CANDIDATES|5486352|0|0|22274113243|0 / 22274113243|0 / 5486352|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|15999|0|0|392|0.185702%|3|211489|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **8 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.998542|0.000000%|0.291615%|
|2|SNOWBALL PORTUGUESE DIRECT|0.938922|0.000656%|12.214929%|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|0.938922|0.000656%|12.214929%|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|0.846554|0.000364%|30.688771%|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|0.513632|0.000006%|97.273598%|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|0.503949|&lt;0.000001%|99.210240%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.997084|1.000000|0.998542|0.999999|0.000001|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.970538|0.877851|0.999993|0.938922|0.999963|0.000037|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.970538|0.877851|0.999993|0.938922|0.999963|0.000037|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.979145|0.693112|0.999996|0.846554|0.999921|0.000079|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.991719|0.027264|1.000000|0.513632|0.999760|0.000240|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.999608|0.007898|1.000000|0.503949|0.999756|0.000244|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999415|0.998540|0.997666|0.997084|0.998541|0.998541|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.950467|0.921871|0.894944|0.855065|0.923032|0.923014|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.950467|0.921871|0.894944|0.855065|0.923032|0.923014|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.904492|0.811666|0.736120|0.683029|0.823807|0.823773|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.122815|0.053069|0.033847|0.027258|0.164433|0.164413|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.038278|0.015671|0.009853|0.007898|0.088851|0.088840|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|5470353|0|15999|22274113243|0 / 22274113243|15999 / 5486352|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|4816198|146201|670154|22273967042|146201 / 22274113243|670154 / 5486352|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|4816198|146201|670154|22273967042|146201 / 22274113243|670154 / 5486352|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|3802658|80995|1683694|22274032248|80995 / 22274113243|1683694 / 5486352|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|149580|1249|5336772|22274111994|1249 / 22274113243|5336772 / 5486352|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|43329|17|5443023|22274113226|17 / 22274113243|5443023 / 5486352|

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
|Radixor|0 / 22274113243|0 / 5486352|

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
|1|Radixor|ALL_CANDIDATES|5486352|0|0|22274113243|0 / 22274113243|0 / 5486352|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|15999|0|0|392|0.185702%|3|211489|

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
- Source SHA-256: `edf16b07be8a535943ddf37caeb8807755c95e9e1fb13244145f28be74b491d8`
- Evaluation command: `./gradlew stemmingQuality --no-daemon`
- Dictionary language: `PT_PT`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
