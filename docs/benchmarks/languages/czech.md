# Czech Stemmer Benchmarks

This page reports same-language stemming benchmarks for Czech. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `CS_CZ` | 5,113 | 56,612 | 10,049 | 46,563 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **56,612**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 675 | 1.192% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 22,681 | 40.064% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 14,980 | 26.461% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 10,109 | 17.857% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 8,167 | 14.426% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.465% | 99.439% | 99.582% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 84.850% | 82.269% | 96.806% | Benchmark-only Czech Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene CzechStemFilter | 16.784% | 15.538% | 22.559% | Lucene Czech suffix stemmer implemented as a TokenFilter. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `czechRadixor` | 3.332 | 0.240 | 71.6 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 346.819 | 3.622 | 7448.4 | 104.091 | Benchmark-only Czech Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene CzechStemFilter | `czechLuceneCzechStemFilter` | 3.163 | 0.253 | 67.9 | 0.949 | Czech suffix stemmer implemented as a Lucene TokenFilter. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `CS_CZ` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/cs_cz/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.996565** among 3 deterministic stemmers. The runner-up is `HUNSPELL CZECH LUCENE FILTER` at 0.853752, a difference of 0.142813. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.997139** among 3 deterministic stemmers. The runner-up is `HUNSPELL CZECH LUCENE FILTER` at 0.852770, a difference of 0.144369. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **7 result rows**, **3 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.996565|3867 / 1334876815 (0.000290%)|2073 / 301835 (0.686799%)|0.988432|0.990189|0.990191|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.853752|11408 / 1334876815 (0.000855%)|88283 / 301835 (29.248762%)|0.888560|0.810759|0.819499|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.793614|14480 / 1334876815 (0.001085%)|124586 / 301835 (41.276194%)|0.829234|0.718241|0.736765|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.987264|0.993132|0.999997|0.996565|0.999996|0.000004|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.949289|0.707512|0.999991|0.853752|0.999925|0.000075|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.924477|0.587238|0.999989|0.793614|0.999896|0.000104|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.988432|0.990189|0.991953|0.980569|0.990194|0.990191|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.888560|0.810759|0.745486|0.681745|0.819533|0.819499|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.829234|0.718241|0.633453|0.560356|0.736809|0.736765|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990187|0.998733|0.998686|0.998709|0.998709|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.810723|0.995777|0.952852|0.973842|0.973842|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.718192|0.993801|0.944977|0.968774|0.968774|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|299762|3867|2073|1334872948|3867 / 1334876815|2073 / 301835|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|213552|11408|88283|1334865407|11408 / 1334876815|88283 / 301835|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|177249|14480|124586|1334862335|14480 / 1334876815|124586 / 301835|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 1334876815 (0.000000%)|0 / 301835 (0.000000%)|1.000000|1.000000|1.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ANY_CANDIDATE|0.871577|10102 / 1334876815 (0.000757%)|77523 / 301835 (25.683900%)|0.904855|0.836596|0.843258|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ANY_CANDIDATE|0.956905|0.743161|0.999992|0.871577|0.999934|0.000066|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ANY_CANDIDATE|0.904855|0.836596|0.777914|0.719094|0.843288|0.843258|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL CZECH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|301835|0|0|1334876815|0 / 1334876815|0 / 301835|
|2|HUNSPELL CZECH LUCENE FILTER|ANY_CANDIDATE|224312|10102|77523|1334866713|10102 / 1334876815|77523 / 301835|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999998|5850 / 1334876815 (0.000438%)|0 / 301835 (0.000000%)|0.984732|0.990402|0.990446|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.871575|13917 / 1334876815 (0.001043%)|77523 / 301835 (25.683900%)|0.893851|0.830687|0.836477|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.980987|1.000000|0.999996|0.999998|0.999996|0.000004|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.941581|0.743161|0.999990|0.871575|0.999932|0.000068|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.984732|0.990402|0.996139|0.980987|0.990448|0.990446|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.893851|0.830687|0.775861|0.710406|0.836509|0.836477|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|301835|5850|0|1334870965|5850 / 1334876815|0 / 301835|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|224312|13917|77523|1334862898|13917 / 1334876815|77523 / 301835|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|2073|3867|1983|596|1.153340%|4|52319|
|HUNSPELL CZECH LUCENE FILTER|10760|1306|2509|3317|6.418840%|5|55596|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **7 result rows**, **3 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.997139|3863 / 1298544215 (0.000297%)|1709 / 298813 (0.571930%)|0.988580|0.990710|0.990714|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.852770|11239 / 1298544215 (0.000866%)|87986 / 298813 (29.445171%)|0.888009|0.809505|0.818403|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.791794|13950 / 1298544215 (0.001074%)|124426 / 298813 (41.640089%)|0.828709|0.715948|0.735055|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.987165|0.994281|0.999997|0.997139|0.999996|0.000004|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.949389|0.705548|0.999991|0.852770|0.999924|0.000076|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.925931|0.583599|0.999989|0.791794|0.999893|0.000107|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.988580|0.990710|0.992849|0.981591|0.990716|0.990714|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.888009|0.809505|0.743753|0.679973|0.818437|0.818403|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.828709|0.715948|0.630198|0.557569|0.735100|0.735055|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990708|0.998726|0.999030|0.998878|0.998878|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|0.809467|0.995812|0.952394|0.973619|0.973619|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|0.715897|0.993897|0.944297|0.968463|0.968463|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|297104|3863|1709|1298540352|3863 / 1298544215|1709 / 298813|
|2|HUNSPELL CZECH LUCENE FILTER|PRIMARY_OUTPUT|210827|11239|87986|1298532976|11239 / 1298544215|87986 / 298813|
|3|CZECH LUCENE CZECH STEM FILTER|PRIMARY_OUTPUT|174387|13950|124426|1298530265|13950 / 1298544215|124426 / 298813|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 1298544215 (0.000000%)|0 / 298813 (0.000000%)|1.000000|1.000000|1.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ANY_CANDIDATE|0.870432|10028 / 1298544215 (0.000772%)|77431 / 298813 (25.912862%)|0.904004|0.835052|0.841852|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ANY_CANDIDATE|0.956666|0.740871|0.999992|0.870432|0.999933|0.000067|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL CZECH LUCENE FILTER|ANY_CANDIDATE|0.904004|0.835052|0.775874|0.716815|0.841883|0.841852|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL CZECH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|298813|0|0|1298544215|0 / 1298544215|0 / 298813|
|2|HUNSPELL CZECH LUCENE FILTER|ANY_CANDIDATE|221382|10028|77431|1298534187|10028 / 1298544215|77431 / 298813|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999998|5782 / 1298544215 (0.000445%)|0 / 298813 (0.000000%)|0.984756|0.990418|0.990461|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.870430|13601 / 1298544215 (0.001047%)|77431 / 298813 (25.912862%)|0.893574|0.829463|0.835425|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.981017|1.000000|0.999996|0.999998|0.999996|0.000004|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.942119|0.740871|0.999990|0.870430|0.999930|0.000070|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.984756|0.990418|0.996145|0.981017|0.990463|0.990461|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|0.893574|0.829463|0.773936|0.708617|0.835457|0.835425|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|298813|5782|0|1298538433|5782 / 1298544215|0 / 298813|
|2|HUNSPELL CZECH LUCENE FILTER|ALL_CANDIDATES|221382|13601|77431|1298530614|13601 / 1298544215|77431 / 298813|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|1709|3863|1919|540|1.059488%|4|51543|
|HUNSPELL CZECH LUCENE FILTER|10555|1211|2362|3237|6.351044%|5|54804|

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
- Dictionary language: `CS_CZ`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
