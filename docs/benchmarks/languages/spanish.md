# Spanish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Spanish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Model ID | Model version | Language | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| `es-es-default` | `1.0.0` | `ES_ES` | 65,059 | 926,393 | 120,121 | 806,272 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete default-model dictionary. The total number of preferred patch commands analyzed for this language is **926,393**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 8,534 | 0.921% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 522,685 | 56.422% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 243,410 | 26.275% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 124,386 | 13.427% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 27,378 | 2.955% |

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

Speed uses JMH average time, 5 warmup iterations, 10 measurement iterations, 3 independent forks, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `spanishRadixor` | 81.605 | 1.347 | 101.2 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 2033.430 | 14.863 | 2522.0 | 24.918 | Benchmark-only Spanish Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene SpanishMinimalStemFilter | `spanishLuceneSpanishMinimalStemFilter` | 42.144 | 1.556 | 52.3 | 0.516 | Minimal Spanish suffix reducer; narrow baseline. |
| Lucene SpanishLightStemFilter | `spanishLuceneSpanishLightStemFilter` | 44.479 | 1.291 | 55.2 | 0.545 | Light Spanish suffix stemmer. |
| Lucene SpanishPluralStemFilter | `spanishLuceneSpanishPluralStemFilter` | 96.537 | 3.418 | 119.7 | 1.183 | Plural-oriented Spanish suffix reducer. |
| Official Snowball direct | `snowballDirect[SPANISH]` | 172.151 | 7.261 | 213.5 | 2.110 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[SPANISH]` | 201.697 | 9.363 | 250.2 | 2.472 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |




## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `ES_ES` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The default model is `es-es-default`, loaded from classpath resource `org/egothor/stemmer/models/es-es-default/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.989448** among 7 deterministic stemmers. The runner-up is `SNOWBALL SPANISH LUCENE FILTER` at 0.652438, a difference of 0.337010. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.989580** among 7 deterministic stemmers. The runner-up is `SNOWBALL SPANISH DIRECT` at 0.652542, a difference of 0.337038. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **11 result rows**, **7 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.989448|0.000000%|2.110334%|
|2|SNOWBALL SPANISH LUCENE FILTER|0.652438|0.000414%|69.511918%|
|3|SNOWBALL SPANISH DIRECT|0.652438|0.000413%|69.511932%|
|4|HUNSPELL SPANISH LUCENE FILTER|0.615028|0.000068%|76.994273%|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|0.514565|0.000009%|97.087060%|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|0.503764|0.000002%|99.247265%|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|0.501678|0.000001%|99.664470%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1.000000|0.978897|1.000000|0.989448|0.999998|0.000002|
|2|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.895438|0.304881|0.999996|0.652438|0.999915|0.000085|
|3|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.895510|0.304881|0.999996|0.652438|0.999915|0.000085|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.975281|0.230057|0.999999|0.615028|0.999910|0.000090|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.974423|0.029129|1.000000|0.514565|0.999887|0.000113|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.979154|0.007527|1.000000|0.503764|0.999885|0.000115|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.970596|0.003355|1.000000|0.501678|0.999884|0.000116|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.995707|0.989336|0.983046|0.978897|0.989392|0.989391|
|2|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.645406|0.454882|0.351206|0.294400|0.522496|0.522469|
|3|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.645436|0.454891|0.351208|0.294407|0.522517|0.522490|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.591847|0.372295|0.271557|0.228724|0.473678|0.473655|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.130091|0.056568|0.036142|0.029107|0.168477|0.168467|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.036514|0.014940|0.009391|0.007526|0.085851|0.085846|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.016548|0.006687|0.004191|0.003355|0.057067|0.057063|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|41053986|0|885054|360919543590|0 / 360919543590|885054 / 41939040|
|2|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|12786409|1493087|29152631|360918050503|1493087 / 360919543590|29152631 / 41939040|
|3|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|12786403|1491944|29152637|360918051646|1491944 / 360919543590|29152637 / 41939040|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|9648381|244539|32290659|360919299051|244539 / 360919543590|32290659 / 41939040|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|1221659|32066|40717381|360919511524|32066 / 360919543590|40717381 / 41939040|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|315690|6721|41623350|360919536869|6721 / 360919543590|41623350 / 41939040|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|140718|4263|41798322|360919539327|4263 / 360919543590|41798322 / 41939040|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|0.000062%|76.009935%|
|Radixor|0.000000%|0.001493%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|223500 / 360919543590|31877837 / 41939040|
|Radixor|0 / 360919543590|626 / 41939040|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.999993|&lt;0.000001%|0.001493%|
|2|HUNSPELL SPANISH LUCENE FILTER|0.619950|0.000073%|76.009935%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999959|0.999985|1.000000|0.999993|1.000000|0.000000|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.974467|0.239901|0.999999|0.619950|0.999911|0.000089|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999964|0.999972|0.999980|0.999944|0.999972|0.999972|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.604361|0.385016|0.282490|0.238402|0.483503|0.483480|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|41938414|1737|626|360919541853|1737 / 360919543590|626 / 41939040|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|10061203|263629|31877837|360919279961|263629 / 360919543590|31877837 / 41939040|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|412822|21039|19090|11309|1.331001%|5|861853|
|Radixor|884428|0|1737|20967|2.467690%|21|871404|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **11 result rows**, **7 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|0.989580|&lt;0.000001%|2.084022%|
|2|SNOWBALL SPANISH DIRECT|0.652542|0.000410%|69.491126%|
|3|SNOWBALL SPANISH LUCENE FILTER|0.652542|0.000410%|69.491126%|
|4|HUNSPELL SPANISH LUCENE FILTER|0.614924|0.000068%|77.015229%|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|0.514575|0.000009%|97.085003%|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|0.503767|0.000002%|99.246590%|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|0.501679|0.000001%|99.664108%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.999999|0.979160|1.000000|0.989580|0.999998|0.000002|
|2|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.896551|0.305089|0.999996|0.652542|0.999915|0.000085|
|3|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.896551|0.305089|0.999996|0.652542|0.999915|0.000085|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.975224|0.229848|0.999999|0.614924|0.999910|0.000090|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.974539|0.029150|1.000000|0.514575|0.999887|0.000113|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.979521|0.007534|1.000000|0.503767|0.999884|0.000116|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.971230|0.003359|1.000000|0.501679|0.999884|0.000116|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.995761|0.989470|0.983258|0.979159|0.989525|0.989523|
|2|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|0.646055|0.455257|0.351461|0.294714|0.522999|0.522972|
|3|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.646055|0.455257|0.351461|0.294714|0.522999|0.522972|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|0.591553|0.372016|0.271323|0.228513|0.473448|0.473426|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.130175|0.056607|0.036167|0.029128|0.168546|0.168536|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|0.036546|0.014953|0.009400|0.007533|0.085906|0.085901|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.016565|0.006695|0.004195|0.003359|0.057116|0.057113|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|40958710|34|871756|359407144881|34 / 359407144915|871756 / 41830466|
|2|SNOWBALL SPANISH DIRECT|PRIMARY_OUTPUT|12762004|1472547|29068462|359405672368|1472547 / 359407144915|29068462 / 41830466|
|3|SNOWBALL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|12762004|1472547|29068462|359405672368|1472547 / 359407144915|29068462 / 41830466|
|4|HUNSPELL SPANISH LUCENE FILTER|PRIMARY_OUTPUT|9614637|244260|32215829|359406900655|244260 / 359407144915|32215829 / 41830466|
|5|SPANISH LUCENE SPANISH LIGHT STEM FILTER|PRIMARY_OUTPUT|1219357|31857|40611109|359407113058|31857 / 359407144915|40611109 / 41830466|
|6|SPANISH LUCENE SPANISH PLURAL STEM FILTER|PRIMARY_OUTPUT|315155|6589|41515311|359407138326|6589 / 359407144915|41515311 / 41830466|
|7|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|140505|4162|41689961|359407140753|4162 / 359407144915|41689961 / 41830466|

