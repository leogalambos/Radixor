# Spanish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Spanish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `es-es-default` | `1.0.0` | `ES_ES` | 65,059 | 926,393 | 120,121 | 806,272 | 806,272 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **926,393**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 8,534 | 0.921% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 522,685 | 56.422% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 243,410 | 26.275% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 124,386 | 13.427% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 27,378 | 2.955% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 97.459% | 97.544% | 96.891% | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | 49.074% | 42.656% | 92.154% | Benchmark-only Spanish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene SpanishMinimalStemFilter | 17.284% | 5.347% | 97.403% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SpanishPluralStemFilter | 15.140% | 5.802% | 77.820% | Plural-focused suffix reducer; narrow baseline. |
| Lucene SpanishLightStemFilter | 9.577% | 7.088% | 26.279% | Light suffix stemmer; intentionally narrower than Radixor's dictionary-trained transformation model. |
| Lucene SnowballFilter | 4.889% | 4.287% | 8.932% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 4.889% | 4.287% | 8.930% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `spanishRadixor` | 70.807 | 1.533 | 87.8 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 1983.491 | 40.315 | 2460.1 | 28.013 | Benchmark-only Spanish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene SpanishMinimalStemFilter | `spanishLuceneSpanishMinimalStemFilter` | 39.667 | 1.296 | 49.2 | 0.560 | Minimal Spanish suffix reducer; narrow baseline. |
| Lucene SpanishLightStemFilter | `spanishLuceneSpanishLightStemFilter` | 42.394 | 1.768 | 52.6 | 0.599 | Light Spanish suffix stemmer. |
| Lucene SpanishPluralStemFilter | `spanishLuceneSpanishPluralStemFilter` | 93.092 | 4.240 | 115.5 | 1.315 | Plural-oriented Spanish suffix reducer. |
| Official Snowball direct | `snowballDirect[SPANISH]` | 180.797 | 8.287 | 224.2 | 2.553 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[SPANISH]` | 201.614 | 17.917 | 250.1 | 2.847 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

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

Model `es-es-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 827,660 | 82.066% (81.789–82.910) | 80.995% (80.671–81.943) | 89.364% (88.856–89.475) |
| 20% | 732,142 | 84.175% (83.996–84.838) | 83.310% (83.160–84.044) | 90.087% (89.771–90.303) |
| 30% | 638,770 | 85.558% (85.336–86.089) | 84.789% (84.495–85.404) | 90.925% (90.760–91.204) |
| 40% | 544,394 | 86.496% (86.375–86.920) | 85.764% (85.618–86.311) | 91.721% (91.215–91.822) |
| 50% | 450,843 | 87.642% (87.181–87.836) | 86.991% (86.460–87.243) | 92.320% (92.067–92.411) |
| 60% | 357,877 | 88.208% (87.787–88.272) | 87.558% (87.076–87.614) | 92.915% (92.860–93.031) |
| 70% | 265,246 | 88.730% (88.448–88.971) | 88.095% (87.737–88.338) | 93.593% (93.457–93.713) |
| 80% | 177,896 | 89.154% (88.946–89.572) | 88.448% (88.281–88.984) | 93.934% (93.855–94.442) |
| 90% | 89,614 | 89.801% (89.306–90.166) | 89.176% (88.546–89.533) | 94.711% (94.645–94.920) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **80.995%**
  at 10% training knowledge to **89.176%** at 90%, a measured
  **+8.181 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+7.735 pp** and
  preservation of unseen already-root forms changes by **+5.348 pp**. These separate
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

