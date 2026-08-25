# Portuguese Stemmer Benchmarks

This page reports same-language stemming benchmarks for Portuguese. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `pt-pt-default` | `1.0.0` | `PT_PT` | 4,001 | 215,490 | 8,002 | 207,488 | 207,488 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **215,490**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 3,806 | 1.766% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 120,535 | 55.935% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 71,284 | 33.080% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 8,003 | 3.714% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 11,862 | 5.505% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.815% | 99.808% | 100.000% | Radixor dictionary-trained patch-command stemmer. |
| Lucene PortugueseLightStemFilter | 8.966% | 5.558% | 97.326% | Light suffix stemmer; intentionally narrower than Radixor's dictionary-trained transformation model. |
| Lucene PortugueseMinimalStemFilter | 5.539% | 1.896% | 100.000% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SnowballFilter | 0.625% | 0.558% | 2.374% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 0.625% | 0.558% | 2.374% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene PortugueseStemFilter | 0.312% | 0.308% | 0.425% | Portuguese RSLP-style Lucene TokenFilter stemmer. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `portugueseRadixor` | 11.444 | 0.204 | 55.2 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene PortugueseLightStemFilter | `portugueseLucenePortugueseLightStemFilter` | 10.557 | 0.211 | 50.9 | 0.923 | Light Portuguese suffix stemmer. |
| Lucene PortugueseMinimalStemFilter | `portugueseLucenePortugueseMinimalStemFilter` | 14.744 | 0.203 | 71.1 | 1.288 | Minimal Portuguese suffix reducer. |
| Official Snowball direct | `snowballDirect[PORTUGUESE]` | 51.852 | 2.250 | 249.9 | 4.531 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[PORTUGUESE]` | 62.553 | 1.666 | 301.5 | 5.466 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |
| Lucene PortugueseStemFilter | `portugueseLucenePortugueseStemFilter` | 144.379 | 3.988 | 695.8 | 12.616 | Portuguese RSLP-style Lucene TokenFilter. |

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

Model `pt-pt-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 193,881 | 90.423% (88.898–91.005) | 90.053% (88.476–90.666) | 99.833% (99.778–100.000) |
| 20% | 172,258 | 91.775% (90.674–92.113) | 91.460% (90.315–91.809) | 99.938% (99.750–100.000) |
| 30% | 150,645 | 92.584% (91.628–93.106) | 92.297% (91.306–92.854) | 99.893% (99.643–100.000) |
| 40% | 129,092 | 93.154% (92.758–94.021) | 92.889% (92.480–93.795) | 99.958% (99.875–100.000) |
| 50% | 107,492 | 93.601% (93.084–94.280) | 93.358% (92.822–94.061) | 99.900% (99.800–100.000) |
| 60% | 85,970 | 93.987% (93.386–94.179) | 93.758% (93.130–93.957) | 99.938% (99.937–100.000) |
| 70% | 64,495 | 93.982% (93.731–94.081) | 93.752% (93.489–93.852) | 100.000% (99.917–100.000) |
| 80% | 43,021 | 94.273% (93.721–94.673) | 94.056% (93.478–94.472) | 100.000% (99.875–100.000) |
| 90% | 21,519 | 94.620% (93.771–95.072) | 94.412% (93.530–94.882) | 100.000% (99.749–100.000) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **90.053%**
  at 10% training knowledge to **94.412%** at 90%, a measured
  **+4.359 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+4.197 pp** and
  preservation of unseen already-root forms changes by **+0.167 pp**. These separate
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

