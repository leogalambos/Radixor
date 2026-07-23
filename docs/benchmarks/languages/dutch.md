# Dutch Stemmer Benchmarks

This page reports same-language stemming benchmarks for Dutch. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| `nl-nl-default` | `1.0.0` | `NL_NL` | 4,992 | 31,466 | 9,981 | 21,485 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **31,466**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 2,107 | 6.696% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 11,484 | 36.497% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 7,732 | 24.573% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 10,127 | 32.184% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 16 | 0.051% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.120% | 98.711% | 100.000% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 46.590% | 22.718% | 97.976% | Benchmark-only Dutch Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Official Snowball direct | 15.954% | 8.992% | 30.939% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene SnowballFilter | 12.620% | 5.441% | 28.073% | Lucene TokenFilter integration path around the Snowball algorithm. |






## Speed

Speed uses JMH average time, 5 warmup iterations, 10 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `radixor[DUTCH]` | 1.410 | 0.139 | 65.6 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 24.183 | 2.889 | 1125.6 | 17.156 | Benchmark-only Dutch Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Official Snowball direct | `snowballDirect[DUTCH]` | 4.560 | 0.205 | 212.2 | 3.235 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[DUTCH]` | 7.762 | 0.262 | 361.3 | 5.506 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |






## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `NL_NL` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `nl-nl-default`, loaded from classpath resource `org/egothor/stemmer/models/nl-nl-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.988733** among 4 deterministic stemmers. The runner-up is `SNOWBALL DUTCH DIRECT` at 0.727093, a difference of 0.261640. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.989114** among 4 deterministic stemmers. The runner-up is `SNOWBALL DUTCH DIRECT` at 0.730509, a difference of 0.258605. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **8 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.988733|0.000000%|2.253364%|
|2|SNOWBALL DUTCH DIRECT|0.727093|0.000870%|54.580443%|
|3|HUNSPELL DUTCH LUCENE FILTER|0.642844|0.000104%|71.431010%|
|4|SNOWBALL DUTCH LUCENE FILTER|0.617975|0.000221%|76.404861%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.977466|1.000000|0.988733|0.999996|0.000004|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.907391|0.454196|0.999991|0.727093|0.999889|0.000111|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.981029|0.285690|0.999999|0.642844|0.999865|0.000135|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.952453|0.235951|0.999998|0.617975|0.999854|0.000146|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.995411|0.988605|0.981891|0.977466|0.988669|0.988667|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.756437|0.605372|0.504600|0.434074|0.641976|0.641934|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.659835|0.442513|0.332878|0.284120|0.529405|0.529368|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.592568|0.378209|0.277738|0.233204|0.474060|0.474022|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|62985|0|1452|343168663|0 / 343168663|1452 / 64437|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|29267|2987|35170|343165676|2987 / 343168663|35170 / 64437|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|18409|356|46028|343168307|356 / 343168663|46028 / 64437|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|15204|759|49233|343167904|759 / 343168663|49233 / 64437|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL DUTCH LUCENE FILTER|0.000096%|66.975495%|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL DUTCH LUCENE FILTER|330 / 343168663|43157 / 64437|
|Radixor|0 / 343168663|0 / 64437|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|HUNSPELL DUTCH LUCENE FILTER|0.665122|0.000147%|66.975495%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|0.976909|0.330245|0.999999|0.665122|0.999873|0.000127|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|0.701991|0.493621|0.380638|0.327687|0.567996|0.567958|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|64437|0|0|343168663|0 / 343168663|0 / 64437|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|21280|503|43157|343168160|503 / 343168663|43157 / 64437|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL DUTCH LUCENE FILTER|2871|26|147|1199|4.576161%|3|27429|
|Radixor|1452|0|0|296|1.129728%|3|26501|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **8 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.989114|0.000000%|2.177156%|
|2|SNOWBALL DUTCH DIRECT|0.730509|0.000926%|53.897299%|
|3|HUNSPELL DUTCH LUCENE FILTER|0.644879|0.000103%|71.024152%|
|4|SNOWBALL DUTCH LUCENE FILTER|0.618013|0.000222%|76.397220%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.978228|1.000000|0.989114|0.999996|0.000004|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.906773|0.461027|0.999991|0.730509|0.999885|0.000115|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.982090|0.289758|0.999999|0.644879|0.999860|0.000140|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.954134|0.236028|0.999998|0.618013|0.999849|0.000151|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.995569|0.988994|0.982507|0.978228|0.989054|0.989052|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.759842|0.611269|0.511295|0.440164|0.646565|0.646521|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.664532|0.447489|0.337317|0.288235|0.533450|0.533411|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.593185|0.378440|0.277851|0.233380|0.474555|0.474515|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|61646|0|1372|322555083|0 / 322555083|1372 / 63018|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|29053|2987|33965|322552096|2987 / 322555083|33965 / 63018|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|18260|333|44758|322554750|333 / 322555083|44758 / 63018|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|14874|715|48144|322554368|715 / 322555083|48144 / 63018|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL DUTCH LUCENE FILTER|0.000095%|66.488940%|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL DUTCH LUCENE FILTER|307 / 322555083|41900 / 63018|
|Radixor|0 / 322555083|0 / 63018|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|HUNSPELL DUTCH LUCENE FILTER|0.667555|0.000148%|66.488940%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|0.977912|0.335111|0.999999|0.667555|0.999869|0.000131|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|0.706770|0.499167|0.385834|0.332593|0.572458|0.572419|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|63018|0|0|322555083|0 / 322555083|0 / 63018|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|21118|477|41900|322554606|477 / 322555083|41900 / 63018|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL DUTCH LUCENE FILTER|2858|26|144|1131|4.452405%|3|26562|
|Radixor|1372|0|0|273|1.074719%|3|25679|

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
- Dictionary language: `NL_NL`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