This section interprets the edit-cost and held-out-family experiment for `ES_ES`
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
| 65,059 | 926,393 | 87.03% | 1,496 | 18 | 13.00× | 54 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 80.556% | 80.556% | +0.000 pp | 0.9362 | 0.9362 | 1.000× | 0.749× |
| 20% | 83.316% | 83.316% | +0.000 pp | 0.9491 | 0.9491 | 1.000× | 0.776× |
| 30% | 85.068% | 85.068% | +0.000 pp | 0.9559 | 0.9559 | 1.000× | 0.795× |
| 40% | 86.248% | 86.248% | +0.000 pp | 0.9599 | 0.9599 | 1.000× | 0.806× |
| 50% | 87.056% | 87.056% | +0.000 pp | 0.9628 | 0.9628 | 1.000× | 0.819× |
| 60% | 87.683% | 87.683% | +0.000 pp | 0.9653 | 0.9653 | 1.000× | 0.827× |
| 70% | 88.195% | 88.195% | +0.000 pp | 0.9665 | 0.9665 | 1.000× | 0.839× |
| 80% | 88.552% | 88.552% | +0.000 pp | 0.9677 | 0.9677 | 1.000× | 0.842× |
| 90% | 89.280% | 89.280% | +0.000 pp | 0.9688 | 0.9688 | 1.000× | 0.851× |

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
| `patch_command_ratio` | `value_references` | +0.993 | +0.966…+1.000 | 45 |
| `patch_command_ratio` | `trie_nodes` | +0.953 | +0.946…+1.000 | 45 |
| `replace_to_delete_insert` | `dense_table_slots` | +0.947 | +0.920…+0.952 | 45 |
| `replace_to_delete_insert` | `trie_edges` | +0.946 | +0.867…+0.952 | 45 |
| `replace_to_delete_insert` | `trie_nodes` | +0.867 | +0.867…+0.937 | 45 |
| `patch_command_ratio` | `trie_edges` | +0.875 | +0.830…+0.978 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `average_path_length` | `unseen_changed_exact` | -1.000 | -1.000…-1.000 | yes | 45 / 45 |
| `average_path_length` | `unseen_f05` | -1.000 | -1.000…-0.864 | yes | 45 / 45 |
| `average_path_length` | `unseen_over_percent` | -0.821 | -1.000…+0.969 | no | 10 / 45 |
| `average_path_length` | `unseen_under_percent` | +1.000 | +0.944…+1.000 | yes | 45 / 45 |

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **80.556%** at 10% knowledge to **89.280%** at 90%, a **+8.725 pp** measured knowledge effect.
- The predeclared selection is `D10I10R1M1`. Its median unseen changed-form exactness differs from baseline by **+0.000 pp** and it reduces the median retained-command count by **18.02%** (0.820× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+8.725 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The non-baseline setting is an efficiency candidate, not a production default: it was selected and evaluated on the same matrix and therefore requires external-corpus or external-dictionary validation before adoption.
- 38 cost/representation-to-quality association(s) are defined in all 45 strata and retain one sign over their central 95% interval. Their direction is evidence for this resource only; inspect the table and machine-readable coefficients before extrapolating.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `ES_ES` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `es-es-default`, loaded from classpath resource `org/egothor/stemmer/models/es-es-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.989448** among 7 deterministic stemmers. The runner-up is `SNOWBALL SPANISH LUCENE FILTER` at 0.652438, a difference of 0.337010. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.989580** among 7 deterministic stemmers. The runner-up is `SNOWBALL SPANISH DIRECT` at 0.652542, a difference of 0.337038. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **11 result rows**, **7 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.989448|0.000000%|2.110334%|
|2|SNOWBALL SPANISH LUCENE FILTER|0.652438|0.000414%|69.511918%|
|3|SNOWBALL SPANISH DIRECT|0.652438|0.000413%|69.511932%|
|4|HUNSPELL SPANISH LUCENE FILTER|0.615028|0.000068%|76.994273%|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|0.514565|0.000009%|97.087060%|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|0.503764|0.000002%|99.247265%|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|0.501678|0.000001%|99.664470%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.978897|1.000000|0.989448|0.999998|0.000002|
|2|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.895438|0.304881|0.999996|0.652438|0.999915|0.000085|
|3|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.895510|0.304881|0.999996|0.652438|0.999915|0.000085|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.975281|0.230057|0.999999|0.615028|0.999910|0.000090|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.974423|0.029129|1.000000|0.514565|0.999887|0.000113|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.979154|0.007527|1.000000|0.503764|0.999885|0.000115|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.970596|0.003355|1.000000|0.501678|0.999884|0.000116|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.995707|0.989336|0.983046|0.978897|0.989392|0.989391|
|2|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.645406|0.454882|0.351206|0.294400|0.522496|0.522469|
|3|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.645436|0.454891|0.351208|0.294407|0.522517|0.522490|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.591847|0.372295|0.271557|0.228724|0.473678|0.473655|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.130091|0.056568|0.036142|0.029107|0.168477|0.168467|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.036514|0.014940|0.009391|0.007526|0.085851|0.085846|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.016548|0.006687|0.004191|0.003355|0.057067|0.057063|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|41053986|0|885054|360919543590|0 / 360919543590|885054 / 41939040|
|2|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|12786409|1493087|29152631|360918050503|1493087 / 360919543590|29152631 / 41939040|
|3|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|12786403|1491944|29152637|360918051646|1491944 / 360919543590|29152637 / 41939040|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|9648381|244539|32290659|360919299051|244539 / 360919543590|32290659 / 41939040|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|1221659|32066|40717381|360919511524|32066 / 360919543590|40717381 / 41939040|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|315690|6721|41623350|360919536869|6721 / 360919543590|41623350 / 41939040|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|140718|4263|41798322|360919539327|4263 / 360919543590|41798322 / 41939040|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|0.000062%|76.009935%|
|Radixor|0.000000%|0.001493%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|223500 / 360919543590|31877837 / 41939040|
|Radixor|0 / 360919543590|626 / 41939040|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.999993|&lt;0.000001%|0.001493%|
|2|HUNSPELL SPANISH LUCENE FILTER|0.619950|0.000073%|76.009935%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999959|0.999985|1.000000|0.999993|1.000000|0.000000|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.974467|0.239901|0.999999|0.619950|0.999911|0.000089|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999964|0.999972|0.999980|0.999944|0.999972|0.999972|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.604361|0.385016|0.282490|0.238402|0.483503|0.483480|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|41938414|1737|626|360919541853|1737 / 360919543590|626 / 41939040|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|10061203|263629|31877837|360919279961|263629 / 360919543590|31877837 / 41939040|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|412822|21039|19090|11309|1.331001%|5|861853|
|Radixor|884428|0|1737|20967|2.467690%|21|871404|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **11 result rows**, **7 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.989580|&lt;0.000001%|2.084022%|
|2|SNOWBALL SPANISH DIRECT|0.652542|0.000410%|69.491126%|
|3|SNOWBALL SPANISH LUCENE FILTER|0.652542|0.000410%|69.491126%|
|4|HUNSPELL SPANISH LUCENE FILTER|0.614924|0.000068%|77.015229%|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|0.514575|0.000009%|97.085003%|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|0.503767|0.000002%|99.246590%|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|0.501679|0.000001%|99.664108%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999999|0.979160|1.000000|0.989580|0.999998|0.000002|
|2|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.896551|0.305089|0.999996|0.652542|0.999915|0.000085|
|3|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.896551|0.305089|0.999996|0.652542|0.999915|0.000085|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.975224|0.229848|0.999999|0.614924|0.999910|0.000090|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.974539|0.029150|1.000000|0.514575|0.999887|0.000113|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.979521|0.007534|1.000000|0.503767|0.999884|0.000116|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.971230|0.003359|1.000000|0.501679|0.999884|0.000116|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.995761|0.989470|0.983258|0.979159|0.989525|0.989523|
|2|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.646055|0.455257|0.351461|0.294714|0.522999|0.522972|
|3|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.646055|0.455257|0.351461|0.294714|0.522999|0.522972|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.591553|0.372016|0.271323|0.228513|0.473448|0.473426|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.130175|0.056607|0.036167|0.029128|0.168546|0.168536|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.036546|0.014953|0.009400|0.007533|0.085906|0.085901|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.016565|0.006695|0.004195|0.003359|0.057116|0.057113|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|40958710|34|871756|359407144881|34 / 359407144915|871756 / 41830466|
|2|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|12762004|1472547|29068462|359405672368|1472547 / 359407144915|29068462 / 41830466|
|3|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|12762004|1472547|29068462|359405672368|1472547 / 359407144915|29068462 / 41830466|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|9614637|244260|32215829|359406900655|244260 / 359407144915|32215829 / 41830466|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|1219357|31857|40611109|359407113058|31857 / 359407144915|40611109 / 41830466|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|315155|6589|41515311|359407138326|6589 / 359407144915|41515311 / 41830466|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|140505|4162|41689961|359407140753|4162 / 359407144915|41689961 / 41830466|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|0.000062%|76.037484%|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|223274 / 359407144915|31806834 / 41830466|
|Radixor|0 / 359407144915|0 / 41830466|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|&lt;0.000001%|0.000000%|
|2|HUNSPELL SPANISH LUCENE FILTER|0.619812|0.000073%|76.037484%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999987|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.974405|0.239625|0.999999|0.619812|0.999911|0.000089|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999989|0.999993|0.999997|0.999987|0.999993|0.999993|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.603992|0.384656|0.282183|0.238126|0.483210|0.483187|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|41830466|560|0|359407144355|560 / 359407144915|0 / 41830466|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|10023632|263289|31806834|359406881626|263289 / 359407144915|31806834 / 41830466|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|408995|20986|19029|11287|1.331204%|5|860048|
|Radixor|871756|34|526|20911|2.466272%|21|869542|

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
- Dictionary language: `ES_ES`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
