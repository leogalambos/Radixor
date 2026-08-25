# Finnish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Finnish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `fi-fi-default` | `1.0.0` | `FI_FI` | 57,027 | 1,865,215 | 110,525 | 1,754,690 | 1,754,690 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **1,865,215**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 1,117 | 0.060% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 1,175,880 | 63.043% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 570,130 | 30.566% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 112,029 | 6.006% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 6,059 | 0.325% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 98.661% | 98.803% | 96.408% | Radixor dictionary-trained patch-command stemmer. |
| Lucene SnowballFilter | 10.991% | 10.268% | 22.471% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 10.995% | 10.272% | 22.462% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene FinnishLightStemFilter | 4.351% | 4.294% | 5.264% | Light suffix stemmer; intentionally narrower than Radixor's dictionary-trained transformation model. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `finnishRadixor` | 261.169 | 4.729 | 148.8 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene FinnishLightStemFilter | `finnishLuceneFinnishLightStemFilter` | 169.322 | 7.190 | 96.5 | 0.648 | Light Finnish suffix stemmer. |
| Official Snowball direct | `snowballDirect[FINNISH]` | 263.094 | 24.236 | 149.9 | 1.007 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[FINNISH]` | 322.750 | 12.740 | 183.9 | 1.236 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-trained patch-command stemmer. Its learned transformations can generalize beyond the word forms listed in the training resource.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- DICTIONARY-GENERALIZATION:START -->

## Dictionary-Family Generalization Conclusion

This is the language-specific conclusion from the independent `radixor-generalization-v1` baseline
experiment. It is intentionally separate from the wider edit-cost protocol below; values from
the two frozen snapshots are not substituted for one another.

### Evidence

Model `fi-fi-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 1,672,373 | 79.210% (78.547–79.594) | 78.644% (77.966–79.067) | 88.247% (87.860–88.496) |
| 20% | 1,481,274 | 81.958% (81.525–81.970) | 81.481% (81.032–81.508) | 89.459% (89.355–89.678) |
| 30% | 1,292,480 | 83.290% (82.866–83.366) | 82.848% (82.399–82.936) | 90.499% (90.312–90.537) |
| 40% | 1,103,628 | 83.991% (83.783–84.165) | 83.556% (83.313–83.715) | 91.299% (91.199–91.628) |
| 50% | 917,680 | 84.681% (84.520–84.723) | 84.232% (84.039–84.280) | 92.203% (92.123–92.566) |
| 60% | 730,977 | 85.172% (85.110–85.293) | 84.708% (84.639–84.829) | 93.033% (92.895–93.427) |
| 70% | 546,876 | 85.689% (85.586–85.804) | 85.219% (85.114–85.335) | 93.710% (93.575–93.808) |
| 80% | 363,208 | 86.152% (86.055–86.432) | 85.686% (85.568–85.979) | 94.265% (94.183–94.726) |
| 90% | 181,248 | 86.644% (86.280–86.823) | 86.177% (85.790–86.342) | 94.835% (94.730–95.762) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **78.644%**
  at 10% training knowledge to **86.177%** at 90%, a measured
  **+7.532 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+7.434 pp** and
  preservation of unseen already-root forms changes by **+6.588 pp**. These separate
  outcomes show whether the changed-form result coexists with preservation behavior.
- The evidence establishes within-resource transfer across withheld dictionary families. It
  does not estimate unrelated domains, misspellings, arbitrary compounds, or external corpora.

The complete ten-level table and split ranges remain in the
[independent generalization report](../generalization.md); raw counters and provenance are in
[`dictionary-generalization.csv`](../data/dictionary-generalization.csv). The
[frozen methodology](../reference/generalization-methodology.md) defines family-level
splitting, unseen-surface leakage control, aggregation, and the limits of the claim.

<!-- DICTIONARY-GENERALIZATION:END -->

<!-- EDIT-COST-GENERALIZATION:START -->

## Edit Costs and Dictionary-Knowledge Generalization

This section interprets the edit-cost and held-out-family experiment for `FI_FI`
separately from the cross-language macro summary. Each knowledge point is the median of
five frozen, nested splits. The primary exactness outcome covers changed forms in withheld
families after excluding normalized surfaces seen in training. Thus the complete dictionary
is the evaluation population, while only genuinely unseen surfaces contribute to this outcome.

