# French Stemmer Benchmarks

This page reports same-language stemming benchmarks for French. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `FR_FR` | 59,240 | 474,110 | 108,141 | 365,969 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **474,110**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 5,370 | 1.133% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 185,263 | 39.076% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 153,886 | 32.458% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 116,519 | 24.576% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 13,072 | 2.757% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 94.831% | 94.859% | 94.734% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 68.923% | 63.617% | 86.876% | Benchmark-only French Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene FrenchMinimalStemFilter | 11.472% | 6.236% | 29.192% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SnowballFilter | 8.551% | 5.183% | 19.952% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 8.462% | 5.067% | 19.952% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene FrenchLightStemFilter | 6.377% | 3.965% | 14.540% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `frenchRadixor` | 47.033 | 4.146 | 128.5 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 1664.935 | 65.928 | 4549.4 | 35.399 | Benchmark-only French Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene FrenchMinimalStemFilter | `frenchLuceneFrenchMinimalStemFilter` | 19.234 | 2.098 | 52.6 | 0.409 | Minimal French suffix reducer; narrow baseline. |
| Lucene FrenchLightStemFilter | `frenchLuceneFrenchLightStemFilter` | 30.560 | 3.680 | 83.5 | 0.650 | Light French suffix stemmer. |
| Official Snowball direct | `snowballDirect[FRENCH]` | 111.057 | 8.172 | 303.5 | 2.361 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[FRENCH]` | 123.648 | 3.500 | 337.9 | 2.629 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `FR_FR` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/fr_fr/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.956992** among 6 deterministic stemmers. The runner-up is `SNOWBALL FRENCH DIRECT` at 0.845262, a difference of 0.111731. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.957224** among 6 deterministic stemmers. The runner-up is `SNOWBALL FRENCH DIRECT` at 0.845414, a difference of 0.111810. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **10 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.956992|318767 / 90396104830 (0.000353%)|469160 / 5454615 (8.601157%)|0.934603|0.926765|0.926851|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.845262|1654723 / 90396104830 (0.001831%)|1687975 / 5454615 (30.945814%)|0.693926|0.692653|0.692638|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.844999|1661388 / 90396104830 (0.001838%)|1690838 / 5454615 (30.998301%)|0.693010|0.691885|0.691869|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.813742|776728 / 90396104830 (0.000859%)|2031881 / 5454615 (37.250677%)|0.769069|0.709075|0.715131|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.518587|276403 / 90396104830 (0.000306%)|5251833 / 5454615 (96.282377%)|0.137547|0.068348|0.125415|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.516830|160438 / 90396104830 (0.000177%)|5271003 / 5454615 (96.633823%)|0.134400|0.063329|0.134021|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.939903|0.913988|0.999996|0.956992|0.999991|0.000009|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.694777|0.690542|0.999982|0.845262|0.999963|0.000037|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.693763|0.690017|0.999982|0.844999|0.999963|0.000037|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.815041|0.627493|0.999991|0.813742|0.999969|0.000031|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.423181|0.037176|0.999997|0.518587|0.999939|0.000061|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.533678|0.033662|0.999998|0.516830|0.999940|0.000060|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.934603|0.926765|0.919056|0.863524|0.926855|0.926851|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.693926|0.692653|0.691385|0.529816|0.692656|0.692638|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.693010|0.691885|0.690763|0.528917|0.691887|0.691869|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.769069|0.709075|0.657765|0.549277|0.715145|0.715131|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.137547|0.068348|0.045472|0.035383|0.125428|0.125415|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.134400|0.063329|0.041424|0.032700|0.134032|0.134021|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.926760|0.988772|0.985214|0.986990|0.986990|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.692635|0.959459|0.944948|0.952148|0.952148|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.691866|0.958698|0.944715|0.951655|0.951655|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.709060|0.978337|0.913706|0.944918|0.944918|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.068339|0.974110|0.812376|0.885922|0.885922|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.063322|0.984019|0.810979|0.889158|0.889158|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|4985455|318767|469160|90395786063|318767 / 90396104830|469160 / 5454615|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|3766640|1654723|1687975|90394450107|1654723 / 90396104830|1687975 / 5454615|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|3763777|1661388|1690838|90394443442|1661388 / 90396104830|1690838 / 5454615|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|3422734|776728|2031881|90395328102|776728 / 90396104830|2031881 / 5454615|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|202782|276403|5251833|90395828427|276403 / 90396104830|5251833 / 5454615|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|183612|160438|5271003|90395944392|160438 / 90396104830|5271003 / 5454615|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.999979|12 / 90396104830 (0.000000%)|232 / 5454615 (0.004253%)|0.999990|0.999978|0.999978|
|2|HUNSPELL FRENCH LUCENE FILTER|ANY_CANDIDATE|0.830964|745831 / 90396104830 (0.000825%)|1844003 / 5454615 (33.806291%)|0.789019|0.736029|0.740670|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.999998|0.999957|1.000000|0.999979|1.000000|0.000000|
|2|HUNSPELL FRENCH LUCENE FILTER|ANY_CANDIDATE|0.828798|0.661937|0.999992|0.830964|0.999971|0.000029|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.999990|0.999978|0.999966|0.999955|0.999978|0.999978|
|2|HUNSPELL FRENCH LUCENE FILTER|ANY_CANDIDATE|0.789019|0.736029|0.689709|0.582315|0.740684|0.740670|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL FRENCH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|5454383|12|232|90396104818|12 / 90396104830|232 / 5454615|
|2|HUNSPELL FRENCH LUCENE FILTER|ANY_CANDIDATE|3610612|745831|1844003|90395358999|745831 / 90396104830|1844003 / 5454615|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999973|1056255 / 90396104830 (0.001168%)|232 / 5454615 (0.004253%)|0.865853|0.911704|0.915270|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|0.830963|1043199 / 90396104830 (0.001154%)|1844003 / 5454615 (33.806291%)|0.750028|0.714377|0.716613|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.837765|0.999957|0.999988|0.999973|0.999988|0.000012|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|0.775840|0.661937|0.999988|0.830963|0.999968|0.000032|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.865853|0.911704|0.962682|0.837735|0.915275|0.915270|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|0.750028|0.714377|0.681961|0.555666|0.716629|0.716613|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|5454383|1056255|232|90395048575|1056255 / 90396104830|232 / 5454615|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|3610612|1043199|1844003|90395061631|1043199 / 90396104830|1844003 / 5454615|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|468928|318755|737488|43040|10.122057%|56|477024|
|HUNSPELL FRENCH LUCENE FILTER|187878|30897|266471|13511|3.177489%|4|439015|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **10 result rows**, **6 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.957224|315266 / 88712126506 (0.000355%)|465436 / 5440559 (8.554930%)|0.935099|0.927248|0.927334|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.845414|1646111 / 88712126506 (0.001856%)|1681970 / 5440559 (30.915389%)|0.694508|0.693130|0.693115|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.845163|1641925 / 88712126506 (0.001851%)|1684703 / 5440559 (30.965623%)|0.694714|0.693068|0.693055|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.813617|763305 / 88712126506 (0.000860%)|2028011 / 5440559 (37.275784%)|0.770537|0.709734|0.715938|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.518442|262689 / 88712126506 (0.000296%)|5239869 / 5440559 (96.311225%)|0.137571|0.067985|0.126383|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.516697|147476 / 88712126506 (0.000166%)|5258873 / 5440559 (96.660527%)|0.134439|0.062979|0.135757|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.940408|0.914451|0.999996|0.957224|0.999991|0.000009|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.695430|0.690846|0.999981|0.845414|0.999962|0.000038|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.695815|0.690344|0.999981|0.845163|0.999963|0.000037|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.817210|0.627242|0.999991|0.813617|0.999969|0.000031|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.433101|0.036888|0.999997|0.518442|0.999938|0.000062|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.551965|0.033395|0.999998|0.516697|0.999939|0.000061|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.935099|0.927248|0.919527|0.864363|0.927338|0.927334|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.694508|0.693130|0.691758|0.530374|0.693134|0.693115|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.694714|0.693068|0.691431|0.530302|0.693074|0.693055|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.770537|0.709734|0.657826|0.550068|0.715953|0.715938|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.137571|0.067985|0.045148|0.035189|0.126397|0.126383|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.134439|0.062979|0.041121|0.032513|0.135767|0.135757|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.927243|0.988916|0.985550|0.987230|0.987230|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|0.693112|0.959521|0.944537|0.951970|0.951970|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.693050|0.959566|0.944385|0.951915|0.951915|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|0.709719|0.979328|0.913162|0.945088|0.945088|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.067976|0.975086|0.811144|0.885591|0.885591|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.062973|0.985086|0.809774|0.888868|0.888868|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|4975123|315266|465436|88711811240|315266 / 88712126506|465436 / 5440559|
|2|SNOWBALL FRENCH DIRECT|PRIMARY_OUTPUT|3758589|1646111|1681970|88710480395|1646111 / 88712126506|1681970 / 5440559|
|3|SNOWBALL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|3755856|1641925|1684703|88710484581|1641925 / 88712126506|1684703 / 5440559|
|4|HUNSPELL FRENCH LUCENE FILTER|PRIMARY_OUTPUT|3412548|763305|2028011|88711363201|763305 / 88712126506|2028011 / 5440559|
|5|FRENCH LUCENE FRENCH LIGHT STEM FILTER|PRIMARY_OUTPUT|200690|262689|5239869|88711863817|262689 / 88712126506|5239869 / 5440559|
|6|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|PRIMARY_OUTPUT|181686|147476|5258873|88711979030|147476 / 88712126506|5258873 / 5440559|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 88712126506 (0.000000%)|0 / 5440559 (0.000000%)|1.000000|1.000000|1.000000|
|2|HUNSPELL FRENCH LUCENE FILTER|ANY_CANDIDATE|0.830852|733584 / 88712126506 (0.000827%)|1840476 / 5440559 (33.828803%)|0.790351|0.736648|0.741404|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL FRENCH LUCENE FILTER|ANY_CANDIDATE|0.830724|0.661712|0.999992|0.830852|0.999971|0.000029|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL FRENCH LUCENE FILTER|ANY_CANDIDATE|0.790351|0.736648|0.689779|0.583090|0.741418|0.741404|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL FRENCH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|5440559|0|0|88712126506|0 / 88712126506|0 / 5440559|
|2|HUNSPELL FRENCH LUCENE FILTER|ANY_CANDIDATE|3600083|733584|1840476|88711392922|733584 / 88712126506|1840476 / 5440559|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999995|938985 / 88712126506 (0.001058%)|0 / 5440559 (0.000000%)|0.878679|0.920560|0.923474|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|0.830850|1027635 / 88712126506 (0.001158%)|1840476 / 5440559 (33.828803%)|0.751538|0.715134|0.717460|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.852813|1.000000|0.999989|0.999995|0.999989|0.000011|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|0.777939|0.661712|0.999988|0.830850|0.999968|0.000032|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.878679|0.920560|0.966634|0.852813|0.923479|0.923474|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|0.751538|0.715134|0.682093|0.556582|0.717476|0.717460|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|5440559|938985|0|88711187521|938985 / 88712126506|0 / 5440559|
|2|HUNSPELL FRENCH LUCENE FILTER|ALL_CANDIDATES|3600083|1027635|1840476|88711098871|1027635 / 88712126506|1840476 / 5440559|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|465436|315266|623719|41130|9.764239%|56|468574|
|HUNSPELL FRENCH LUCENE FILTER|187535|29721|264330|13437|3.189936%|4|434961|

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
- Dictionary language: `FR_FR`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
