# Czech Stemmer Benchmarks

This page reports same-language stemming benchmarks for Czech. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `cs-cz-default` | `1.0.0` | `CS_CZ` | 5,113 | 56,612 | 10,049 | 46,563 | 46,563 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **56,612**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 711 | 1.256% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 22,643 | 39.997% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 15,007 | 26.509% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 10,046 | 17.745% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 8,205 | 14.493% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.465% | 99.439% | 99.582% | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | 84.850% | 82.269% | 96.806% | Benchmark-only Czech Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene CzechStemFilter | 16.784% | 15.538% | 22.559% | Lucene Czech suffix stemmer implemented as a TokenFilter. |
| Official Snowball direct | 19.865% | 18.186% | 27.645% | Official Snowball 3.1.0 generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `czechRadixor` | 3.292 | 0.065 | 70.7 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 378.764 | 38.312 | 8134.4 | 115.055 | Benchmark-only Czech Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene CzechStemFilter | `czechLuceneCzechStemFilter` | 2.958 | 0.050 | 63.5 | 0.898 | Czech suffix stemmer implemented as a Lucene TokenFilter. |
| Official Snowball direct | `snowballDirect[CZECH]` | 4.057 | 0.516 | 87.1 | 1.232 | Official Snowball 3.1.0 generated Java stemmer; direct API. |

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

