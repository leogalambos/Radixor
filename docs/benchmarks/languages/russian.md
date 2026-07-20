# Russian Stemmer Benchmarks

This page reports same-language stemming benchmarks for Russian. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `RU_RU` | 37,410 | 806,279 | 74,808 | 731,471 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **806,279**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 9,260 | 1.148% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 584,785 | 72.529% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 82,864 | 10.277% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 75,646 | 9.382% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 53,724 | 6.663% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 98.807% | 98.696% | 99.896% | Full Radixor dictionary patch-command stemmer. |
| Lucene RussianLightStemFilter | 9.658% | 8.452% | 21.447% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |
| Lucene SnowballFilter | 9.162% | 8.162% | 18.936% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 9.162% | 8.162% | 18.936% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `russianRadixor` | 89.671 | 3.886 | 122.6 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene RussianLightStemFilter | `russianLuceneRussianLightStemFilter` | 60.522 | 5.310 | 82.7 | 0.675 | Light Russian suffix stemmer. |
| Official Snowball direct | `snowballDirect[RUSSIAN]` | 106.031 | 9.287 | 145.0 | 1.182 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[RUSSIAN]` | 137.512 | 10.801 | 188.0 | 1.534 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `RU_RU` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/ru_ru/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.989827** among 4 deterministic stemmers. The runner-up is `SNOWBALL RUSSIAN LUCENE FILTER` at 0.834876, a difference of 0.154951. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.989852** among 4 deterministic stemmers. The runner-up is `SNOWBALL RUSSIAN DIRECT` at 0.834854, a difference of 0.154998. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.989827|155850 / 295576291016 (0.000053%)|266302 / 13089505 (2.034470%)|0.986313|0.983806|0.983814|
|2|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.834876|3785790 / 295576291016 (0.001281%)|4322616 / 13089505 (33.023525%)|0.692485|0.683786|0.683923|
|3|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.834867|3782908 / 295576291016 (0.001280%)|4322849 / 13089505 (33.025305%)|0.692603|0.683851|0.683989|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.617692|321183 / 295576291016 (0.000109%)|10008438 / 13089505 (76.461547%)|0.577011|0.373649|0.461687|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.987992|0.979655|0.999999|0.989827|0.999999|0.000001|
|2|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.698408|0.669765|0.999987|0.834876|0.999973|0.000027|
|3|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.698563|0.669747|0.999987|0.834867|0.999973|0.000027|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.905597|0.235385|0.999999|0.617692|0.999965|0.000035|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.986313|0.983806|0.981311|0.968128|0.983815|0.983814|
|2|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.692485|0.683786|0.675304|0.519510|0.683936|0.683923|
|3|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.692603|0.683851|0.675318|0.519585|0.684003|0.683989|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.577011|0.373649|0.276278|0.229747|0.461696|0.461687|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.983805|0.997699|0.997274|0.997487|0.997487|
|2|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.683773|0.974131|0.953674|0.963794|0.963794|
|3|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.683838|0.974180|0.953661|0.963811|0.963811|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.373638|0.994311|0.870888|0.928516|0.928516|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|12823203|155850|266302|295576135166|155850 / 295576291016|266302 / 13089505|
|2|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|8766889|3785790|4322616|295572505226|3785790 / 295576291016|4322616 / 13089505|
|3|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|8766656|3782908|4322849|295572508108|3782908 / 295576291016|4322849 / 13089505|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|3081067|321183|10008438|295575969833|321183 / 295576291016|10008438 / 13089505|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 295576291016 (0.000000%)|13 / 13089505 (0.000099%)|1.000000|1.000000|1.000000|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0.999999|1.000000|1.000000|1.000000|0.000000|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|0.999999|0.999999|1.000000|1.000000|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|13089492|0|13|295576291016|0 / 295576291016|13 / 13089505|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999999|434710 / 295576291016 (0.000147%)|13 / 13089505 (0.000099%)|0.974119|0.983665|0.983796|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.967857|0.999999|0.999999|0.999999|0.999999|0.000001|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.974119|0.983665|0.993401|0.967856|0.983797|0.983796|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|13089492|434710|13|295575856306|434710 / 295576291016|13 / 13089505|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|266289|155850|278860|19162|2.492190%|4|788492|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **6 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.989852|155850 / 295000681652 (0.000053%)|265613 / 13087126 (2.029575%)|0.986322|0.983830|0.983838|
|2|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.834854|3782908 / 295000681652 (0.001282%)|4322407 / 13087126 (33.027931%)|0.692561|0.683815|0.683953|
|3|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.834854|3782908 / 295000681652 (0.001282%)|4322407 / 13087126 (33.027931%)|0.692561|0.683815|0.683953|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.617630|318921 / 295000681652 (0.000108%)|10008238 / 13087126 (76.473918%)|0.577038|0.373540|0.461703|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.987991|0.979704|0.999999|0.989852|0.999999|0.000001|
|2|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.698516|0.669721|0.999987|0.834854|0.999973|0.000027|
|3|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.698516|0.669721|0.999987|0.834854|0.999973|0.000027|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.906139|0.235261|0.999999|0.617630|0.999965|0.000035|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.986322|0.983830|0.981350|0.968175|0.983839|0.983838|
|2|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.692561|0.683815|0.675288|0.519544|0.683967|0.683953|
|3|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.692561|0.683815|0.675288|0.519544|0.683967|0.683953|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.577038|0.373540|0.276152|0.229664|0.461713|0.461703|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.983829|0.997697|0.997321|0.997509|0.997509|
|2|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|0.683802|0.974149|0.953634|0.963782|0.963782|
|3|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|0.683802|0.974149|0.953634|0.963782|0.963782|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.373528|0.994350|0.870767|0.928464|0.928464|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|12821513|155850|265613|295000525802|155850 / 295000681652|265613 / 13087126|
|2|SNOWBALL RUSSIAN DIRECT|PRIMARY_OUTPUT|8764719|3782908|4322407|294996898744|3782908 / 295000681652|4322407 / 13087126|
|3|SNOWBALL RUSSIAN LUCENE FILTER|PRIMARY_OUTPUT|8764719|3782908|4322407|294996898744|3782908 / 295000681652|4322407 / 13087126|
|4|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|3078888|318921|10008238|295000362731|318921 / 295000681652|10008238 / 13087126|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 295000681652 (0.000000%)|0 / 13087126 (0.000000%)|1.000000|1.000000|1.000000|

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
|1|Radixor|ANY_CANDIDATE|13087126|0|0|295000681652|0 / 295000681652|0 / 13087126|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999999|434710 / 295000681652 (0.000147%)|0 / 13087126 (0.000000%)|0.974115|0.983663|0.983794|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.967851|1.000000|0.999999|0.999999|0.999999|0.000001|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.974115|0.983663|0.993401|0.967851|0.983794|0.983794|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|13087126|434710|0|295000246942|434710 / 295000681652|0 / 13087126|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|265613|155850|278860|18991|2.472358%|4|787549|

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
- Dictionary language: `RU_RU`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