This section interprets the edit-cost and held-out-family experiment for `PT_PT`
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
| 4,001 | 215,490 | 96.29% | 369 | 9 | 26.00× | 77 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 89.204% | 89.204% | +0.000 pp | 0.9611 | 0.9611 | 1.000× | 0.818× |
| 20% | 91.244% | 91.244% | +0.000 pp | 0.9691 | 0.9691 | 1.000× | 0.808× |
| 30% | 91.972% | 91.972% | +0.000 pp | 0.9716 | 0.9716 | 1.000× | 0.811× |
| 40% | 92.514% | 92.514% | +0.000 pp | 0.9735 | 0.9735 | 1.000× | 0.810× |
| 50% | 93.092% | 93.092% | +0.000 pp | 0.9756 | 0.9756 | 1.000× | 0.807× |
| 60% | 93.547% | 93.547% | +0.000 pp | 0.9769 | 0.9769 | 1.000× | 0.813× |
| 70% | 93.644% | 93.644% | +0.000 pp | 0.9771 | 0.9771 | 1.000× | 0.814× |
| 80% | 93.887% | 93.887% | +0.000 pp | 0.9774 | 0.9774 | 1.000× | 0.816× |
| 90% | 94.378% | 94.378% | +0.000 pp | 0.9805 | 0.9805 | 1.000× | 0.825× |

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
| `patch_command_ratio` | `trie_nodes` | +1.000 | +0.965…+1.000 | 45 |
| `patch_command_ratio` | `value_references` | +1.000 | +0.965…+1.000 | 45 |
| `replace_to_delete_insert` | `trie_nodes` | +0.935 | +0.892…+0.935 | 45 |
| `replace_to_delete_insert` | `value_references` | +0.935 | +0.892…+0.935 | 45 |
| `replace_to_delete_insert` | `patch_command_ratio` | +0.935 | +0.786…+0.935 | 45 |
| `replace_cost` | `trie_nodes` | +0.794 | +0.758…+0.794 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `average_path_length` | `unseen_changed_exact` | -1.000 | -1.000…-1.000 | no | 43 / 45 |
| `average_path_length` | `unseen_f05` | -1.000 | -1.000…-1.000 | no | 43 / 45 |
| `average_path_length` | `unseen_over_percent` | -1.000 | -1.000…-1.000 | no | 8 / 45 |
| `average_path_length` | `unseen_under_percent` | +1.000 | +1.000…+1.000 | no | 43 / 45 |

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **89.204%** at 10% knowledge to **94.378%** at 90%, a **+5.174 pp** measured knowledge effect.
- The predeclared selection is `D10I10R1M1`. Its median unseen changed-form exactness differs from baseline by **+0.000 pp** and it reduces the median retained-command count by **18.75%** (0.812× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+5.174 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The non-baseline setting is an efficiency candidate, not a production default: it was selected and evaluated on the same matrix and therefore requires external-corpus or external-dictionary validation before adoption.
- No cost or representation predictor is both defined in all 45 strata and retains one association sign over the central 95% interval for an unseen-form quality outcome. Effects with partial coverage are insufficient for a stable language-level claim; the remaining measured effects are heterogeneous across knowledge levels and splits.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `PT_PT` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `pt-pt-default`, loaded from classpath resource `org/egothor/stemmer/models/pt-pt-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.998542** among 6 deterministic stemmers. The runner-up is `SNOWBALL PORTUGUESE DIRECT` at 0.938922, a difference of 0.059620. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.998542** among 6 deterministic stemmers. The runner-up is `SNOWBALL PORTUGUESE DIRECT` at 0.938922, a difference of 0.059620. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **8 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.998542|0.000000%|0.291615%|
|2|SNOWBALL PORTUGUESE DIRECT|0.938922|0.000656%|12.214929%|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|0.938922|0.000656%|12.214929%|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|0.846554|0.000364%|30.688771%|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|0.513632|0.000006%|97.273598%|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|0.503949|&lt;0.000001%|99.210240%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.997084|1.000000|0.998542|0.999999|0.000001|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.970538|0.877851|0.999993|0.938922|0.999963|0.000037|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.970538|0.877851|0.999993|0.938922|0.999963|0.000037|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.979145|0.693112|0.999996|0.846554|0.999921|0.000079|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.991719|0.027264|1.000000|0.513632|0.999760|0.000240|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.999608|0.007898|1.000000|0.503949|0.999756|0.000244|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999415|0.998540|0.997666|0.997084|0.998541|0.998541|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.950467|0.921871|0.894944|0.855065|0.923032|0.923014|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.950467|0.921871|0.894944|0.855065|0.923032|0.923014|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.904492|0.811666|0.736120|0.683029|0.823807|0.823773|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.122815|0.053069|0.033847|0.027258|0.164433|0.164413|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.038278|0.015671|0.009853|0.007898|0.088851|0.088840|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|5470353|0|15999|22274113243|0 / 22274113243|15999 / 5486352|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|4816198|146201|670154|22273967042|146201 / 22274113243|670154 / 5486352|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|4816198|146201|670154|22273967042|146201 / 22274113243|670154 / 5486352|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|3802658|80995|1683694|22274032248|80995 / 22274113243|1683694 / 5486352|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|149580|1249|5336772|22274111994|1249 / 22274113243|5336772 / 5486352|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|43329|17|5443023|22274113226|17 / 22274113243|5443023 / 5486352|

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
|Radixor|0 / 22274113243|0 / 5486352|

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
|1|Radixor|ALL_CANDIDATES|5486352|0|0|22274113243|0 / 22274113243|0 / 5486352|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|15999|0|0|392|0.185702%|3|211489|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **8 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.998542|0.000000%|0.291615%|
|2|SNOWBALL PORTUGUESE DIRECT|0.938922|0.000656%|12.214929%|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|0.938922|0.000656%|12.214929%|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|0.846554|0.000364%|30.688771%|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|0.513632|0.000006%|97.273598%|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|0.503949|&lt;0.000001%|99.210240%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.997084|1.000000|0.998542|0.999999|0.000001|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.970538|0.877851|0.999993|0.938922|0.999963|0.000037|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.970538|0.877851|0.999993|0.938922|0.999963|0.000037|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.979145|0.693112|0.999996|0.846554|0.999921|0.000079|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.991719|0.027264|1.000000|0.513632|0.999760|0.000240|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.999608|0.007898|1.000000|0.503949|0.999756|0.000244|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999415|0.998540|0.997666|0.997084|0.998541|0.998541|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.950467|0.921871|0.894944|0.855065|0.923032|0.923014|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.950467|0.921871|0.894944|0.855065|0.923032|0.923014|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.904492|0.811666|0.736120|0.683029|0.823807|0.823773|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.122815|0.053069|0.033847|0.027258|0.164433|0.164413|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.038278|0.015671|0.009853|0.007898|0.088851|0.088840|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|5470353|0|15999|22274113243|0 / 22274113243|15999 / 5486352|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|4816198|146201|670154|22273967042|146201 / 22274113243|670154 / 5486352|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|4816198|146201|670154|22273967042|146201 / 22274113243|670154 / 5486352|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|3802658|80995|1683694|22274032248|80995 / 22274113243|1683694 / 5486352|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|149580|1249|5336772|22274111994|1249 / 22274113243|5336772 / 5486352|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|43329|17|5443023|22274113226|17 / 22274113243|5443023 / 5486352|

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
|Radixor|0 / 22274113243|0 / 5486352|

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
|1|Radixor|ALL_CANDIDATES|5486352|0|0|22274113243|0 / 22274113243|0 / 5486352|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|15999|0|0|392|0.185702%|3|211489|

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
- Dictionary language: `PT_PT`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