</details>

#### `ANY_CANDIDATE` oracle bounds

These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.

<div class="quality-summary quality-summary--oracle" markdown="1">

| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |
|---|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|0.000062%|76.037484%|
|Radixor|0.000000%|0.000000%|

</div>

<details class="quality-details" markdown="1"><summary>Oracle-bound pair counts</summary>

| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |
|---|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|223274 / 359407144915|31806834 / 41830466|
|Radixor|0 / 359407144915|0 / 41830466|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-summary" markdown="1">

| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |
|---:|---|---:|---:|---:|
|1|Radixor|1.000000|&lt;0.000001%|0.000000%|
|2|HUNSPELL SPANISH LUCENE FILTER|0.619812|0.000073%|76.037484%|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999987|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.974405|0.239625|0.999999|0.619812|0.999911|0.000089|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999989|0.999993|0.999997|0.999987|0.999993|0.999993|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|0.603992|0.384656|0.282183|0.238126|0.483210|0.483187|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|41830466|560|0|359407144355|560 / 359407144915|0 / 41830466|
|2|HUNSPELL SPANISH LUCENE FILTER|ALL_CANDIDATES|10023632|263289|31806834|359406881626|263289 / 359407144915|31806834 / 41830466|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL SPANISH LUCENE FILTER|408995|20986|19029|11287|1.331204%|5|860048|
|Radixor|871756|34|526|20911|2.466272%|21|869542|

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
- Source SHA-256: `edf16b07be8a535943ddf37caeb8807755c95e9e1fb13244145f28be74b491d8`
- Evaluation command: `./gradlew stemmingQuality --no-daemon`
- Dictionary language: `ES_ES`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Model ID, version, and SHA-256: recorded in every CSV row
- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)

<!-- STEMMING-QUALITY:END -->
