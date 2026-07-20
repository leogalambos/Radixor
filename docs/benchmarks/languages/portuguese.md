# Portuguese Stemmer Benchmarks

This page reports same-language stemming benchmarks for Portuguese. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `PT_PT` | 4,001 | 215,490 | 8,002 | 207,488 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **215,490**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 3,806 | 1.766% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 120,691 | 56.008% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 71,284 | 33.080% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 8,003 | 3.714% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 11,706 | 5.432% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.815% | 99.808% | 100.000% | Full Radixor dictionary patch-command stemmer. |
| Lucene PortugueseLightStemFilter | 8.966% | 5.558% | 97.326% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |
| Lucene PortugueseMinimalStemFilter | 5.539% | 1.896% | 100.000% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SnowballFilter | 0.625% | 0.558% | 2.374% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 0.625% | 0.558% | 2.374% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene PortugueseStemFilter | 0.312% | 0.308% | 0.425% | Portuguese RSLP-style Lucene TokenFilter stemmer. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `portugueseRadixor` | 12.109 | 0.698 | 58.4 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene PortugueseLightStemFilter | `portugueseLucenePortugueseLightStemFilter` | 11.172 | 1.870 | 53.8 | 0.923 | Light Portuguese suffix stemmer. |
| Lucene PortugueseMinimalStemFilter | `portugueseLucenePortugueseMinimalStemFilter` | 16.038 | 1.752 | 77.3 | 1.325 | Minimal Portuguese suffix reducer. |
| Official Snowball direct | `snowballDirect[PORTUGUESE]` | 53.725 | 5.356 | 258.9 | 4.437 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[PORTUGUESE]` | 57.457 | 1.182 | 276.9 | 4.745 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |
| Lucene PortugueseStemFilter | `portugueseLucenePortugueseStemFilter` | 165.447 | 40.334 | 797.4 | 13.663 | Portuguese RSLP-style Lucene TokenFilter. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `PT_PT` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/pt_pt/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.998502** among 6 deterministic stemmers. The runner-up is `SNOWBALL PORTUGUESE DIRECT` at 0.938800, a difference of 0.059702. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.998502** among 6 deterministic stemmers. The runner-up is `SNOWBALL PORTUGUESE DIRECT` at 0.938800, a difference of 0.059702. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **8 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998502|20678 / 22358203756 (0.000092%)|16444 / 5489060 (0.299578%)|0.996389|0.996620|0.996619|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.938800|167230 / 22358203756 (0.000748%)|671821 / 5489060 (12.239272%)|0.947271|0.919888|0.920940|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.938800|167230 / 22358203756 (0.000748%)|671821 / 5489060 (12.239272%)|0.947271|0.919888|0.920940|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.846459|99075 / 22358203756 (0.000443%)|1685572 / 5489060 (30.707844%)|0.901330|0.809975|0.821750|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.513648|2511 / 22358203756 (0.000011%)|5339230 / 5489060 (97.270389%)|0.122843|0.053118|0.163828|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.503954|598 / 22358203756 (0.000003%)|5445654 / 5489060 (99.209227%)|0.038310|0.015690|0.088308|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996236|0.997004|0.999999|0.998502|0.999998|0.000002|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.966450|0.877607|0.999993|0.938800|0.999962|0.000038|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.966450|0.877607|0.999993|0.938800|0.999962|0.000038|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.974613|0.692922|0.999996|0.846459|0.999920|0.000080|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.983517|0.027296|1.000000|0.513648|0.999761|0.000239|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.986410|0.007908|1.000000|0.503954|0.999756|0.000244|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996389|0.996620|0.996850|0.993262|0.996620|0.996619|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.947271|0.919888|0.894045|0.851661|0.920958|0.920940|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.947271|0.919888|0.894045|0.851661|0.920958|0.920940|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.901330|0.809975|0.735434|0.680636|0.821785|0.821750|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.122843|0.053118|0.033885|0.027284|0.163848|0.163828|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.038310|0.015690|0.009865|0.007907|0.088319|0.088308|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996619|0.999299|0.999347|0.999323|0.999323|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.919870|0.996663|0.967924|0.982083|0.982083|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.919870|0.996663|0.967924|0.982083|0.982083|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.809936|0.996729|0.918475|0.956003|0.956003|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.053105|0.999226|0.720580|0.837330|0.837330|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.015686|0.999664|0.692383|0.818122|0.818122|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|5472616|20678|16444|22358183078|20678 / 22358203756|16444 / 5489060|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|4817239|167230|671821|22358036526|167230 / 22358203756|671821 / 5489060|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|4817239|167230|671821|22358036526|167230 / 22358203756|671821 / 5489060|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|3803488|99075|1685572|22358104681|99075 / 22358203756|1685572 / 5489060|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|149830|2511|5339230|22358201245|2511 / 22358203756|5339230 / 5489060|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|43406|598|5445654|22358203158|598 / 22358203756|5445654 / 5489060|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 22358203756 (0.000000%)|0 / 5489060 (0.000000%)|1.000000|1.000000|1.000000|

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
|1|Radixor|ANY_CANDIDATE|5489060|0|0|22358203756|0 / 22358203756|0 / 5489060|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999999|38310 / 22358203756 (0.000171%)|0 / 5489060 (0.000000%)|0.994448|0.996522|0.996528|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.993069|1.000000|0.999998|0.999999|0.999998|0.000002|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.994448|0.996522|0.998606|0.993069|0.996528|0.996528|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|5489060|38310|0|22358165446|38310 / 22358203756|0 / 5489060|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|16444|20678|17632|790|0.373542%|3|212297|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **8 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.998502|20678 / 22358203756 (0.000092%)|16444 / 5489060 (0.299578%)|0.996389|0.996620|0.996619|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.938800|167230 / 22358203756 (0.000748%)|671821 / 5489060 (12.239272%)|0.947271|0.919888|0.920940|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.938800|167230 / 22358203756 (0.000748%)|671821 / 5489060 (12.239272%)|0.947271|0.919888|0.920940|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.846459|99075 / 22358203756 (0.000443%)|1685572 / 5489060 (30.707844%)|0.901330|0.809975|0.821750|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.513648|2511 / 22358203756 (0.000011%)|5339230 / 5489060 (97.270389%)|0.122843|0.053118|0.163828|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.503954|598 / 22358203756 (0.000003%)|5445654 / 5489060 (99.209227%)|0.038310|0.015690|0.088308|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996236|0.997004|0.999999|0.998502|0.999998|0.000002|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.966450|0.877607|0.999993|0.938800|0.999962|0.000038|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.966450|0.877607|0.999993|0.938800|0.999962|0.000038|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.974613|0.692922|0.999996|0.846459|0.999920|0.000080|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.983517|0.027296|1.000000|0.513648|0.999761|0.000239|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.986410|0.007908|1.000000|0.503954|0.999756|0.000244|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996389|0.996620|0.996850|0.993262|0.996620|0.996619|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.947271|0.919888|0.894045|0.851661|0.920958|0.920940|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.947271|0.919888|0.894045|0.851661|0.920958|0.920940|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.901330|0.809975|0.735434|0.680636|0.821785|0.821750|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.122843|0.053118|0.033885|0.027284|0.163848|0.163828|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.038310|0.015690|0.009865|0.007907|0.088319|0.088308|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996619|0.999299|0.999347|0.999323|0.999323|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|0.919870|0.996663|0.967924|0.982083|0.982083|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|0.919870|0.996663|0.967924|0.982083|0.982083|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|0.809936|0.996729|0.918475|0.956003|0.956003|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|0.053105|0.999226|0.720580|0.837330|0.837330|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.015686|0.999664|0.692383|0.818122|0.818122|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|5472616|20678|16444|22358183078|20678 / 22358203756|16444 / 5489060|
|2|SNOWBALL PORTUGUESE DIRECT|PRIMARY_OUTPUT|4817239|167230|671821|22358036526|167230 / 22358203756|671821 / 5489060|
|3|SNOWBALL PORTUGUESE LUCENE FILTER|PRIMARY_OUTPUT|4817239|167230|671821|22358036526|167230 / 22358203756|671821 / 5489060|
|4|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|PRIMARY_OUTPUT|3803488|99075|1685572|22358104681|99075 / 22358203756|1685572 / 5489060|
|5|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|PRIMARY_OUTPUT|149830|2511|5339230|22358201245|2511 / 22358203756|5339230 / 5489060|
|6|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|PRIMARY_OUTPUT|43406|598|5445654|22358203158|598 / 22358203756|5445654 / 5489060|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 22358203756 (0.000000%)|0 / 5489060 (0.000000%)|1.000000|1.000000|1.000000|

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
|1|Radixor|ANY_CANDIDATE|5489060|0|0|22358203756|0 / 22358203756|0 / 5489060|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999999|38310 / 22358203756 (0.000171%)|0 / 5489060 (0.000000%)|0.994448|0.996522|0.996528|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.993069|1.000000|0.999998|0.999999|0.999998|0.000002|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.994448|0.996522|0.998606|0.993069|0.996528|0.996528|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|5489060|38310|0|22358165446|38310 / 22358203756|0 / 5489060|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|16444|20678|17632|790|0.373542%|3|212297|

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
- Dictionary language: `PT_PT`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