Model `cs-cz-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 50,905 | 69.921% (66.235–70.712) | 65.439% (60.991–66.671) | 89.636% (88.686–90.665) |
| 20% | 45,111 | 72.003% (69.047–72.412) | 68.155% (64.204–68.726) | 89.767% (89.232–91.428) |
| 30% | 39,562 | 73.325% (71.387–74.104) | 69.913% (67.160–70.569) | 90.571% (89.035–90.982) |
| 40% | 33,944 | 74.037% (73.092–74.837) | 70.380% (69.385–71.348) | 90.721% (89.903–91.126) |
| 50% | 28,195 | 74.013% (73.648–74.299) | 70.612% (69.908–70.743) | 91.128% (89.182–91.481) |
| 60% | 22,567 | 74.064% (73.458–75.665) | 70.641% (69.532–72.243) | 91.201% (89.195–91.780) |
| 70% | 16,910 | 74.517% (73.186–75.890) | 70.679% (69.464–72.757) | 91.409% (90.430–92.421) |
| 80% | 11,295 | 74.375% (72.961–75.341) | 70.977% (68.757–71.820) | 91.822% (89.763–92.700) |
| 90% | 5,497 | 73.270% (71.970–75.506) | 69.900% (67.596–72.033) | 91.296% (89.606–92.449) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **65.439%**
  at 10% training knowledge to **69.900%** at 90%, a measured
  **+4.461 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+3.349 pp** and
  preservation of unseen already-root forms changes by **+1.660 pp**. These separate
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

This section interprets the edit-cost and held-out-family experiment for `CS_CZ`
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
| 5,113 | 56,612 | 82.25% | 537 | 12 | 19.50× | 54 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 64.331% | 64.331% | +0.000 pp | 0.9049 | 0.9049 | 1.000× | 0.953× |
| 20% | 66.560% | 66.560% | +0.000 pp | 0.9085 | 0.9085 | 1.000× | 0.935× |
| 30% | 68.753% | 68.753% | +0.000 pp | 0.9203 | 0.9203 | 1.000× | 0.915× |
| 40% | 69.876% | 69.876% | +0.000 pp | 0.9233 | 0.9233 | 1.000× | 0.928× |
| 50% | 70.887% | 70.887% | +0.000 pp | 0.9279 | 0.9278 | 1.000× | 0.931× |
| 60% | 70.925% | 70.925% | +0.000 pp | 0.9300 | 0.9300 | 1.000× | 0.940× |
| 70% | 71.428% | 71.428% | +0.000 pp | 0.9327 | 0.9327 | 1.000× | 0.931× |
| 80% | 71.146% | 71.146% | +0.000 pp | 0.9310 | 0.9310 | 1.000× | 0.952× |
| 90% | 70.970% | 70.970% | +0.000 pp | 0.9300 | 0.9300 | 1.000× | 0.957× |

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
| `patch_command_ratio` | `trie_nodes` | +0.932 | +0.478…+1.000 | 45 |
| `patch_command_ratio` | `value_references` | +0.965 | +0.477…+1.000 | 45 |
| `replace_cost` | `trie_edges` | +0.741 | +0.135…+0.775 | 45 |
| `match_cost` | `trie_edges` | -0.145 | -0.189…-0.132 | 45 |
| `match_cost` | `dense_table_slots` | -0.146 | -0.205…-0.132 | 45 |
| `match_cost` | `patch_command_ratio` | -0.151 | -0.217…-0.130 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `average_path_length` | `unseen_changed_exact` | -1.000 | -1.000…+1.000 | no | 38 / 45 |
| `average_path_length` | `unseen_f05` | -1.000 | -1.000…+0.875 | no | 41 / 45 |
| `replace_to_delete_insert` | `unseen_over_percent` | -0.730 | -0.876…-0.396 | no | 17 / 45 |
| `average_path_length` | `unseen_under_percent` | +1.000 | -1.000…+1.000 | no | 41 / 45 |

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **64.331%** at 10% knowledge to **70.970%** at 90%, a **+6.639 pp** measured knowledge effect.
- The predeclared selection is `D10I10R1M1`. Its median unseen changed-form exactness differs from baseline by **+0.000 pp** and it reduces the median retained-command count by **5.83%** (0.942× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+6.639 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The non-baseline setting is an efficiency candidate, not a production default: it was selected and evaluated on the same matrix and therefore requires external-corpus or external-dictionary validation before adoption.
- No cost or representation predictor is both defined in all 45 strata and retains one association sign over the central 95% interval for an unseen-form quality outcome. Effects with partial coverage are insufficient for a stable language-level claim; the remaining measured effects are heterogeneous across knowledge levels and splits.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `CS_CZ` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `cs-cz-default`, loaded from classpath resource `org/egothor/stemmer/models/cs-cz-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.996617** among 4 deterministic stemmers. The runner-up is `HUNSPELL CZECH LUCENE FILTER` at 0.854132, a difference of 0.142485. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.997195** among 4 deterministic stemmers. The runner-up is `HUNSPELL CZECH LUCENE FILTER` at 0.853150, a difference of 0.144045. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **8 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.996617|0.000000%|0.676519%|
|2|HUNSPELL CZECH LUCENE FILTER|0.854132|0.000691%|29.172837%|
|3|CZECH LUCENE CZECH STEM FILTER|0.794343|0.000928%|41.130549%|
|4|SNOWBALL CZECH DIRECT|0.786366|0.000904%|42.725842%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.993235|1.000000|0.996617|0.999998|0.000002|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.958877|0.708272|0.999993|0.854132|0.999927|0.000073|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.935210|0.588695|0.999991|0.794343|0.999897|0.000103|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|0.935153|0.572742|0.999991|0.786366|0.999894|0.000106|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998640|0.996606|0.994581|0.993235|0.996612|0.996611|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.895506|0.814739|0.747335|0.687392|0.824103|0.824070|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.836710|0.722556|0.635811|0.565626|0.741992|0.741949|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|0.830101|0.710396|0.620864|0.550864|0.731848|0.731804|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|298476|0|2033|1320705191|0 / 1320705191|2033 / 300509|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|212842|9128|87667|1320696063|9128 / 1320705191|87667 / 300509|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|176908|12256|123601|1320692935|12256 / 1320705191|123601 / 300509|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|172114|11935|128395|1320693256|11935 / 1320705191|128395 / 300509|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.000000%|
|HUNSPELL CZECH LUCENE FILTER|0.000650%|25.611213%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 1320705191|0 / 300509|
|HUNSPELL CZECH LUCENE FILTER|8582 / 1320705191|76964 / 300509|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|HUNSPELL CZECH LUCENE FILTER|0.871940|0.000816%|25.611213%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.954016|0.743888|0.999992|0.871940|0.999934|0.000066|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.903001|0.835949|0.778167|0.718138|0.842426|0.842395|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|300509|0|0|1320705191|0 / 1320705191|0 / 300509|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|223545|10775|76964|1320694416|10775 / 1320705191|76964 / 300509|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|2033|0|0|321|0.624501%|4|51739|
|HUNSPELL CZECH LUCENE FILTER|10703|546|1647|3194|6.213887%|5|55179|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **8 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.997195|0.000000%|0.561033%|
|2|HUNSPELL CZECH LUCENE FILTER|0.853150|0.000700%|29.369351%|
|3|CZECH LUCENE CZECH STEM FILTER|0.792522|0.000918%|41.494586%|
|4|SNOWBALL CZECH DIRECT|0.784821|0.000923%|43.034822%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.994390|1.000000|0.997195|0.999999|0.000001|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.958957|0.706306|0.999993|0.853150|0.999925|0.000075|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.936557|0.585054|0.999991|0.792522|0.999895|0.000105|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|0.934577|0.569652|0.999991|0.784821|0.999891|0.000109|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998873|0.997187|0.995507|0.994390|0.997191|0.997190|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.894932|0.813466|0.745594|0.685581|0.822993|0.822960|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.836092|0.720206|0.632534|0.562751|0.740227|0.740184|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|0.828436|0.707849|0.617907|0.547807|0.729646|0.729601|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|295818|0|1669|1284770069|0 / 1284770069|1669 / 297487|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|210117|8993|87370|1284761076|8993 / 1284770069|87370 / 297487|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|174046|11790|123441|1284758279|11790 / 1284770069|123441 / 297487|
|4|SNOWBALL CZECH DIRECT|PRIMARY_OUTPUT|169464|11863|128023|1284758206|11863 / 1284770069|128023 / 297487|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.000000%|
|HUNSPELL CZECH LUCENE FILTER|0.000663%|25.840457%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 1284770069|0 / 297487|
|HUNSPELL CZECH LUCENE FILTER|8518 / 1284770069|76872 / 297487|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|HUNSPELL CZECH LUCENE FILTER|0.870794|0.000819%|25.840457%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.954473|0.741595|0.999992|0.870794|0.999932|0.000068|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.902651|0.834675|0.776220|0.716259|0.841328|0.841297|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|297487|0|0|1284770069|0 / 1284770069|0 / 297487|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|220615|10523|76872|1284759546|10523 / 1284770069|76872 / 297487|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|1669|0|0|269|0.530603%|4|50975|
|HUNSPELL CZECH LUCENE FILTER|10498|475|1530|3117|6.148293%|5|54394|

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
- Dictionary language: `CS_CZ`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
