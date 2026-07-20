# Norwegian Bokmal Stemmer Benchmarks

This page reports same-language stemming benchmarks for Norwegian Bokmal. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `NB_NO` | 17,929 | 90,757 | 33,376 | 57,381 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **90,757**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 1,500 | 1.653% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 4,296 | 4.734% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 47,619 | 52.469% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 34,420 | 37.925% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 2,922 | 3.220% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 96.852% | 97.637% | 95.503% | Full Radixor dictionary patch-command stemmer. |
| Lucene NorwegianMinimalStemFilter | 57.107% | 53.913% | 62.599% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Official Snowball direct | 54.824% | 51.791% | 60.040% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene SnowballFilter | 54.803% | 51.780% | 60.001% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Lucene NorwegianLightStemFilter | 52.136% | 50.616% | 54.749% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `norwegianBokmalRadixor` | 3.631 | 1.377 | 63.3 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene NorwegianMinimalStemFilter | `norwegianBokmalLuceneNorwegianMinimalStemFilter` | 2.910 | 0.177 | 50.7 | 0.801 | Minimal Norwegian suffix reducer. |
| Lucene NorwegianLightStemFilter | `norwegianBokmalLuceneNorwegianLightStemFilter` | 3.335 | 0.116 | 58.1 | 0.919 | Light Norwegian suffix stemmer. |
| Official Snowball direct | `snowballDirect[NORWEGIAN_BOKMAL]` | 4.277 | 0.082 | 74.5 | 1.178 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[NORWEGIAN_BOKMAL]` | 6.077 | 0.208 | 105.9 | 1.674 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `NB_NO` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/nb_no/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.974783** among 5 deterministic stemmers. The runner-up is `SNOWBALL NORWEGIAN BOKMAL DIRECT` at 0.874964, a difference of 0.099819. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.975000** among 5 deterministic stemmers. The runner-up is `SNOWBALL NORWEGIAN BOKMAL DIRECT` at 0.874991, a difference of 0.100009. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **7 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.974783|11482 / 2835618215 (0.000405%)|7170 / 142180 (5.042903%)|0.927078|0.935387|0.935488|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.874964|23997 / 2835618215 (0.000846%)|35554 / 142180 (25.006330%)|0.802095|0.781707|0.782399|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.874834|24046 / 2835618215 (0.000848%)|35591 / 142180 (25.032353%)|0.801759|0.781401|0.782091|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.850006|25171 / 2835618215 (0.000888%)|42651 / 142180 (29.997890%)|0.776381|0.745871|0.747464|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.832414|14772 / 2835618215 (0.000521%)|47654 / 142180 (33.516669%)|0.815763|0.751764|0.758263|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.921620|0.949571|0.999996|0.974783|0.999993|0.000007|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.816288|0.749937|0.999992|0.874964|0.999979|0.000021|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.815930|0.749676|0.999992|0.874834|0.999979|0.000021|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.798148|0.700021|0.999991|0.850006|0.999976|0.000024|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.864847|0.664833|0.999995|0.832414|0.999978|0.000022|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.927078|0.935387|0.943846|0.878617|0.935491|0.935488|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.802095|0.781707|0.762330|0.641641|0.782409|0.782399|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.801759|0.781401|0.762052|0.641229|0.782102|0.782091|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.776381|0.745871|0.717668|0.594732|0.747476|0.747464|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.815763|0.751764|0.697076|0.602261|0.758274|0.758263|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.935384|0.993354|0.994615|0.993984|0.993984|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.781696|0.988328|0.971120|0.979648|0.979648|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.781391|0.988295|0.971086|0.979615|0.979615|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.745859|0.987774|0.965622|0.976573|0.976573|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.751753|0.992089|0.962516|0.977079|0.977079|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|135010|11482|7170|2835606733|11482 / 2835618215|7170 / 142180|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|106626|23997|35554|2835594218|23997 / 2835618215|35554 / 142180|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|106589|24046|35591|2835594169|24046 / 2835618215|35591 / 142180|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|99529|25171|42651|2835593044|25171 / 2835618215|42651 / 142180|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|94526|14772|47654|2835603443|14772 / 2835618215|47654 / 142180|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 2835618215 (0.000000%)|0 / 142180 (0.000000%)|1.000000|1.000000|1.000000|

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
|1|Radixor|ANY_CANDIDATE|142180|0|0|2835618215|0 / 2835618215|0 / 142180|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999996|20161 / 2835618215 (0.000711%)|0 / 142180 (0.000000%)|0.898118|0.933794|0.935844|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.875811|1.000000|0.999993|0.999996|0.999993|0.000007|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.898118|0.933794|0.972422|0.875811|0.935848|0.935844|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|142180|20161|0|2835598054|20161 / 2835618215|0 / 142180|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|7170|11482|8679|4237|5.626079%|9|79825|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **7 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.975000|11482 / 2831176784 (0.000406%)|7104 / 142091 (4.999613%)|0.927151|0.935591|0.935695|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.874991|23997 / 2831176784 (0.000848%)|35524 / 142091 (25.000880%)|0.802043|0.781698|0.782388|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.874798|23993 / 2831176784 (0.000847%)|35579 / 142091 (25.039587%)|0.801914|0.781464|0.782161|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.849947|25118 / 2831176784 (0.000887%)|42641 / 142091 (30.009642%)|0.776513|0.745896|0.747500|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.832344|14719 / 2831176784 (0.000520%)|47644 / 142091 (33.530625%)|0.815950|0.751796|0.758325|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.921608|0.950004|0.999996|0.975000|0.999993|0.000007|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.816205|0.749991|0.999992|0.874991|0.999979|0.000021|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.816153|0.749604|0.999992|0.874798|0.999979|0.000021|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.798359|0.699904|0.999991|0.849947|0.999976|0.000024|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.865169|0.664694|0.999995|0.832344|0.999978|0.000022|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.927151|0.935591|0.944186|0.878976|0.935698|0.935695|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.802043|0.781698|0.762360|0.641630|0.782398|0.782388|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.801914|0.781464|0.762031|0.641314|0.782171|0.782161|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.776513|0.745896|0.717603|0.594765|0.747512|0.747500|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.815950|0.751796|0.696995|0.602302|0.758335|0.758325|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.935587|0.993348|0.994694|0.994020|0.994020|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|0.781688|0.988318|0.971127|0.979647|0.979647|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|0.781454|0.988310|0.971074|0.979616|0.979616|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.745885|0.987789|0.965603|0.976570|0.976570|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.751785|0.992107|0.962494|0.977076|0.977076|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|134987|11482|7104|2831165302|11482 / 2831176784|7104 / 142091|
|2|SNOWBALL NORWEGIAN BOKMAL DIRECT|PRIMARY_OUTPUT|106567|23997|35524|2831152787|23997 / 2831176784|35524 / 142091|
|3|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|PRIMARY_OUTPUT|106512|23993|35579|2831152791|23993 / 2831176784|35579 / 142091|
|4|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|PRIMARY_OUTPUT|99450|25118|42641|2831151666|25118 / 2831176784|42641 / 142091|
|5|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|94447|14719|47644|2831162065|14719 / 2831176784|47644 / 142091|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 2831176784 (0.000000%)|0 / 142091 (0.000000%)|1.000000|1.000000|1.000000|

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
|1|Radixor|ANY_CANDIDATE|142091|0|0|2831176784|0 / 2831176784|0 / 142091|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999996|20161 / 2831176784 (0.000712%)|0 / 142091 (0.000000%)|0.898061|0.933756|0.935808|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.875743|1.000000|0.999993|0.999996|0.999993|0.000007|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.898061|0.933756|0.972405|0.875743|0.935811|0.935808|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|142091|20161|0|2831156623|20161 / 2831176784|0 / 142091|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|7104|11482|8679|4204|5.586637%|9|79733|

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
- Dictionary language: `NB_NO`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
