# Swedish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Swedish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `sv-se-default` | `1.0.0` | `SV_SE` | 12,371 | 110,468 | 24,731 | 85,737 | 85,737 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **110,468**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 711 | 0.644% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 14,126 | 12.787% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 68,749 | 62.234% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 23,583 | 21.348% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 3,299 | 2.986% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 96.713% | 97.407% | 94.307% | Radixor dictionary-trained patch-command stemmer. |
| Lucene SwedishMinimalStemFilter | 49.532% | 49.186% | 50.730% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SwedishLightStemFilter | 45.672% | 46.383% | 43.209% | Light suffix stemmer; intentionally narrower than Radixor's dictionary-trained transformation model. |
| Official Snowball direct | 40.068% | 37.512% | 48.926% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene SnowballFilter | 38.785% | 35.839% | 48.999% | Lucene TokenFilter integration path around the Snowball algorithm. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `swedishRadixor` | 5.232 | 0.112 | 61.0 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene SwedishMinimalStemFilter | `swedishLuceneSwedishMinimalStemFilter` | 4.504 | 0.065 | 52.5 | 0.861 | Minimal Swedish suffix reducer. |
| Lucene SwedishLightStemFilter | `swedishLuceneSwedishLightStemFilter` | 4.682 | 0.092 | 54.6 | 0.895 | Light Swedish suffix stemmer. |
| Official Snowball direct | `snowballDirect[SWEDISH]` | 8.027 | 0.979 | 93.6 | 1.534 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[SWEDISH]` | 10.296 | 0.977 | 120.1 | 1.968 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

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

Model `sv-se-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 98,793 | 72.010% (70.983–72.505) | 67.214% (66.144–68.191) | 87.789% (86.974–88.659) |
| 20% | 87,316 | 75.205% (74.488–75.890) | 70.861% (70.211–72.027) | 89.668% (89.346–90.344) |
| 30% | 75,916 | 77.249% (76.663–77.381) | 73.248% (72.517–73.345) | 91.335% (90.486–91.670) |
| 40% | 64,540 | 78.689% (78.113–78.970) | 74.894% (74.347–75.197) | 91.778% (91.278–92.147) |
| 50% | 53,431 | 79.553% (79.081–79.924) | 75.813% (75.376–76.278) | 92.204% (92.067–92.705) |
| 60% | 42,503 | 80.584% (80.441–80.710) | 77.113% (76.939–77.166) | 93.114% (92.742–93.192) |
| 70% | 31,613 | 81.378% (80.737–81.694) | 77.755% (77.158–78.370) | 93.450% (93.333–94.124) |
| 80% | 20,899 | 81.612% (81.194–82.294) | 78.137% (77.386–78.776) | 94.577% (93.139–94.972) |
| 90% | 10,386 | 82.188% (81.351–82.660) | 78.458% (77.818–79.022) | 94.926% (93.807–95.482) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **67.214%**
  at 10% training knowledge to **78.458%** at 90%, a measured
  **+11.244 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+10.177 pp** and
  preservation of unseen already-root forms changes by **+7.137 pp**. These separate
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

This section interprets the edit-cost and held-out-family experiment for `SV_SE`
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
| 12,371 | 110,468 | 77.61% | 332 | 10 | 23.40× | 59 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 67.345% | 67.345% | +0.000 pp | 0.8694 | 0.8695 | 1.000× | 0.938× |
| 20% | 70.754% | 70.754% | +0.000 pp | 0.8839 | 0.8839 | 1.000× | 0.936× |
| 30% | 72.696% | 72.696% | +0.000 pp | 0.8936 | 0.8936 | 1.000× | 0.947× |
| 40% | 74.618% | 74.618% | +0.000 pp | 0.9004 | 0.9004 | 1.000× | 0.954× |
| 50% | 75.990% | 75.990% | +0.000 pp | 0.9070 | 0.9070 | 1.000× | 0.951× |
| 60% | 76.587% | 76.587% | +0.000 pp | 0.9099 | 0.9099 | 1.000× | 0.955× |
| 70% | 77.567% | 77.567% | +0.000 pp | 0.9140 | 0.9140 | 1.000× | 0.953× |
| 80% | 78.862% | 78.862% | +0.000 pp | 0.9218 | 0.9218 | 1.000× | 0.953× |
| 90% | 79.922% | 79.922% | +0.000 pp | 0.9247 | 0.9247 | 1.000× | 0.954× |

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
| `patch_command_ratio` | `trie_nodes` | +0.986 | +0.576…+1.000 | 45 |
| `replace_to_delete_insert` | `value_references` | -0.436 | -0.662…-0.436 | 45 |
| `delete_cost` | `value_references` | +0.291 | +0.291…+0.349 | 45 |
| `insert_cost` | `value_references` | +0.291 | +0.291…+0.349 | 45 |
| `replace_cost` | `value_references` | -0.200 | -0.419…-0.200 | 45 |
| `match_cost` | `trie_nodes` | -0.227 | -0.229…-0.166 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `replace_to_delete_insert` | `unseen_f05` | +0.849 | -0.255…+0.881 | no | 6 / 45 |
| `replace_to_delete_insert` | `unseen_over_percent` | -0.849 | -0.881…+0.255 | no | 6 / 45 |
| `replace_to_delete_insert` | `unseen_under_percent` | +0.730 | +0.730…+0.730 | no | 1 / 45 |

