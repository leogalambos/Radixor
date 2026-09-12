# Lithuanian Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 28,889 distinct usable word forms" title="Relative dictionary size 4 of 5; 28,889 distinct usable word forms">★★★★☆</span>

This page reports dictionary corpus, exact-root agreement, and runtime evidence for the independently available `lt-lt-default` Lithuanian model.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 28,889 distinct usable word forms" title="Relative dictionary size 4 of 5; 28,889 distinct usable word forms">★★★★☆</span>. The exact count is **28,889 distinct usable word forms** after parser-compatible filtering and exact, case-preserved deduplication. Stars rank dictionary size relative to all benchmarked dictionaries in five nearly equal groups; they do **not** measure linguistic quality or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The language metadata declares left-to-right writing.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-09-11 Radixor/Java `4.4.0` snapshot. Speed benchmark operations process changed tokens. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). Dictionary-size stars are contextual metadata, not an accuracy result.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Distinct usable forms | Complete quality tokens | Already-root tokens | Changed tokens | Timing workload | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `lt-lt-default` | `1.0.0` | `LT_LT` | 1,391 | 28,889 | 28,998 | 1,391 | 27,607 | changed tokens | 27,607 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **28,998**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 101 | 0.348% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 25,472 | 87.841% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 643 | 2.217% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 1,392 | 4.800% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 1,390 | 4.793% |

## Accuracy

Accuracy uses the complete dictionary and reports exact agreement with the dictionary root for each identified model and candidate.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.624% | 99.605% | 100.000% | Exact model-ID benchmark; measured in this snapshot. |
| Official Snowball direct (Java) | 0.003% | 0.000% | 0.072% | Official Snowball 3.1.0 generated Java stemmer; measured in this snapshot. |
| Lucene SnowballFilter | 0.003% | 0.000% | 0.072% | Lucene integration of the matching Snowball algorithm; measured in this snapshot. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 3 independent forks, and 1 thread.

The canonical timing workload prefers changed tokens are preferred; a root-only dictionary uses its complete root-preservation corpus. Smaller populations are repeated deterministically to the timing minimum. Relative factors use the Radixor row as the baseline.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `radixor[lt-lt-default]` | 3.552 | 0.460 | 128.7 | 1.000 | Canonical model timing workload; measured in this snapshot. |
| Official Snowball direct (Java) | `snowballDirect[LITHUANIAN]` | 4.243 | 0.345 | 153.7 | 1.194 | Official generated Java stemmer; measured in this snapshot. The Radixor and Snowball 99.9% JMH intervals overlap; statistically tied, so the runtime ratio is a point estimate only. |
| Lucene SnowballFilter | `luceneSnowballFilter[LITHUANIAN]` | 5.523 | 0.556 | 200.1 | 1.555 | Lucene TokenStream integration; measured in this snapshot. |

## Interpretation Notes

- The star tier reflects only relative distinct-form count among all benchmarked dictionaries.
- Values shown above come from the identified canonical benchmark snapshot.
- Runtime and exact-root agreement describe different properties and must be interpreted together.

<!-- DICTIONARY-GENERALIZATION:START -->

## Dictionary-Family Generalization Conclusion

This is the language-specific conclusion from the independent `radixor-generalization-v1` baseline
experiment. It is intentionally separate from the wider edit-cost protocol below; values from
the two frozen snapshots are not substituted for one another.

### Evidence

