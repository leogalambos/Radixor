# Ukrainian Stemmer Benchmarks

This page reports same-language stemming benchmarks for Ukrainian. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `uk-ua-default` | `1.0.0` | `UK_UA` | 1,493 | 15,737 | 2,985 | 12,752 | 12,752 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **15,737**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 267 | 1.697% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 4,156 | 26.409% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 5,883 | 37.383% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 2,962 | 18.822% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 2,469 | 15.689% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.307% | 99.365% | 99.062% | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | 86.815% | 83.759% | 99.866% | Benchmark-only Ukrainian Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene MorfologikFilter | 92.362% | 90.637% | 99.732% | Dictionary-based path; Morfologik can emit multiple terms. |
| Morfologik direct | 92.362% | 90.637% | 99.732% | Direct dictionary lookup; first returned stem is used for quality when no ranking weight is exposed. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `ukrainianRadixor` | 0.625 | 0.017 | 49.0 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 49.515 | 6.147 | 3883.0 | 79.197 | Benchmark-only Ukrainian Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Morfologik direct | `ukrainianMorfologikDirect` | 8.299 | 0.124 | 650.8 | 13.275 | Direct Morfologik dictionary lookup; first returned stem is used for quality. |
| Lucene MorfologikFilter | `ukrainianLuceneMorfologikFilter` | 14.580 | 0.206 | 1143.4 | 23.320 | Dictionary-based Morfologik TokenFilter; may emit multiple terms. |

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

Model `uk-ua-default` version `1.0.0` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 14,144 | 50.982% (50.365–54.332) | 42.530% (42.187–47.222) | 85.464% (84.667–88.227) |
| 20% | 12,528 | 55.281% (54.207–58.249) | 47.743% (46.925–51.631) | 87.400% (85.313–88.161) |
| 30% | 10,951 | 58.222% (57.492–59.390) | 51.048% (50.564–52.388) | 87.380% (86.917–89.403) |
| 40% | 9,392 | 58.933% (57.994–60.117) | 51.880% (50.380–53.150) | 89.013% (87.486–90.578) |
| 50% | 7,838 | 60.214% (59.232–62.314) | 53.268% (51.925–55.648) | 90.135% (88.140–91.180) |
| 60% | 6,256 | 61.416% (58.976–62.308) | 54.708% (52.041–55.903) | 90.211% (87.669–91.525) |
| 70% | 4,675 | 62.139% (60.712–63.062) | 55.456% (54.248–56.768) | 89.367% (85.811–90.562) |
| 80% | 3,166 | 62.236% (61.727–64.985) | 56.335% (54.703–59.359) | 88.475% (87.879–93.255) |
| 90% | 1,576 | 63.233% (61.940–65.736) | 57.218% (55.192–59.594) | 90.476% (88.514–93.243) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **42.530%**
  at 10% training knowledge to **57.218%** at 90%, a measured
  **+14.689 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+12.251 pp** and
  preservation of unseen already-root forms changes by **+5.012 pp**. These separate
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

