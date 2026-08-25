# Hungarian Stemmer Benchmarks

This page reports same-language stemming benchmarks for Hungarian. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `hu-hu-default` | `1.0.0` | `HU_HU` | 19,406 | 935,713 | 38,775 | 896,938 | 896,938 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **935,713**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 15 | 0.002% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 149,173 | 15.942% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 750,282 | 80.183% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 36,139 | 3.862% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 104 | 0.011% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.222% | 99.537% | 91.948% | Radixor dictionary-trained patch-command stemmer. |
| Lucene SnowballFilter | 66.445% | 66.938% | 55.043% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 66.445% | 66.938% | 55.043% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene HungarianLightStemFilter | 14.748% | 14.777% | 14.086% | Light suffix stemmer; intentionally narrower than Radixor's dictionary-trained transformation model. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `hungarianRadixor` | 55.040 | 0.877 | 61.4 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene HungarianLightStemFilter | `hungarianLuceneHungarianLightStemFilter` | 88.848 | 3.854 | 99.1 | 1.614 | Light Hungarian suffix stemmer. |
| Official Snowball direct | `snowballDirect[HUNGARIAN]` | 167.949 | 9.811 | 187.2 | 3.051 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[HUNGARIAN]` | 186.896 | 11.344 | 208.4 | 3.396 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

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

Model `hu-hu-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 840,551 | 86.761% (86.100–87.668) | 86.876% (86.177–87.841) | 84.086% (83.633–85.050) |
| 20% | 746,292 | 88.280% (87.847–88.842) | 88.393% (87.931–88.968) | 85.865% (85.589–85.920) |
| 30% | 651,802 | 89.047% (88.909–89.354) | 89.126% (89.006–89.437) | 87.178% (86.603–87.420) |
| 40% | 557,829 | 90.002% (89.679–90.181) | 90.053% (89.707–90.254) | 88.776% (88.318–89.016) |
| 50% | 463,693 | 90.729% (90.537–90.756) | 90.777% (90.567–90.810) | 89.530% (89.200–89.806) |
| 60% | 370,552 | 91.156% (91.058–91.366) | 91.187% (91.074–91.418) | 90.673% (90.092–91.129) |
| 70% | 277,343 | 91.756% (91.390–92.053) | 91.744% (91.350–92.058) | 91.940% (90.827–92.376) |
| 80% | 184,401 | 91.771% (91.652–92.138) | 91.756% (91.609–92.123) | 92.536% (92.147–92.726) |
| 90% | 92,253 | 92.212% (91.551–92.510) | 92.203% (91.476–92.460) | 93.446% (92.426–93.786) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **86.876%**
  at 10% training knowledge to **92.203%** at 90%, a measured
  **+5.327 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+5.450 pp** and
  preservation of unseen already-root forms changes by **+9.360 pp**. These separate
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

