# Finnish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Finnish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `FI_FI` | 57,027 | 1,865,215 | 110,525 | 1,754,690 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **1,865,215**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 745 | 0.040% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 1,176,003 | 63.049% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 565,585 | 30.323% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 116,946 | 6.270% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 5,936 | 0.318% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 98.661% | 98.803% | 96.408% | Full Radixor dictionary patch-command stemmer. |
| Lucene SnowballFilter | 10.991% | 10.268% | 22.471% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 10.991% | 10.268% | 22.471% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene FinnishLightStemFilter | 4.351% | 4.294% | 5.264% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `finnishRadixor` | 308.076 | 15.529 | 175.6 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene FinnishLightStemFilter | `finnishLuceneFinnishLightStemFilter` | 175.250 | 46.995 | 99.9 | 0.569 | Light Finnish suffix stemmer. |
| Official Snowball direct | `snowballDirect[FINNISH]` | 264.652 | 63.054 | 150.8 | 0.859 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[FINNISH]` | 374.883 | 238.157 | 213.6 | 1.217 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `FI_FI` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/fi_fi/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.984594** among 4 deterministic stemmers. The runner-up is `SNOWBALL FINNISH LUCENE FILTER` at 0.740353, a difference of 0.244242. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.988068** among 4 deterministic stemmers. The runner-up is `SNOWBALL FINNISH DIRECT` at 0.738400, a difference of 0.249668. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.984594|731279 / 1641126814491 (0.000045%)|971268 / 31523695 (3.081073%)|0.975128|0.972893|0.972899|
|2|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.740353|1922153 / 1641126814491 (0.000117%)|16370057 / 31523695 (51.929372%)|0.758996|0.623613|0.653138|
|3|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.739729|1544812 / 1641126814491 (0.000094%)|16409363 / 31523695 (52.054060%)|0.769880|0.627374|0.659540|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.695969|2223150 / 1641126814491 (0.000135%)|19168306 / 31523695 (60.806025%)|0.687649|0.536000|0.576338|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.976624|0.969189|1.000000|0.984594|0.999999|0.000001|
|2|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.887434|0.480706|0.999999|0.740353|0.999989|0.000011|
|3|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.907269|0.479459|0.999999|0.739729|0.999989|0.000011|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.847505|0.391940|0.999999|0.695969|0.999987|0.000013|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.975128|0.972893|0.970667|0.947216|0.972900|0.972899|
|2|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.758996|0.623613|0.529216|0.453080|0.653142|0.653138|
|3|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.769880|0.627374|0.529384|0.457061|0.659544|0.659540|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.687649|0.536000|0.439152|0.366120|0.576343|0.576338|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.972892|0.996085|0.993746|0.994914|0.994914|
|2|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.623608|0.990718|0.904385|0.945585|0.945585|
|3|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.627369|0.991872|0.904139|0.945975|0.945975|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.535994|0.988126|0.886473|0.934544|0.934544|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|30552427|731279|971268|1641126083212|731279 / 1641126814491|971268 / 31523695|
|2|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|15153638|1922153|16370057|1641124892338|1922153 / 1641126814491|16370057 / 31523695|
|3|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|15114332|1544812|16409363|1641125269679|1544812 / 1641126814491|16409363 / 31523695|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|12355389|2223150|19168306|1641124591341|2223150 / 1641126814491|19168306 / 31523695|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 1641126814491 (0.000000%)|0 / 31523695 (0.000000%)|1.000000|1.000000|1.000000|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|31523695|0|0|1641126814491|0 / 1641126814491|0 / 31523695|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999999|1683575 / 1641126814491 (0.000103%)|0 / 31523695 (0.000000%)|0.959025|0.973991|0.974320|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.949301|1.000000|0.999999|0.999999|0.999999|0.000001|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.959025|0.973991|0.989432|0.949301|0.974321|0.974320|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|31523695|1683575|0|1641125130916|1683575 / 1641126814491|0 / 31523695|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|971268|731279|952296|57328|3.164291%|6|1876272|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.988068|730145 / 1543589444152 (0.000047%)|735305 / 30813833 (2.386282%)|0.976268|0.976219|0.976218|
|2|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.738400|1513705 / 1543589444152 (0.000098%)|16121763 / 30813833 (52.319888%)|0.768117|0.624934|0.657464|
|3|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.738400|1513705 / 1543589444152 (0.000098%)|16121763 / 30813833 (52.319888%)|0.768117|0.624934|0.657464|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.694529|1806392 / 1543589444152 (0.000117%)|18825444 / 30813833 (61.094133%)|0.697056|0.537492|0.581469|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.976301|0.976137|1.000000|0.988068|0.999999|0.000001|
|2|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.906595|0.476801|0.999999|0.738400|0.999989|0.000011|
|3|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.906595|0.476801|0.999999|0.738400|0.999989|0.000011|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.869053|0.389059|0.999999|0.694529|0.999987|0.000013|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.976268|0.976219|0.976170|0.953543|0.976219|0.976218|
|2|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.768117|0.624934|0.526744|0.454475|0.657469|0.657464|
|3|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.768117|0.624934|0.526744|0.454475|0.657469|0.657464|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.697056|0.537492|0.437372|0.367514|0.581474|0.581469|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.976218|0.996000|0.996069|0.996035|0.996035|
|2|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|0.624929|0.991732|0.902933|0.945252|0.945252|
|3|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|0.624929|0.991732|0.902933|0.945252|0.945252|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.537486|0.989268|0.885294|0.934397|0.934397|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|30078528|730145|735305|1543588714007|730145 / 1543589444152|735305 / 30813833|
|2|SNOWBALL FINNISH DIRECT|PRIMARY_OUTPUT|14692070|1513705|16121763|1543587930447|1513705 / 1543589444152|16121763 / 30813833|
|3|SNOWBALL FINNISH LUCENE FILTER|PRIMARY_OUTPUT|14692070|1513705|16121763|1543587930447|1513705 / 1543589444152|16121763 / 30813833|
|4|FINNISH LUCENE FINNISH LIGHT STEM FILTER|PRIMARY_OUTPUT|11988389|1806392|18825444|1543587637760|1806392 / 1543589444152|18825444 / 30813833|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 1543589444152 (0.000000%)|0 / 30813833 (0.000000%)|1.000000|1.000000|1.000000|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|30813833|0|0|1543589444152|0 / 1543589444152|0 / 30813833|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999999|1653320 / 1543589444152 (0.000107%)|0 / 30813833 (0.000000%)|0.958843|0.973873|0.974205|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.949077|1.000000|0.999999|0.999999|0.999999|0.000001|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.958843|0.973873|0.989383|0.949077|0.974206|0.974205|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|30813833|1653320|0|1543587790832|1653320 / 1543589444152|0 / 30813833|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|735305|730145|923175|44331|2.523029%|6|1805864|

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
- Dictionary language: `FI_FI`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
