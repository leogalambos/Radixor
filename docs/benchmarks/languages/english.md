# English Stemmer Benchmarks

This page reports same-language stemming benchmarks for English. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `us-uk-default` | `1.0.0` | `US_UK` | 396,939 | 1,004,374 | 793,874 | 210,500 | 210,500 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **1,004,374**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 73 | 0.007% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 22,481 | 2.238% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 202,637 | 20.175% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 779,106 | 77.571% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 77 | 0.008% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 97.478% | 97.197% | 97.552% | Radixor dictionary-trained patch-command stemmer. |
| Lucene EnglishMinimalStemFilter | 90.981% | 65.189% | 97.820% | Minimal English plural reduction, not a full stemmer. |
| Lucene KStemFilter | 80.076% | 76.608% | 80.996% | Krovetz-style English stemming TokenFilter; broader than minimal suffix reducers. |
| Lucene HunspellStemFilter | 80.243% | 12.750% | 98.139% | Benchmark-only English Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene EnglishPossessiveFilter | 79.032% | 0.003% | 99.987% | Possessive-ending remover only, not a full stemmer. |
| Snowball English / Porter2 | 40.346% | 46.302% | 38.767% | Porter2 rule-based suffix stemmer, distinct from original Porter. |
| Lucene PorterStemFilter | 39.538% | 46.201% | 37.772% | Lucene TokenFilter path for Porter suffix rules; not dictionary-root equivalent. |
| Lucene PorterStemmer direct copy | 39.538% | 46.201% | 37.772% | Direct Porter suffix-rule implementation generated under build for benchmark-only use. |
| OpenNLP PorterStemmer | 39.538% | 46.201% | 37.772% | Apache OpenNLP Porter suffix-rule implementation. |
| Snowball original Porter | 39.529% | 46.179% | 37.766% | Classic Porter rule-based suffix stemmer. |
| Paice/Husk Lancaster | 28.055% | 37.039% | 25.673% | Aggressive Paice/Husk rule stemmer that often produces shorter stems. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `radixorUsUkProfiPreferredStem` | 14.704 | 1.264 | 69.9 | 1.000 | Full dictionary patch-command stemmer using compiled patch commands. |
| Lucene EnglishPossessiveFilter | `luceneEnglishPossessiveFilter` | 14.863 | 0.185 | 70.6 | 1.011 | Possessive-ending remover only; not a full stemmer. |
| Lucene EnglishMinimalStemFilter | `luceneEnglishMinimalStemFilter` | 16.355 | 0.203 | 77.7 | 1.112 | Narrow plural reduction filter; not a full stemmer. |
| Lucene PorterStemmer direct copy | `lucenePorterStemmerCopied` | 16.307 | 0.186 | 77.5 | 1.109 | Benchmark-only generated copy of Lucene package-private Porter implementation. |
| OpenNLP PorterStemmer | `opennlpPorterStemmer` | 16.589 | 0.187 | 78.8 | 1.128 | Apache OpenNLP Porter implementation. |
| Snowball original Porter | `snowballOriginalPorter` | 31.038 | 2.329 | 147.4 | 2.111 | Classic Porter suffix-rule stemmer; historical English baseline, not a dictionary-equivalent stemmer. |
| Lucene PorterStemFilter | `lucenePorterStemFilter` | 29.161 | 0.379 | 138.5 | 1.983 | Lucene TokenFilter integration path for Porter; includes TokenStream overhead. |
| Lucene KStemFilter | `luceneKStemFilter` | 40.642 | 0.542 | 193.1 | 2.764 | Krovetz-style English TokenFilter; broader than minimal suffix filters. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 72.868 | 1.118 | 346.2 | 4.956 | Benchmark-only English Hunspell comparison using the benchmark Hunspell corpus. |
| Snowball English / Porter2 | `snowballEnglishPorter2` | 44.546 | 2.449 | 211.6 | 3.030 | Porter2 suffix-rule stemmer, distinct from original Porter. |
| Paice/Husk Lancaster | `paiceHuskLancaster` | 138.567 | 3.367 | 658.3 | 9.424 | Aggressive rule-based English stemmer. |

## Interpretation Notes

