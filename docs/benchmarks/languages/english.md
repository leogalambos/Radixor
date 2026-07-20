# English Stemmer Benchmarks

This page reports same-language stemming benchmarks for English. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `US_UK` | 396,939 | 1,004,374 | 793,874 | 210,500 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **1,004,374**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 28 | 0.003% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 22,493 | 2.240% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 186,764 | 18.595% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 795,024 | 79.156% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 65 | 0.006% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 97.478% | 97.197% | 97.552% | Full Radixor dictionary patch-command stemmer. |
| Lucene EnglishMinimalStemFilter | 90.981% | 65.189% | 97.820% | Minimal English plural reduction, not a full stemmer. |
| Lucene KStemFilter | 80.076% | 76.608% | 80.996% | Krovetz-style English stemming TokenFilter; broader than minimal suffix reducers. |
| Lucene HunspellStemFilter | 80.243% | 12.750% | 98.139% | Benchmark-only English Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene EnglishPossessiveFilter | 79.032% | 0.003% | 99.987% | Possessive-ending remover only, not a full stemmer. |
| Snowball English / Porter2 | 40.342% | 46.296% | 38.763% | Porter2 rule-based suffix stemmer, distinct from original Porter. |
| Lucene PorterStemFilter | 39.538% | 46.201% | 37.772% | Lucene TokenFilter path for Porter suffix rules; not dictionary-root equivalent. |
| Lucene PorterStemmer direct copy | 39.538% | 46.201% | 37.772% | Direct Porter suffix-rule implementation generated under build for benchmark-only use. |
| OpenNLP PorterStemmer | 39.538% | 46.201% | 37.772% | Apache OpenNLP Porter suffix-rule implementation. |
| Snowball original Porter | 39.529% | 46.179% | 37.766% | Classic Porter rule-based suffix stemmer. |
| Paice/Husk Lancaster | 28.055% | 37.039% | 25.673% | Aggressive Paice/Husk rule stemmer that often produces shorter stems. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `radixorUsUkProfiPreferredStem` | 21.987 | 8.707 | 104.5 | 1.000 | Full dictionary patch-command stemmer using compiled patch commands. |
| Lucene EnglishPossessiveFilter | `luceneEnglishPossessiveFilter` | 24.539 | 1.515 | 116.6 | 1.116 | Possessive-ending remover only; not a full stemmer. |
| Lucene EnglishMinimalStemFilter | `luceneEnglishMinimalStemFilter` | 22.702 | 1.195 | 107.8 | 1.032 | Narrow plural reduction filter; not a full stemmer. |
| Lucene PorterStemmer direct copy | `lucenePorterStemmerCopied` | 24.696 | 13.235 | 117.3 | 1.123 | Benchmark-only generated copy of Lucene package-private Porter implementation. |
| OpenNLP PorterStemmer | `opennlpPorterStemmer` | 23.121 | 12.528 | 109.8 | 1.052 | Apache OpenNLP Porter implementation. |
| Snowball original Porter | `snowballOriginalPorter` | 38.904 | 10.353 | 184.8 | 1.769 | Classic Porter suffix-rule stemmer; historical English baseline, not a dictionary-equivalent stemmer. |
| Lucene PorterStemFilter | `lucenePorterStemFilter` | 37.021 | 1.196 | 175.9 | 1.684 | Lucene TokenFilter integration path for Porter; includes TokenStream overhead. |
| Lucene KStemFilter | `luceneKStemFilter` | 51.640 | 2.591 | 245.3 | 2.349 | Krovetz-style English TokenFilter; broader than minimal suffix filters. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 79.785 | 1.347 | 379.0 | 3.629 | Benchmark-only English Hunspell comparison using the benchmark Hunspell corpus. |
| Snowball English / Porter2 | `snowballEnglishPorter2` | 52.437 | 0.773 | 249.1 | 2.385 | Porter2 suffix-rule stemmer, distinct from original Porter. |
| Paice/Husk Lancaster | `paiceHuskLancaster` | 141.556 | 12.324 | 672.5 | 6.438 | Aggressive rule-based English stemmer. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `US_UK` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/us_uk/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.965159** among 11 deterministic stemmers. The runner-up is `ENGLISH LUCENE PORTER COPIED` at 0.954627, a difference of 0.010533. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.965820** among 11 deterministic stemmers. The runner-up is `ENGLISH LUCENE PORTER COPIED` at 0.954900, a difference of 0.010920. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **15 result rows**, **11 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.965159|1149886 / 184490451771 (0.000623%)|21869 / 313870 (6.967534%)|0.240076|0.332621|0.434052|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.954627|1557406 / 184490451771 (0.000844%)|28480 / 313870 (9.073820%)|0.185679|0.264659|0.375252|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.954627|1557406 / 184490451771 (0.000844%)|28480 / 313870 (9.073820%)|0.185679|0.264659|0.375252|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.954627|1557406 / 184490451771 (0.000844%)|28480 / 313870 (9.073820%)|0.185679|0.264659|0.375252|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.954537|1566711 / 184490451771 (0.000849%)|28536 / 313870 (9.091662%)|0.184753|0.263477|0.374240|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.954490|1555293 / 184490451771 (0.000843%)|28566 / 313870 (9.101220%)|0.185835|0.264849|0.375363|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.952394|3062661 / 184490451771 (0.001660%)|29879 / 313870 (9.519546%)|0.103643|0.155164|0.277089|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.878441|1368501 / 184490451771 (0.000742%)|76305 / 313870 (24.311020%)|0.176284|0.247472|0.334598|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.718599|1122264 / 184490451771 (0.000608%)|176645 / 313870 (56.279670%)|0.128204|0.174436|0.218251|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.573277|1981986 / 184490451771 (0.001074%)|267868 / 313870 (85.343614%)|0.027298|0.039287|0.057655|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.500008|1115154 / 184490451771 (0.000604%)|313863 / 313870 (99.997770%)|0.000007|0.000010|0.000009|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.202513|0.930325|0.999994|0.965159|0.999994|0.000006|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.154868|0.909262|0.999992|0.954627|0.999991|0.000009|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.154868|0.909262|0.999992|0.954627|0.999991|0.000009|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.154868|0.909262|0.999992|0.954627|0.999991|0.000009|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.154064|0.909083|0.999992|0.954537|0.999991|0.000009|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.155006|0.908988|0.999992|0.954490|0.999991|0.000009|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.084858|0.904805|0.999983|0.952394|0.999983|0.000017|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.147917|0.756890|0.999993|0.878441|0.999992|0.000008|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.108953|0.437203|0.999994|0.718599|0.999993|0.000007|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.022684|0.146564|0.999989|0.573277|0.999988|0.000012|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.000006|0.000022|0.999994|0.500008|0.999992|0.000008|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.240076|0.332621|0.541270|0.199487|0.434054|0.434052|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.185679|0.264659|0.460563|0.152511|0.375254|0.375252|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.185679|0.264659|0.460563|0.152511|0.375254|0.375252|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.185679|0.264659|0.460563|0.152511|0.375254|0.375252|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.184753|0.263477|0.459102|0.151727|0.374242|0.374240|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.185835|0.264849|0.460751|0.152637|0.375365|0.375363|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.103643|0.155164|0.308543|0.084107|0.277092|0.277089|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.176284|0.247472|0.415099|0.141208|0.334600|0.334598|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.128204|0.174436|0.272816|0.095552|0.218253|0.218251|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.027298|0.039287|0.070051|0.020037|0.057659|0.057655|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.000007|0.000010|0.000015|0.000005|0.000012|0.000009|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.332619|0.994215|0.997770|0.995989|0.995989|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.264656|0.969648|0.997199|0.983231|0.983231|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.264656|0.969648|0.997199|0.983231|0.983231|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.264656|0.969648|0.997199|0.983231|0.983231|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.263474|0.969037|0.997182|0.982908|0.982908|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.264847|0.969891|0.997193|0.983353|0.983353|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.155162|0.937768|0.996600|0.966289|0.966289|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.247470|0.980687|0.992108|0.986364|0.986364|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.174433|0.995202|0.981174|0.988138|0.988138|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.039284|0.993096|0.963677|0.978166|0.978166|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.000007|0.995789|0.958019|0.976539|0.976539|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|292001|1149886|21869|184489301885|1149886 / 184490451771|21869 / 313870|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|285390|1557406|28480|184488894365|1557406 / 184490451771|28480 / 313870|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|285390|1557406|28480|184488894365|1557406 / 184490451771|28480 / 313870|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|285390|1557406|28480|184488894365|1557406 / 184490451771|28480 / 313870|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|285334|1566711|28536|184488885060|1566711 / 184490451771|28536 / 313870|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|285304|1555293|28566|184488896478|1555293 / 184490451771|28566 / 313870|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|283991|3062661|29879|184487389110|3062661 / 184490451771|29879 / 313870|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|237565|1368501|76305|184489083270|1368501 / 184490451771|76305 / 313870|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|137225|1122264|176645|184489329507|1122264 / 184490451771|176645 / 313870|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|46002|1981986|267868|184488469785|1981986 / 184490451771|267868 / 313870|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|7|1115154|313863|184489336617|1115154 / 184490451771|313863 / 313870|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.999976|12 / 184490451771 (0.000000%)|15 / 313870 (0.004779%)|0.999960|0.999957|0.999957|
|2|HUNSPELL ENGLISH LUCENE FILTER|ANY_CANDIDATE|0.581603|1978852 / 184490451771 (0.001073%)|262641 / 313870 (83.678274%)|0.030370|0.043712|0.064174|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.999962|0.999952|1.000000|0.999976|1.000000|0.000000|
|2|HUNSPELL ENGLISH LUCENE FILTER|ANY_CANDIDATE|0.025235|0.163217|0.999989|0.581603|0.999988|0.000012|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.999960|0.999957|0.999954|0.999914|0.999957|0.999957|
|2|HUNSPELL ENGLISH LUCENE FILTER|ANY_CANDIDATE|0.030370|0.043712|0.077961|0.022344|0.064178|0.064174|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL ENGLISH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|313855|12|15|184490451759|12 / 184490451771|15 / 313870|
|2|HUNSPELL ENGLISH LUCENE FILTER|ANY_CANDIDATE|51229|1978852|262641|184488472919|1978852 / 184490451771|262641 / 313870|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999945|11482166 / 184490451771 (0.006224%)|15 / 313870 (0.004779%)|0.033039|0.051834|0.163107|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.581603|2008917 / 184490451771 (0.001089%)|262641 / 313870 (83.678274%)|0.029943|0.043158|0.063704|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.026607|0.999952|0.999938|0.999945|0.999938|0.000062|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.024867|0.163217|0.999989|0.581603|0.999988|0.000012|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.033039|0.051834|0.120237|0.026607|0.163112|0.163107|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.029943|0.043158|0.077254|0.022055|0.063708|0.063704|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|313855|11482166|15|184478969605|11482166 / 184490451771|15 / 313870|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|51229|2008917|262641|184488442854|2008917 / 184490451771|262641 / 313870|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|21854|1149874|10332280|29208|4.808384%|1355|2838145|
|HUNSPELL ENGLISH LUCENE FILTER|5227|3134|26931|6837|1.125545%|4|614296|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **15 result rows**, **11 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.965820|1148489 / 170474840204 (0.000674%)|21319 / 311891 (6.835401%)|0.239424|0.331902|0.433722|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.954900|1552702 / 170474840204 (0.000911%)|28130 / 311891 (9.019177%)|0.185277|0.264166|0.374937|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.954900|1552702 / 170474840204 (0.000911%)|28130 / 311891 (9.019177%)|0.185277|0.264166|0.374937|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.954900|1552702 / 170474840204 (0.000911%)|28130 / 311891 (9.019177%)|0.185277|0.264166|0.374937|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.954850|1561891 / 170474840204 (0.000916%)|28161 / 311891 (9.029116%)|0.184375|0.263016|0.373964|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.954762|1550615 / 170474840204 (0.000910%)|28216 / 311891 (9.046750%)|0.185431|0.264353|0.375045|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.952710|3045870 / 170474840204 (0.001787%)|29493 / 311891 (9.456188%)|0.103633|0.155157|0.277170|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.880820|1367069 / 170474840204 (0.000802%)|74340 / 311891 (23.835250%)|0.176477|0.247899|0.335789|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.719516|1120871 / 170474840204 (0.000657%)|174959 / 311891 (56.096200%)|0.128139|0.174470|0.218621|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.573619|1978041 / 170474840204 (0.001160%)|265965 / 311891 (85.274984%)|0.027312|0.039323|0.057799|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.500005|1113773 / 170474840204 (0.000653%)|311886 / 311891 (99.998397%)|0.000005|0.000007|0.000005|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.201918|0.931646|0.999993|0.965820|0.999993|0.000007|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.154515|0.909808|0.999991|0.954900|0.999991|0.000009|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.154515|0.909808|0.999991|0.954900|0.999991|0.000009|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.154515|0.909808|0.999991|0.954900|0.999991|0.000009|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.153731|0.909709|0.999991|0.954850|0.999991|0.000009|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.154651|0.909532|0.999991|0.954762|0.999991|0.000009|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.084848|0.905438|0.999982|0.952710|0.999982|0.000018|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.148042|0.761647|0.999992|0.880820|0.999992|0.000008|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.108866|0.439038|0.999993|0.719516|0.999992|0.000008|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.022691|0.147250|0.999988|0.573619|0.999987|0.000013|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.000004|0.000016|0.999993|0.500005|0.999992|0.000008|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.239424|0.331902|0.540775|0.198970|0.433723|0.433722|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.185277|0.264166|0.460049|0.152184|0.374939|0.374937|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.185277|0.264166|0.460049|0.152184|0.374939|0.374937|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.185277|0.264166|0.460049|0.152184|0.374939|0.374937|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.184375|0.263016|0.458637|0.151421|0.373966|0.373964|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.185431|0.264353|0.460234|0.152308|0.375047|0.375045|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.103633|0.155157|0.308576|0.084103|0.277173|0.277170|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.176477|0.247899|0.416437|0.141487|0.335791|0.335789|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.128139|0.174470|0.273277|0.095572|0.218624|0.218621|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.027312|0.039323|0.070190|0.020056|0.057804|0.057799|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.000005|0.000007|0.000011|0.000004|0.000008|0.000005|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.331900|0.993959|0.997731|0.995842|0.995842|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|0.264164|0.968645|0.997109|0.982671|0.982671|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|0.264164|0.968645|0.997109|0.982671|0.982671|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|0.264164|0.968645|0.997109|0.982671|0.982671|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|0.263014|0.968020|0.997096|0.982343|0.982343|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|0.264351|0.968894|0.997102|0.982795|0.982795|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|0.155154|0.936077|0.996487|0.965338|0.965338|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|0.247897|0.979822|0.991991|0.985869|0.985869|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|0.174467|0.994994|0.980520|0.987704|0.987704|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|0.039320|0.993066|0.962317|0.977450|0.977450|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|0.000004|0.995605|0.956423|0.975621|0.975621|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|290572|1148489|21319|170473691715|1148489 / 170474840204|21319 / 311891|
|2|ENGLISH LUCENE PORTER COPIED|PRIMARY_OUTPUT|283761|1552702|28130|170473287502|1552702 / 170474840204|28130 / 311891|
|3|ENGLISH LUCENE PORTER FILTER|PRIMARY_OUTPUT|283761|1552702|28130|170473287502|1552702 / 170474840204|28130 / 311891|
|4|ENGLISH OPENNLP PORTER|PRIMARY_OUTPUT|283761|1552702|28130|170473287502|1552702 / 170474840204|28130 / 311891|
|5|ENGLISH SNOWBALL PORTER2|PRIMARY_OUTPUT|283730|1561891|28161|170473278313|1561891 / 170474840204|28161 / 311891|
|6|ENGLISH SNOWBALL ORIGINAL PORTER|PRIMARY_OUTPUT|283675|1550615|28216|170473289589|1550615 / 170474840204|28216 / 311891|
|7|ENGLISH PAICE HUSK LANCASTER|PRIMARY_OUTPUT|282398|3045870|29493|170471794334|3045870 / 170474840204|29493 / 311891|
|8|ENGLISH LUCENE KSTEM FILTER|PRIMARY_OUTPUT|237551|1367069|74340|170473473135|1367069 / 170474840204|74340 / 311891|
|9|ENGLISH LUCENE MINIMAL FILTER|PRIMARY_OUTPUT|136932|1120871|174959|170473719333|1120871 / 170474840204|174959 / 311891|
|10|HUNSPELL ENGLISH LUCENE FILTER|PRIMARY_OUTPUT|45926|1978041|265965|170472862163|1978041 / 170474840204|265965 / 311891|
|11|ENGLISH LUCENE POSSESSIVE FILTER|PRIMARY_OUTPUT|5|1113773|311886|170473726431|1113773 / 170474840204|311886 / 311891|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 170474840204 (0.000000%)|0 / 311891 (0.000000%)|1.000000|1.000000|1.000000|
|2|HUNSPELL ENGLISH LUCENE FILTER|ANY_CANDIDATE|0.581994|1974950 / 170474840204 (0.001158%)|260741 / 311891 (83.600040%)|0.030387|0.043756|0.064341|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL ENGLISH LUCENE FILTER|ANY_CANDIDATE|0.025246|0.164000|0.999988|0.581994|0.999987|0.000013|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL ENGLISH LUCENE FILTER|ANY_CANDIDATE|0.030387|0.043756|0.078123|0.022367|0.064345|0.064341|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL ENGLISH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|311891|0|0|170474840204|0 / 170474840204|0 / 311891|
|2|HUNSPELL ENGLISH LUCENE FILTER|ANY_CANDIDATE|51150|1974950|260741|170472865254|1974950 / 170474840204|260741 / 311891|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999966|11470018 / 170474840204 (0.006728%)|0 / 311891 (0.000000%)|0.032872|0.051579|0.162697|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.581994|2004598 / 170474840204 (0.001176%)|260741 / 311891 (83.600040%)|0.029965|0.043208|0.063875|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.026472|1.000000|0.999933|0.999966|0.999933|0.000067|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.024881|0.164000|0.999988|0.581994|0.999987|0.000013|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.032872|0.051579|0.119687|0.026472|0.162702|0.162697|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|0.029965|0.043208|0.077422|0.022081|0.063879|0.063875|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|311891|11470018|0|170463370186|11470018 / 170474840204|0 / 311891|
|2|HUNSPELL ENGLISH LUCENE FILTER|ALL_CANDIDATES|51150|2004598|260741|170472835606|2004598 / 170474840204|260741 / 311891|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|21319|1148489|10321529|28826|4.936720%|1355|2812871|
|HUNSPELL ENGLISH LUCENE FILTER|5224|3091|26557|6786|1.162165%|4|590716|