Cost labels have the fixed form `D<delete>I<insert>R<replace>M<match>`. `D` is the cost
of deleting a source character, `I` of inserting a target character, `R` of replacing a
source character, and `M` of keeping an equal source/target character unchanged (the match
or skip step). For example, `D2I5R3M0` means delete cost 2, insert cost 5, replace cost 3,
and match cost 0. The numbers are relative dynamic-programming costs, not command counts.

### Evidence

| Dictionary rows | Evaluated forms | Changed-form share | Baseline commands | Exact cost classes | Grid reduction | Largest exact class |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 57,027 | 1,865,215 | 94.07% | 2,683 | 7 | 33.43× | 110 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 78.695% | 78.695% | -0.000 pp | 0.9236 | 0.9237 | 1.000× | 0.734× |
| 20% | 81.330% | 81.341% | +0.011 pp | 0.9343 | 0.9344 | 1.000× | 0.728× |
| 30% | 82.603% | 82.606% | +0.003 pp | 0.9390 | 0.9390 | 1.000× | 0.730× |
| 40% | 83.524% | 83.527% | +0.004 pp | 0.9426 | 0.9426 | 1.000× | 0.736× |
| 50% | 84.138% | 84.143% | +0.005 pp | 0.9454 | 0.9455 | 1.000× | 0.727× |
| 60% | 84.797% | 84.799% | +0.002 pp | 0.9478 | 0.9478 | 1.000× | 0.735× |
| 70% | 85.184% | 85.188% | +0.004 pp | 0.9494 | 0.9494 | 1.000× | 0.741× |
| 80% | 85.673% | 85.678% | +0.005 pp | 0.9513 | 0.9514 | 1.000× | 0.749× |
| 90% | 85.707% | 85.709% | +0.002 pp | 0.9517 | 0.9517 | 1.000× | 0.750× |

### Within-language associations

