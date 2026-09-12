# Turkish Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 222,553 distinct usable word forms" title="Relative dictionary size 5 of 5; 222,553 distinct usable word forms">★★★★★</span>

This page reports dictionary corpus, exact-root agreement, and runtime evidence for the independently available `tr-tr-default` Turkish model.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 222,553 distinct usable word forms" title="Relative dictionary size 5 of 5; 222,553 distinct usable word forms">★★★★★</span>. The exact count is **222,553 distinct usable word forms** after parser-compatible filtering and exact, case-preserved deduplication. Stars rank dictionary size relative to all benchmarked dictionaries in five nearly equal groups; they do **not** measure linguistic quality or benchmark accuracy.
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
| `tr-tr-default` | `1.0.0` | `TR_TR` | 3,017 | 222,553 | 224,205 | 3,017 | 221,188 | changed tokens | 221,188 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **224,205**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 568 | 0.253% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 116,469 | 51.948% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 103,111 | 45.990% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 3,005 | 1.340% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 1,052 | 0.469% |

## Accuracy

Accuracy uses the complete dictionary and reports exact agreement with the dictionary root for each identified model and candidate.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.263% | 99.280% | 98.044% | Exact model-ID benchmark; measured in this snapshot. |
| Official Snowball direct (Java) | 44.149% | 43.751% | 73.351% | Official Snowball 3.1.0 generated Java stemmer; measured in this snapshot. |
| Lucene SnowballFilter | 44.133% | 43.734% | 73.351% | Lucene integration of the matching Snowball algorithm; measured in this snapshot. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 3 independent forks, and 1 thread.

The canonical timing workload prefers changed tokens are preferred; a root-only dictionary uses its complete root-preservation corpus. Smaller populations are repeated deterministically to the timing minimum. Relative factors use the Radixor row as the baseline.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `radixor[tr-tr-default]` | 25.962 | 2.892 | 117.4 | 1.000 | Canonical model timing workload; measured in this snapshot. |
| Official Snowball direct (Java) | `snowballDirect[TURKISH]` | 97.758 | 4.259 | 442.0 | 3.765 | Official generated Java stemmer; measured in this snapshot. |
| Lucene SnowballFilter | `luceneSnowballFilter[TURKISH]` | 102.162 | 3.781 | 461.9 | 3.935 | Lucene TokenStream integration; measured in this snapshot. |

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

