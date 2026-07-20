# Polish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Polish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `PL_PL` | 9,990 | 132,308 | 19,957 | 112,351 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **132,308**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 1,719 | 1.299% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 53,303 | 40.287% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 37,051 | 28.004% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 20,415 | 15.430% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 19,820 | 14.980% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 98.837% | 98.744% | 99.359% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 89.545% | 88.272% | 96.713% | Benchmark-only Polish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene MorfologikFilter | 87.729% | 86.606% | 94.047% | Dictionary-based path; Morfologik can emit multiple terms. |
| Lucene StempelFilter | 70.009% | 69.262% | 74.220% | Lucene TokenFilter integration path for table-driven Polish Stempel. |
| Lucene StempelStemmer direct | 70.009% | 69.262% | 74.220% | Direct table-driven Polish Stempel stemmer API. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `polishRadixor` | 9.049 | 0.485 | 80.5 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 483.316 | 11.455 | 4301.8 | 53.408 | Benchmark-only Polish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene StempelStemmer direct | `polishLuceneStempelStemmerDirect` | 41.932 | 1.916 | 373.2 | 4.634 | Direct table-driven Polish Stempel stemmer API. |
| Lucene StempelFilter | `polishLuceneStempelFilter` | 45.277 | 13.693 | 403.0 | 5.003 | Lucene TokenFilter integration path for table-driven Polish Stempel. |
| Lucene MorfologikFilter | `polishLuceneMorfologikFilter` | 135.763 | 31.634 | 1208.4 | 15.002 | Dictionary-based Morfologik TokenFilter; may emit multiple terms. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `PL_PL` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/pl_pl/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.990388** among 5 deterministic stemmers. The runner-up is `POLISH LUCENE MORFOLOGIK FILTER` at 0.948154, a difference of 0.042234. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.990579** among 5 deterministic stemmers. The runner-up is `POLISH LUCENE MORFOLOGIK FILTER` at 0.948177, a difference of 0.042402. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **11 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990388|13669 / 7482478003 (0.000183%)|21547 / 1120967 (1.922180%)|0.986324|0.984237|0.984241|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.948154|99228 / 7482478003 (0.001326%)|116220 / 1120967 (10.367834%)|0.907324|0.903167|0.903179|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.933222|52652 / 7482478003 (0.000704%)|149705 / 1120967 (13.354987%)|0.930930|0.905656|0.906571|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.855748|66669 / 7482478003 (0.000891%)|323394 / 1120967 (28.849556%)|0.871106|0.803515|0.810296|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.855748|66669 / 7482478003 (0.000891%)|323394 / 1120967 (28.849556%)|0.871106|0.803515|0.810296|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.987720|0.980778|0.999998|0.990388|0.999995|0.000005|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.910118|0.896322|0.999987|0.948154|0.999971|0.000029|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.948578|0.866450|0.999993|0.933222|0.999973|0.000027|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.922858|0.711504|0.999991|0.855748|0.999948|0.000052|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.922858|0.711504|0.999991|0.855748|0.999948|0.000052|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.986324|0.984237|0.982159|0.968963|0.984243|0.984241|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.907324|0.903167|0.899047|0.823432|0.903193|0.903179|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.930930|0.905656|0.881718|0.827579|0.906584|0.906571|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.871106|0.803515|0.745659|0.671564|0.810320|0.810296|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.871106|0.803515|0.745659|0.671564|0.810320|0.810296|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.984234|0.996967|0.996469|0.996718|0.996718|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.903153|0.990022|0.977054|0.983495|0.983495|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.905642|0.994546|0.970520|0.982386|0.982386|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.803490|0.991767|0.931069|0.960460|0.960460|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.803490|0.991767|0.931069|0.960460|0.960460|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1099420|13669|21547|7482464334|13669 / 7482478003|21547 / 1120967|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|1004747|99228|116220|7482378775|99228 / 7482478003|116220 / 1120967|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|971262|52652|149705|7482425351|52652 / 7482478003|149705 / 1120967|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|797573|66669|323394|7482411334|66669 / 7482478003|323394 / 1120967|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|797573|66669|323394|7482411334|66669 / 7482478003|323394 / 1120967|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 7482478003 (0.000000%)|0 / 1120967 (0.000000%)|1.000000|1.000000|1.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.987570|85532 / 7482478003 (0.001143%)|27855 / 1120967 (2.484908%)|0.936598|0.950693|0.950985|
|3|HUNSPELL POLISH LUCENE FILTER|ANY_CANDIDATE|0.963982|42213 / 7482478003 (0.000564%)|80743 / 1120967 (7.202977%)|0.954209|0.944197|0.944333|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.927432|0.975151|0.999989|0.987570|0.999985|0.000015|
|3|HUNSPELL POLISH LUCENE FILTER|ANY_CANDIDATE|0.961002|0.927970|0.999994|0.963982|0.999984|0.000016|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.936598|0.950693|0.965218|0.906020|0.950992|0.950985|
|3|HUNSPELL POLISH LUCENE FILTER|ANY_CANDIDATE|0.954209|0.944197|0.934394|0.894293|0.944342|0.944333|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|POLISH LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|3|HUNSPELL POLISH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1120967|0|0|7482478003|0 / 7482478003|0 / 1120967|
|2|POLISH LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|1093112|85532|27855|7482392471|85532 / 7482478003|27855 / 1120967|
|3|HUNSPELL POLISH LUCENE FILTER|ANY_CANDIDATE|1040224|42213|80743|7482435790|42213 / 7482478003|80743 / 1120967|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999997|38073 / 7482478003 (0.000509%)|0 / 1120967 (0.000000%)|0.973547|0.983301|0.983436|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.987566|143096 / 7482478003 (0.001912%)|27855 / 1120967 (2.484908%)|0.901045|0.927476|0.928576|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.963980|82745 / 7482478003 (0.001106%)|80743 / 1120967 (7.202977%)|0.926646|0.927142|0.927132|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.967151|1.000000|0.999995|0.999997|0.999995|0.000005|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.884246|0.975151|0.999981|0.987566|0.999977|0.000023|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.926316|0.927970|0.999989|0.963980|0.999978|0.000022|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.973547|0.983301|0.993253|0.967151|0.983438|0.983436|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.901045|0.927476|0.955505|0.864761|0.928587|0.928576|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.926646|0.927142|0.927639|0.864180|0.927143|0.927132|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1120967|38073|0|7482439930|38073 / 7482478003|0 / 1120967|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|1093112|143096|27855|7482334907|143096 / 7482478003|27855 / 1120967|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|1040224|82745|80743|7482395258|82745 / 7482478003|80743 / 1120967|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|68962|10439|30093|11447|9.356634%|6|135231|
|POLISH LUCENE MORFOLOGIK FILTER|88365|13696|43868|12873|10.522229%|5|136636|
|Radixor|21547|13669|24404|2866|2.342632%|4|125778|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **11 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990579|13669 / 7310252699 (0.000187%)|21000 / 1114651 (1.883998%)|0.986350|0.984397|0.984400|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.948177|99224 / 7310252699 (0.001357%)|115513 / 1114651 (10.363154%)|0.906972|0.902966|0.902976|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.933309|51950 / 7310252699 (0.000711%)|148667 / 1114651 (13.337538%)|0.931269|0.905928|0.906847|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.856382|66274 / 7310252699 (0.000907%)|320158 / 1114651 (28.722712%)|0.871591|0.804380|0.811082|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.856382|66274 / 7310252699 (0.000907%)|320158 / 1114651 (28.722712%)|0.871591|0.804380|0.811082|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.987656|0.981160|0.999998|0.990579|0.999995|0.000005|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.909662|0.896368|0.999986|0.948177|0.999971|0.000029|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.948965|0.866625|0.999993|0.933309|0.999973|0.000027|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.923006|0.712773|0.999991|0.856382|0.999947|0.000053|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.923006|0.712773|0.999991|0.856382|0.999947|0.000053|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.986350|0.984397|0.982452|0.969274|0.984403|0.984400|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.906972|0.902966|0.898996|0.823098|0.902991|0.902976|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.931269|0.905928|0.881929|0.828033|0.906861|0.906847|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.871591|0.804380|0.746792|0.672772|0.811106|0.811082|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.871591|0.804380|0.746792|0.672772|0.811106|0.811082|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.984395|0.996926|0.996647|0.996786|0.996786|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|0.902952|0.989889|0.977012|0.983408|0.983408|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|0.905914|0.994584|0.970514|0.982402|0.982402|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|0.804354|0.991711|0.931318|0.960566|0.960566|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|0.804354|0.991711|0.931318|0.960566|0.960566|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1093651|13669|21000|7310239030|13669 / 7310252699|21000 / 1114651|
|2|POLISH LUCENE MORFOLOGIK FILTER|PRIMARY_OUTPUT|999138|99224|115513|7310153475|99224 / 7310252699|115513 / 1114651|
|3|HUNSPELL POLISH LUCENE FILTER|PRIMARY_OUTPUT|965984|51950|148667|7310200749|51950 / 7310252699|148667 / 1114651|
|4|POLISH LUCENE STEMPEL DIRECT|PRIMARY_OUTPUT|794493|66274|320158|7310186425|66274 / 7310252699|320158 / 1114651|
|5|POLISH LUCENE STEMPEL FILTER|PRIMARY_OUTPUT|794493|66274|320158|7310186425|66274 / 7310252699|320158 / 1114651|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 7310252699 (0.000000%)|0 / 1114651 (0.000000%)|1.000000|1.000000|1.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.987661|85532 / 7310252699 (0.001170%)|27494 / 1114651 (2.466602%)|0.936331|0.950586|0.950885|
|3|HUNSPELL POLISH LUCENE FILTER|ANY_CANDIDATE|0.963946|41671 / 7310252699 (0.000570%)|80368 / 1114651 (7.210149%)|0.954406|0.944290|0.944429|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.927063|0.975334|0.999988|0.987661|0.999985|0.000015|
|3|HUNSPELL POLISH LUCENE FILTER|ANY_CANDIDATE|0.961271|0.927899|0.999994|0.963946|0.999983|0.000017|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|POLISH LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|0.936331|0.950586|0.965282|0.905826|0.950892|0.950885|
|3|HUNSPELL POLISH LUCENE FILTER|ANY_CANDIDATE|0.954406|0.944290|0.934386|0.894459|0.944437|0.944429|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|POLISH LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|3|HUNSPELL POLISH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1114651|0|0|7310252699|0 / 7310252699|0 / 1114651|
|2|POLISH LUCENE MORFOLOGIK FILTER|ANY_CANDIDATE|1087157|85532|27494|7310167167|85532 / 7310252699|27494 / 1114651|
|3|HUNSPELL POLISH LUCENE FILTER|ANY_CANDIDATE|1034283|41671|80368|7310211028|41671 / 7310252699|80368 / 1114651|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999997|38073 / 7310252699 (0.000521%)|0 / 1114651 (0.000000%)|0.973401|0.983208|0.983344|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.987657|143085 / 7310252699 (0.001957%)|27494 / 1114651 (2.466602%)|0.900618|0.927255|0.928372|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.963944|81865 / 7310252699 (0.001120%)|80368 / 1114651 (7.210149%)|0.926903|0.927276|0.927265|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.966971|1.000000|0.999995|0.999997|0.999995|0.000005|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.883694|0.975334|0.999980|0.987657|0.999977|0.000023|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.926654|0.927899|0.999989|0.963944|0.999978|0.000022|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.973401|0.983208|0.993215|0.966971|0.983347|0.983344|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|0.900618|0.927255|0.955516|0.864376|0.928384|0.928372|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|0.926903|0.927276|0.927649|0.864412|0.927276|0.927265|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1114651|38073|0|7310214626|38073 / 7310252699|0 / 1114651|
|2|POLISH LUCENE MORFOLOGIK FILTER|ALL_CANDIDATES|1087157|143085|27494|7310109614|143085 / 7310252699|27494 / 1114651|
|3|HUNSPELL POLISH LUCENE FILTER|ALL_CANDIDATES|1034283|81865|80368|7310170834|81865 / 7310252699|80368 / 1114651|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL POLISH LUCENE FILTER|68299|10279|29915|11265|9.315692%|6|133595|
|POLISH LUCENE MORFOLOGIK FILTER|88019|13692|43861|12763|10.554476%|5|135105|
|Radixor|21000|13669|24404|2780|2.298946%|4|124274|

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
- Dictionary language: `PL_PL`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