Spearman coefficients are calculated independently inside each seed × knowledge
stratum across the normalized cost grid. The table reports the median and central
95% empirical interval across up to 45 strata. A relationship is called stable
only when it is defined in all 45 strata and the interval retains one sign.
These intervals are descriptive, not multiplicity-adjusted confidence intervals.
Every predictor and outcome label is defined in the [methodology glossary](../reference/edit-cost-methodology.md#predictor-and-outcome-glossary).

The strongest structural pairs whose central interval retains one sign are:

| Predictor | Structural outcome | Median Spearman ρ | Central 95% | Strata |
| --- | --- | ---: | ---: | ---: |
| `replace_to_delete_insert` | `logical_leaf_paths` | +0.766 | +0.503…+0.766 | 45 |
| `replace_cost` | `logical_leaf_paths` | +0.670 | +0.456…+0.670 | 45 |
| `edit_cost_imbalance` | `trie_edges` | -0.279 | -0.279…-0.274 | 45 |
| `replace_to_delete_insert` | `average_path_length` | -0.258 | -0.504…-0.258 | 45 |
| `delete_cost` | `average_path_length` | +0.240 | +0.240…+0.303 | 45 |
| `insert_cost` | `average_path_length` | +0.240 | +0.240…+0.303 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `logical_leaf_paths` | `unseen_changed_exact` | -0.875 | -1.000…+0.360 | no | 44 / 45 |
| `average_path_length` | `unseen_f05` | -0.828 | -0.992…+0.720 | no | 45 / 45 |
| `replace_to_delete_insert` | `unseen_over_percent` | -0.587 | -0.887…+0.407 | no | 30 / 45 |
| `logical_leaf_paths` | `unseen_under_percent` | +0.890 | -0.163…+1.000 | no | 45 / 45 |

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **78.695%** at 10% knowledge to **85.707%** at 90%, a **+7.012 pp** measured knowledge effect.
- The predeclared selection is `D10I10R1M1`. Its median unseen changed-form exactness differs from baseline by **+0.005 pp** and it reduces the median retained-command count by **26.37%** (0.736× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+7.014 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The non-baseline setting is an efficiency candidate, not a production default: it was selected and evaluated on the same matrix and therefore requires external-corpus or external-dictionary validation before adoption.
- 2 cost/representation-to-quality association(s) are defined in all 45 strata and retain one sign over their central 95% interval. Their direction is evidence for this resource only; inspect the table and machine-readable coefficients before extrapolating.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `FI_FI` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `fi-fi-default`, loaded from classpath resource `org/egothor/stemmer/models/fi-fi-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.984838** among 4 deterministic stemmers. The runner-up is `SNOWBALL FINNISH LUCENE FILTER` at 0.740279, a difference of 0.244559. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.988242** among 4 deterministic stemmers. The runner-up is `SNOWBALL FINNISH DIRECT` at 0.738543, a difference of 0.249699. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.984838|&lt;0.000001%|3.032474%|
|2|SNOWBALL FINNISH LUCENE FILTER|0.740279|0.000081%|51.944179%|
|3|SNOWBALL FINNISH DIRECT|0.739870|0.000060%|52.025976%|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|0.695725|0.000094%|60.854936%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999974|0.969675|1.000000|0.984838|0.999999|0.000001|
|2|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.921471|0.480558|0.999999|0.740279|0.999989|0.000011|
|3|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.940647|0.479740|0.999999|0.739870|0.999989|0.000011|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.890914|0.391451|0.999999|0.695725|0.999987|0.000013|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.993763|0.984591|0.975587|0.969650|0.984708|0.984708|
|2|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.778598|0.631685|0.531413|0.461652|0.665448|0.665443|
|3|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.789035|0.635413|0.531862|0.465645|0.671764|0.671760|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.709787|0.543915|0.440884|0.373546|0.590550|0.590545|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|30511413|804|954186|1599841738533|804 / 1599841739337|954186 / 31465599|
|2|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|15121052|1288634|16344547|1599840450703|1288634 / 1599841739337|16344547 / 31465599|
|3|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|15095314|952479|16370285|1599840786858|952479 / 1599841739337|16370285 / 31465599|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|12317229|1508153|19148370|1599840231184|1508153 / 1599841739337|19148370 / 31465599|

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
|Radixor|0 / 1599841739337|0 / 31465599|

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
|1|Radixor|ALL_CANDIDATES|0.999926|1.000000|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999941|0.999963|0.999985|0.999926|0.999963|0.999963|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|31465599|2327|0|1599841737010|2327 / 1599841739337|0 / 31465599|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|954186|804|1523|34395|1.922815%|6|1826768|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.988242|&lt;0.000001%|2.351587%|
|2|SNOWBALL FINNISH DIRECT|0.738543|0.000062%|52.291340%|
|3|SNOWBALL FINNISH LUCENE FILTER|0.738344|0.000062%|52.331112%|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|0.694308|0.000077%|61.138333%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999973|0.976484|1.000000|0.988242|1.000000|0.000000|
|2|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.939988|0.477087|0.999999|0.738543|0.999989|0.000011|
|3|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.939951|0.476689|0.999999|0.738344|0.999989|0.000011|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.911893|0.388617|0.999999|0.694308|0.999987|0.000013|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.995185|0.988089|0.981093|0.976459|0.988159|0.988159|
|2|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.787224|0.632932|0.529209|0.462985|0.669668|0.669664|
|3|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.786987|0.632573|0.528815|0.462601|0.669376|0.669372|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.718421|0.544981|0.438999|0.374553|0.595296|0.595291|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|30037514|804|723369|1504706134249|804 / 1504706135053|723369 / 30760883|
|2|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|14675605|936938|16085278|1504705198115|936938 / 1504706135053|16085278 / 30760883|
|3|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|14663371|936765|16097512|1504705198288|936765 / 1504706135053|16097512 / 30760883|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|11954192|1155011|18806691|1504704980042|1155011 / 1504706135053|18806691 / 30760883|

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
|Radixor|0 / 1504706135053|0 / 30760883|

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
|1|Radixor|ALL_CANDIDATES|0.999927|1.000000|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999942|0.999964|0.999985|0.999927|0.999964|0.999964|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|30760883|2235|0|1504706132818|2235 / 1504706135053|0 / 30760883|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|723369|804|1431|22060|1.271628%|6|1758300|

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
- Source SHA-256: `85763189eab4d0fbb047c2d5d3554c66abf9732182bd0d8fd758d7aef680e66f`
- Evaluation command: `./gradlew stemmingQuality --no-daemon`
- Dictionary language: `FI_FI`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