No within-stratum coefficient is defined for `unseen_changed_exact` because these outcomes do not vary across cost configurations in the measured language strata. Within this matrix, that is observed cost insensitivity for those outcomes, not missing measurement.

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **67.345%** at 10% knowledge to **79.922%** at 90%, a **+12.577 pp** measured knowledge effect.
- The predeclared selection is `D10I10R1M1`. Its median unseen changed-form exactness differs from baseline by **+0.000 pp** and it reduces the median retained-command count by **4.72%** (0.953× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+12.577 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The non-baseline setting is an efficiency candidate, not a production default: it was selected and evaluated on the same matrix and therefore requires external-corpus or external-dictionary validation before adoption.
- No cost or representation predictor is both defined in all 45 strata and retains one association sign over the central 95% interval for an unseen-form quality outcome. Effects with partial coverage are insufficient for a stable language-level claim; the remaining measured effects are heterogeneous across knowledge levels and splits.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `SV_SE` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `sv-se-default`, loaded from classpath resource `org/egothor/stemmer/models/sv-se-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.977619** among 5 deterministic stemmers. The runner-up is `SNOWBALL SWEDISH DIRECT` at 0.808543, a difference of 0.169076. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.977573** among 5 deterministic stemmers. The runner-up is `SNOWBALL SWEDISH DIRECT` at 0.808611, a difference of 0.168961. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **7 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.977619|0.000000%|4.476263%|
|2|SNOWBALL SWEDISH DIRECT|0.808543|0.000821%|38.290570%|
|3|SNOWBALL SWEDISH LUCENE FILTER|0.800222|0.000775%|39.954747%|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|0.797907|0.000439%|40.418073%|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|0.785227|0.000534%|42.954113%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.955237|1.000000|0.977619|0.999996|0.000004|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.863080|0.617094|0.999992|0.808543|0.999960|0.000040|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.866630|0.600453|0.999992|0.800222|0.999959|0.000041|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.919176|0.595819|0.999996|0.797907|0.999962|0.000038|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.899588|0.570459|0.999995|0.785227|0.999959|0.000041|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990715|0.977106|0.963866|0.955237|0.977362|0.977361|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.799353|0.719647|0.654396|0.562070|0.729796|0.729777|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.796053|0.709394|0.639751|0.549660|0.721367|0.721348|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.829176|0.722989|0.640913|0.566158|0.740043|0.740026|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.806522|0.698179|0.615497|0.536309|0.716364|0.716347|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|362653|0|16994|4529284143|0 / 4529284143|16994 / 379647|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|234278|37166|145369|4529246977|37166 / 4529284143|145369 / 379647|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|227960|35082|151687|4529249061|35082 / 4529284143|151687 / 379647|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|226201|19890|153446|4529264253|19890 / 4529284143|153446 / 379647|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|216573|24174|163074|4529259969|24174 / 4529284143|163074 / 379647|

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
|Radixor|0 / 4529284143|0 / 379647|

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
|1|Radixor|ALL_CANDIDATES|379647|0|0|4529284143|0 / 4529284143|0 / 379647|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|16994|0|0|2840|2.983789%|5|98108|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **7 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.977573|0.000000%|4.485467%|
|2|SNOWBALL SWEDISH DIRECT|0.808611|0.000824%|38.276920%|
|3|SNOWBALL SWEDISH LUCENE FILTER|0.800274|0.000778%|39.944519%|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|0.797785|0.000441%|40.442582%|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|0.785141|0.000536%|42.971167%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.955145|1.000000|0.977573|0.999996|0.000004|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.862864|0.617231|0.999992|0.808611|0.999960|0.000040|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.866412|0.600555|0.999992|0.800274|0.999959|0.000041|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.918993|0.595574|0.999996|0.797785|0.999962|0.000038|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.899375|0.570288|0.999995|0.785141|0.999959|0.000041|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990695|0.977058|0.963791|0.955145|0.977315|0.977314|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.799250|0.719665|0.654494|0.562091|0.729785|0.729766|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.795941|0.709393|0.639820|0.549658|0.721337|0.721319|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.828962|0.722752|0.640668|0.565867|0.739816|0.739800|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.806317|0.697987|0.615318|0.536083|0.716172|0.716155|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|361874|0|16994|4507704713|0 / 4507704713|16994 / 378868|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|233849|37166|145019|4507667547|37166 / 4507704713|145019 / 378868|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|227531|35082|151337|4507669631|35082 / 4507704713|151337 / 378868|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|225644|19890|153224|4507684823|19890 / 4507704713|153224 / 378868|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|216064|24174|162804|4507680539|24174 / 4507704713|162804 / 378868|

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
|Radixor|0 / 4507704713|0 / 378868|

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
|1|Radixor|ALL_CANDIDATES|378868|0|0|4507704713|0 / 4507704713|0 / 378868|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|16994|0|0|2840|2.990922%|5|97881|

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
- Dictionary language: `SV_SE`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
