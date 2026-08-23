# Polish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Polish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so a lower runtime can coexist with lower dictionary-root agreement. Read the speed and accuracy tables together. The Radixor rows in this refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) shows the resulting quality/speed envelope explicitly.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `pl-pl-unimorph` | `1.0.0` | `PL_PL` | 9,990 | 132,308 | 19,957 | 112,351 | 112,351 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **132,308**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 1,836 | 1.388% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 52,996 | 40.055% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 37,137 | 28.069% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 20,219 | 15.282% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 20,120 | 15.207% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 98.837% | 98.744% | 99.359% | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | 89.545% | 88.272% | 96.713% | Benchmark-only Polish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene MorfologikFilter | 87.729% | 86.606% | 94.047% | Dictionary-based path; Morfologik can emit multiple terms. |
| Lucene StempelFilter | 70.009% | 69.262% | 74.220% | Lucene TokenFilter integration path for table-driven Polish Stempel. |
| Lucene StempelStemmer direct | 70.009% | 69.262% | 74.220% | Direct table-driven Polish Stempel stemmer API. |
| Official Snowball direct | 22.315% | 20.225% | 34.078% | Official Snowball 3.1.0 generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 7 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `polishRadixor` | 8.094 | 0.275 | 72.0 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 490.286 | 31.447 | 4363.9 | 60.574 | Benchmark-only Polish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene StempelStemmer direct | `polishLuceneStempelStemmerDirect` | 31.579 | 0.289 | 281.1 | 3.902 | Direct table-driven Polish Stempel stemmer API. |
| Lucene StempelFilter | `polishLuceneStempelFilter` | 39.451 | 0.628 | 351.1 | 4.874 | Lucene TokenFilter integration path for table-driven Polish Stempel. |
| Lucene MorfologikFilter | `polishLuceneMorfologikFilter` | 137.717 | 1.971 | 1225.8 | 17.015 | Dictionary-based Morfologik TokenFilter; may emit multiple terms. |
| Official Snowball direct | `snowballDirect[POLISH]` | 9.668 | 1.083 | 86.1 | 1.194 | Official Snowball 3.1.0 generated Java stemmer; direct API. |

## Interpretation Notes

