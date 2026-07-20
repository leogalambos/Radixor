# Spanish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Spanish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `ES_ES` | 65,059 | 926,393 | 120,121 | 806,272 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **926,393**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 5,367 | 0.579% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 524,682 | 56.637% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 240,872 | 26.001% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 130,089 | 14.043% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 25,383 | 2.740% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 97.459% | 97.544% | 96.891% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 49.074% | 42.656% | 92.154% | Benchmark-only Spanish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene SpanishMinimalStemFilter | 17.284% | 5.347% | 97.403% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SpanishPluralStemFilter | 15.140% | 5.802% | 77.820% | Plural-focused suffix reducer; narrow baseline. |
| Lucene SpanishLightStemFilter | 9.577% | 7.088% | 26.279% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |
| Lucene SnowballFilter | 4.889% | 4.287% | 8.932% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 4.889% | 4.287% | 8.930% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `spanishRadixor` | 78.919 | 7.253 | 97.9 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 2079.041 | 193.548 | 2578.6 | 26.344 | Benchmark-only Spanish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene SpanishMinimalStemFilter | `spanishLuceneSpanishMinimalStemFilter` | 45.596 | 4.639 | 56.6 | 0.578 | Minimal Spanish suffix reducer; narrow baseline. |
| Lucene SpanishLightStemFilter | `spanishLuceneSpanishLightStemFilter` | 42.003 | 1.683 | 52.1 | 0.532 | Light Spanish suffix stemmer. |
| Lucene SpanishPluralStemFilter | `spanishLuceneSpanishPluralStemFilter` | 93.734 | 6.247 | 116.3 | 1.188 | Plural-oriented Spanish suffix reducer. |
| Official Snowball direct | `snowballDirect[SPANISH]` | 171.995 | 11.035 | 213.3 | 2.179 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[SPANISH]` | 211.138 | 17.940 | 261.9 | 2.675 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `ES_ES` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/es_es/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.989295** among 7 deterministic stemmers. The runner-up is `SNOWBALL SPANISH LUCENE FILTER` at 0.652614, a difference of 0.336680. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.989429** among 7 deterministic stemmers. The runner-up is `SNOWBALL SPANISH DIRECT` at 0.652720, a difference of 0.336709. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **11 result rows**, **7 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.989295|288483 / 379567318110 (0.000076%)|898652 / 41973336 (2.141007%)|0.990105|0.985755|0.985780|
|2|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.652614|2230481 / 379567318110 (0.000588%)|29161643 / 41973336 (69.476591%)|0.627151|0.449411|0.509848|
|3|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.652614|2228819 / 379567318110 (0.000587%)|29161649 / 41973336 (69.476605%)|0.627192|0.449424|0.509876|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.615102|536192 / 379567318110 (0.000141%)|32310860 / 41973336 (76.979490%)|0.583708|0.370408|0.466992|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.514823|147956 / 379567318110 (0.000039%)|40729019 / 41973336 (97.035458%)|0.130864|0.057387|0.162762|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.503874|58578 / 379567318110 (0.000015%)|41648091 / 41973336 (99.225115%)|0.037377|0.015357|0.081026|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.501768|47859 / 379567318110 (0.000013%)|41824873 / 41973336 (99.646292%)|0.017361|0.007041|0.051714|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.993026|0.978590|0.999999|0.989295|0.999997|0.000003|
|2|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.851718|0.305234|0.999994|0.652614|0.999917|0.000083|
|3|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.851812|0.305234|0.999994|0.652614|0.999917|0.000083|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.947425|0.230205|0.999999|0.615102|0.999913|0.000087|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.893731|0.029645|1.000000|0.514823|0.999892|0.000108|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.847383|0.007749|1.000000|0.503874|0.999890|0.000110|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.756222|0.003537|1.000000|0.501768|0.999890|0.000110|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990105|0.985755|0.981443|0.971910|0.985781|0.985780|
|2|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.627151|0.449411|0.350170|0.289832|0.509876|0.509848|
|3|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.627192|0.449424|0.350173|0.289843|0.509904|0.509876|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.583708|0.370408|0.271278|0.227301|0.467014|0.466992|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.130864|0.057387|0.036752|0.029541|0.162773|0.162762|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.037377|0.015357|0.009664|0.007738|0.081032|0.081026|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.017361|0.007041|0.004416|0.003533|0.051719|0.051714|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.985753|0.995418|0.993266|0.994341|0.994341|
|2|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.449379|0.981386|0.852461|0.912391|0.912391|
|3|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.449392|0.981406|0.852463|0.912401|0.912401|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.370381|0.993314|0.790558|0.880414|0.880414|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.057381|0.993824|0.756690|0.859195|0.859195|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.015355|0.995442|0.723731|0.838115|0.838115|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.007040|0.995635|0.710610|0.829316|0.829316|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|41074684|288483|898652|379567029627|288483 / 379567318110|898652 / 41973336|
|2|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|12811693|2230481|29161643|379565087629|2230481 / 379567318110|29161643 / 41973336|
|3|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|12811687|2228819|29161649|379565089291|2228819 / 379567318110|29161649 / 41973336|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|9662476|536192|32310860|379566781918|536192 / 379567318110|32310860 / 41973336|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|1244317|147956|40729019|379567170154|147956 / 379567318110|40729019 / 41973336|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|325245|58578|41648091|379567259532|58578 / 379567318110|41648091 / 41973336|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|148463|47859|41824873|379567270251|47859 / 379567318110|41824873 / 41973336|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.999993|2 / 379567318110 (0.000000%)|626 / 41973336 (0.001491%)|0.999997|0.999993|0.999993|
|2|HUNSPELL SPANISH LUCENE FILTER|ANY_CANDIDATE|0.620065|416345 / 379567318110 (0.000110%)|31894218 / 41973336 (75.986855%)|0.600268|0.384195|0.480192|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0.999985|1.000000|0.999993|1.000000|0.000000|
|2|HUNSPELL SPANISH LUCENE FILTER|ANY_CANDIDATE|0.960331|0.240131|0.999999|0.620065|0.999915|0.000085|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.999997|0.999993|0.999988|0.999985|0.999993|0.999993|
|2|HUNSPELL SPANISH LUCENE FILTER|ANY_CANDIDATE|0.600268|0.384195|0.282504|0.237773|0.480214|0.480192|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL SPANISH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|41972710|2|626|379567318108|2 / 379567318110|626 / 41973336|
|2|HUNSPELL SPANISH LUCENE FILTER|ANY_CANDIDATE|10079118|416345|31894218|379566901765|416345 / 379567318110|31894218 / 41973336|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999991|1349800 / 379567318110 (0.000356%)|626 / 41973336 (0.001491%)|0.974915|0.984168|0.984289|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.620065|888077 / 379567318110 (0.000234%)|31894218 / 41973336 (75.986855%)|0.587073|0.380771|0.469749|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.968843|0.999985|0.999996|0.999991|0.999996|0.000004|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.919024|0.240131|0.999998|0.620065|0.999914|0.000086|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.974915|0.984168|0.993598|0.968829|0.984291|0.984289|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.587073|0.380771|0.281759|0.235156|0.469773|0.469749|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|41972710|1349800|626|379565968310|1349800 / 379567318110|626 / 41973336|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|10079118|888077|31894218|379566430033|888077 / 379567318110|31894218 / 41973336|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|416642|119847|351885|17877|2.051686%|5|890999|
|Radixor|898026|288481|1061317|42637|4.893313%|21|916797|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **11 result rows**, **7 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.989429|276044 / 377860669765 (0.000073%)|885033 / 41863370 (2.114099%)|0.990385|0.986031|0.986056|
|2|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.652720|2201196 / 377860669765 (0.000583%)|29076352 / 41863370 (69.455354%)|0.627946|0.449839|0.510450|
|3|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.652720|2201196 / 377860669765 (0.000583%)|29076352 / 41863370 (69.455354%)|0.627946|0.449839|0.510450|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.614999|531181 / 377860669765 (0.000141%)|32234855 / 41863370 (77.000144%)|0.583531|0.370163|0.466854|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.514832|146613 / 377860669765 (0.000039%)|40621522 / 41863370 (97.033569%)|0.130949|0.057424|0.162875|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.503877|57716 / 377860669765 (0.000015%)|41538714 / 41863370 (99.224487%)|0.037409|0.015370|0.081139|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.501770|47148 / 377860669765 (0.000012%)|41715144 / 41863370 (99.645929%)|0.017379|0.007049|0.051824|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.993309|0.978859|0.999999|0.989429|0.999997|0.000003|
|2|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.853138|0.305446|0.999994|0.652720|0.999917|0.000083|
|3|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.853138|0.305446|0.999994|0.652720|0.999917|0.000083|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.947717|0.229999|0.999999|0.614999|0.999913|0.000087|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.894406|0.029664|1.000000|0.514832|0.999892|0.000108|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.849058|0.007755|1.000000|0.503877|0.999890|0.000110|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.758678|0.003541|1.000000|0.501770|0.999889|0.000111|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.990385|0.986031|0.981715|0.972447|0.986057|0.986056|
|2|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.627946|0.449839|0.350441|0.290188|0.510478|0.510450|
|3|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.627946|0.449839|0.350441|0.290188|0.510478|0.510450|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.583531|0.370163|0.271053|0.227117|0.466876|0.466854|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.130949|0.057424|0.036775|0.029561|0.162886|0.162875|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.037409|0.015370|0.009672|0.007744|0.081145|0.081139|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.017379|0.007049|0.004421|0.003537|0.051829|0.051824|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.986029|0.995464|0.993323|0.994392|0.994392|
|2|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.449806|0.981469|0.852556|0.912482|0.912482|
|3|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.449806|0.981469|0.852556|0.912482|0.912482|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.370136|0.993362|0.790500|0.880396|0.880396|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.057417|0.993866|0.756725|0.859234|0.859234|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.015368|0.995484|0.723753|0.838145|0.838145|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.007048|0.995676|0.710626|0.829341|0.829341|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|40978337|276044|885033|377860393721|276044 / 377860669765|885033 / 41863370|
|2|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|12787018|2201196|29076352|377858468569|2201196 / 377860669765|29076352 / 41863370|
|3|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|12787018|2201196|29076352|377858468569|2201196 / 377860669765|29076352 / 41863370|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|9628515|531181|32234855|377860138584|531181 / 377860669765|32234855 / 41863370|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|1241848|146613|40621522|377860523152|146613 / 377860669765|40621522 / 41863370|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|324656|57716|41538714|377860612049|57716 / 377860669765|41538714 / 41863370|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|148226|47148|41715144|377860622617|47148 / 377860669765|41715144 / 41863370|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 377860669765 (0.000000%)|0 / 41863370 (0.000000%)|1.000000|1.000000|1.000000|
|2|HUNSPELL SPANISH LUCENE FILTER|ANY_CANDIDATE|0.619928|412198 / 377860669765 (0.000109%)|31822108 / 41863370 (76.014205%)|0.600000|0.383864|0.479978|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL SPANISH LUCENE FILTER|ANY_CANDIDATE|0.960568|0.239858|0.999999|0.619928|0.999915|0.000085|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL SPANISH LUCENE FILTER|ANY_CANDIDATE|0.600000|0.383864|0.282205|0.237519|0.480000|0.479978|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL SPANISH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|41863370|0|0|377860669765|0 / 377860669765|0 / 41863370|
|2|HUNSPELL SPANISH LUCENE FILTER|ANY_CANDIDATE|10041262|412198|31822108|377860257567|412198 / 377860669765|31822108 / 41863370|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999998|1255381 / 377860669765 (0.000332%)|0 / 41863370 (0.000000%)|0.976572|0.985228|0.985334|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.619928|878949 / 377860669765 (0.000233%)|31822108 / 41863370 (76.014205%)|0.586905|0.380469|0.469606|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.970885|1.000000|0.999997|0.999998|0.999997|0.000003|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.919512|0.239858|0.999998|0.619928|0.999913|0.000087|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.976572|0.985228|0.994038|0.970885|0.985335|0.985334|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.586905|0.380469|0.281467|0.234926|0.469630|0.469606|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|41863370|1255381|0|377859414384|1255381 / 377860669765|0 / 41863370|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|10041262|878949|31822108|377859790816|878949 / 377860669765|31822108 / 41863370|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|412747|118983|347768|17807|2.048262%|5|888962|
|Radixor|885033|276044|979337|42403|4.877434%|21|914127|

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
- Dictionary language: `ES_ES`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
