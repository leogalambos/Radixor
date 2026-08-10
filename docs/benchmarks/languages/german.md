# German Stemmer Benchmarks

This page reports same-language stemming benchmarks for German. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed tokens | JMH timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| `de-de-default` | `1.0.0` | `DE_DE` | 54,092 | 333,036 | 90,535 | 242,501 | 242,501 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **333,036**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 12,107 | 3.635% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 81,805 | 24.563% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 142,376 | 42.751% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 88,820 | 26.670% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 7,928 | 2.381% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 92.725% | 92.847% | 92.396% | Radixor dictionary-trained patch-command stemmer. |
| Lucene HunspellStemFilter | 47.064% | 29.661% | 93.678% | Benchmark-only German Hunspell dictionary compared via Lucene HunspellStemFilter. |
| CISTEM (German) | 24.675% | 23.724% | 27.222% | Benchmark-only CISTEM implementation. |
| Lucene GermanLightStemFilter | 37.434% | 35.465% | 42.707% | Light suffix stemmer; intentionally narrower than Radixor's lexicon-trained transformation model. |
| Lucene GermanMinimalStemFilter | 27.640% | 24.951% | 34.844% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SnowballFilter | 30.956% | 28.853% | 36.589% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 30.483% | 29.030% | 34.376% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene GermanStemFilter | 21.559% | 19.312% | 27.576% | German Lucene stemming TokenFilter; broader than minimal/light variants. |

## Speed

Speed uses JMH average time, 5 warmup iterations, 10 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `germanRadixor` | 27.697 | 0.583 | 114.2 | 1.000 | Radixor dictionary-trained patch-command stemmer. |
| CISTEM | `germanCistem` | 289.568 | 8.761 | 1194.1 | 10.455 | Benchmark-only CISTEM implementation. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 265.653 | 10.779 | 1095.5 | 9.591 | Benchmark-only German Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene GermanMinimalStemFilter | `germanLuceneGermanMinimalStemFilter` | 22.385 | 0.217 | 92.3 | 0.808 | Minimal German suffix reduction; narrow baseline. |
| Lucene GermanLightStemFilter | `germanLuceneGermanLightStemFilter` | 23.170 | 0.383 | 95.5 | 0.837 | Light German suffix stemmer; narrower than Radixor's lexicon-trained transformation model. |
| Lucene GermanStemFilter | `germanLuceneGermanStemFilter` | 67.453 | 0.967 | 278.2 | 2.435 | Older German stemming TokenFilter with normalization requirements. |
| Lucene SnowballFilter | `luceneSnowballFilter[GERMAN]` | 105.203 | 2.035 | 433.8 | 3.798 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |
| Official Snowball direct | `snowballDirect[GERMAN]` | 91.847 | 2.301 | 378.7 | 3.316 | Official Snowball generated Java stemmer; direct API. |

## Interpretation Notes

