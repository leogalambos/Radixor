# Russian Stemmer Benchmarks

This page reports same-language stemming benchmarks for Russian. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `ru-ru-default` | `1.0.0` | `RU_RU` | 37,410 | 806,279 | 74,808 | 731,471 | 731,471 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **806,279**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 9,287 | 1.152% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 580,915 | 72.049% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 82,956 | 10.289% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 75,527 | 9.367% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 57,594 | 7.143% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 98.807% | 98.696% | 99.896% | Radixor dictionary-trained patch-command stemmer. |
| Lucene RussianLightStemFilter | 9.658% | 8.452% | 21.447% | Light suffix stemmer; intentionally narrower than Radixor's dictionary-trained transformation model. |
| Lucene SnowballFilter | 9.162% | 8.162% | 18.936% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 9.162% | 8.162% | 18.936% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `russianRadixor` | 78.162 | 2.240 | 106.9 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene RussianLightStemFilter | `russianLuceneRussianLightStemFilter` | 57.502 | 2.358 | 78.6 | 0.736 | Light Russian suffix stemmer. |
| Official Snowball direct | `snowballDirect[RUSSIAN]` | 107.685 | 11.649 | 147.2 | 1.378 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[RUSSIAN]` | 133.100 | 6.117 | 182.0 | 1.703 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

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

Model `ru-ru-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 723,165 | 76.473% (76.268–77.001) | 74.948% (74.735–75.545) | 91.268% (90.883–91.995) |
| 20% | 640,968 | 78.648% (78.391–79.002) | 77.254% (76.977–77.673) | 91.951% (91.925–92.274) |
| 30% | 560,108 | 79.739% (79.241–79.963) | 78.423% (77.874–78.684) | 92.571% (92.488–92.673) |
| 40% | 479,920 | 80.312% (79.666–80.706) | 79.019% (78.331–79.445) | 93.010% (92.649–93.088) |
| 50% | 398,725 | 80.854% (80.304–81.219) | 79.578% (78.976–79.972) | 93.316% (93.219–93.447) |
| 60% | 318,625 | 81.182% (80.809–81.611) | 79.915% (79.506–80.371) | 93.632% (93.525–93.734) |
| 70% | 238,018 | 81.575% (81.414–81.952) | 80.296% (80.115–80.758) | 94.006% (93.750–94.173) |
| 80% | 158,745 | 81.665% (81.300–82.243) | 80.343% (79.956–81.034) | 94.359% (93.988–94.456) |
| 90% | 79,477 | 82.007% (81.842–82.574) | 80.744% (80.515–81.372) | 94.506% (94.455–94.967) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **74.948%**
  at 10% training knowledge to **80.744%** at 90%, a measured
  **+5.796 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+5.534 pp** and
  preservation of unseen already-root forms changes by **+3.238 pp**. These separate
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