Model `lt-lt-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 26,154 | 41.955% (39.757–46.002) | 39.455% (37.165–43.747) | 90.895% (90.415–91.693) |
| 20% | 23,192 | 50.338% (47.680–52.416) | 48.199% (45.387–50.333) | 93.172% (91.105–93.980) |
| 30% | 20,211 | 51.961% (49.300–54.333) | 49.943% (47.092–52.387) | 92.916% (92.189–93.737) |
| 40% | 17,342 | 54.480% (50.707–55.714) | 52.544% (48.617–53.826) | 92.934% (92.695–93.533) |
| 50% | 14,406 | 54.463% (52.847–55.810) | 52.556% (50.779–53.867) | 92.374% (91.655–93.813) |
| 60% | 11,423 | 54.959% (53.373–55.165) | 53.023% (51.393–53.172) | 92.973% (92.266–93.705) |
| 70% | 8,816 | 55.172% (53.005–56.545) | 53.233% (51.076–54.632) | 93.046% (92.326–94.245) |
| 80% | 5,924 | 56.555% (52.829–58.980) | 54.788% (50.959–57.262) | 93.165% (92.446–93.885) |
| 90% | 3,098 | 52.408% (48.059–60.658) | 50.780% (45.635–59.213) | 92.806% (89.928–94.245) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **39.455%** at 10% training knowledge to **50.780%** at 90%, a measured **+11.326 percentage-point** change.
- Unseen all-form exactness moves from **41.955%** at 10% training knowledge to **52.408%** at 90%, a measured **+10.453 percentage-point** change.
- Preservation of unseen already-root forms moves from **90.895%** at 10% training knowledge to **92.806%** at 90%, a measured **+1.911 percentage-point** change.
- The evidence establishes within-resource transfer across withheld dictionary families. It
  does not estimate unrelated domains, misspellings, arbitrary compounds, or external corpora.

The complete ten-level table and split ranges remain in the
[independent generalization report](../generalization.md); raw counters and provenance are in
[active machine-readable snapshot](../data/dictionary-generalization-2026-09-11.csv). The
[frozen methodology](../reference/generalization-methodology.md) defines family-level
splitting, unseen-surface leakage control, aggregation, and the limits of the claim.

<!-- DICTIONARY-GENERALIZATION:END -->

<!-- EDIT-COST-GENERALIZATION:START -->
<!-- Reserved for deterministic edit-cost publication. -->
<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `LT_LT` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality-2026-09-11.csv).

### Evaluation Scope and Key Findings

The default model is `lt-lt-default`, loaded from classpath resource `org/egothor/stemmer/models/lt-lt-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.998532** among 3 deterministic stemmers. The runner-up is `SNOWBALL LITHUANIAN DIRECT` at 0.786093, a difference of 0.212439. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.998532** among 3 deterministic stemmers. The runner-up is `SNOWBALL LITHUANIAN DIRECT` at 0.786093, a difference of 0.212439. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **5 result rows**, **3 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.998532|0.000000%|0.293541%|
|2|SNOWBALL LITHUANIAN DIRECT|0.786093|0.004131%|42.777290%|
|3|SNOWBALL LITHUANIAN LUCENE FILTER|0.785955|0.004230%|42.804748%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.997065|1.000000|0.998532|0.999997|0.000003|
|2|SNOWBALL LITHUANIAN DIRECT|PRIMARY_OUTPUT|0.938457|0.572227|0.999959|0.786093|0.999488|0.000512|
|3|SNOWBALL LITHUANIAN LUCENE FILTER|PRIMARY_OUTPUT|0.937045|0.571953|0.999958|0.785955|0.999487|0.000513|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999412|0.998530|0.997650|0.997065|0.998531|0.998530|
|2|SNOWBALL LITHUANIAN DIRECT|PRIMARY_OUTPUT|0.831964|0.710950|0.620670|0.551530|0.732810|0.732600|
|3|SNOWBALL LITHUANIAN LUCENE FILTER|PRIMARY_OUTPUT|0.830960|0.710333|0.620288|0.550788|0.732083|0.731872|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|457532|0|1347|416813837|0 / 416813837|1347 / 458879|
|2|SNOWBALL LITHUANIAN DIRECT|PRIMARY_OUTPUT|262583|17220|196296|416796617|17220 / 416813837|196296 / 458879|
|3|SNOWBALL LITHUANIAN LUCENE FILTER|PRIMARY_OUTPUT|262457|17633|196422|416796204|17633 / 416813837|196422 / 458879|

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
|Radixor|0 / 416813837|0 / 458879|

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
|1|Radixor|ALL_CANDIDATES|458879|0|0|416813837|0 / 416813837|0 / 458879|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|1347|0|0|109|0.377306%|2|28998|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **5 result rows**, **3 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.998532|0.000000%|0.293541%|
|2|SNOWBALL LITHUANIAN DIRECT|0.786093|0.004131%|42.777290%|
|3|SNOWBALL LITHUANIAN LUCENE FILTER|0.785955|0.004230%|42.804748%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.997065|1.000000|0.998532|0.999997|0.000003|
|2|SNOWBALL LITHUANIAN DIRECT|PRIMARY_OUTPUT|0.938457|0.572227|0.999959|0.786093|0.999488|0.000512|
|3|SNOWBALL LITHUANIAN LUCENE FILTER|PRIMARY_OUTPUT|0.937045|0.571953|0.999958|0.785955|0.999487|0.000513|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999412|0.998530|0.997650|0.997065|0.998531|0.998530|
|2|SNOWBALL LITHUANIAN DIRECT|PRIMARY_OUTPUT|0.831964|0.710950|0.620670|0.551530|0.732810|0.732600|
|3|SNOWBALL LITHUANIAN LUCENE FILTER|PRIMARY_OUTPUT|0.830960|0.710333|0.620288|0.550788|0.732083|0.731872|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|457532|0|1347|416813837|0 / 416813837|1347 / 458879|
|2|SNOWBALL LITHUANIAN DIRECT|PRIMARY_OUTPUT|262583|17220|196296|416796617|17220 / 416813837|196296 / 458879|
|3|SNOWBALL LITHUANIAN LUCENE FILTER|PRIMARY_OUTPUT|262457|17633|196422|416796204|17633 / 416813837|196422 / 458879|

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
|Radixor|0 / 416813837|0 / 458879|

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
|1|Radixor|ALL_CANDIDATES|458879|0|0|416813837|0 / 416813837|0 / 458879|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|1347|0|0|109|0.377306%|2|28998|

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

- Authoritative source: `docs/benchmarks/data/stemming-quality-2026-09-11.csv`
- Source SHA-256: `24bddfeed06a60bb3eeed58e1bfc93aed46c1d32e7bebd293aec2dacfddfef5b`
- Evaluation command: `./gradlew stemmingQuality --no-daemon`
- Dictionary language: `LT_LT`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