- Radixor is a dictionary-trained patch-command stemmer. Its learned transformations can generalize beyond the word forms listed in the training resource.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `PL_PL` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `pl-pl-unimorph`, loaded from classpath resource `org/egothor/stemmer/models/pl-pl-unimorph/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.991105** among 6 deterministic stemmers. The runner-up is `POLISH LUCENE MORFOLOGIK FILTER` at 0.948392, a difference of 0.042713. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.991301** among 6 deterministic stemmers. The runner-up is `POLISH LUCENE MORFOLOGIK FILTER` at 0.948417, a difference of 0.042884. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **12 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.991105|0.000000%|1.779024%|
|2|POLISH LUCENE MORFOLOGIK FILTER|0.948392|0.001042%|10.320543%|
|3|HUNSPELL POLISH LUCENE FILTER|0.933457|0.000383%|13.308172%|
|4|POLISH LUCENE STEMPEL DIRECT|0.855699|0.000602%|28.859618%|
|5|POLISH LUCENE STEMPEL FILTER|0.855699|0.000602%|28.859618%|
|6|SNOWBALL POLISH DIRECT|0.823625|0.000967%|35.273970%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.982210|1.000000|0.991105|0.999997|0.000003|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.929398|0.896795|0.999990|0.948392|0.999974|0.000026|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.971931|0.866918|0.999996|0.933457|0.999976|0.000024|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.947549|0.711404|0.999994|0.855699|0.999950|0.000050|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.947549|0.711404|0.999994|0.855699|0.999950|0.000050|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|0.910978|0.647260|0.999990|0.823625|0.999936|0.000064|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996391|0.991025|0.985717|0.982210|0.991065|0.991064|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.922689|0.912805|0.903131|0.839597|0.912951|0.912938|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.948942|0.916426|0.886065|0.845744|0.917924|0.917913|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.888559|0.812669|0.748723|0.684450|0.821030|0.821007|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.888559|0.812669|0.748723|0.684450|0.821030|0.821007|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|0.842338|0.756803|0.687038|0.608756|0.767880|0.767852|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1097200|0|19873|7303238338|0 / 7303238338|19873 / 1117073|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|1001785|76101|115288|7303162237|76101 / 7303238338|115288 / 1117073|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|968411|27967|148662|7303210371|27967 / 7303238338|148662 / 1117073|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|794690|43990|322383|7303194348|43990 / 7303238338|322383 / 1117073|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|794690|43990|322383|7303194348|43990 / 7303238338|322383 / 1117073|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|723037|70656|394036|7303167682|70656 / 7303238338|394036 / 1117073|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|0.000356%|7.227639%|
|POLISH LUCENE MORFOLOGIK FILTER|0.001000%|2.493123%|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|25967 / 7303238338|80738 / 1117073|
|POLISH LUCENE MORFOLOGIK FILTER|73019 / 7303238338|27850 / 1117073|
|Radixor|0 / 7303238338|0 / 1117073|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|POLISH LUCENE MORFOLOGIK FILTER|0.987528|0.001376%|2.493123%|
|3|HUNSPELL POLISH LUCENE FILTER|0.963859|0.000609%|7.227639%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.915516|0.975069|0.999986|0.987528|0.999982|0.000018|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.958830|0.927724|0.999994|0.963859|0.999983|0.000017|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.926837|0.944354|0.962546|0.894575|0.944823|0.944815|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.952443|0.943020|0.933782|0.892184|0.943149|0.943140|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1117073|0|0|7303238338|0 / 7303238338|0 / 1117073|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|1089223|100514|27850|7303137824|100514 / 7303238338|27850 / 1117073|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|1036335|44498|80738|7303193840|44498 / 7303238338|80738 / 1117073|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|67924|2000|16531|10485|8.674824%|6|132492|
|POLISH LUCENE MORFOLOGIK FILTER|87438|3082|24413|11776|9.742941%|5|133810|
|Radixor|19873|0|0|1392|1.151679%|4|122430|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **12 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.991301|0.000000%|1.739895%|
|2|POLISH LUCENE MORFOLOGIK FILTER|0.948417|0.001067%|10.315578%|
|3|HUNSPELL POLISH LUCENE FILTER|0.933546|0.000382%|13.290396%|
|4|POLISH LUCENE STEMPEL DIRECT|0.856335|0.000611%|28.732387%|
|5|POLISH LUCENE STEMPEL FILTER|0.856335|0.000611%|28.732387%|
|6|SNOWBALL POLISH DIRECT|0.823465|0.000990%|35.306102%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.982601|1.000000|0.991301|0.999997|0.000003|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.929032|0.896844|0.999989|0.948417|0.999973|0.000027|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.972469|0.867096|0.999996|0.933546|0.999975|0.000025|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.947796|0.712676|0.999994|0.856335|0.999949|0.000051|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.947796|0.712676|0.999994|0.856335|0.999949|0.000051|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|0.910487|0.646939|0.999990|0.823465|0.999935|0.000065|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996471|0.991224|0.986032|0.982601|0.991262|0.991261|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.922411|0.912654|0.903102|0.839342|0.912796|0.912783|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.949394|0.916764|0.886303|0.846320|0.918272|0.918260|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.889130|0.813590|0.749881|0.685758|0.821871|0.821848|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.889130|0.813590|0.749881|0.685758|0.821871|0.821848|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|0.841894|0.756414|0.686693|0.608253|0.767483|0.767454|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1091431|0|19326|7133100218|0 / 7133100218|19326 / 1110757|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|996176|76097|114581|7133024121|76097 / 7133100218|114581 / 1110757|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|963133|27267|147624|7133072951|27267 / 7133100218|147624 / 1110757|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|791610|43601|319147|7133056617|43601 / 7133100218|319147 / 1110757|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|791610|43601|319147|7133056617|43601 / 7133100218|319147 / 1110757|
|6|SNOWBALL POLISH DIRECT|PRIMARY_OUTPUT|718592|70647|392165|7133029571|70647 / 7133100218|392165 / 1110757|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|0.000356%|7.234976%|
|POLISH LUCENE MORFOLOGIK FILTER|0.001024%|2.474799%|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|25425 / 7133100218|80363 / 1110757|
|POLISH LUCENE MORFOLOGIK FILTER|73019 / 7133100218|27489 / 1110757|
|Radixor|0 / 7133100218|0 / 1110757|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000000%|0.000000%|
|2|POLISH LUCENE MORFOLOGIK FILTER|0.987619|0.001409%|2.474799%|
|3|HUNSPELL POLISH LUCENE FILTER|0.963822|0.000612%|7.234976%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.915099|0.975252|0.999986|0.987619|0.999982|0.000018|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.959377|0.927650|0.999994|0.963822|0.999983|0.000017|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.926529|0.944219|0.962597|0.894332|0.944697|0.944688|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.952859|0.943247|0.933827|0.892590|0.943380|0.943372|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1110757|0|0|7133100218|0 / 7133100218|0 / 1110757|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|1083268|100503|27489|7132999715|100503 / 7133100218|27489 / 1110757|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|1030394|43630|80363|7133056588|43630 / 7133100218|80363 / 1110757|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|67261|1842|16363|10303|8.625294%|6|130856|
|POLISH LUCENE MORFOLOGIK FILTER|87092|3078|24406|11666|9.766348%|5|132279|
|Radixor|19326|0|0|1306|1.093335%|4|120926|

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
- Dictionary language: `PL_PL`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
