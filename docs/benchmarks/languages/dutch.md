# Dutch Stemmer Benchmarks

This page reports same-language stemming benchmarks for Dutch. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `NL_NL` | 4,992 | 31,466 | 9,981 | 21,485 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **31,466**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 2,107 | 6.696% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 11,484 | 36.497% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 7,732 | 24.573% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 10,127 | 32.184% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 16 | 0.051% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 99.120% | 98.711% | 100.000% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 46.590% | 22.718% | 97.976% | Benchmark-only Dutch Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Official Snowball direct | 15.954% | 8.992% | 30.939% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene SnowballFilter | 12.620% | 5.441% | 28.073% | Lucene TokenFilter integration path around the Snowball algorithm. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `radixor[DUTCH]` | 1.331 | 0.114 | 61.9 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 22.760 | 1.387 | 1059.3 | 17.105 | Benchmark-only Dutch Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Official Snowball direct | `snowballDirect[DUTCH]` | 4.146 | 0.291 | 193.0 | 3.116 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[DUTCH]` | 7.375 | 0.595 | 343.3 | 5.543 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `NL_NL` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/nl_nl/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.988661** among 4 deterministic stemmers. The runner-up is `SNOWBALL DUTCH DIRECT` at 0.727087, a difference of 0.261574. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.989040** among 4 deterministic stemmers. The runner-up is `SNOWBALL DUTCH DIRECT` at 0.730495, a difference of 0.258544. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **8 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.988661|1214 / 350437960 (0.000346%)|1464 / 64566 (2.267447%)|0.980362|0.979221|0.979219|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.727087|4382 / 350437960 (0.001250%)|35241 / 64566 (54.581359%)|0.735353|0.596807|0.628557|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.643123|1333 / 350437960 (0.000380%)|46084 / 64566 (71.375027%)|0.642512|0.438061|0.516674|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.618497|1588 / 350437960 (0.000453%)|49264 / 64566 (76.300220%)|0.579068|0.375712|0.463333|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.981124|0.977326|0.999997|0.988661|0.999992|0.000008|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.869997|0.454186|0.999987|0.727087|0.999887|0.000113|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.932728|0.286250|0.999996|0.643123|0.999865|0.000135|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.905980|0.236998|0.999995|0.618497|0.999855|0.000145|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.980362|0.979221|0.978083|0.959289|0.979223|0.979219|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.735353|0.596807|0.502190|0.425321|0.628602|0.628557|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.642512|0.438061|0.332316|0.280459|0.516714|0.516674|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.579068|0.375712|0.278062|0.231309|0.463374|0.463333|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.979217|0.997464|0.997003|0.997234|0.997234|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.596756|0.992815|0.917346|0.953590|0.953590|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.438012|0.996932|0.889026|0.939892|0.939892|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.375664|0.995828|0.888410|0.939057|0.939057|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|63102|1214|1464|350436746|1214 / 350437960|1464 / 64566|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|29325|4382|35241|350433578|4382 / 350437960|35241 / 64566|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|18482|1333|46084|350436627|1333 / 350437960|46084 / 64566|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|15302|1588|49264|350436372|1588 / 350437960|49264 / 64566|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 350437960 (0.000000%)|0 / 64566 (0.000000%)|1.000000|1.000000|1.000000|
|2|HUNSPELL DUTCH LUCENE FILTER|ANY_CANDIDATE|0.665519|1164 / 350437960 (0.000332%)|43192 / 64566 (66.895889%)|0.690741|0.490770|0.560268|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL DUTCH LUCENE FILTER|ANY_CANDIDATE|0.948354|0.331041|0.999997|0.665519|0.999873|0.000127|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL DUTCH LUCENE FILTER|ANY_CANDIDATE|0.690741|0.490770|0.380588|0.325179|0.560307|0.560268|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL DUTCH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|64566|0|0|350437960|0 / 350437960|0 / 64566|
|2|HUNSPELL DUTCH LUCENE FILTER|ANY_CANDIDATE|21374|1164|43192|350436796|1164 / 350437960|43192 / 64566|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999996|2651 / 350437960 (0.000756%)|0 / 64566 (0.000000%)|0.968198|0.979884|0.980078|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|0.665518|1738 / 350437960 (0.000496%)|43192 / 64566 (66.895889%)|0.680640|0.487557|0.553265|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.960561|1.000000|0.999992|0.999996|0.999992|0.000008|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|0.924801|0.331041|0.999995|0.665518|0.999872|0.000128|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.968198|0.979884|0.991855|0.960561|0.980082|0.980078|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|0.680640|0.487557|0.379812|0.322364|0.553306|0.553265|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|64566|2651|0|350435309|2651 / 350437960|0 / 64566|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|21374|1738|43192|350436222|1738 / 350437960|43192 / 64566|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL DUTCH LUCENE FILTER|2892|169|405|1254|4.736186%|3|27763|
|Radixor|1464|1214|1437|572|2.160366%|3|27061|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **8 result rows**, **4 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.989040|1214 / 329603856 (0.000368%)|1384 / 63147 (2.191711%)|0.980194|0.979401|0.979398|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.730495|4382 / 329603856 (0.001329%)|34036 / 63147 (53.899631%)|0.738412|0.602463|0.632953|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.645159|1310 / 329603856 (0.000397%)|44814 / 63147 (70.967742%)|0.646808|0.442880|0.520498|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.618546|1544 / 329603856 (0.000468%)|48175 / 63147 (76.290243%)|0.579362|0.375883|0.463566|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.980723|0.978083|0.999996|0.989040|0.999992|0.000008|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.869167|0.461004|0.999987|0.730495|0.999883|0.000117|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.933310|0.290323|0.999996|0.645159|0.999860|0.000140|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.906515|0.237098|0.999995|0.618546|0.999849|0.000151|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.980194|0.979401|0.978610|0.959634|0.979402|0.979398|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.738412|0.602463|0.508789|0.431089|0.633000|0.632953|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.646808|0.442880|0.336718|0.284422|0.520539|0.520498|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.579362|0.375883|0.278182|0.231439|0.463608|0.463566|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.979397|0.997373|0.997139|0.997256|0.997256|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|0.602410|0.992557|0.918059|0.953856|0.953856|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.442829|0.996884|0.889061|0.939890|0.939890|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|0.375834|0.995817|0.887492|0.938539|0.938539|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|61763|1214|1384|329602642|1214 / 329603856|1384 / 63147|
|2|SNOWBALL DUTCH DIRECT|PRIMARY_OUTPUT|29111|4382|34036|329599474|4382 / 329603856|34036 / 63147|
|3|HUNSPELL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|18333|1310|44814|329602546|1310 / 329603856|44814 / 63147|
|4|SNOWBALL DUTCH LUCENE FILTER|PRIMARY_OUTPUT|14972|1544|48175|329602312|1544 / 329603856|48175 / 63147|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 329603856 (0.000000%)|0 / 63147 (0.000000%)|1.000000|1.000000|1.000000|
|2|HUNSPELL DUTCH LUCENE FILTER|ANY_CANDIDATE|0.667956|1141 / 329603856 (0.000346%)|41935 / 63147 (66.408539%)|0.695206|0.496187|0.564555|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL DUTCH LUCENE FILTER|ANY_CANDIDATE|0.948955|0.335915|0.999997|0.667956|0.999869|0.000131|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL DUTCH LUCENE FILTER|ANY_CANDIDATE|0.695206|0.496187|0.385755|0.329953|0.564595|0.564555|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL DUTCH LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|63147|0|0|329603856|0 / 329603856|0 / 63147|
|2|HUNSPELL DUTCH LUCENE FILTER|ANY_CANDIDATE|21212|1141|41935|329602715|1141 / 329603856|41935 / 63147|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999996|2651 / 329603856 (0.000804%)|0 / 63147 (0.000000%)|0.967506|0.979441|0.979644|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|0.667955|1712 / 329603856 (0.000519%)|41935 / 63147 (66.408539%)|0.684952|0.492895|0.557477|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.959710|1.000000|0.999992|0.999996|0.999992|0.000008|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|0.925318|0.335915|0.999995|0.667955|0.999868|0.000132|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.967506|0.979441|0.991674|0.959710|0.979648|0.979644|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|0.684952|0.492895|0.384956|0.327048|0.557519|0.557477|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|63147|2651|0|329601205|2651 / 329603856|0 / 63147|
|2|HUNSPELL DUTCH LUCENE FILTER|ALL_CANDIDATES|21212|1712|41935|329602144|1712 / 329603856|41935 / 63147|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|HUNSPELL DUTCH LUCENE FILTER|2879|169|402|1186|4.618740%|3|26896|
|Radixor|1384|1214|1437|549|2.138017%|3|26239|

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
- Dictionary language: `NL_NL`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