- Radixor is a dictionary-trained patch-command stemmer. Its learned transformations can generalize beyond the word forms listed in the training resource.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `US_UK` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `us-uk-default`, loaded from classpath resource `org/egothor/stemmer/models/us-uk-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.965537** among 11 deterministic stemmers. The runner-up is `ENGLISH LUCENE PORTER COPIED` at 0.954796, a difference of 0.010741. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.966202** among 11 deterministic stemmers. The runner-up is `ENGLISH LUCENE PORTER COPIED` at 0.955064, a difference of 0.011139. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **15 result rows**, **11 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.965537|&lt;0.000001%|6.892502%|
|2|ENGLISH LUCENE PORTER COPIED|0.954796|0.000207%|9.040545%|
|3|ENGLISH LUCENE PORTER FILTER|0.954796|0.000207%|9.040545%|
|4|ENGLISH OPENNLP PORTER|0.954796|0.000207%|9.040545%|
|5|ENGLISH SNOWBALL PORTER2|0.954732|0.000212%|9.053310%|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|0.954659|0.000206%|9.067990%|
|7|ENGLISH PAICE HUSK LANCASTER|0.952535|0.000960%|9.492110%|
|8|ENGLISH LUCENE KSTEM FILTER|0.878645|0.000110%|24.270875%|
|9|ENGLISH LUCENE MINIMAL FILTER|0.718958|0.000001%|56.208454%|
|10|HUNSPELL ENGLISH LUCENE FILTER|0.573139|0.000012%|85.372182%|
|11|ENGLISH LUCENE POSSESSIVE FILTER|0.500011|&lt;0.000001%|99.997766%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999990|0.931075|1.000000|0.965537|1.000000|0.000000|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.440121|0.909595|0.999998|0.954796|0.999998|0.000002|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.440121|0.909595|0.999998|0.954796|0.999998|0.000002|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.440121|0.909595|0.999998|0.954796|0.999998|0.000002|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.434309|0.909467|0.999998|0.954732|0.999998|0.000002|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.441440|0.909320|0.999998|0.954659|0.999998|0.000002|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.144284|0.905079|0.999990|0.952535|0.999990|0.000010|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.551014|0.757291|0.999999|0.878645|0.999998|0.000002|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.989894|0.437915|1.000000|0.718958|0.999999|0.000001|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.681277|0.146278|1.000000|0.573139|0.999998|0.000002|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.148936|0.000022|1.000000|0.500011|0.999998|0.000002|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.985403|0.964303|0.944087|0.931066|0.964917|0.964917|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.490783|0.593208|0.749662|0.421675|0.632717|0.632716|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.490783|0.593208|0.749662|0.421675|0.632717|0.632716|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.490783|0.593208|0.749662|0.421675|0.632717|0.632716|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.484986|0.587880|0.746192|0.416310|0.628482|0.628481|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.492079|0.594348|0.750277|0.422827|0.633570|0.633569|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.173443|0.248891|0.440518|0.142133|0.361370|0.361368|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.582762|0.637891|0.704541|0.468312|0.645971|0.645970|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.790591|0.607210|0.492883|0.435966|0.658399|0.658399|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.393465|0.240844|0.173533|0.136909|0.315683|0.315683|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.000112|0.000045|0.000028|0.000022|0.001824|0.001824|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|291757|3|21598|175199424127|3 / 175199424130|21598 / 313355|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|285026|362583|28329|175199061547|362583 / 175199424130|28329 / 313355|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|285026|362583|28329|175199061547|362583 / 175199424130|28329 / 313355|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|285026|362583|28329|175199061547|362583 / 175199424130|28329 / 313355|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|284986|371197|28369|175199052933|371197 / 175199424130|28369 / 313355|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|284940|360538|28415|175199063592|360538 / 175199424130|28415 / 313355|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|283611|1682034|29744|175197742096|1682034 / 175199424130|29744 / 313355|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|237301|193361|76054|175199230769|193361 / 175199424130|76054 / 313355|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|137223|1401|176132|175199422729|1401 / 175199424130|176132 / 313355|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|45837|21444|267518|175199402686|21444 / 175199424130|267518 / 313355|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|7|40|313348|175199424090|40 / 175199424130|313348 / 313355|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.004787%|
|HUNSPELL ENGLISH LUCENE FILTER|0.000012%|83.719424%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 175199424130|15 / 313355|
|HUNSPELL ENGLISH LUCENE FILTER|20367 / 175199424130|262339 / 313355|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.999976|&lt;0.000001%|0.004787%|
|2|HUNSPELL ENGLISH LUCENE FILTER|0.581403|0.000022%|83.719424%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999825|0.999952|1.000000|0.999976|1.000000|0.000000|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.568132|0.162806|1.000000|0.581403|0.999998|0.000002|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999850|0.999888|0.999927|0.999777|0.999888|0.999888|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.379279|0.253086|0.189902|0.144876|0.304130|0.304130|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|313340|55|15|175199424075|55 / 175199424130|15 / 313355|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|51016|38780|262339|175199385350|38780 / 175199424130|262339 / 313355|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|21583|3|52|13718|2.317441%|1355|607918|
|HUNSPELL ENGLISH LUCENE FILTER|5179|1077|17336|5736|0.969007%|4|597698|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **15 result rows**, **11 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.966202|&lt;0.000001%|6.759543%|
|2|ENGLISH LUCENE PORTER COPIED|0.955064|0.000222%|8.987032%|
|3|ENGLISH LUCENE PORTER FILTER|0.955064|0.000222%|8.987032%|
|4|ENGLISH OPENNLP PORTER|0.955064|0.000222%|8.987032%|
|5|ENGLISH SNOWBALL PORTER2|0.955040|0.000228%|8.991849%|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|0.954926|0.000221%|9.014651%|
|7|ENGLISH PAICE HUSK LANCASTER|0.952850|0.001032%|9.428933%|
|8|ENGLISH LUCENE KSTEM FILTER|0.881028|0.000120%|23.794246%|
|9|ENGLISH LUCENE MINIMAL FILTER|0.719875|0.000001%|56.025075%|
|10|HUNSPELL ENGLISH LUCENE FILTER|0.573484|0.000012%|85.303261%|
|11|ENGLISH LUCENE POSSESSIVE FILTER|0.500008|&lt;0.000001%|99.998394%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999990|0.932405|1.000000|0.966202|1.000000|0.000000|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.440920|0.910130|0.999998|0.955064|0.999998|0.000002|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.440920|0.910130|0.999998|0.955064|0.999998|0.000002|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.440920|0.910130|0.999998|0.955064|0.999998|0.000002|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.435153|0.910082|0.999998|0.955040|0.999998|0.000002|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.442235|0.909853|0.999998|0.954926|0.999998|0.000002|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.144700|0.905711|0.999990|0.952850|0.999990|0.000010|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.551013|0.762058|0.999999|0.881028|0.999998|0.000002|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.989965|0.439749|1.000000|0.719875|0.999999|0.000001|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.700136|0.146967|1.000000|0.573484|0.999998|0.000002|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.121951|0.000016|1.000000|0.500008|0.999998|0.000002|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.985700|0.965015|0.945181|0.932396|0.965606|0.965606|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.491609|0.594049|0.750417|0.422524|0.633478|0.633477|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.491609|0.594049|0.750417|0.422524|0.633478|0.633477|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.491609|0.594049|0.750417|0.422524|0.633478|0.633477|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.485863|0.588782|0.747021|0.417215|0.629305|0.629304|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.492900|0.595181|0.751027|0.423671|0.634326|0.634325|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.173928|0.249533|0.441413|0.142553|0.362017|0.362015|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.583322|0.639575|0.707836|0.470129|0.648000|0.647999|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.791820|0.608984|0.494744|0.437798|0.659800|0.659800|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.399444|0.242939|0.174549|0.138264|0.320776|0.320775|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.000080|0.000032|0.000020|0.000016|0.001399|0.001399|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|290334|3|21048|161561989635|3 / 161561989638|21048 / 311382|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|283398|359344|27984|161561630294|359344 / 161561989638|27984 / 311382|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|283398|359344|27984|161561630294|359344 / 161561989638|27984 / 311382|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|283398|359344|27984|161561630294|359344 / 161561989638|27984 / 311382|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|283383|367843|27999|161561621795|367843 / 161561989638|27999 / 311382|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|283312|357325|28070|161561632313|357325 / 161561989638|28070 / 311382|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|282022|1666990|29360|161560322648|1666990 / 161561989638|29360 / 311382|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|237291|193354|74091|161561796284|193354 / 161561989638|74091 / 311382|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|136930|1388|174452|161561988250|1388 / 161561989638|174452 / 311382|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|45763|19600|265619|161561970038|19600 / 161561989638|265619 / 311382|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|5|36|311377|161561989602|36 / 161561989638|311377 / 311382|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.000000%|
|HUNSPELL ENGLISH LUCENE FILTER|0.000011%|83.640994%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 161561989638|0 / 311382|
|HUNSPELL ENGLISH LUCENE FILTER|18564 / 161561989638|260443 / 311382|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|&lt;0.000001%|0.000000%|
|2|HUNSPELL ENGLISH LUCENE FILTER|0.581795|0.000023%|83.640994%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999952|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.581828|0.163590|1.000000|0.581795|0.999998|0.000002|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999961|0.999976|0.999990|0.999952|0.999976|0.999976|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.384979|0.255377|0.191058|0.146379|0.308515|0.308514|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|311382|15|0|161561989623|15 / 161561989638|0 / 311382|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|50939|36611|260443|161561953027|36611 / 161561989638|260443 / 311382|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|21048|3|12|13357|2.349760%|1355|584042|
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
- Source SHA-256: `f15f8e653022e0333955b8b82f42944aa1c5a14a5ce54e628bb1a9c9aed42132`
- Evaluation command: `./gradlew stemmingQuality --no-daemon`
- Dictionary language: `US_UK`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