### Output Policies and Metric Definitions

`PRIMARY_OUTPUT` uses one deterministic stem per form and therefore defines a strict partition. `ANY_CANDIDATE` is an optimistic oracle-assisted pairwise upper bound: a same-group pair succeeds when candidates intersect, while a different-group pair succeeds when a non-colliding selection exists. Candidate choices may differ between pairs, so this is not deterministic runtime behaviour and need not represent one globally consistent assignment. `ALL_CANDIDATES` activates every returned candidate; forms are related when candidate sets intersect. Alternatives can reduce under-stemming but can introduce cross-group collisions, and the resulting relation can overlap and need not be a partition.

For each row, `TP = underPossiblePairs - underErrorPairs`, `FN = underErrorPairs`, `FP = overErrorPairs`, and `TN = overPossiblePairs - overErrorPairs`. TP and FN concern same-group pairs; FP and TN concern different-group pairs. Consequently, under-stemming and over-stemming use different denominators. Undefined values are rendered as `n/a`.

- Under-stemming rate: `FN / (TP + FN)`, the false-negative rate over same-group pairs.
- Over-stemming rate: `FP / (TN + FP)`, the false-positive rate over different-group pairs.
- Pairwise precision: `TP / (TP + FP)`, the fraction of predicted conflations that are gold-standard positive pairs.
- Pairwise recall: `TP / (TP + FN)`, the fraction of gold-standard positive pairs successfully connected.
- Pairwise specificity: `TN / (TN + FP)`, the fraction of different-group pairs correctly separated.
- Balanced accuracy: `(recall + specificity) / 2`. It gives equal weight to positive and negative pair classes and is less dominated by the large true-negative class than ordinary accuracy. It does not replace the raw errors or other metrics.
- Pairwise F-beta: `((1 + betaSquared) * TP) / (((1 + betaSquared) * TP) + (betaSquared * FN) + FP)`. F0.5 emphasizes precision and penalizes over-stemming more; F1 weights precision and recall equally; F2 emphasizes recall and penalizes under-stemming more.
- MCC: `(TP * TN - FP * FN) / sqrt((TP + FP) * (TP + FN) * (TN + FP) * (TN + FN))`. It uses all confusion counts and remains useful under class imbalance, except when its denominator is degenerate.
- Jaccard index: `TP / (TP + FP + FN)`.
- Fowlkes–Mallows index: `sqrt(precision * recall)`.
- Pairwise accuracy: `(TP + TN) / (TP + TN + FP + FN)`. It can be dominated by true-negative cross-group pairs.
- Pairwise error rate: `(FP + FN) / (TP + TN + FP + FN)`.

Adjusted Rand Index uses the gold/predicted contingency table and chance correction. Homogeneity is `1 - H(gold | predicted) / H(gold)`; completeness is `1 - H(predicted | gold) / H(predicted)`; V-measure is their harmonic mean; normalized mutual information uses the arithmetic-mean entropy normalization `MI / ((H(gold) + H(predicted)) / 2)`. These partition-only metrics apply to `PRIMARY_OUTPUT`; candidate-relation rows show `n/a`.

### Provenance

- Authoritative source: `docs/benchmarks/data/stemming-quality.csv`
- Source SHA-256: `5a93a6ab60e46489737cd649eb1ac48182114b9038f7f20195ab9d1c1fc0dd28`
- Evaluation command: `./gradlew stemmingQuality`
- Dictionary language: `US_UK`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