Model `tr-tr-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 201,851 | 77.397% (74.873–79.731) | 77.296% (74.748–79.658) | 84.869% (83.617–85.031) |
| 20% | 178,334 | 79.471% (77.942–80.778) | 79.417% (77.814–80.700) | 86.502% (83.375–87.385) |
| 30% | 155,798 | 81.433% (79.698–81.551) | 81.355% (79.593–81.479) | 87.189% (84.405–87.542) |
| 40% | 133,132 | 81.522% (79.947–82.252) | 81.444% (79.832–82.181) | 87.290% (87.073–88.373) |
| 50% | 111,440 | 81.608% (79.858–83.287) | 81.531% (79.726–83.240) | 87.754% (86.752–89.744) |
| 60% | 88,540 | 81.961% (81.480–83.611) | 81.884% (81.381–83.550) | 88.067% (87.026–90.678) |
| 70% | 66,129 | 81.756% (81.048–83.798) | 81.660% (80.939–83.734) | 88.737% (87.585–91.023) |
| 80% | 43,638 | 81.792% (81.129–82.694) | 81.709% (81.009–82.607) | 88.985% (88.055–89.932) |
| 90% | 22,024 | 82.355% (79.663–85.071) | 82.279% (79.549–85.021) | 88.776% (88.014–92.177) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **77.296%** at 10% training knowledge to **82.279%** at 90%, a measured **+4.983 percentage-point** change.
- Unseen all-form exactness moves from **77.397%** at 10% training knowledge to **82.355%** at 90%, a measured **+4.957 percentage-point** change.
- Preservation of unseen already-root forms moves from **84.869%** at 10% training knowledge to **88.776%** at 90%, a measured **+3.907 percentage-point** change.
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

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `TR_TR` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality-2026-09-11.csv).

### Evaluation Scope and Key Findings

The default model is `tr-tr-default`, loaded from classpath resource `org/egothor/stemmer/models/tr-tr-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.991555** among 3 deterministic stemmers. The runner-up is `SNOWBALL TURKISH DIRECT` at 0.612583, a difference of 0.378972. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.991555** among 3 deterministic stemmers. The runner-up is `SNOWBALL TURKISH DIRECT` at 0.612583, a difference of 0.378972. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **5 result rows**, **3 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.991555|0.000000%|1.688964%|
|2|SNOWBALL TURKISH DIRECT|0.612583|0.000303%|77.483040%|
|3|SNOWBALL TURKISH LUCENE FILTER|0.612537|0.000303%|77.492390%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.983110|1.000000|0.991555|0.999990|0.000010|
|2|SNOWBALL TURKISH DIRECT|PRIMARY_OUTPUT|0.978058|0.225170|0.999997|0.612583|0.999533|0.000467|
|3|SNOWBALL TURKISH LUCENE FILTER|PRIMARY_OUTPUT|0.978049|0.225076|0.999997|0.612537|0.999533|0.000467|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996576|0.991483|0.986442|0.983110|0.991519|0.991514|
|2|SNOWBALL TURKISH DIRECT|PRIMARY_OUTPUT|0.586109|0.366064|0.266144|0.224038|0.469286|0.469171|
|3|SNOWBALL TURKISH LUCENE FILTER|PRIMARY_OUTPUT|0.585980|0.365939|0.266039|0.223945|0.469186|0.469072|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|14572636|0|250355|24749984637|0 / 24749984637|250355 / 14822991|
|2|SNOWBALL TURKISH DIRECT|PRIMARY_OUTPUT|3337687|74878|11485304|24749909759|74878 / 24749984637|11485304 / 14822991|
|3|SNOWBALL TURKISH LUCENE FILTER|PRIMARY_OUTPUT|3336301|74878|11486690|24749909759|74878 / 24749984637|11486690 / 14822991|

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
|Radixor|0 / 24749984637|0 / 14822991|

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
|1|Radixor|ALL_CANDIDATES|14822991|0|0|24749984637|0 / 24749984637|0 / 14822991|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|250355|0|0|1640|0.736903%|3|224205|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **5 result rows**, **3 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.991555|0.000000%|1.688964%|
|2|SNOWBALL TURKISH DIRECT|0.612583|0.000303%|77.483040%|
|3|SNOWBALL TURKISH LUCENE FILTER|0.612537|0.000303%|77.492390%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.983110|1.000000|0.991555|0.999990|0.000010|
|2|SNOWBALL TURKISH DIRECT|PRIMARY_OUTPUT|0.978058|0.225170|0.999997|0.612583|0.999533|0.000467|
|3|SNOWBALL TURKISH LUCENE FILTER|PRIMARY_OUTPUT|0.978049|0.225076|0.999997|0.612537|0.999533|0.000467|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996576|0.991483|0.986442|0.983110|0.991519|0.991514|
|2|SNOWBALL TURKISH DIRECT|PRIMARY_OUTPUT|0.586109|0.366064|0.266144|0.224038|0.469286|0.469171|
|3|SNOWBALL TURKISH LUCENE FILTER|PRIMARY_OUTPUT|0.585980|0.365939|0.266039|0.223945|0.469186|0.469072|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|14572636|0|250355|24749984637|0 / 24749984637|250355 / 14822991|
|2|SNOWBALL TURKISH DIRECT|PRIMARY_OUTPUT|3337687|74878|11485304|24749909759|74878 / 24749984637|11485304 / 14822991|
|3|SNOWBALL TURKISH LUCENE FILTER|PRIMARY_OUTPUT|3336301|74878|11486690|24749909759|74878 / 24749984637|11486690 / 14822991|

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
|Radixor|0 / 24749984637|0 / 14822991|

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
|1|Radixor|ALL_CANDIDATES|14822991|0|0|24749984637|0 / 24749984637|0 / 14822991|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|250355|0|0|1640|0.736903%|3|224205|

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
- Dictionary language: `TR_TR`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
