# French Stemmer Benchmarks

This page reports same-language stemming benchmarks for French. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `fr-fr-default` | `1.0.0` | `FR_FR` | 59,240 | 474,110 | 108,141 | 365,969 | 365,969 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **474,110**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 10,082 | 2.127% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 184,521 | 38.919% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 154,760 | 32.642% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 110,933 | 23.398% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 13,814 | 2.914% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 94.831% | 94.859% | 94.734% | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | 68.923% | 63.617% | 86.876% | Benchmark-only French Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene FrenchMinimalStemFilter | 11.472% | 6.236% | 29.192% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SnowballFilter | 8.551% | 5.183% | 19.952% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 8.462% | 5.067% | 19.952% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene FrenchLightStemFilter | 6.377% | 3.965% | 14.540% | Light suffix stemmer; intentionally narrower than Radixor's dictionary-trained transformation model. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `frenchRadixor` | 42.603 | 1.019 | 116.4 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 1699.026 | 35.808 | 4642.5 | 39.881 | Benchmark-only French Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene FrenchMinimalStemFilter | `frenchLuceneFrenchMinimalStemFilter` | 17.239 | 0.422 | 47.1 | 0.405 | Minimal French suffix reducer; narrow baseline. |
| Lucene FrenchLightStemFilter | `frenchLuceneFrenchLightStemFilter` | 28.180 | 1.108 | 77.0 | 0.661 | Light French suffix stemmer. |
| Official Snowball direct | `snowballDirect[FRENCH]` | 110.345 | 5.041 | 301.5 | 2.590 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[FRENCH]` | 124.104 | 5.294 | 339.1 | 2.913 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

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

Model `fr-fr-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 420,578 | 74.276% (73.276–74.926) | 69.627% (68.400–70.528) | 90.013% (89.987–90.359) |
| 20% | 369,337 | 76.769% (76.583–76.946) | 72.504% (72.339–72.772) | 91.463% (90.996–91.884) |
| 30% | 318,984 | 78.161% (78.141–78.274) | 74.079% (73.976–74.276) | 92.537% (92.395–92.906) |
| 40% | 270,536 | 79.255% (79.039–79.552) | 75.314% (75.088–75.702) | 93.386% (93.086–93.418) |
| 50% | 222,533 | 80.344% (79.709–80.528) | 76.569% (75.778–76.888) | 94.006% (93.785–94.099) |
| 60% | 175,326 | 81.224% (80.630–81.431) | 77.560% (76.779–77.867) | 94.813% (94.641–94.831) |
| 70% | 130,120 | 82.101% (81.643–82.329) | 78.644% (78.015–78.839) | 95.431% (95.257–95.560) |
| 80% | 85,315 | 82.721% (81.861–83.091) | 79.345% (78.141–79.693) | 96.074% (95.896–96.266) |
| 90% | 41,750 | 83.469% (82.278–83.711) | 80.031% (78.595–80.412) | 96.664% (96.351–96.813) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **69.627%**
  at 10% training knowledge to **80.031%** at 90%, a measured
  **+10.404 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+9.193 pp** and
  preservation of unseen already-root forms changes by **+6.651 pp**. These separate
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

