# Hungarian Stemmer Benchmarks

This page reports same-language stemming benchmarks for Hungarian. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `HU_HU` | 19,406 | 935,713 | 38,775 | 896,938 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **935,713**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 15 | 0.002% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 149,173 | 15.942% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 746,296 | 79.757% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 40,125 | 4.288% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 104 | 0.011% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.222% | 99.537% | 91.948% | Full Radixor dictionary patch-command stemmer. |
| Lucene SnowballFilter | 66.445% | 66.938% | 55.043% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 66.445% | 66.938% | 55.043% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene HungarianLightStemFilter | 14.748% | 14.777% | 14.086% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `hungarianRadixor` | 62.232 | 6.412 | 69.4 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HungarianLightStemFilter | `hungarianLuceneHungarianLightStemFilter` | 92.813 | 6.929 | 103.5 | 1.491 | Light Hungarian suffix stemmer. |
| Official Snowball direct | `snowballDirect[HUNGARIAN]` | 157.765 | 13.202 | 175.9 | 2.535 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[HUNGARIAN]` | 188.863 | 15.880 | 210.6 | 3.035 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `HU_HU` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/hu_hu/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.995491** among 4 deterministic stemmers. The runner-up is `SNOWBALL HUNGARIAN LUCENE FILTER` at 0.822606, a difference of 0.172885. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.996163** among 4 deterministic stemmers. The runner-up is `SNOWBALL HUNGARIAN DIRECT` at 0.821708, a difference of 0.174455. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.995491|272900 / 419820542893 (0.000065%)|199837 / 22162103 (0.901706%)|0.988376|0.989352|0.989353|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.822606|1792049 / 419820542893 (0.000427%)|7862745 / 22162103 (35.478334%)|0.826288|0.747610|0.757196|
|3|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.822348|1506056 / 419820542893 (0.000359%)|7874191 / 22162103 (35.529981%)|0.837137|0.752866|0.763681|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.816668|4132555 / 419820542893 (0.000984%)|8125833 / 22162103 (36.665442%)|0.740018|0.696055|0.699478|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.987727|0.990983|0.999999|0.995491|0.999999|0.000001|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.888633|0.645217|0.999996|0.822606|0.999977|0.000023|
|3|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.904644|0.644700|0.999996|0.822348|0.999978|0.000022|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.772547|0.633346|0.999990|0.816668|0.999971|0.000029|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.988376|0.989352|0.990330|0.978929|0.989353|0.989353|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.826288|0.747610|0.682613|0.596947|0.757206|0.757196|
|3|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.837137|0.752866|0.684009|0.603677|0.763691|0.763681|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.740018|0.696055|0.657023|0.533807|0.699492|0.699478|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.989352|0.998036|0.997809|0.997922|0.997922|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.747599|0.990687|0.924490|0.956445|0.956445|
|3|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.752855|0.991948|0.924304|0.956932|0.956932|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.696040|0.982615|0.926772|0.953877|0.953877|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|21962266|272900|199837|419820269993|272900 / 419820542893|199837 / 22162103|
|2|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|14299358|1792049|7862745|419818750844|1792049 / 419820542893|7862745 / 22162103|
|3|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|14287912|1506056|7874191|419819036837|1506056 / 419820542893|7874191 / 22162103|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|14036270|4132555|8125833|419816410338|4132555 / 419820542893|8125833 / 22162103|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 419820542893 (0.000000%)|0 / 22162103 (0.000000%)|1.000000|1.000000|1.000000|

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
|1|Radixor|ANY_CANDIDATE|22162103|0|0|419820542893|0 / 419820542893|0 / 22162103|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999999|460158 / 419820542893 (0.000110%)|0 / 22162103 (0.000000%)|0.983661|0.989725|0.989777|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.979659|1.000000|0.999999|0.999999|0.999999|0.000001|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.983661|0.989725|0.995865|0.979659|0.989777|0.989777|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|22162103|460158|0|419820082735|460158 / 419820542893|0 / 22162103|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|199837|272900|187258|12320|1.344473%|5|929326|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996163|272775 / 385870694917 (0.000071%)|164277 / 21411411 (0.767240%)|0.988321|0.989820|0.989822|
|2|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.821708|1496670 / 385870694917 (0.000388%)|7634885 / 21411411 (35.658019%)|0.834899|0.751079|0.761809|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.821708|1496670 / 385870694917 (0.000388%)|7634885 / 21411411 (35.658019%)|0.834899|0.751079|0.761809|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.815077|3639046 / 385870694917 (0.000943%)|7918708 / 21411411 (36.983588%)|0.750108|0.700135|0.704477|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.987325|0.992328|0.999999|0.996163|0.999999|0.000001|
|2|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.902007|0.643420|0.999996|0.821708|0.999976|0.000024|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.902007|0.643420|0.999996|0.821708|0.999976|0.000024|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.787585|0.630164|0.999991|0.815077|0.999970|0.000030|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.988321|0.989820|0.991323|0.979845|0.989823|0.989822|
|2|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.834899|0.751079|0.682555|0.601383|0.761820|0.761809|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.834899|0.751079|0.682555|0.601383|0.761820|0.761809|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.750108|0.700135|0.656404|0.538621|0.704491|0.704477|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.989819|0.997945|0.998273|0.998109|0.998109|
|2|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|0.751068|0.991610|0.923288|0.956230|0.956230|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|0.751068|0.991610|0.923288|0.956230|0.956230|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.700120|0.983687|0.925487|0.953700|0.953700|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|21247134|272775|164277|385870422142|272775 / 385870694917|164277 / 21411411|
|2|SNOWBALL HUNGARIAN DIRECT|PRIMARY_OUTPUT|13776526|1496670|7634885|385869198247|1496670 / 385870694917|7634885 / 21411411|
|3|SNOWBALL HUNGARIAN LUCENE FILTER|PRIMARY_OUTPUT|13776526|1496670|7634885|385869198247|1496670 / 385870694917|7634885 / 21411411|
|4|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|13492703|3639046|7918708|385867055871|3639046 / 385870694917|7918708 / 21411411|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 385870694917 (0.000000%)|0 / 21411411 (0.000000%)|1.000000|1.000000|1.000000|

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
|1|Radixor|ANY_CANDIDATE|21411411|0|0|385870694917|0 / 385870694917|0 / 21411411|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999999|458462 / 385870694917 (0.000119%)|0 / 21411411 (0.000000%)|0.983159|0.989407|0.989462|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.979037|1.000000|0.999999|0.999999|0.999999|0.000001|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.983159|0.989407|0.995736|0.979037|0.989463|0.989462|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|21411411|458462|0|385870236455|458462 / 385870694917|0 / 21411411|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|164277|272775|185687|11153|1.269532%|5|890245|

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
- Dictionary language: `HU_HU`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
