# Polish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Polish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `pl-pl-unimorph` | `1.0.0` | `PL_PL` | 9,990 | 132,308 | 19,957 | 112,351 | 112,351 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **132,308**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 1,836 | 1.388% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 52,996 | 40.055% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 37,137 | 28.069% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 20,219 | 15.282% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 20,120 | 15.207% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 98.837% | 98.744% | 99.359% | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | 89.545% | 88.272% | 96.713% | Benchmark-only Polish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene MorfologikFilter | 87.729% | 86.606% | 94.047% | Dictionary-based path; Morfologik can emit multiple terms. |
| Lucene StempelFilter | 70.009% | 69.262% | 74.220% | Lucene TokenFilter integration path for table-driven Polish Stempel. |
| Lucene StempelStemmer direct | 70.009% | 69.262% | 74.220% | Direct table-driven Polish Stempel stemmer API. |
| Official Snowball direct | 22.315% | 20.225% | 34.078% | Official Snowball 3.1.0 generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `polishRadixor` | 8.470 | 0.176 | 75.4 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 537.060 | 32.708 | 4780.2 | 63.408 | Benchmark-only Polish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene StempelStemmer direct | `polishLuceneStempelStemmerDirect` | 33.209 | 0.662 | 295.6 | 3.921 | Direct table-driven Polish Stempel stemmer API. |
| Lucene StempelFilter | `polishLuceneStempelFilter` | 41.041 | 0.656 | 365.3 | 4.846 | Lucene TokenFilter integration path for table-driven Polish Stempel. |
| Lucene MorfologikFilter | `polishLuceneMorfologikFilter` | 140.092 | 1.931 | 1246.9 | 16.540 | Dictionary-based Morfologik TokenFilter; may emit multiple terms. |
| Official Snowball direct | `snowballDirect[POLISH]` | 10.168 | 1.442 | 90.5 | 1.201 | Official Snowball 3.1.0 generated Java stemmer; direct API. |

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

Model `pl-pl-unimorph` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 118,711 | 65.763% (65.395–67.001) | 61.996% (61.861–63.283) | 86.633% (85.310–87.965) |
| 20% | 105,119 | 68.340% (67.352–68.619) | 64.876% (63.830–65.097) | 87.827% (87.200–88.611) |
| 30% | 91,501 | 69.088% (68.299–70.063) | 65.677% (64.723–66.909) | 88.256% (87.960–88.753) |
| 40% | 78,368 | 70.344% (69.790–71.025) | 67.165% (66.449–67.971) | 88.216% (88.039–88.588) |
| 50% | 65,434 | 71.160% (70.975–71.928) | 68.066% (67.805–69.051) | 88.517% (88.330–89.078) |
| 60% | 52,228 | 71.858% (71.219–72.488) | 68.868% (68.033–69.513) | 89.261% (88.574–89.356) |
| 70% | 39,130 | 72.217% (71.567–72.865) | 69.231% (68.375–70.016) | 89.428% (88.990–89.729) |
| 80% | 25,947 | 73.298% (71.542–73.560) | 70.344% (68.301–70.589) | 90.225% (89.430–90.594) |
| 90% | 12,963 | 73.070% (71.833–74.408) | 69.972% (68.686–71.530) | 90.985% (89.948–91.264) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **61.996%**
  at 10% training knowledge to **69.972%** at 90%, a measured
  **+7.976 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+7.307 pp** and
  preservation of unseen already-root forms changes by **+4.352 pp**. These separate
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

