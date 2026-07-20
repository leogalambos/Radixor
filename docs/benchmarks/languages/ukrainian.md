# Ukrainian Stemmer Benchmarks

This page reports same-language stemming benchmarks for Ukrainian. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `UK_UA` | 1,493 | 15,737 | 2,985 | 12,752 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **15,737**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 249 | 1.582% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 4,160 | 26.435% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 5,859 | 37.231% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 3,004 | 19.089% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 2,465 | 15.664% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.307% | 99.365% | 99.062% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 86.815% | 83.759% | 99.866% | Benchmark-only Ukrainian Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene MorfologikFilter | 92.362% | 90.637% | 99.732% | Dictionary-based path; Morfologik can emit multiple terms. |
| Morfologik direct | 92.362% | 90.637% | 99.732% | Direct dictionary lookup; first returned stem is used for quality when no ranking weight is exposed. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `ukrainianRadixor` | 0.682 | 0.057 | 53.5 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 43.527 | 1.207 | 3413.3 | 63.799 | Benchmark-only Ukrainian Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Morfologik direct | `ukrainianMorfologikDirect` | 8.680 | 0.073 | 680.7 | 12.723 | Direct Morfologik dictionary lookup; first returned stem is used for quality. |
| Lucene MorfologikFilter | `ukrainianLuceneMorfologikFilter` | 14.575 | 0.248 | 1143.0 | 21.364 | Dictionary-based Morfologik TokenFilter; may emit multiple terms. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `UK_UA` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/uk_ua/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.995343** among 4 deterministic stemmers. The runner-up is `UKRAINIAN LUCENE MORFOLOGIK FILTER` at 0.928768, a difference of 0.066575. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.995342** among 4 deterministic stemmers. The runner-up is `UKRAINIAN LUCENE MORFOLOGIK FILTER` at 0.928751, a difference of 0.066591. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **12 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.995343|880 / 101387550 (0.000868%)|608 / 65340 (0.930517%)|0.987406|0.988637|0.988632|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.928768|828 / 101387550 (0.000817%)|9308 / 65340 (14.245485%)|0.956896|0.917054|0.919223|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.928646|828 / 101387550 (0.000817%)|9324 / 65340 (14.269972%)|0.956832|0.916912|0.919090|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.885793|794 / 101387550 (0.000783%)|14924 / 65340 (22.840526%)|0.933008|0.865139|0.871499|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.986588|0.990695|0.999991|0.995343|0.999985|0.000015|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.985438|0.857545|0.999992|0.928768|0.999900|0.000100|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.985434|0.857300|0.999992|0.928646|0.999900|0.000100|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.984495|0.771595|0.999992|0.885793|0.999845|0.000155|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.987406|0.988637|0.989871|0.977529|0.988639|0.988632|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.956896|0.917054|0.880397|0.846814|0.919270|0.919223|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.956832|0.916912|0.880190|0.846572|0.919137|0.919090|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.933008|0.865139|0.806475|0.762331|0.871568|0.871499|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.988630|0.997994|0.998266|0.998130|0.998130|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.917004|0.997990|0.971000|0.984310|0.984310|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.916862|0.997990|0.970876|0.984246|0.984246|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.865063|0.998114|0.949804|0.973360|0.973360|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|64732|880|608|101386670|880 / 101387550|608 / 65340|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|56032|828|9308|101386722|828 / 101387550|9308 / 65340|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|56016|828|9324|101386722|828 / 101387550|9324 / 65340|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|50416|794|14924|101386756|794 / 101387550|14924 / 65340|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 101387550 (0.000000%)|0 / 65340 (0.000000%)|1.000000|1.000000|1.000000|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.962151|122 / 101387550 (0.000120%)|4946 / 65340 (7.569636%)|0.982323|0.959732|0.960413|
|3|UKRAINIAN MORFOLOGIK DIRECT|ANY_CANDIDATE|0.962029|122 / 101387550 (0.000120%)|4962 / 65340 (7.594123%)|0.982267|0.959599|0.960286|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ANY_CANDIDATE|0.927570|326 / 101387550 (0.000322%)|9465 / 65340 (14.485767%)|0.962884|0.919443|0.922008|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.997984|0.924304|0.999999|0.962151|0.999950|0.000050|
|3|UKRAINIAN MORFOLOGIK DIRECT|ANY_CANDIDATE|0.997983|0.924059|0.999999|0.962029|0.999950|0.000050|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ANY_CANDIDATE|0.994199|0.855142|0.999997|0.927570|0.999903|0.000097|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.982323|0.959732|0.938156|0.922581|0.960438|0.960413|
|3|UKRAINIAN MORFOLOGIK DIRECT|ANY_CANDIDATE|0.982267|0.959599|0.937954|0.922337|0.960310|0.960286|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ANY_CANDIDATE|0.962884|0.919443|0.879752|0.850897|0.922053|0.922008|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|3|UKRAINIAN MORFOLOGIK DIRECT|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|65340|0|0|101387550|0 / 101387550|0 / 65340|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|60394|122|4946|101387428|122 / 101387550|4946 / 65340|
|3|UKRAINIAN MORFOLOGIK DIRECT|ANY_CANDIDATE|60378|122|4962|101387428|122 / 101387550|4962 / 65340|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ANY_CANDIDATE|55875|326|9465|101387224|326 / 101387550|9465 / 65340|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999993|1490 / 101387550 (0.001470%)|0 / 65340 (0.000000%)|0.982084|0.988727|0.988782|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.962145|1368 / 101387550 (0.001349%)|4946 / 65340 (7.569636%)|0.966650|0.950323|0.950669|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|0.962023|1368 / 101387550 (0.001349%)|4962 / 65340 (7.594123%)|0.966592|0.950191|0.950541|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|0.927565|1271 / 101387550 (0.001254%)|9465 / 65340 (14.485767%)|0.950501|0.912349|0.914347|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.977705|1.000000|0.999985|0.999993|0.999985|0.000015|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.977850|0.924304|0.999987|0.962145|0.999938|0.000062|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|0.977845|0.924059|0.999987|0.962023|0.999938|0.000062|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|0.977759|0.855142|0.999987|0.927565|0.999894|0.000106|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.982084|0.988727|0.995460|0.977705|0.988789|0.988782|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.966650|0.950323|0.934539|0.905349|0.950700|0.950669|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|0.966592|0.950191|0.934337|0.905109|0.950571|0.950541|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|0.950501|0.912349|0.877142|0.838825|0.914398|0.914347|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|65340|1490|0|101386060|1490 / 101387550|0 / 65340|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|60394|1368|4946|101386182|1368 / 101387550|4946 / 65340|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|60378|1368|4962|101386182|1368 / 101387550|4962 / 65340|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|55875|1271|9465|101386279|1271 / 101387550|9465 / 65340|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL UKRAINIAN LUCENE FILTER|5459|468|477|1322|9.280449%|6|15740|
|UKRAINIAN LUCENE MORFOLOGIK FILTER|4362|706|540|2207|15.493155%|6|16937|
|UKRAINIAN MORFOLOGIK DIRECT|4362|706|540|2207|15.493155%|6|16937|
|Radixor|608|880|610|190|1.333801%|2|14435|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **12 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.995342|880 / 101259406 (0.000869%)|608 / 65324 (0.930745%)|0.987403|0.988634|0.988629|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.928751|828 / 101259406 (0.000818%)|9308 / 65324 (14.248974%)|0.956884|0.917032|0.919202|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.928751|828 / 101259406 (0.000818%)|9308 / 65324 (14.248974%)|0.956884|0.917032|0.919202|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.885796|794 / 101259406 (0.000784%)|14920 / 65324 (22.839998%)|0.933007|0.865141|0.871500|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.986585|0.990693|0.999991|0.995342|0.999985|0.000015|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.985434|0.857510|0.999992|0.928751|0.999900|0.000100|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.985434|0.857510|0.999992|0.928751|0.999900|0.000100|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.984492|0.771600|0.999992|0.885796|0.999845|0.000155|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.987403|0.988634|0.989868|0.977524|0.988636|0.988629|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.956884|0.917032|0.880367|0.846777|0.919249|0.919202|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.956884|0.917032|0.880367|0.846777|0.919249|0.919202|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.933007|0.865141|0.806479|0.762334|0.871570|0.871500|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.988627|0.997992|0.998264|0.998128|0.998128|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.916982|0.997988|0.970978|0.984298|0.984298|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|0.916982|0.997988|0.970978|0.984298|0.984298|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|0.865065|0.998113|0.949788|0.973351|0.973351|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|64716|880|608|101258526|880 / 101259406|608 / 65324|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|56016|828|9308|101258578|828 / 101259406|9308 / 65324|
|3|UKRAINIAN MORFOLOGIK DIRECT|PRIMARY_OUTPUT|56016|828|9308|101258578|828 / 101259406|9308 / 65324|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|PRIMARY_OUTPUT|50404|794|14920|101258612|794 / 101259406|14920 / 65324|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 101259406 (0.000000%)|0 / 65324 (0.000000%)|1.000000|1.000000|1.000000|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.962142|122 / 101259406 (0.000120%)|4946 / 65324 (7.571490%)|0.982318|0.959722|0.960404|
|3|UKRAINIAN MORFOLOGIK DIRECT|ANY_CANDIDATE|0.962142|122 / 101259406 (0.000120%)|4946 / 65324 (7.571490%)|0.982318|0.959722|0.960404|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ANY_CANDIDATE|0.927552|326 / 101259406 (0.000322%)|9465 / 65324 (14.489315%)|0.962874|0.919422|0.921988|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.997983|0.924285|0.999999|0.962142|0.999950|0.000050|
|3|UKRAINIAN MORFOLOGIK DIRECT|ANY_CANDIDATE|0.997983|0.924285|0.999999|0.962142|0.999950|0.000050|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ANY_CANDIDATE|0.994198|0.855107|0.999997|0.927552|0.999903|0.000097|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.982318|0.959722|0.938141|0.922562|0.960428|0.960404|
|3|UKRAINIAN MORFOLOGIK DIRECT|ANY_CANDIDATE|0.982318|0.959722|0.938141|0.922562|0.960428|0.960404|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ANY_CANDIDATE|0.962874|0.919422|0.879722|0.850861|0.922033|0.921988|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|3|UKRAINIAN MORFOLOGIK DIRECT|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|65324|0|0|101259406|0 / 101259406|0 / 65324|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|60378|122|4946|101259284|122 / 101259406|4946 / 65324|
|3|UKRAINIAN MORFOLOGIK DIRECT|ANY_CANDIDATE|60378|122|4946|101259284|122 / 101259406|4946 / 65324|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ANY_CANDIDATE|55859|326|9465|101259080|326 / 101259406|9465 / 65324|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999993|1490 / 101259406 (0.001471%)|0 / 65324 (0.000000%)|0.982079|0.988724|0.988779|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.962136|1368 / 101259406 (0.001351%)|4946 / 65324 (7.571490%)|0.966642|0.950311|0.950657|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|0.962136|1368 / 101259406 (0.001351%)|4946 / 65324 (7.571490%)|0.966642|0.950311|0.950657|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|0.927547|1271 / 101259406 (0.001255%)|9465 / 65324 (14.489315%)|0.950487|0.912326|0.914325|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.977699|1.000000|0.999985|0.999993|0.999985|0.000015|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.977845|0.924285|0.999986|0.962136|0.999938|0.000062|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|0.977845|0.924285|0.999986|0.962136|0.999938|0.000062|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|0.977752|0.855107|0.999987|0.927547|0.999894|0.000106|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.982079|0.988724|0.995459|0.977699|0.988787|0.988779|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.966642|0.950311|0.934522|0.905326|0.950688|0.950657|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|0.966642|0.950311|0.934522|0.905326|0.950688|0.950657|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|0.950487|0.912326|0.877111|0.838787|0.914376|0.914325|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|65324|1490|0|101257916|1490 / 101259406|0 / 65324|
|2|UKRAINIAN LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|60378|1368|4946|101258038|1368 / 101259406|4946 / 65324|
|3|UKRAINIAN MORFOLOGIK DIRECT|ALL_CANDIDATES|60378|1368|4946|101258038|1368 / 101259406|4946 / 65324|
|4|HUNSPELL UKRAINIAN LUCENE FILTER|ALL_CANDIDATES|55859|1271|9465|101258135|1271 / 101259406|9465 / 65324|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL UKRAINIAN LUCENE FILTER|5455|468|477|1321|9.279292%|6|15730|
|UKRAINIAN LUCENE MORFOLOGIK FILTER|4362|706|540|2207|15.502950%|6|16928|
|UKRAINIAN MORFOLOGIK DIRECT|4362|706|540|2207|15.502950%|6|16928|
|Radixor|608|880|610|190|1.334645%|2|14426|

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
- Dictionary language: `UK_UA`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
