# Dutch Stemmer Benchmarks

This page reports same-language stemming benchmarks for Dutch. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `nl-nl-default` | `1.0.0` | `NL_NL` | 4,992 | 31,466 | 9,981 | 21,485 | 21,485 |

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
| Radixor | 99.120% | 98.711% | 100.000% | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | 46.590% | 22.718% | 97.976% | Benchmark-only Dutch Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Official Snowball direct | 15.954% | 8.992% | 30.939% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene SnowballFilter | 12.620% | 5.441% | 28.073% | Lucene TokenFilter integration path around the Snowball algorithm. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `radixor[DUTCH]` | 1.395 | 0.185 | 64.9 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 26.133 | 4.062 | 1216.3 | 18.735 | Benchmark-only Dutch Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Official Snowball direct | `snowballDirect[DUTCH]` | 4.299 | 0.274 | 200.1 | 3.082 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[DUTCH]` | 7.270 | 0.290 | 338.4 | 5.212 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

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

Model `nl-nl-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 28,267 | 67.039% (65.411–67.577) | 55.524% (53.812–55.891) | 92.616% (90.386–92.866) |
| 20% | 25,065 | 69.543% (68.997–70.471) | 58.908% (57.908–60.208) | 92.781% (92.430–93.088) |
| 30% | 21,856 | 71.097% (70.602–72.004) | 61.026% (59.937–61.731) | 93.565% (92.739–94.166) |
| 40% | 18,714 | 72.501% (71.803–73.110) | 62.655% (61.524–63.525) | 93.853% (93.540–94.029) |
| 50% | 15,581 | 73.408% (73.027–73.660) | 63.952% (63.070–64.202) | 94.186% (93.539–94.678) |
| 60% | 12,412 | 73.828% (73.295–73.872) | 64.368% (63.661–64.668) | 94.242% (93.471–95.070) |
| 70% | 9,298 | 74.137% (73.135–74.845) | 64.553% (63.215–66.338) | 94.412% (93.155–95.287) |
| 80% | 6,160 | 74.369% (74.091–75.340) | 64.865% (64.550–66.383) | 94.408% (92.901–95.806) |
| 90% | 3,085 | 74.468% (73.250–76.272) | 65.891% (63.190–67.730) | 94.512% (92.371–95.697) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **55.524%**
  at 10% training knowledge to **65.891%** at 90%, a measured
  **+10.366 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+7.429 pp** and
  preservation of unseen already-root forms changes by **+1.896 pp**. These separate
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

This section interprets the edit-cost and held-out-family experiment for `NL_NL`
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
| 4,992 | 31,466 | 68.28% | 538 | 27 | 8.67× | 54 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 55.658% | 55.663% | +0.005 pp | 0.8046 | 0.8047 | 1.000× | 0.987× |
| 20% | 60.163% | 60.168% | +0.006 pp | 0.8256 | 0.8257 | 1.000× | 0.976× |
| 30% | 61.761% | 61.767% | +0.007 pp | 0.8324 | 0.8325 | 1.000× | 0.972× |
| 40% | 62.953% | 62.961% | +0.008 pp | 0.8382 | 0.8383 | 1.000× | 0.969× |
| 50% | 63.877% | 63.887% | +0.009 pp | 0.8402 | 0.8403 | 1.000× | 0.960× |
| 60% | 64.947% | 64.947% | +0.000 pp | 0.8478 | 0.8478 | 1.000× | 0.955× |
| 70% | 65.345% | 65.345% | +0.000 pp | 0.8486 | 0.8486 | 1.000× | 0.948× |
| 80% | 66.253% | 66.253% | +0.000 pp | 0.8539 | 0.8539 | 1.000× | 0.942× |
| 90% | 68.281% | 68.281% | +0.000 pp | 0.8632 | 0.8632 | 1.000× | 0.936× |

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
| `patch_command_ratio` | `trie_nodes` | +1.000 | +0.985…+1.000 | 45 |
| `patch_command_ratio` | `value_references` | +1.000 | +0.985…+1.000 | 45 |
| `patch_command_ratio` | `trie_edges` | +0.985 | +0.940…+1.000 | 45 |
| `patch_command_ratio` | `dense_table_slots` | +0.973 | +0.920…+1.000 | 45 |
| `replace_to_delete_insert` | `trie_edges` | -0.874 | -0.941…-0.844 | 45 |
| `replace_to_delete_insert` | `patch_command_ratio` | -0.843 | -0.874…-0.841 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `trie_edges` | `unseen_changed_exact` | -0.935 | -0.998…-0.803 | yes | 45 / 45 |
| `trie_edges` | `unseen_f05` | -0.936 | -0.998…-0.615 | yes | 45 / 45 |
| `logical_leaf_paths` | `unseen_over_percent` | +0.877 | -0.431…+0.999 | no | 35 / 45 |
| `trie_edges` | `unseen_under_percent` | +0.936 | +0.803…+0.998 | yes | 45 / 45 |

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **55.658%** at 10% knowledge to **68.281%** at 90%, a **+12.623 pp** measured knowledge effect.
- The predeclared selection is `D10I1R10M0`. Its median unseen changed-form exactness differs from baseline by **+0.005 pp** and it reduces the median retained-command count by **3.86%** (0.961× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+12.618 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The non-baseline setting is an efficiency candidate, not a production default: it was selected and evaluated on the same matrix and therefore requires external-corpus or external-dictionary validation before adoption.
- 39 cost/representation-to-quality association(s) are defined in all 45 strata and retain one sign over their central 95% interval. Their direction is evidence for this resource only; inspect the table and machine-readable coefficients before extrapolating.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

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
- Source SHA-256: `85763189eab4d0fbb047c2d5d3554c66abf9732182bd0d8fd758d7aef680e66f`
- Evaluation command: `./gradlew stemmingQuality --no-daemon`
- Dictionary language: `NL_NL`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