This section interprets the edit-cost and held-out-family experiment for `UK_UA`
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
| 1,493 | 15,737 | 81.03% | 286 | 7 | 33.43× | 76 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 43.396% | 43.396% | +0.000 pp | 0.7798 | 0.7795 | 1.000× | 0.944× |
| 20% | 48.639% | 48.639% | +0.000 pp | 0.8092 | 0.8092 | 1.000× | 0.947× |
| 30% | 49.864% | 49.864% | +0.000 pp | 0.8168 | 0.8168 | 1.000× | 0.942× |
| 40% | 51.644% | 51.644% | +0.000 pp | 0.8298 | 0.8298 | 1.000× | 0.958× |
| 50% | 52.642% | 52.642% | +0.000 pp | 0.8348 | 0.8348 | 1.000× | 0.946× |
| 60% | 54.724% | 54.724% | +0.000 pp | 0.8443 | 0.8443 | 1.000× | 0.934× |
| 70% | 56.602% | 56.602% | +0.000 pp | 0.8487 | 0.8487 | 1.000× | 0.936× |
| 80% | 55.397% | 55.397% | +0.000 pp | 0.8552 | 0.8552 | 1.000× | 0.943× |
| 90% | 56.474% | 56.474% | +0.000 pp | 0.8561 | 0.8561 | 1.000× | 0.949× |

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
| `patch_command_ratio` | `trie_nodes` | +1.000 | +0.479…+1.000 | 45 |
| `patch_command_ratio` | `value_references` | +1.000 | +0.479…+1.000 | 45 |
| `match_cost` | `patch_command_ratio` | -0.217 | -0.227…-0.166 | 45 |
| `match_cost` | `trie_nodes` | -0.217 | -0.227…-0.166 | 45 |
| `match_cost` | `value_references` | -0.217 | -0.227…-0.166 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `average_path_length` | `unseen_changed_exact` | +1.000 | -1.000…+1.000 | no | 17 / 45 |
| `average_path_length` | `unseen_f05` | +1.000 | -1.000…+1.000 | no | 19 / 45 |
| `trie_edges` | `unseen_over_percent` | -0.641 | -0.641…-0.641 | no | 1 / 45 |
| `average_path_length` | `unseen_under_percent` | -1.000 | -1.000…+1.000 | no | 19 / 45 |

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **43.396%** at 10% knowledge to **56.474%** at 90%, a **+13.078 pp** measured knowledge effect.
- The predeclared selection is `D10I10R1M1`. Its median unseen changed-form exactness differs from baseline by **+0.000 pp** and it reduces the median retained-command count by **5.67%** (0.943× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+13.078 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The non-baseline setting is an efficiency candidate, not a production default: it was selected and evaluated on the same matrix and therefore requires external-corpus or external-dictionary validation before adoption.
- No cost or representation predictor is both defined in all 45 strata and retains one association sign over the central 95% interval for an unseen-form quality outcome. Effects with partial coverage are insufficient for a stable language-level claim; the remaining measured effects are heterogeneous across knowledge levels and splits.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `UK_UA` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `uk-ua-default`, loaded from classpath resource `org/egothor/stemmer/models/uk-ua-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.995816** among 4 deterministic stemmers. The runner-up is `UKRAINIAN LUCENE MORFOLOGIK FILTER` at 0.928906, a difference of 0.066910. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.995815** among 4 deterministic stemmers. The runner-up is `UKRAINIAN LUCENE MORFOLOGIK FILTER` at 0.928888, a difference of 0.066926. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **12 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.995816|0.000000%|0.836852%|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.928906|0.000028%|14.218810%|
|3|UKRAINIAN MORFOLOGIK DIRECT|0.928783|0.000028%|14.243378%|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|0.885789|0.000006%|22.842226%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.991631|1.000000|0.995816|0.999995|0.000005|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.999499|0.857812|1.000000|0.928906|0.999907|0.000093|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.999499|0.857566|1.000000|0.928783|0.999907|0.000093|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.999881|0.771578|1.000000|0.885789|0.999851|0.000149|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998315|0.995798|0.993294|0.991631|0.995807|0.995804|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.967537|0.923251|0.882842|0.857443|0.925949|0.925906|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.967474|0.923109|0.882634|0.857198|0.925817|0.925774|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.944015|0.871018|0.808499|0.771507|0.878343|0.878277|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|64580|0|545|100039050|0 / 100039050|545 / 65125|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|55865|28|9260|100039022|28 / 100039050|9260 / 65125|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|55849|28|9276|100039022|28 / 100039050|9276 / 65125|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|50249|6|14876|100039044|6 / 100039050|14876 / 65125|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL UKRAINIAN LUCENE FILTER|0.000000%|14.533589%|
|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.000000%|7.594626%|
|UKRAINIAN MORFOLOGIK DIRECT|0.000000%|7.619194%|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL UKRAINIAN LUCENE FILTER|0 / 100039050|9465 / 65125|
|UKRAINIAN LUCENE MORFOLOGIK FILTER|0 / 100039050|4946 / 65125|
|UKRAINIAN MORFOLOGIK DIRECT|0 / 100039050|4962 / 65125|
|Radixor|0 / 100039050|0 / 65125|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.962027|0.000059%|7.594626%|
|3|UKRAINIAN MORFOLOGIK DIRECT|0.961904|0.000059%|7.619194%|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|0.927332|0.000047%|14.533589%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.999021|0.924054|0.999999|0.962027|0.999950|0.000050|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|0.999020|0.923808|0.999999|0.961904|0.999950|0.000050|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|0.999156|0.854664|1.000000|0.927332|0.999905|0.000095|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.983070|0.960076|0.938133|0.923217|0.960806|0.960782|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|0.983014|0.959943|0.937931|0.922972|0.960678|0.960654|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|0.966477|0.921279|0.880120|0.854048|0.924090|0.924046|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|65125|0|0|100039050|0 / 100039050|0 / 65125|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|60179|59|4946|100038991|59 / 100039050|4946 / 65125|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|60163|59|4962|100038991|59 / 100039050|4962 / 65125|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|55660|47|9465|100039003|47 / 100039050|9465 / 65125|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL UKRAINIAN LUCENE FILTER|5411|6|41|1259|8.897527%|6|15577|
|UKRAINIAN LUCENE MORFOLOGIK FILTER|4314|28|31|2130|15.053004%|6|16748|
|UKRAINIAN MORFOLOGIK DIRECT|4314|28|31|2130|15.053004%|6|16748|
|Radixor|545|0|0|95|0.671378%|2|14245|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **12 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.995815|0.000000%|0.837058%|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.928888|0.000028%|14.222304%|
|3|UKRAINIAN MORFOLOGIK DIRECT|0.928888|0.000028%|14.222304%|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|0.885791|0.000006%|22.841696%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.991629|1.000000|0.995815|0.999995|0.000005|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.999499|0.857777|1.000000|0.928888|0.999907|0.000093|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.999499|0.857777|1.000000|0.928888|0.999907|0.000093|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.999881|0.771583|1.000000|0.885791|0.999851|0.000149|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998315|0.995797|0.993292|0.991629|0.995806|0.995803|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.967528|0.923231|0.882812|0.857408|0.925930|0.925887|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.967528|0.923231|0.882812|0.857408|0.925930|0.925887|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.944017|0.871021|0.808503|0.771512|0.878346|0.878280|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|64564|0|545|99911761|0 / 99911761|545 / 65109|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|55849|28|9260|99911733|28 / 99911761|9260 / 65109|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|55849|28|9260|99911733|28 / 99911761|9260 / 65109|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|50237|6|14872|99911755|6 / 99911761|14872 / 65109|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL UKRAINIAN LUCENE FILTER|0.000000%|14.537161%|
|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.000000%|7.596492%|
|UKRAINIAN MORFOLOGIK DIRECT|0.000000%|7.596492%|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL UKRAINIAN LUCENE FILTER|0 / 99911761|9465 / 65109|
|UKRAINIAN LUCENE MORFOLOGIK FILTER|0 / 99911761|4946 / 65109|
|UKRAINIAN MORFOLOGIK DIRECT|0 / 99911761|4946 / 65109|
|Radixor|0 / 99911761|0 / 65109|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.962017|0.000059%|7.596492%|
|3|UKRAINIAN MORFOLOGIK DIRECT|0.962017|0.000059%|7.596492%|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|0.927314|0.000047%|14.537161%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.999020|0.924035|0.999999|0.962017|0.999950|0.000050|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|0.999020|0.924035|0.999999|0.962017|0.999950|0.000050|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|0.999156|0.854628|1.000000|0.927314|0.999905|0.000095|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.983065|0.960066|0.938118|0.923199|0.960796|0.960772|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|0.983065|0.960066|0.938118|0.923199|0.960796|0.960772|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|0.966468|0.921258|0.880089|0.854012|0.924071|0.924027|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|65109|0|0|99911761|0 / 99911761|0 / 65109|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|60163|59|4946|99911702|59 / 99911761|4946 / 65109|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|60163|59|4946|99911702|59 / 99911761|4946 / 65109|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|55644|47|9465|99911714|47 / 99911761|9465 / 65109|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL UKRAINIAN LUCENE FILTER|5407|6|41|1258|8.896118%|6|15567|
|UKRAINIAN LUCENE MORFOLOGIK FILTER|4314|28|31|2130|15.062584%|6|16739|
|UKRAINIAN MORFOLOGIK DIRECT|4314|28|31|2130|15.062584%|6|16739|
|Radixor|545|0|0|95|0.671805%|2|14236|

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
- Dictionary language: `UK_UA`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
