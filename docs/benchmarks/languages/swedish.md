# Swedish Stemmer Benchmarks

This page reports same-language stemming benchmarks for Swedish. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `SV_SE` | 12,371 | 110,468 | 24,731 | 85,737 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **110,468**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 502 | 0.454% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 14,268 | 12.916% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 66,796 | 60.466% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 25,745 | 23.305% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 3,157 | 2.858% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 96.713% | 97.407% | 94.307% | Full Radixor dictionary patch-command stemmer. |
| Lucene SwedishMinimalStemFilter | 49.532% | 49.186% | 50.730% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SwedishLightStemFilter | 45.672% | 46.383% | 43.209% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |
| Official Snowball direct | 40.068% | 37.512% | 48.926% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene SnowballFilter | 38.785% | 35.839% | 48.999% | Lucene TokenFilter integration path around the Snowball algorithm. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `swedishRadixor` | 5.489 | 0.355 | 64.0 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| Lucene SwedishMinimalStemFilter | `swedishLuceneSwedishMinimalStemFilter` | 4.630 | 0.130 | 54.0 | 0.843 | Minimal Swedish suffix reducer. |
| Lucene SwedishLightStemFilter | `swedishLuceneSwedishLightStemFilter` | 4.876 | 0.328 | 56.9 | 0.888 | Light Swedish suffix stemmer. |
| Official Snowball direct | `snowballDirect[SWEDISH]` | 7.517 | 0.072 | 87.7 | 1.370 | Official Snowball generated Java stemmer; direct API. |
| Lucene SnowballFilter | `luceneSnowballFilter[SWEDISH]` | 9.793 | 0.338 | 114.2 | 1.784 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `SV_SE` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/sv_se/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.974636** among 5 deterministic stemmers. The runner-up is `SNOWBALL SWEDISH DIRECT` at 0.807534, a difference of 0.167101. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.974584** among 5 deterministic stemmers. The runner-up is `SNOWBALL SWEDISH DIRECT` at 0.807599, a difference of 0.166985. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **7 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.974636|24473 / 4812155436 (0.000509%)|19546 / 385342 (5.072377%)|0.939665|0.943246|0.943260|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.807534|67105 / 4812155436 (0.001394%)|148325 / 385342 (38.491781%)|0.739832|0.687540|0.692339|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.799307|64262 / 4812155436 (0.001335%)|154666 / 385342 (40.137333%)|0.736940|0.678180|0.684227|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.796072|40227 / 4812155436 (0.000836%)|157161 / 385342 (40.784809%)|0.781991|0.698068|0.709491|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.783685|45941 / 4812155436 (0.000955%)|166707 / 385342 (43.262089%)|0.757232|0.672808|0.684713|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.937292|0.949276|0.999995|0.974636|0.999991|0.000009|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.779348|0.615082|0.999986|0.807534|0.999955|0.000045|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.782117|0.598627|0.999987|0.799307|0.999955|0.000045|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.850127|0.592152|0.999992|0.796072|0.999959|0.000041|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.826360|0.567379|0.999990|0.783685|0.999956|0.000044|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.939665|0.943246|0.946855|0.892588|0.943265|0.943260|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.739832|0.687540|0.642152|0.523856|0.692361|0.692339|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.736940|0.678180|0.628098|0.513065|0.684249|0.684227|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.781991|0.698068|0.630412|0.536179|0.709510|0.709491|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.757232|0.672808|0.605321|0.506941|0.684733|0.684713|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.943241|0.992631|0.993395|0.993013|0.993013|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.687518|0.984860|0.942685|0.963311|0.963311|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.678157|0.985207|0.939659|0.961894|0.961894|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.698048|0.988493|0.944582|0.966038|0.966038|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.672787|0.986795|0.942303|0.964036|0.964036|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|365796|24473|19546|4812130963|24473 / 4812155436|19546 / 385342|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|237017|67105|148325|4812088331|67105 / 4812155436|148325 / 385342|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|230676|64262|154666|4812091174|64262 / 4812155436|154666 / 385342|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|228181|40227|157161|4812115209|40227 / 4812155436|157161 / 385342|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|218635|45941|166707|4812109495|45941 / 4812155436|166707 / 385342|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 4812155436 (0.000000%)|0 / 385342 (0.000000%)|1.000000|1.000000|1.000000|

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
|1|Radixor|ANY_CANDIDATE|385342|0|0|4812155436|0 / 4812155436|0 / 385342|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999995|47848 / 4812155436 (0.000994%)|0 / 385342 (0.000000%)|0.909640|0.941544|0.943152|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.889545|1.000000|0.999990|0.999995|0.999990|0.000010|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.909640|0.941544|0.975768|0.889545|0.943157|0.943152|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|385342|47848|0|4812107588|47848 / 4812155436|0 / 385342|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|19546|24473|23375|5767|5.878216%|5|104148|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **7 result rows**, **5 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.974584|24473 / 4789911577 (0.000511%)|19546 / 384563 (5.082652%)|0.939544|0.943132|0.943146|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.807599|67105 / 4789911577 (0.001401%)|147975 / 384563 (38.478741%)|0.739645|0.687500|0.692274|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.799355|64262 / 4789911577 (0.001342%)|154316 / 384563 (40.127625%)|0.736744|0.678122|0.684143|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.795947|40227 / 4789911577 (0.000840%)|156939 / 384563 (40.809698%)|0.781694|0.697790|0.709212|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.783598|45941 / 4789911577 (0.000959%)|166437 / 384563 (43.279515%)|0.756945|0.672575|0.684469|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.937167|0.949173|0.999995|0.974584|0.999991|0.000009|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.779037|0.615213|0.999986|0.807599|0.999955|0.000045|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.781800|0.598724|0.999987|0.799355|0.999954|0.000046|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.849816|0.591903|0.999992|0.795947|0.999959|0.000041|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.826025|0.567205|0.999990|0.783598|0.999956|0.000044|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.939544|0.943132|0.946748|0.892384|0.943151|0.943146|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.739645|0.687500|0.642223|0.523810|0.692296|0.692274|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.736744|0.678122|0.628142|0.512999|0.684165|0.684143|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.781694|0.697790|0.630152|0.535851|0.709231|0.709212|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.756945|0.672575|0.605126|0.506676|0.684489|0.684469|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.943127|0.992612|0.993378|0.992995|0.992995|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|0.687478|0.984821|0.942695|0.963298|0.963298|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|0.678100|0.985169|0.939661|0.961877|0.961877|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.697770|0.988463|0.944528|0.965996|0.965996|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|0.672553|0.986761|0.942265|0.964000|0.964000|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|365017|24473|19546|4789887104|24473 / 4789911577|19546 / 384563|
|2|SNOWBALL SWEDISH DIRECT|PRIMARY_OUTPUT|236588|67105|147975|4789844472|67105 / 4789911577|147975 / 384563|
|3|SNOWBALL SWEDISH LUCENE FILTER|PRIMARY_OUTPUT|230247|64262|154316|4789847315|64262 / 4789911577|154316 / 384563|
|4|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|PRIMARY_OUTPUT|227624|40227|156939|4789871350|40227 / 4789911577|156939 / 384563|
|5|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|PRIMARY_OUTPUT|218126|45941|166437|4789865636|45941 / 4789911577|166437 / 384563|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 4789911577 (0.000000%)|0 / 384563 (0.000000%)|1.000000|1.000000|1.000000|

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
|1|Radixor|ANY_CANDIDATE|384563|0|0|4789911577|0 / 4789911577|0 / 384563|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999995|47848 / 4789911577 (0.000999%)|0 / 384563 (0.000000%)|0.909473|0.941433|0.943047|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.889346|1.000000|0.999990|0.999995|0.999990|0.000010|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.909473|0.941433|0.975720|0.889346|0.943051|0.943047|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|384563|47848|0|4789863729|47848 / 4789911577|0 / 384563|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|19546|24473|23375|5767|5.891848%|5|103921|

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
- Dictionary language: `SV_SE`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