This section interprets the edit-cost and held-out-family experiment for `FR_FR`
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
| 59,240 | 474,110 | 77.19% | 2,730 | 32 | 7.31× | 54 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 69.937% | 69.937% | +0.000 pp | 0.8816 | 0.8816 | 1.000× | 0.718× |
| 20% | 72.375% | 72.375% | +0.000 pp | 0.8939 | 0.8939 | 1.000× | 0.721× |
| 30% | 74.543% | 74.542% | -0.000 pp | 0.9055 | 0.9055 | 1.000× | 0.720× |
| 40% | 75.720% | 75.720% | +0.000 pp | 0.9100 | 0.9100 | 1.000× | 0.724× |
| 50% | 76.846% | 76.846% | +0.000 pp | 0.9156 | 0.9156 | 1.000× | 0.732× |
| 60% | 77.664% | 77.664% | +0.000 pp | 0.9201 | 0.9201 | 1.000× | 0.734× |
| 70% | 78.435% | 78.435% | +0.000 pp | 0.9239 | 0.9239 | 1.000× | 0.744× |
| 80% | 79.746% | 79.746% | +0.000 pp | 0.9289 | 0.9289 | 1.000× | 0.741× |
| 90% | 80.058% | 80.058% | +0.000 pp | 0.9320 | 0.9320 | 1.000× | 0.750× |

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
| `replace_to_delete_insert` | `patch_command_ratio` | +0.956 | +0.943…+0.959 | 45 |
| `patch_command_ratio` | `value_references` | +0.972 | +0.934…+1.000 | 45 |
| `patch_command_ratio` | `trie_nodes` | +0.948 | +0.931…+0.999 | 45 |
| `replace_to_delete_insert` | `value_references` | +0.924 | +0.882…+0.957 | 45 |
| `replace_to_delete_insert` | `trie_nodes` | +0.899 | +0.882…+0.956 | 45 |
| `patch_command_ratio` | `trie_edges` | +0.821 | +0.814…+0.913 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `average_path_length` | `unseen_changed_exact` | +1.000 | -1.000…+1.000 | no | 38 / 45 |
| `average_path_length` | `unseen_f05` | +0.822 | -0.902…+1.000 | no | 39 / 45 |
| `value_references` | `unseen_over_percent` | -0.846 | -0.984…-0.723 | no | 18 / 45 |
| `logical_leaf_paths` | `unseen_under_percent` | +0.853 | +0.692…+1.000 | no | 39 / 45 |

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **69.937%** at 10% knowledge to **80.058%** at 90%, a **+10.121 pp** measured knowledge effect.
- The predeclared selection is `D10I10R1M1`. Its median unseen changed-form exactness differs from baseline by **+0.000 pp** and it reduces the median retained-command count by **26.64%** (0.734× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+10.121 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The non-baseline setting is an efficiency candidate, not a production default: it was selected and evaluated on the same matrix and therefore requires external-corpus or external-dictionary validation before adoption.
- No cost or representation predictor is both defined in all 45 strata and retains one association sign over the central 95% interval for an unseen-form quality outcome. Effects with partial coverage are insufficient for a stable language-level claim; the remaining measured effects are heterogeneous across knowledge levels and splits.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `FR_FR` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `fr-fr-default`, loaded from classpath resource `org/egothor/stemmer/models/fr-fr-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.958627** among 6 deterministic stemmers. The runner-up is `SNOWBALL FRENCH DIRECT` at 0.848662, a difference of 0.109965. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.958856** among 6 deterministic stemmers. The runner-up is `SNOWBALL FRENCH DIRECT` at 0.848826, a difference of 0.110031. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **10 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.958627|&lt;0.000001%|8.274665%|
|2|SNOWBALL FRENCH DIRECT|0.848662|0.001338%|30.266309%|
|3|SNOWBALL FRENCH LUCENE FILTER|0.848404|0.001345%|30.317815%|
|4|HUNSPELL FRENCH LUCENE FILTER|0.816824|0.000540%|36.634583%|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|0.518478|0.000187%|96.304159%|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|0.516784|0.000083%|96.643216%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999994|0.917253|1.000000|0.958627|0.999995|0.000005|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.774195|0.697337|0.999987|0.848662|0.999967|0.000033|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.773169|0.696822|0.999987|0.848404|0.999967|0.000033|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.885315|0.633654|0.999995|0.816824|0.999970|0.000030|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.565022|0.036958|0.999998|0.518478|0.999935|0.000065|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.726387|0.033568|0.999999|0.516784|0.999936|0.000064|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.982273|0.956838|0.932688|0.917248|0.957731|0.957728|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.757497|0.733759|0.711463|0.579478|0.734761|0.734745|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.756590|0.733013|0.710861|0.578548|0.734003|0.733987|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.820168|0.738637|0.671850|0.585587|0.748988|0.748975|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.146469|0.069379|0.045455|0.035936|0.144507|0.144495|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.141655|0.064170|0.041481|0.033149|0.156151|0.156143|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|4925833|29|444366|81606871827|29 / 81606871856|444366 / 5370199|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|3744838|1092238|1625361|81605779618|1092238 / 81606871856|1625361 / 5370199|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|3742072|1097843|1628127|81605774013|1097843 / 81606871856|1628127 / 5370199|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|3402849|440809|1967350|81606431047|440809 / 81606871856|1967350 / 5370199|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|198474|152794|5171725|81606719062|152794 / 81606871856|5171725 / 5370199|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|180266|67902|5189933|81606803954|67902 / 81606871856|5189933 / 5370199|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.004320%|
|HUNSPELL FRENCH LUCENE FILTER|0.000539%|33.189869%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 81606871856|232 / 5370199|
|HUNSPELL FRENCH LUCENE FILTER|439665 / 81606871856|1782362 / 5370199|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.999978|0.000003%|0.004320%|
|2|HUNSPELL FRENCH LUCENE FILTER|0.834048|0.000614%|33.189869%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999571|0.999957|1.000000|0.999978|1.000000|0.000000|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|0.877537|0.668101|0.999994|0.834048|0.999972|0.000028|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999648|0.999764|0.999880|0.999528|0.999764|0.999764|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|0.825765|0.758630|0.701590|0.611123|0.765691|0.765678|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|5369967|2303|232|81606869553|2303 / 81606871856|232 / 5370199|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|3587837|500695|1782362|81606371161|500695 / 81606871856|1782362 / 5370199|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|444134|29|2274|21844|5.406783%|56|427440|
|HUNSPELL FRENCH LUCENE FILTER|184988|1144|59886|8230|2.037073%|4|412364|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **10 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.958856|&lt;0.000001%|8.228703%|
|2|SNOWBALL FRENCH DIRECT|0.848826|0.001356%|30.233460%|
|3|SNOWBALL FRENCH LUCENE FILTER|0.848580|0.001353%|30.282729%|
|4|HUNSPELL FRENCH LUCENE FILTER|0.816702|0.000540%|36.658999%|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|0.518338|0.000181%|96.332173%|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|0.516654|0.000076%|96.669051%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.917713|1.000000|0.958856|0.999995|0.000005|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.774357|0.697665|0.999986|0.848826|0.999966|0.000034|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.774620|0.697173|0.999986|0.848580|0.999966|0.000034|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.886736|0.633410|0.999995|0.816702|0.999970|0.000030|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.575115|0.036678|0.999998|0.518338|0.999934|0.000066|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.746071|0.033309|0.999999|0.516654|0.999935|0.000065|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.982383|0.957091|0.933069|0.917713|0.957973|0.957971|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.757699|0.734013|0.711764|0.579795|0.735012|0.734995|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.757784|0.733859|0.711398|0.579603|0.734877|0.734860|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.821061|0.738965|0.671794|0.585999|0.749445|0.749431|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.146117|0.068959|0.045128|0.035711|0.145238|0.145227|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.141311|0.063772|0.041177|0.032936|0.157643|0.157634|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|4915501|1|440750|80279496864|1 / 80279496865|440750 / 5356251|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|3736871|1088903|1619380|80278407962|1088903 / 80279496865|1619380 / 5356251|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|3734232|1086494|1622019|80278410371|1086494 / 80279496865|1622019 / 5356251|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|3392703|433354|1963548|80279063511|433354 / 80279496865|1963548 / 5356251|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|196458|145140|5159793|80279351725|145140 / 80279496865|5159793 / 5356251|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|178414|60724|5177837|80279436141|60724 / 80279496865|5177837 / 5356251|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.000000%|
|HUNSPELL FRENCH LUCENE FILTER|0.000539%|33.211718%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 80279496865|0 / 5356251|
|HUNSPELL FRENCH LUCENE FILTER|432307 / 80279496865|1778903 / 5356251|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|&lt;0.000001%|0.000000%|
|2|HUNSPELL FRENCH LUCENE FILTER|0.833938|0.000614%|33.211718%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999986|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|0.878983|0.667883|0.999994|0.833938|0.999972|0.000028|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999989|0.999993|0.999997|0.999986|0.999993|0.999993|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|0.826722|0.759029|0.701582|0.611641|0.766197|0.766184|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|5356251|75|0|80279496790|75 / 80279496865|0 / 5356251|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|3577348|492522|1778903|80279004343|492522 / 80279496865|1778903 / 5356251|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|440750|1|74|20611|5.143594%|56|422336|
|HUNSPELL FRENCH LUCENE FILTER|184645|1047|59168|8194|2.044860%|4|409028|

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
- Dictionary language: `FR_FR`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