This section interprets the edit-cost and held-out-family experiment for `RU_RU`
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
| 37,410 | 806,279 | 90.72% | 1,840 | 21 | 11.14× | 54 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 74.976% | 74.969% | -0.007 pp | 0.9306 | 0.9306 | 1.000× | 0.891× |
| 20% | 77.397% | 77.392% | -0.005 pp | 0.9394 | 0.9394 | 1.000× | 0.923× |
| 30% | 78.138% | 78.133% | -0.005 pp | 0.9421 | 0.9421 | 1.000× | 0.937× |
| 40% | 78.965% | 78.961% | -0.004 pp | 0.9453 | 0.9453 | 1.000× | 0.951× |
| 50% | 79.603% | 79.596% | -0.007 pp | 0.9464 | 0.9464 | 1.000× | 0.970× |
| 60% | 79.886% | 79.880% | -0.006 pp | 0.9479 | 0.9478 | 1.000× | 0.979× |
| 70% | 80.439% | 80.436% | -0.003 pp | 0.9490 | 0.9490 | 1.000× | 1.002× |
| 80% | 80.806% | 80.800% | -0.006 pp | 0.9502 | 0.9502 | 1.000× | 1.015× |
| 90% | 81.082% | 81.078% | -0.004 pp | 0.9498 | 0.9498 | 1.000× | 1.023× |

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
| `replace_to_delete_insert` | `value_references` | +0.949 | +0.922…+0.957 | 45 |
| `replace_to_delete_insert` | `trie_nodes` | +0.948 | +0.913…+0.957 | 45 |
| `replace_to_delete_insert` | `dense_table_slots` | +0.896 | +0.830…+0.936 | 45 |
| `replace_to_delete_insert` | `trie_edges` | +0.830 | +0.796…+0.919 | 45 |
| `replace_cost` | `value_references` | +0.811 | +0.786…+0.813 | 45 |
| `replace_cost` | `trie_nodes` | +0.810 | +0.782…+0.813 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `logical_leaf_paths` | `unseen_changed_exact` | -1.000 | -1.000…-0.785 | yes | 45 / 45 |
| `logical_leaf_paths` | `unseen_f05` | -0.928 | -1.000…+0.525 | no | 45 / 45 |
| `trie_nodes` | `unseen_over_percent` | -0.628 | -0.966…+0.182 | no | 29 / 45 |
| `logical_leaf_paths` | `unseen_under_percent` | +1.000 | +0.783…+1.000 | yes | 45 / 45 |

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **74.976%** at 10% knowledge to **81.082%** at 90%, a **+6.106 pp** measured knowledge effect.
- The predeclared selection is `D10I10R1M1`. Its median unseen changed-form exactness differs from baseline by **-0.004 pp** and it reduces the median retained-command count by **3.04%** (0.970× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+6.108 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The non-baseline setting is an efficiency candidate, not a production default: it was selected and evaluated on the same matrix and therefore requires external-corpus or external-dictionary validation before adoption.
- 25 cost/representation-to-quality association(s) are defined in all 45 strata and retain one sign over their central 95% interval. Their direction is evidence for this resource only; inspect the table and machine-readable coefficients before extrapolating.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `RU_RU` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `ru-ru-default`, loaded from classpath resource `org/egothor/stemmer/models/ru-ru-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.990188** among 4 deterministic stemmers. The runner-up is `SNOWBALL RUSSIAN LUCENE FILTER` at 0.834565, a difference of 0.155624. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.990213** among 4 deterministic stemmers. The runner-up is `SNOWBALL RUSSIAN DIRECT` at 0.834542, a difference of 0.155670. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.990188|0.000000%|1.962362%|
|2|SNOWBALL RUSSIAN LUCENE FILTER|0.834565|0.001215%|33.085867%|
|3|SNOWBALL RUSSIAN DIRECT|0.834556|0.001214%|33.087654%|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|0.616440|0.000059%|76.711890%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.980376|1.000000|0.990188|0.999999|0.000001|
|2|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.713518|0.669141|0.999988|0.834565|0.999973|0.000027|
|3|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.713680|0.669123|0.999988|0.834556|0.999973|0.000027|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.946958|0.232881|0.999999|0.616440|0.999965|0.000035|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996013|0.990091|0.984239|0.980376|0.990140|0.990139|
|2|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.704178|0.690618|0.677570|0.527438|0.690974|0.690960|
|3|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.704300|0.690684|0.677584|0.527515|0.691043|0.691029|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.586986|0.373828|0.274241|0.229882|0.469605|0.469596|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|12781761|0|255845|288279885172|0 / 288279885172|255845 / 13037606|
|2|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|8724001|3502741|4313605|288276382431|3502741 / 288279885172|4313605 / 13037606|
|3|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|8723768|3499880|4313838|288276385292|3499880 / 288279885172|4313838 / 13037606|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|3036212|170067|10001394|288279715105|170067 / 288279885172|10001394 / 13037606|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.000100%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 288279885172|13 / 13037606|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000100%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|0.999999|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|0.999999|0.999999|1.000000|1.000000|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|13037593|0|13|288279885172|0 / 288279885172|13 / 13037606|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|255832|0|0|9613|1.265979%|4|769106|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.990213|0.000000%|1.957434%|
|2|SNOWBALL RUSSIAN DIRECT|0.834542|0.001216%|33.090302%|
|3|SNOWBALL RUSSIAN LUCENE FILTER|0.834542|0.001216%|33.090302%|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|0.616378|0.000058%|76.724356%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.980426|1.000000|0.990213|0.999999|0.000001|
|2|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.713634|0.669097|0.999988|0.834542|0.999973|0.000027|
|3|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.713634|0.669097|0.999988|0.834542|0.999973|0.000027|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.947585|0.232756|0.999999|0.616378|0.999965|0.000035|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996023|0.990116|0.984279|0.980426|0.990164|0.990164|
|2|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.704259|0.690648|0.677554|0.527474|0.691007|0.690993|
|3|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.704259|0.690648|0.677554|0.527474|0.691007|0.690993|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.587020|0.373716|0.274113|0.229798|0.469634|0.469625|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|12780071|0|255156|287711428009|0 / 287711428009|255156 / 13035227|
|2|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|8721831|3499880|4313396|287707928129|3499880 / 287711428009|4313396 / 13035227|
|3|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|8721831|3499880|4313396|287707928129|3499880 / 287711428009|4313396 / 13035227|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|3034033|167825|10001194|287711260184|167825 / 287711428009|10001194 / 13035227|

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
|Radixor|0 / 287711428009|0 / 13035227|

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
|1|Radixor|ALL_CANDIDATES|13035227|0|0|287711428009|0 / 287711428009|0 / 13035227|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|255156|0|0|9442|1.244687%|4|768163|

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
- Dictionary language: `RU_RU`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