This section interprets the edit-cost and held-out-family experiment for `PL_PL`
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
| 9,990 | 132,308 | 84.92% | 846 | 19 | 12.32× | 54 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 61.850% | 61.850% | +0.000 pp | 0.8594 | 0.8593 | 1.000× | 0.980× |
| 20% | 65.469% | 65.469% | +0.000 pp | 0.8818 | 0.8818 | 1.000× | 0.967× |
| 30% | 66.483% | 66.483% | +0.000 pp | 0.8867 | 0.8867 | 1.000× | 0.951× |
| 40% | 67.190% | 67.190% | +0.000 pp | 0.8926 | 0.8926 | 1.000× | 0.947× |
| 50% | 67.725% | 67.725% | +0.000 pp | 0.8952 | 0.8952 | 1.000× | 0.943× |
| 60% | 68.669% | 68.669% | +0.000 pp | 0.8963 | 0.8963 | 1.000× | 0.939× |
| 70% | 69.319% | 69.319% | +0.000 pp | 0.9031 | 0.9031 | 1.000× | 0.937× |
| 80% | 69.764% | 69.764% | +0.000 pp | 0.9062 | 0.9062 | 1.000× | 0.938× |
| 90% | 70.138% | 70.138% | +0.000 pp | 0.9064 | 0.9064 | 1.000× | 0.935× |

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
| `replace_cost` | `trie_nodes` | +0.777 | +0.694…+0.821 | 45 |
| `patch_command_ratio` | `value_references` | +0.954 | +0.687…+1.000 | 45 |
| `replace_to_delete_insert` | `trie_nodes` | +0.779 | +0.680…+0.858 | 45 |
| `replace_to_delete_insert` | `trie_edges` | +0.732 | +0.654…+0.889 | 45 |
| `replace_to_delete_insert` | `dense_table_slots` | +0.733 | +0.654…+0.874 | 45 |
| `replace_cost` | `trie_edges` | +0.687 | +0.632…+0.796 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `average_path_length` | `unseen_changed_exact` | -1.000 | -1.000…+0.950 | no | 42 / 45 |
| `average_path_length` | `unseen_f05` | -1.000 | -1.000…+0.745 | no | 42 / 45 |
| `average_path_length` | `unseen_over_percent` | -0.845 | -1.000…+0.397 | no | 10 / 45 |
| `average_path_length` | `unseen_under_percent` | +1.000 | -0.955…+1.000 | no | 42 / 45 |

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **61.850%** at 10% knowledge to **70.138%** at 90%, a **+8.288 pp** measured knowledge effect.
- The predeclared selection is `D10I10R1M1`. Its median unseen changed-form exactness differs from baseline by **+0.000 pp** and it reduces the median retained-command count by **5.88%** (0.941× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+8.288 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The non-baseline setting is an efficiency candidate, not a production default: it was selected and evaluated on the same matrix and therefore requires external-corpus or external-dictionary validation before adoption.
- No cost or representation predictor is both defined in all 45 strata and retains one association sign over the central 95% interval for an unseen-form quality outcome. Effects with partial coverage are insufficient for a stable language-level claim; the remaining measured effects are heterogeneous across knowledge levels and splits.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `PL_PL` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `pl-pl-unimorph`, loaded from classpath resource `org/egothor/stemmer/models/pl-pl-unimorph/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.991105** among 6 deterministic stemmers. The runner-up is `POLISH LUCENE MORFOLOGIK FILTER` at 0.948392, a difference of 0.042713. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.991301** among 6 deterministic stemmers. The runner-up is `POLISH LUCENE MORFOLOGIK FILTER` at 0.948417, a difference of 0.042884. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **12 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.991105|0.000000%|1.779024%|
|2|POLISH LUCENE MORFOLOGIK FILTER|0.948392|0.001042%|10.320543%|
|3|HUNSPELL POLISH LUCENE FILTER|0.933457|0.000383%|13.308172%|
|4|POLISH LUCENE STEMPEL DIRECT|0.855699|0.000602%|28.859618%|
|5|POLISH LUCENE STEMPEL FILTER|0.855699|0.000602%|28.859618%|
|6|SNOWBALL POLISH DIRECT|0.823625|0.000967%|35.273970%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.982210|1.000000|0.991105|0.999997|0.000003|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.929398|0.896795|0.999990|0.948392|0.999974|0.000026|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.971931|0.866918|0.999996|0.933457|0.999976|0.000024|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.947549|0.711404|0.999994|0.855699|0.999950|0.000050|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.947549|0.711404|0.999994|0.855699|0.999950|0.000050|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|0.910978|0.647260|0.999990|0.823625|0.999936|0.000064|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996391|0.991025|0.985717|0.982210|0.991065|0.991064|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.922689|0.912805|0.903131|0.839597|0.912951|0.912938|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.948942|0.916426|0.886065|0.845744|0.917924|0.917913|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.888559|0.812669|0.748723|0.684450|0.821030|0.821007|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.888559|0.812669|0.748723|0.684450|0.821030|0.821007|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|0.842338|0.756803|0.687038|0.608756|0.767880|0.767852|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1097200|0|19873|7303238338|0 / 7303238338|19873 / 1117073|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|1001785|76101|115288|7303162237|76101 / 7303238338|115288 / 1117073|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|968411|27967|148662|7303210371|27967 / 7303238338|148662 / 1117073|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|794690|43990|322383|7303194348|43990 / 7303238338|322383 / 1117073|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|794690|43990|322383|7303194348|43990 / 7303238338|322383 / 1117073|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|723037|70656|394036|7303167682|70656 / 7303238338|394036 / 1117073|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|0.000356%|7.227639%|
|POLISH LUCENE MORFOLOGIK FILTER|0.001000%|2.493123%|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|25967 / 7303238338|80738 / 1117073|
|POLISH LUCENE MORFOLOGIK FILTER|73019 / 7303238338|27850 / 1117073|
|Radixor|0 / 7303238338|0 / 1117073|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|POLISH LUCENE MORFOLOGIK FILTER|0.987528|0.001376%|2.493123%|
|3|HUNSPELL POLISH LUCENE FILTER|0.963859|0.000609%|7.227639%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.915516|0.975069|0.999986|0.987528|0.999982|0.000018|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.958830|0.927724|0.999994|0.963859|0.999983|0.000017|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.926837|0.944354|0.962546|0.894575|0.944823|0.944815|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.952443|0.943020|0.933782|0.892184|0.943149|0.943140|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1117073|0|0|7303238338|0 / 7303238338|0 / 1117073|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|1089223|100514|27850|7303137824|100514 / 7303238338|27850 / 1117073|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|1036335|44498|80738|7303193840|44498 / 7303238338|80738 / 1117073|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|67924|2000|16531|10485|8.674824%|6|132492|
|POLISH LUCENE MORFOLOGIK FILTER|87438|3082|24413|11776|9.742941%|5|133810|
|Radixor|19873|0|0|1392|1.151679%|4|122430|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **12 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.991301|0.000000%|1.739895%|
|2|POLISH LUCENE MORFOLOGIK FILTER|0.948417|0.001067%|10.315578%|
|3|HUNSPELL POLISH LUCENE FILTER|0.933546|0.000382%|13.290396%|
|4|POLISH LUCENE STEMPEL DIRECT|0.856335|0.000611%|28.732387%|
|5|POLISH LUCENE STEMPEL FILTER|0.856335|0.000611%|28.732387%|
|6|SNOWBALL POLISH DIRECT|0.823465|0.000990%|35.306102%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.982601|1.000000|0.991301|0.999997|0.000003|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.929032|0.896844|0.999989|0.948417|0.999973|0.000027|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.972469|0.867096|0.999996|0.933546|0.999975|0.000025|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.947796|0.712676|0.999994|0.856335|0.999949|0.000051|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.947796|0.712676|0.999994|0.856335|0.999949|0.000051|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|0.910487|0.646939|0.999990|0.823465|0.999935|0.000065|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996471|0.991224|0.986032|0.982601|0.991262|0.991261|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.922411|0.912654|0.903102|0.839342|0.912796|0.912783|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.949394|0.916764|0.886303|0.846320|0.918272|0.918260|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.889130|0.813590|0.749881|0.685758|0.821871|0.821848|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.889130|0.813590|0.749881|0.685758|0.821871|0.821848|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|0.841894|0.756414|0.686693|0.608253|0.767483|0.767454|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1091431|0|19326|7133100218|0 / 7133100218|19326 / 1110757|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|996176|76097|114581|7133024121|76097 / 7133100218|114581 / 1110757|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|963133|27267|147624|7133072951|27267 / 7133100218|147624 / 1110757|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|791610|43601|319147|7133056617|43601 / 7133100218|319147 / 1110757|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|791610|43601|319147|7133056617|43601 / 7133100218|319147 / 1110757|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|718592|70647|392165|7133029571|70647 / 7133100218|392165 / 1110757|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|0.000356%|7.234976%|
|POLISH LUCENE MORFOLOGIK FILTER|0.001024%|2.474799%|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|25425 / 7133100218|80363 / 1110757|
|POLISH LUCENE MORFOLOGIK FILTER|73019 / 7133100218|27489 / 1110757|
|Radixor|0 / 7133100218|0 / 1110757|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|POLISH LUCENE MORFOLOGIK FILTER|0.987619|0.001409%|2.474799%|
|3|HUNSPELL POLISH LUCENE FILTER|0.963822|0.000612%|7.234976%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.915099|0.975252|0.999986|0.987619|0.999982|0.000018|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.959377|0.927650|0.999994|0.963822|0.999983|0.000017|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.926529|0.944219|0.962597|0.894332|0.944697|0.944688|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.952859|0.943247|0.933827|0.892590|0.943380|0.943372|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1110757|0|0|7133100218|0 / 7133100218|0 / 1110757|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|1083268|100503|27489|7132999715|100503 / 7133100218|27489 / 1110757|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|1030394|43630|80363|7133056588|43630 / 7133100218|80363 / 1110757|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|67261|1842|16363|10303|8.625294%|6|130856|
|POLISH LUCENE MORFOLOGIK FILTER|87092|3078|24406|11666|9.766348%|5|132279|
|Radixor|19326|0|0|1306|1.093335%|4|120926|

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
- Dictionary language: `PL_PL`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
