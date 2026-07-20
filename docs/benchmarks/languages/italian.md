# Italian Stemmer Benchmarks

This page reports same-language stemming benchmarks for Italian. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `IT_IT` | 10,009 | 337,546 | 20,004 | 317,542 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **337,546**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 302,171 | 89.520% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 12,348 | 3.658% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 20,013 | 5.929% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 3,014 | 0.893% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.056% | 98.997% | 100.000% | Full Radixor dictionary patch-command stemmer. |
| Lucene ItalianLightStemFilter | 0.466% | 0.479% | 0.270% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |
| Lucene SnowballFilter | 0.041% | 0.043% | 0.010% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 0.041% | 0.043% | 0.010% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `italianRadixor` | 24.491 | 3.128 | 77.1 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene ItalianLightStemFilter | `italianLuceneItalianLightStemFilter` | 15.977 | 1.041 | 50.3 | 0.652 | Light Italian suffix stemmer. |
| Official Snowball direct | `snowballDirect[ITALIAN]` | 109.526 | 12.572 | 344.9 | 4.472 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[ITALIAN]` | 116.260 | 7.459 | 366.1 | 4.747 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `IT_IT` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/it_it/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.996507** among 4 deterministic stemmers. The runner-up is `SNOWBALL ITALIAN DIRECT` at 0.866189, a difference of 0.130318. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.996512** among 4 deterministic stemmers. The runner-up is `SNOWBALL ITALIAN DIRECT` at 0.866205, a difference of 0.130307. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996507|124172 / 53638521211 (0.000231%)|42908 / 6143814 (0.698394%)|0.982618|0.986492|0.986512|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.866189|504775 / 53638521211 (0.000941%)|1644164 / 6143814 (26.761292%)|0.859975|0.807240|0.811470|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.866189|504775 / 53638521211 (0.000941%)|1644164 / 6143814 (26.761292%)|0.859975|0.807240|0.811470|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.508926|10589 / 53638521211 (0.000020%)|6034130 / 6143814 (98.214725%)|0.082782|0.035020|0.127588|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.980053|0.993016|0.999998|0.996507|0.999997|0.000003|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.899134|0.732387|0.999991|0.866189|0.999960|0.000040|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.899134|0.732387|0.999991|0.866189|0.999960|0.000040|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.911959|0.017853|1.000000|0.508926|0.999887|0.000113|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.982618|0.986492|0.990396|0.973344|0.986513|0.986512|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.859975|0.807240|0.760598|0.676783|0.811489|0.811470|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.859975|0.807240|0.760598|0.676783|0.811489|0.811470|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.082782|0.035020|0.022207|0.017822|0.127597|0.127588|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.986490|0.995780|0.997113|0.996446|0.996446|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.807220|0.987994|0.933408|0.959925|0.959925|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.807220|0.987994|0.933408|0.959925|0.959925|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.035016|0.997481|0.737537|0.848037|0.848037|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|6100906|124172|42908|53638397039|124172 / 53638521211|42908 / 6143814|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|4499650|504775|1644164|53638016436|504775 / 53638521211|1644164 / 6143814|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|4499650|504775|1644164|53638016436|504775 / 53638521211|1644164 / 6143814|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|109684|10589|6034130|53638510622|10589 / 53638521211|6034130 / 6143814|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.999993|0 / 53638521211 (0.000000%)|80 / 6143814 (0.001302%)|0.999997|0.999993|0.999993|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0.999987|1.000000|0.999993|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.999997|0.999993|0.999990|0.999987|0.999993|0.999993|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|6143734|0|80|53638521211|0 / 53638521211|80 / 6143814|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999992|170950 / 53638521211 (0.000319%)|80 / 6143814 (0.001302%)|0.978222|0.986272|0.986363|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.972928|0.999987|0.999997|0.999992|0.999997|0.000003|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.978222|0.986272|0.994455|0.972916|0.986365|0.986363|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|6143734|170950|80|53638350261|170950 / 53638521211|80 / 6143814|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|42828|124172|46778|6254|1.909321%|4|334175|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996512|124171 / 53611667072 (0.000232%)|42828 / 6142174 (0.697278%)|0.982617|0.986495|0.986515|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.866205|504774 / 53611667072 (0.000942%)|1643522 / 6142174 (26.757985%)|0.859970|0.807252|0.811479|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.866205|504774 / 53611667072 (0.000942%)|1643522 / 6142174 (26.757985%)|0.859970|0.807252|0.811479|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.508927|10588 / 53611667072 (0.000020%)|6032516 / 6142174 (98.214671%)|0.082784|0.035021|0.127589|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.980048|0.993027|0.999998|0.996512|0.999997|0.000003|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.899114|0.732420|0.999991|0.866205|0.999960|0.000040|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.899114|0.732420|0.999991|0.866205|0.999960|0.000040|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.911947|0.017853|1.000000|0.508927|0.999887|0.000113|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.982617|0.986495|0.990404|0.973350|0.986516|0.986515|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.859970|0.807252|0.760624|0.676800|0.811498|0.811479|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.859970|0.807252|0.760624|0.676800|0.811498|0.811479|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.082784|0.035021|0.022208|0.017823|0.127598|0.127589|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.986493|0.995780|0.997115|0.996447|0.996447|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|0.807232|0.987991|0.933413|0.959927|0.959927|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|0.807232|0.987991|0.933413|0.959927|0.959927|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.035017|0.997481|0.737534|0.848035|0.848035|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|6099346|124171|42828|53611542901|124171 / 53611667072|42828 / 6142174|
|2|SNOWBALL ITALIAN DIRECT|PRIMARY_OUTPUT|4498652|504774|1643522|53611162298|504774 / 53611667072|1643522 / 6142174|
|3|SNOWBALL ITALIAN LUCENE FILTER|PRIMARY_OUTPUT|4498652|504774|1643522|53611162298|504774 / 53611667072|1643522 / 6142174|
|4|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|109658|10588|6032516|53611656484|10588 / 53611667072|6032516 / 6142174|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 53611667072 (0.000000%)|0 / 6142174 (0.000000%)|1.000000|1.000000|1.000000|

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
|1|Radixor|ANY_CANDIDATE|6142174|0|0|53611667072|0 / 53611667072|0 / 6142174|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999998|170949 / 53611667072 (0.000319%)|0 / 6142174 (0.000000%)|0.978219|0.986275|0.986366|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.972922|1.000000|0.999997|0.999998|0.999997|0.000003|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.978219|0.986275|0.994464|0.972922|0.986368|0.986366|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|6142174|170949|0|53611496123|170949 / 53611667072|0 / 6142174|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|42828|124171|46778|6252|1.909188%|4|334089|

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
- Dictionary language: `IT_IT`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