This section interprets the edit-cost and held-out-family experiment for `HU_HU`
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
| 19,406 | 935,713 | 95.86% | 462 | 12 | 19.50× | 54 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 86.780% | 86.780% | +0.000 pp | 0.9561 | 0.9561 | 1.000× | 0.876× |
| 20% | 88.502% | 88.502% | +0.000 pp | 0.9625 | 0.9625 | 1.000× | 0.896× |
| 30% | 89.578% | 89.578% | +0.000 pp | 0.9667 | 0.9667 | 1.000× | 0.888× |
| 40% | 90.256% | 90.256% | +0.000 pp | 0.9690 | 0.9690 | 1.000× | 0.888× |
| 50% | 90.699% | 90.699% | +0.000 pp | 0.9703 | 0.9703 | 1.000× | 0.889× |
| 60% | 91.040% | 91.040% | +0.000 pp | 0.9719 | 0.9719 | 1.000× | 0.889× |
| 70% | 91.447% | 91.447% | +0.000 pp | 0.9726 | 0.9726 | 1.000× | 0.885× |
| 80% | 91.759% | 91.759% | +0.000 pp | 0.9743 | 0.9743 | 1.000× | 0.888× |
| 90% | 91.867% | 91.867% | +0.000 pp | 0.9748 | 0.9748 | 1.000× | 0.888× |

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
| `patch_command_ratio` | `value_references` | +0.910 | +0.849…+1.000 | 45 |
| `replace_to_delete_insert` | `patch_command_ratio` | -0.778 | -0.849…-0.584 | 45 |
| `replace_to_delete_insert` | `value_references` | -0.509 | -0.577…-0.371 | 45 |
| `delete_cost` | `patch_command_ratio` | +0.399 | +0.338…+0.407 | 45 |
| `insert_cost` | `patch_command_ratio` | +0.399 | +0.338…+0.407 | 45 |
| `replace_cost` | `patch_command_ratio` | -0.509 | -0.593…-0.328 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `replace_to_delete_insert` | `unseen_changed_exact` | +0.849 | +0.849…+0.849 | no | 1 / 45 |
| `trie_edges` | `unseen_f05` | -0.694 | -0.748…-0.010 | no | 24 / 45 |
| `replace_to_delete_insert` | `unseen_over_percent` | -0.709 | -0.889…+0.730 | no | 9 / 45 |
| `trie_edges` | `unseen_under_percent` | +0.707 | +0.042…+0.851 | no | 24 / 45 |

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **86.780%** at 10% knowledge to **91.867%** at 90%, a **+5.087 pp** measured knowledge effect.
- The predeclared selection is `D1I1R10M0`. Its median unseen changed-form exactness differs from baseline by **+0.000 pp** and it reduces the median retained-command count by **11.17%** (0.888× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+5.087 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The non-baseline setting is an efficiency candidate, not a production default: it was selected and evaluated on the same matrix and therefore requires external-corpus or external-dictionary validation before adoption.
- No cost or representation predictor is both defined in all 45 strata and retains one association sign over the central 95% interval for an unseen-form quality outcome. Effects with partial coverage are insufficient for a stable language-level claim; the remaining measured effects are heterogeneous across knowledge levels and splits.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `HU_HU` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `hu-hu-default`, loaded from classpath resource `org/egothor/stemmer/models/hu-hu-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.995555** among 4 deterministic stemmers. The runner-up is `SNOWBALL HUNGARIAN LUCENE FILTER` at 0.822963, a difference of 0.172592. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.996227** among 4 deterministic stemmers. The runner-up is `SNOWBALL HUNGARIAN DIRECT` at 0.822077, a difference of 0.174151. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.995555|&lt;0.000001%|0.889037%|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|0.822963|0.000378%|35.407050%|
|3|SNOWBALL HUNGARIAN DIRECT|0.822704|0.000309%|35.458800%|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|0.816967|0.000915%|36.605593%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999998|0.991110|1.000000|0.995555|1.000000|0.000000|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.901236|0.645929|0.999996|0.822963|0.999977|0.000023|
|3|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.917622|0.645412|0.999997|0.822704|0.999978|0.000022|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.786953|0.633944|0.999991|0.816967|0.999971|0.000029|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998208|0.995534|0.992875|0.991108|0.995544|0.995544|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.835212|0.752518|0.684724|0.603229|0.762978|0.762967|
|3|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.846240|0.757814|0.686119|0.610064|0.769574|0.769564|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.750715|0.702210|0.659593|0.541082|0.706318|0.706304|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|21921219|39|196636|414653743434|39 / 414653743473|196636 / 22117855|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|14286575|1565633|7831280|414652177840|1565633 / 414653743473|7831280 / 22117855|
|3|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|14275129|1281527|7842726|414652461946|1281527 / 414653743473|7842726 / 22117855|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|14021483|3795942|8096372|414649947531|3795942 / 414653743473|8096372 / 22117855|

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
|Radixor|0 / 414653743473|0 / 22117855|

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
|1|Radixor|ALL_CANDIDATES|0.999991|1.000000|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999993|0.999996|0.999998|0.999991|0.999996|0.999996|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|22117855|192|0|414653743281|192 / 414653743473|0 / 22117855|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|196636|39|153|6664|0.731754%|5|917595|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.996227|&lt;0.000001%|0.754564%|
|2|SNOWBALL HUNGARIAN DIRECT|0.822077|0.000334%|35.584346%|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|0.822077|0.000334%|35.584346%|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|0.815385|0.000869%|36.922109%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999998|0.992454|1.000000|0.996227|1.000000|0.000000|
|2|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.915319|0.644157|0.999997|0.822077|0.999977|0.000023|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.915319|0.644157|0.999997|0.822077|0.999977|0.000023|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.802756|0.630779|0.999991|0.815385|0.999971|0.000029|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998480|0.996212|0.993954|0.992453|0.996219|0.996219|
|2|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.844241|0.756163|0.684726|0.607928|0.767860|0.767849|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.844241|0.756163|0.684726|0.607928|0.767860|0.767849|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.761246|0.706452|0.659016|0.546135|0.711591|0.711577|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|21206087|39|161230|380936197647|39 / 380936197686|161230 / 21367317|
|2|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|13763897|1273370|7603420|380934924316|1273370 / 380936197686|7603420 / 21367317|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|13763897|1273370|7603420|380934924316|1273370 / 380936197686|7603420 / 21367317|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|13478053|3311675|7889264|380932886011|3311675 / 380936197686|7889264 / 21367317|

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
|Radixor|0 / 380936197686|0 / 21367317|

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
|1|Radixor|ALL_CANDIDATES|0.999991|1.000000|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999993|0.999996|0.999998|0.999991|0.999996|0.999996|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|21367317|192|0|380936197494|192 / 380936197686|0 / 21367317|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|161230|39|153|5518|0.632162%|5|878574|

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
- Dictionary language: `HU_HU`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
