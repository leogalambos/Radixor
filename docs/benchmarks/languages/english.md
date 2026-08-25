# English Stemmer Benchmarks

This page reports same-language stemming benchmarks for English. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). The command distribution, exact-root accuracy, and speed tables belong to the published 2026-08-25 Radixor/Java `4.2.0-6-g84e57fb` snapshot. Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

<!-- BENCHMARK-EVIDENCE-MAP:START -->
!!! info "How to read this page"
    Start with the [corpus](#dictionary-corpus) and [patch-command distribution](#radixor-patch-command-distribution), then compare [exact-root agreement](#accuracy) with [runtime](#speed). The [dictionary-family experiment](#dictionary-family-generalization-conclusion), [edit-cost experiment](#edit-costs-and-dictionary-knowledge-generalization), and [pairwise linguistic evaluation](#stemming-quality) answer separate questions. Their 10–90% curves use independent frozen protocols and must not be substituted for one another.
<!-- BENCHMARK-EVIDENCE-MAP:END -->

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `us-uk-default` | `1.0.1` | `US_UK` | 396,939 | 1,002,414 | 793,874 | 208,540 | 208,540 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **1,002,414**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 73 | 0.007% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 22,481 | 2.243% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 202,637 | 20.215% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 777,146 | 77.527% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 77 | 0.008% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 97.668% | 98.110% | 97.552% | Radixor dictionary-trained patch-command stemmer. |
| Lucene EnglishMinimalStemFilter | 91.159% | 65.801% | 97.820% | Minimal English plural reduction, not a full stemmer. |
| Lucene KStemFilter | 80.233% | 77.328% | 80.996% | Krovetz-style English stemming TokenFilter; broader than minimal suffix reducers. |
| Lucene HunspellStemFilter | 80.400% | 12.869% | 98.139% | Benchmark-only English Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene EnglishPossessiveFilter | 79.186% | 0.003% | 99.987% | Possessive-ending remover only, not a full stemmer. |
| Snowball English / Porter2 | 40.425% | 46.737% | 38.767% | Porter2 rule-based suffix stemmer, distinct from original Porter. |
| Lucene PorterStemFilter | 39.616% | 46.636% | 37.772% | Lucene TokenFilter path for Porter suffix rules; not dictionary-root equivalent. |
| Lucene PorterStemmer direct copy | 39.616% | 46.636% | 37.772% | Direct Porter suffix-rule implementation generated under build for benchmark-only use. |
| OpenNLP PorterStemmer | 39.616% | 46.636% | 37.772% | Apache OpenNLP Porter suffix-rule implementation. |
| Snowball original Porter | 39.606% | 46.613% | 37.766% | Classic Porter rule-based suffix stemmer. |
| Paice/Husk Lancaster | 28.110% | 37.387% | 25.673% | Aggressive Paice/Husk rule stemmer that often produces shorter stems. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

<!-- ENGLISH-SPEED-SUITES:START -->
!!! note "Separate English speed suites"
    The `80.8 ns/token` Radixor value below is from the multilingual same-language comparison suite. The [coverage experiment](../reference/english-coverage.md) reports its own full-knowledge point from a separate benchmark method and run. Treat both as suite-specific estimates with their published uncertainty, not as interchangeable values.
<!-- ENGLISH-SPEED-SUITES:END -->

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `radixorUsUkProfiPreferredStem` | 16.840 | 1.732 | 80.8 | 1.000 | Full dictionary patch-command stemmer using compiled patch commands. |
| Lucene EnglishPossessiveFilter | `luceneEnglishPossessiveFilter` | 16.439 | 0.771 | 78.8 | 0.976 | Possessive-ending remover only; not a full stemmer. |
| Lucene EnglishMinimalStemFilter | `luceneEnglishMinimalStemFilter` | 18.665 | 0.717 | 89.5 | 1.108 | Narrow plural reduction filter; not a full stemmer. |
| Lucene PorterStemmer direct copy | `lucenePorterStemmerCopied` | 16.770 | 0.260 | 80.4 | 0.996 | Benchmark-only generated copy of Lucene package-private Porter implementation. |
| OpenNLP PorterStemmer | `opennlpPorterStemmer` | 16.888 | 0.247 | 81.0 | 1.003 | Apache OpenNLP Porter implementation. |
| Snowball original Porter | `snowballOriginalPorter` | 32.842 | 2.612 | 157.5 | 1.950 | Classic Porter suffix-rule stemmer; historical English baseline, not a dictionary-equivalent stemmer. |
| Lucene PorterStemFilter | `lucenePorterStemFilter` | 31.412 | 0.750 | 150.6 | 1.865 | Lucene TokenFilter integration path for Porter; includes TokenStream overhead. |
| Lucene KStemFilter | `luceneKStemFilter` | 43.402 | 1.146 | 208.1 | 2.577 | Krovetz-style English TokenFilter; broader than minimal suffix filters. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 72.329 | 1.052 | 346.8 | 4.295 | Benchmark-only English Hunspell comparison using the benchmark Hunspell corpus. |
| Snowball English / Porter2 | `snowballEnglishPorter2` | 46.742 | 2.724 | 224.1 | 2.776 | Porter2 suffix-rule stemmer, distinct from original Porter. |
| Paice/Husk Lancaster | `paiceHuskLancaster` | 138.158 | 3.983 | 662.5 | 8.204 | Aggressive rule-based English stemmer. |

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

Model `us-uk-default` version `1.0.1` is evaluated over five
predeclared nested splits. Unseen metrics remove withheld occurrences whose normalized surface
also appeared in training. Parentheses show the observed split minimum–maximum.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 898,572 | 92.805% (92.757–92.843) | 76.010% (75.505–76.378) | 97.184% (97.156–97.293) |
| 20% | 795,287 | 93.155% (93.122–93.220) | 76.608% (76.306–76.669) | 97.509% (97.464–97.541) |
| 30% | 692,940 | 93.440% (93.409–93.459) | 76.944% (76.715–76.961) | 97.741% (97.718–97.755) |
| 40% | 591,318 | 93.699% (93.682–93.712) | 77.308% (77.149–77.479) | 97.940% (97.912–97.948) |
| 50% | 490,475 | 93.934% (93.888–94.011) | 77.643% (77.410–77.726) | 98.133% (98.089–98.192) |
| 60% | 390,750 | 94.178% (94.121–94.242) | 77.976% (77.824–78.311) | 98.314% (98.268–98.319) |
| 70% | 291,831 | 94.425% (94.290–94.438) | 78.505% (77.971–78.614) | 98.454% (98.436–98.500) |
| 80% | 193,781 | 94.608% (94.514–94.637) | 78.795% (78.580–79.033) | 98.592% (98.577–98.658) |
| 90% | 96,702 | 94.680% (94.609–94.847) | 78.854% (78.538–79.603) | 98.744% (98.642–98.790) |

### Generalization conclusion

- Median exactness on genuinely unseen changed forms moves from **76.010%**
  at 10% training knowledge to **78.854%** at 90%, a measured
  **+2.843 percentage-point** change for this dictionary.
- Over the same endpoints, unseen all-form exactness changes by **+1.875 pp** and
  preservation of unseen already-root forms changes by **+1.560 pp**. These separate
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

This section interprets the edit-cost and held-out-family experiment for `US_UK`
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
| 396,939 | 1,002,414 | 20.80% | 337 | 15 | 15.60× | 54 |

The exact classes are based on command-by-command equality over the complete dictionary,
not equality of aggregate trie metrics. A higher class count means that this dictionary
exposes more cost-dependent encoder decisions; it does not by itself mean better quality.

| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 75.824% | 75.824% | +0.000 pp | 0.8800 | 0.8800 | 1.000× | 1.000× |
| 20% | 76.283% | 76.283% | +0.000 pp | 0.8841 | 0.8841 | 1.000× | 1.000× |
| 30% | 76.794% | 76.794% | +0.000 pp | 0.8890 | 0.8890 | 1.000× | 1.000× |
| 40% | 77.289% | 77.289% | +0.000 pp | 0.8928 | 0.8928 | 1.000× | 1.000× |
| 50% | 77.779% | 77.779% | +0.000 pp | 0.8961 | 0.8961 | 1.000× | 1.000× |
| 60% | 78.057% | 78.057% | +0.000 pp | 0.8985 | 0.8985 | 1.000× | 1.000× |
| 70% | 78.403% | 78.403% | +0.000 pp | 0.9020 | 0.9020 | 1.000× | 1.000× |
| 80% | 78.806% | 78.806% | +0.000 pp | 0.9054 | 0.9054 | 1.000× | 1.000× |
| 90% | 79.131% | 79.131% | +0.000 pp | 0.9076 | 0.9076 | 1.000× | 1.000× |

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
| `patch_command_ratio` | `value_references` | +0.815 | +0.683…+1.000 | 45 |
| `replace_to_delete_insert` | `patch_command_ratio` | -0.794 | -0.885…-0.599 | 45 |
| `replace_cost` | `patch_command_ratio` | -0.687 | -0.737…-0.437 | 45 |
| `patch_command_ratio` | `trie_nodes` | +0.807 | +0.351…+1.000 | 45 |
| `replace_to_delete_insert` | `trie_nodes` | -0.669 | -0.794…-0.324 | 45 |
| `replace_to_delete_insert` | `value_references` | -0.723 | -0.805…-0.313 | 45 |

For each quality outcome, the largest absolute median association is shown even when its
interval crosses zero. This prevents a large median in heterogeneous strata from being
misreported as a portable language-level effect.

| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |
| --- | --- | ---: | ---: | --- | ---: |
| `trie_edges` | `unseen_f05` | +1.000 | +0.250…+1.000 | no | 4 / 45 |
| `trie_edges` | `unseen_over_percent` | -1.000 | -1.000…-0.250 | no | 4 / 45 |

No within-stratum coefficient is defined for `unseen_changed_exact`, `unseen_under_percent` because these outcomes do not vary across cost configurations in the measured language strata. Within this matrix, that is observed cost insensitivity for those outcomes, not missing measurement.

### Edit-cost conclusion

- With baseline costs, median unseen changed-form exactness changes from **75.824%** at 10% knowledge to **79.131%** at 90%, a **+3.308 pp** measured knowledge effect.
- The predeclared selection is `D1I1R1M0`. Its median unseen changed-form exactness differs from baseline by **+0.000 pp** and it does not change the median retained-command count (1.000× baseline).
- Under the selected costs, the 10%–90% knowledge change is **+3.308 pp**. This quantifies generalization for this dictionary; it is not a claim about unrelated domains or lexical resources.
- The selection rule retains the production baseline, so this experiment supplies no measured reason to change edit costs for this language under the predeclared objective.
- No cost or representation predictor is both defined in all 45 strata and retains one association sign over the central 95% interval for an unseen-form quality outcome. Effects with partial coverage are insufficient for a stable language-level claim; the remaining measured effects are heterogeneous across knowledge levels and splits.

The complete evidence is available in the [raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the [per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the [per-language association table](../data/edit-cost-language-correlations.csv). See the [cross-language analysis](../edit-cost-sensitivity.md) and [frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.

<!-- EDIT-COST-GENERALIZATION:END -->

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `US_UK` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `us-uk-default`, loaded from classpath resource `org/egothor/stemmer/models/us-uk-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.976120** among 11 deterministic stemmers. The runner-up is `ENGLISH LUCENE PORTER COPIED` at 0.965135, a difference of 0.010985. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.976863** among 11 deterministic stemmers. The runner-up is `ENGLISH LUCENE PORTER COPIED` at 0.965469, a difference of 0.011393. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **15 result rows**, **11 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.976120|&lt;0.000001%|4.775939%|
|2|ENGLISH LUCENE PORTER COPIED|0.965135|0.000207%|6.972812%|
|3|ENGLISH LUCENE PORTER FILTER|0.965135|0.000207%|6.972812%|
|4|ENGLISH OPENNLP PORTER|0.965135|0.000207%|6.972812%|
|5|ENGLISH SNOWBALL PORTER2|0.965070|0.000212%|6.985868%|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|0.964995|0.000206%|7.000881%|
|7|ENGLISH PAICE HUSK LANCASTER|0.962815|0.000960%|7.435948%|
|8|ENGLISH LUCENE KSTEM FILTER|0.887253|0.000110%|22.549365%|
|9|ENGLISH LUCENE MINIMAL FILTER|0.723935|0.000001%|55.212964%|
|10|HUNSPELL ENGLISH LUCENE FILTER|0.574800|0.000012%|85.039982%|
|11|ENGLISH LUCENE POSSESSIVE FILTER|0.500011|&lt;0.000001%|99.997715%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999990|0.952241|1.000000|0.976120|1.000000|0.000000|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.440121|0.930272|0.999998|0.965135|0.999998|0.000002|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.440121|0.930272|0.999998|0.965135|0.999998|0.000002|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.440121|0.930272|0.999998|0.965135|0.999998|0.000002|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.434309|0.930141|0.999998|0.965070|0.999998|0.000002|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.441440|0.929991|0.999998|0.964995|0.999998|0.000002|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.144282|0.925641|0.999990|0.962815|0.999990|0.000010|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.551014|0.774506|0.999999|0.887253|0.999999|0.000001|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.989894|0.447870|1.000000|0.723935|0.999999|0.000001|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.681262|0.149600|1.000000|0.574800|0.999998|0.000002|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.148936|0.000023|1.000000|0.500011|0.999998|0.000002|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990061|0.975531|0.961422|0.952231|0.975823|0.975823|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.491963|0.597539|0.760812|0.426065|0.639869|0.639868|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.491963|0.597539|0.760812|0.426065|0.639869|0.639868|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.491963|0.597539|0.760812|0.426065|0.639869|0.639868|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.486138|0.592134|0.757239|0.420590|0.635585|0.635584|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.493266|0.598696|0.761449|0.427243|0.640731|0.640730|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.173588|0.249650|0.444357|0.142629|0.365449|0.365447|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.584762|0.643919|0.716392|0.474838|0.653272|0.653271|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.796987|0.616713|0.502949|0.445832|0.665841|0.665840|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.398218|0.245328|0.177269|0.139814|0.319244|0.319244|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.000114|0.000046|0.000029|0.000023|0.001845|0.001845|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|291757|3|14633|175199431092|3 / 175199431095|14633 / 306390|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|285026|362583|21364|175199068512|362583 / 175199431095|21364 / 306390|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|285026|362583|21364|175199068512|362583 / 175199431095|21364 / 306390|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|285026|362583|21364|175199068512|362583 / 175199431095|21364 / 306390|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|284986|371197|21404|175199059898|371197 / 175199431095|21404 / 306390|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|284940|360538|21450|175199070557|360538 / 175199431095|21450 / 306390|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|283607|1682038|22783|175197749057|1682038 / 175199431095|22783 / 306390|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|237301|193361|69089|175199237734|193361 / 175199431095|69089 / 306390|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|137223|1401|169167|175199429694|1401 / 175199431095|169167 / 306390|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|45836|21445|260554|175199409650|21445 / 175199431095|260554 / 306390|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|7|40|306383|175199431055|40 / 175199431095|306383 / 306390|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.004569%|
|HUNSPELL ENGLISH LUCENE FILTER|0.000012%|83.349652%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 175199431095|14 / 306390|
|HUNSPELL ENGLISH LUCENE FILTER|20368 / 175199431095|255375 / 306390|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.999977|&lt;0.000001%|0.004569%|
|2|HUNSPELL ENGLISH LUCENE FILTER|0.583252|0.000022%|83.349652%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999827|0.999954|1.000000|0.999977|1.000000|0.000000|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.568121|0.166503|1.000000|0.583252|0.999998|0.000002|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999852|0.999891|0.999929|0.999781|0.999891|0.999891|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.383241|0.257531|0.193921|0.147796|0.307562|0.307561|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|306376|53|14|175199431042|53 / 175199431095|14 / 306390|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|51015|38781|255375|175199392314|38781 / 175199431095|255375 / 306390|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|14619|3|50|13716|2.317103%|8|605958|
|HUNSPELL ENGLISH LUCENE FILTER|5179|1077|17336|5736|0.969007%|4|597698|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **15 result rows**, **11 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.976863|&lt;0.000001%|4.627473%|
|2|ENGLISH LUCENE PORTER COPIED|0.965469|0.000222%|6.905897%|
|3|ENGLISH LUCENE PORTER FILTER|0.965469|0.000222%|6.905897%|
|4|ENGLISH OPENNLP PORTER|0.965469|0.000222%|6.905897%|
|5|ENGLISH SNOWBALL PORTER2|0.965445|0.000228%|6.910824%|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|0.965328|0.000221%|6.934147%|
|7|ENGLISH PAICE HUSK LANCASTER|0.963199|0.001032%|7.359216%|
|8|ENGLISH LUCENE KSTEM FILTER|0.889741|0.000120%|22.051698%|
|9|ENGLISH LUCENE MINIMAL FILTER|0.724902|0.000001%|55.019529%|
|10|HUNSPELL ENGLISH LUCENE FILTER|0.575162|0.000012%|84.967529%|
|11|ENGLISH LUCENE POSSESSIVE FILTER|0.500008|&lt;0.000001%|99.998358%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999990|0.953725|1.000000|0.976863|1.000000|0.000000|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.440920|0.930941|0.999998|0.965469|0.999998|0.000002|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.440920|0.930941|0.999998|0.965469|0.999998|0.000002|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.440920|0.930941|0.999998|0.965469|0.999998|0.000002|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.435153|0.930892|0.999998|0.965445|0.999998|0.000002|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.442235|0.930659|0.999998|0.965328|0.999998|0.000002|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.144698|0.926408|0.999990|0.963199|0.999990|0.000010|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.551013|0.779483|0.999999|0.889741|0.999998|0.000002|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.989965|0.449805|1.000000|0.724902|0.999999|0.000001|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.700121|0.150325|1.000000|0.575162|0.999998|0.000002|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.121951|0.000016|1.000000|0.500008|0.999998|0.000002|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990381|0.976310|0.962632|0.953716|0.976584|0.976583|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.492799|0.598414|0.761648|0.426955|0.640680|0.640679|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.492799|0.598414|0.761648|0.426955|0.640680|0.640679|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.492799|0.598414|0.761648|0.426955|0.640680|0.640679|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.487025|0.593070|0.758150|0.421535|0.636459|0.636458|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.494097|0.599565|0.762279|0.428128|0.641537|0.641536|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.174075|0.250301|0.445287|0.143054|0.366127|0.366125|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.585325|0.645632|0.719793|0.476703|0.655367|0.655366|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.798246|0.618559|0.504903|0.447763|0.667301|0.667301|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.404349|0.247507|0.178333|0.141231|0.324416|0.324415|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.000082|0.000033|0.000021|0.000016|0.001415|0.001415|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|290334|3|14087|161561996596|3 / 161561996599|14087 / 304421|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|283398|359344|21023|161561637255|359344 / 161561996599|21023 / 304421|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|283398|359344|21023|161561637255|359344 / 161561996599|21023 / 304421|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|283398|359344|21023|161561637255|359344 / 161561996599|21023 / 304421|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|283383|367843|21038|161561628756|367843 / 161561996599|21038 / 304421|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|283312|357325|21109|161561639274|357325 / 161561996599|21109 / 304421|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|282018|1666994|22403|161560329605|1666994 / 161561996599|22403 / 304421|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|237291|193354|67130|161561803245|193354 / 161561996599|67130 / 304421|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|136930|1388|167491|161561995211|1388 / 161561996599|167491 / 304421|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|45762|19601|258659|161561976998|19601 / 161561996599|258659 / 304421|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|5|36|304416|161561996563|36 / 161561996599|304416 / 304421|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.000000%|
|HUNSPELL ENGLISH LUCENE FILTER|0.000011%|83.267252%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 161561996599|0 / 304421|
|HUNSPELL ENGLISH LUCENE FILTER|18565 / 161561996599|253483 / 304421|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|&lt;0.000001%|0.000000%|
|2|HUNSPELL ENGLISH LUCENE FILTER|0.583664|0.000023%|83.267252%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999967|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.581816|0.167327|1.000000|0.583664|0.999998|0.000002|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999974|0.999984|0.999993|0.999967|0.999984|0.999984|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.389065|0.259907|0.195130|0.149364|0.312016|0.312015|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|304421|10|0|161561996589|10 / 161561996599|0 / 304421|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|50938|36612|253483|161561959987|36612 / 161561996599|253483 / 304421|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|14087|3|7|13355|2.349408%|8|582082|
|HUNSPELL ENGLISH LUCENE FILTER|5176|1036|17011|5685|1.000104%|4|574142|

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
- Dictionary language: `US_UK`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