- Radixor is a dictionary-trained patch-command stemmer. Its learned transformations can generalize beyond the word forms listed in the training resource.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `DE_DE` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `de-de-default`, loaded from classpath resource `org/egothor/stemmer/models/de-de-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.910445** among 8 deterministic stemmers. The runner-up is `GERMAN CISTEM` at 0.878527, a difference of 0.031918. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.966959** among 8 deterministic stemmers. The runner-up is `GERMAN CISTEM` at 0.914727, a difference of 0.052232. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **12 result rows**, **8 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.910445|0.000002%|17.910967%|
|2|GERMAN CISTEM|0.878527|0.000674%|24.293900%|
|3|SNOWBALL GERMAN DIRECT|0.776012|0.000171%|44.797420%|
|4|SNOWBALL GERMAN LUCENE FILTER|0.769071|0.000371%|46.185528%|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|0.753833|0.000191%|49.233299%|
|6|GERMAN LUCENE GERMAN STEM FILTER|0.720992|0.000443%|55.801084%|
|7|HUNSPELL GERMAN LUCENE FILTER|0.640308|0.000290%|71.938102%|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|0.595748|0.000088%|80.850384%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999400|0.820890|1.000000|0.910445|0.999994|0.000006|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.797231|0.757061|0.999993|0.878527|0.999985|0.000015|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.918571|0.552026|0.999998|0.776012|0.999983|0.000017|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.835220|0.538145|0.999996|0.769071|0.999980|0.000020|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.902792|0.507667|0.999998|0.753833|0.999981|0.000019|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.777304|0.441989|0.999996|0.720992|0.999976|0.000024|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.771720|0.280619|0.999997|0.640308|0.999972|0.000028|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.883845|0.191496|0.999999|0.595748|0.999971|0.000029|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.957746|0.901392|0.851302|0.820486|0.905758|0.905755|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.788860|0.776627|0.764768|0.634824|0.776886|0.776879|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.810886|0.689618|0.599903|0.526272|0.712092|0.712085|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.752175|0.654552|0.579359|0.486494|0.670425|0.670416|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.781189|0.649884|0.556368|0.481355|0.676991|0.676984|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.674901|0.563540|0.483723|0.392311|0.586140|0.586130|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.571639|0.411577|0.321543|0.259110|0.465359|0.465349|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.512941|0.314789|0.227071|0.186795|0.411404|0.411396|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1103976|663|240876|38436733230|663 / 38436733893|240876 / 1344852|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|1018135|258954|326717|38436474939|258954 / 38436733893|326717 / 1344852|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|742393|65811|602459|38436668082|65811 / 38436733893|602459 / 1344852|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|723725|142783|621127|38436591110|142783 / 38436733893|621127 / 1344852|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|682737|73514|662115|38436660379|73514 / 38436733893|662115 / 1344852|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|594410|170297|750442|38436563596|170297 / 38436733893|750442 / 1344852|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|377391|111635|967461|38436622258|111635 / 38436733893|967461 / 1344852|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|257534|33845|1087318|38436700048|33845 / 38436733893|1087318 / 1344852|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000001%|8.261653%|
|HUNSPELL GERMAN LUCENE FILTER|0.000216%|70.811435%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|502 / 38436733893|111107 / 1344852|
|HUNSPELL GERMAN LUCENE FILTER|83073 / 38436733893|952309 / 1344852|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.958692|0.000018%|8.261653%|
|2|HUNSPELL GERMAN LUCENE FILTER|0.645941|0.000354%|70.811435%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.994469|0.917383|1.000000|0.958692|0.999997|0.000003|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|0.742744|0.291886|0.999996|0.645941|0.999972|0.000028|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.978033|0.954372|0.931829|0.912726|0.955149|0.955147|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|0.567444|0.419080|0.332218|0.265086|0.465614|0.465603|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1233745|6862|111107|38436727031|6862 / 38436733893|111107 / 1344852|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|392543|135961|952309|38436597932|135961 / 38436733893|952309 / 1344852|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|129769|161|6199|29035|10.471893%|8|313927|
|HUNSPELL GERMAN LUCENE FILTER|15152|28562|24326|6482|2.337827%|3|283881|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **12 result rows**, **8 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.966959|0.000001%|6.608210%|
|2|GERMAN CISTEM|0.914727|0.000812%|17.053716%|
|3|SNOWBALL GERMAN DIRECT|0.794997|0.000391%|41.000236%|
|4|SNOWBALL GERMAN LUCENE FILTER|0.774716|0.000325%|45.056540%|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|0.768968|0.000130%|46.206331%|
|6|GERMAN LUCENE GERMAN STEM FILTER|0.716147|0.000358%|56.770194%|
|7|HUNSPELL GERMAN LUCENE FILTER|0.659574|0.000556%|68.084626%|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|0.574999|0.000045%|85.000064%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999900|0.933918|1.000000|0.966959|0.999995|0.000005|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.892172|0.829463|0.999992|0.914727|0.999978|0.000022|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.924304|0.589998|0.999996|0.794997|0.999963|0.000037|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.931871|0.549435|0.999997|0.774716|0.999960|0.000040|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.971001|0.537937|0.999999|0.768968|0.999961|0.000039|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.907196|0.432298|0.999996|0.716147|0.999950|0.000050|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.823043|0.319154|0.999994|0.659574|0.999939|0.000061|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.964480|0.149999|1.000000|0.574999|0.999931|0.000069|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.985968|0.965783|0.946408|0.933831|0.966346|0.966343|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.878883|0.859676|0.841289|0.753887|0.860246|0.860236|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.830220|0.720249|0.636004|0.562804|0.738469|0.738454|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.817997|0.691285|0.598564|0.528217|0.715543|0.715527|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.836342|0.692324|0.590620|0.529431|0.722729|0.722714|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.743781|0.585563|0.482850|0.413990|0.626242|0.626223|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.625524|0.459951|0.363685|0.298660|0.512520|0.512499|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.462363|0.259621|0.180482|0.149175|0.380357|0.380343|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|801691|80|56726|10594963454|80 / 10594963534|56726 / 858417|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|712025|86055|146392|10594877479|86055 / 10594963534|146392 / 858417|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|506464|41477|351953|10594922057|41477 / 10594963534|351953 / 858417|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|471644|34482|386773|10594929052|34482 / 10594963534|386773 / 858417|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|461774|13791|396643|10594949743|13791 / 10594963534|396643 / 858417|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|371092|37962|487325|10594925572|37962 / 10594963534|487325 / 858417|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|273967|58904|584450|10594904630|58904 / 10594963534|584450 / 858417|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|128762|4742|729655|10594958792|4742 / 10594963534|729655 / 858417|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|Radixor|0.000000%|0.000000%|
|HUNSPELL GERMAN LUCENE FILTER|0.000383%|66.866802%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|Radixor|0 / 10594963534|0 / 858417|
|HUNSPELL GERMAN LUCENE FILTER|40608 / 10594963534|573996 / 858417|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|0.000014%|0.000000%|
|2|HUNSPELL GERMAN LUCENE FILTER|0.665663|0.000629%|66.866802%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.998267|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|0.810178|0.331332|0.999994|0.665663|0.999940|0.000060|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.998613|0.999133|0.999653|0.998267|0.999133|0.999133|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|0.628511|0.470321|0.375748|0.307464|0.518110|0.518088|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|858417|1490|0|10594962044|1490 / 10594963534|0 / 858417|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|284421|66639|573996|10594896895|66639 / 10594963534|573996 / 858417|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|56726|80|1410|10454|7.181227%|8|157137|
|HUNSPELL GERMAN LUCENE FILTER|10454|18296|7735|4538|3.117315%|3|150205|

### Output Policies and Metric Definitions

Each distinct surface form is one item and may belong to several gold groups. Two forms are gold-related when their membership sets intersect; a relation shared by several groups is counted once. `PRIMARY_OUTPUT` uses one deterministic stem per form. `ANY_CANDIDATE` is an optimistic oracle-assisted pairwise upper bound: a gold-related pair succeeds when candidates intersect, while a gold-negative pair succeeds when a non-colliding selection exists. Candidate choices may differ between pairs, so this is not deterministic runtime behaviour and does not define one confusion matrix. `ALL_CANDIDATES` activates every returned candidate; forms are related when candidate sets intersect.

For `PRIMARY_OUTPUT` and `ALL_CANDIDATES`, `TP = underPossiblePairs - underErrorPairs`, `FN = underErrorPairs`, `FP = overErrorPairs`, and `TN = overPossiblePairs - overErrorPairs`. `ANY_CANDIDATE` publishes only its separate oracle-assisted under/over bounds; confusion-derived metrics are mathematically inapplicable and are not presented in its language-page section. Their machine-readable CSV fields remain empty. Undefined metric denominators in otherwise applicable policies are rendered as `n/a`.

- Under-stemming rate (Paice UI): `FN / (TP + FN)`, the false-negative rate over gold-related pairs.
- Over-stemming rate (Paice OI): `FP / (TN + FP)`, the false-positive rate over gold-negative pairs.
- Pairwise precision: `TP / (TP + FP)`, the fraction of predicted conflations that are gold-standard positive pairs.
- Pairwise recall: `TP / (TP + FN)`, the fraction of gold-standard positive pairs successfully connected.
- Pairwise specificity: `TN / (TN + FP)`, the fraction of gold-negative pairs correctly separated.
- Balanced accuracy: `(recall + specificity) / 2`. It gives equal weight to positive and negative pair classes and is less dominated by the large true-negative class than ordinary accuracy. It does not replace the raw errors or other metrics.
- Pairwise F-beta: `((1 + betaSquared) * TP) / (((1 + betaSquared) * TP) + (betaSquared * FN) + FP)`. F0.5 emphasizes precision and penalizes over-stemming more; F1 weights precision and recall equally; F2 emphasizes recall and penalizes under-stemming more.
- MCC: `(TP * TN - FP * FN) / sqrt((TP + FP) * (TP + FN) * (TN + FP) * (TN + FN))`. It uses all confusion counts and remains useful under class imbalance, except when its denominator is degenerate.
- Jaccard index: `TP / (TP + FP + FN)`.
- Fowlkes–Mallows index: `sqrt(precision * recall)`.
- Pairwise accuracy: `(TP + TN) / (TP + TN + FP + FN)`. It can be dominated by true-negative cross-group pairs.
- Pairwise error rate: `(FP + FN) / (TP + TN + FP + FN)`.

Standard ARI, homogeneity, completeness, V-measure, and NMI are not calculated: their usual contingency-table definitions require an exclusive gold partition, while this gold standard is an overlapping cover.

### Provenance

- Authoritative source: `docs/benchmarks/data/stemming-quality.csv`
- Source SHA-256: `d34f325da320a2e040b54d8d8b5c216d70448f08cfb8659a423e99882aa1afb5`
- Evaluation command: `./gradlew stemmingQuality --no-daemon`
- Dictionary language: `DE_DE`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
