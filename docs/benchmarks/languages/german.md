# German Stemmer Benchmarks

This page reports same-language stemming benchmarks for German. Accuracy is listed first because speed without root agreement is not enough to interpret stemmer quality.

All speed values are environment-specific and were measured on the hardware and JVM listed in the [benchmark overview](../index.md). Speed benchmark operations process changed dictionary tokens only. Accuracy uses the complete Radixor dictionary for the language.

Radixor must not be read as simply "slower" when a narrow competitor has a lower timing row. In these tables Radixor is the quality-oriented baseline: its exact-root accuracy is typically close to 100%, while many faster rule-based, light, minimal, or possessive filters reach that speed by doing much less linguistic work and often score far lower in `All exact` and `Changed exact`. The Radixor rows in this benchmark refresh use the contracted compiled patch trie: compilation collapses uniform patch-command subtrees into accepting leaves, reducing hot lookup depth while preserving the preferred stemming result measured by the accuracy pass. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows the resulting quality/speed envelope explicitly. The same interpretation applies to this language page: speed rows must be read together with the accuracy table above them.

## Dictionary Corpus

| Resource | Dictionary rows | Complete quality tokens | Already-root tokens | Changed speed tokens |
| --- | ---: | ---: | ---: | ---: |
| `DE_DE` | 39,315 | 213,440 | 73,799 | 139,641 |

## Radixor Patch Command Distribution

Radixor stores the preferred transformation for each normalized dictionary word form as a compiled patch command. This distribution shows which runtime command class is selected by the trained trie for the complete language dictionary. The total number of preferred patch commands analyzed for this language is **213,440**.

| Command class | Meaning | Word forms | Share |
| --- | --- | ---: | ---: |
| `AppendCharacterCommand` | Appends one character to the end of the word form. | 3,627 | 1.699% |
| `BackwardCompoundCommand` | Applies a multi-step backward patch made from skip, delete, insert, and replace operations. | 48,605 | 22.772% |
| `DeleteSuffixCommand` | Deletes one or more trailing characters from the word form. | 80,443 | 37.689% |
| `PreserveCommand` | Returns the word form unchanged because it already matches the preferred root. | 75,717 | 35.475% |
| `ReplaceLastCharacterCommand` | Replaces the final character of the word form. | 5,048 | 2.365% |

## Accuracy

Accuracy is computed from JMH auxiliary counters in the current report. The counters are deterministic for a fixed corpus and stemmer; percentages divide matching counters by evaluated counters from the same report and are not timing metrics.

| Stemmer | All exact | Changed exact | Root preserved | Note |
| --- | ---: | ---: | ---: | --- |
| Radixor | 92.725% | 92.847% | 92.396% | Full Radixor dictionary patch-command stemmer. |
| Lucene HunspellStemFilter | 47.064% | 29.661% | 93.678% | Benchmark-only German Hunspell dictionary compared via Lucene HunspellStemFilter. |
| CISTEM (German) | 24.675% | 23.724% | 27.222% | Benchmark-only CISTEM implementation. |
| Lucene GermanLightStemFilter | 37.434% | 35.465% | 42.707% | Light suffix stemmer; intentionally narrower than a dictionary-derived stemmer. |
| Lucene GermanMinimalStemFilter | 27.640% | 24.951% | 34.844% | Minimal suffix reducer; narrow baseline, not a full stemmer. |
| Lucene SnowballFilter | 30.956% | 28.853% | 36.589% | Lucene TokenFilter integration path around the Snowball algorithm. |
| Official Snowball direct | 30.481% | 29.027% | 34.376% | Official Snowball generated Java stemmer; rule-based suffix algorithm. |
| Lucene GermanStemFilter | 21.559% | 19.312% | 27.576% | German Lucene stemming TokenFilter; broader than minimal/light variants. |

## Speed

Speed uses JMH average time, 3 warmup iterations, 5 measurement iterations, 1 fork, and 1 thread. Relative factor is computed against the single Radixor row on this language page. Values below 1.000 are faster than that Radixor baseline; values above 1.000 are slower.

| Stemmer | Benchmark method | Score ms/op | Error ms | ns/token | Relative vs Radixor | Note |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Radixor | `germanRadixor` | 41.166 | 2.396 | 294.8 | 1.000 | Full Radixor dictionary patch-command stemmer. |
| CISTEM | `germanCistem` | 248.392 | 12.294 | 1778.8 | 6.034 | Benchmark-only CISTEM implementation. |
| Lucene HunspellStemFilter | `luceneHunspellStemFilter` | 281.322 | 3.411 | 2014.6 | 6.834 | Benchmark-only German Hunspell dictionary compared via Lucene HunspellStemFilter. |
| Lucene GermanMinimalStemFilter | `germanLuceneGermanMinimalStemFilter` | 23.562 | 0.969 | 168.7 | 0.572 | Minimal German suffix reduction; narrow baseline. |
| Lucene GermanLightStemFilter | `germanLuceneGermanLightStemFilter` | 24.410 | 1.034 | 174.8 | 0.593 | Light German suffix stemmer; narrower than a dictionary stemmer. |
| Lucene GermanStemFilter | `germanLuceneGermanStemFilter` | 71.039 | 4.443 | 508.7 | 1.726 | Older German stemming TokenFilter with normalization requirements. |
| Lucene SnowballFilter | `luceneSnowballFilter[GERMAN]` | 105.771 | 9.617 | 757.4 | 2.569 | Lucene TokenFilter path around Snowball; includes TokenStream overhead. |
| Official Snowball direct | `snowballDirect[GERMAN]` | 100.688 | 9.018 | 721.0 | 2.446 | Official Snowball generated Java stemmer; direct API. |

## Interpretation Notes

- Radixor is a dictionary-derived patch-command stemmer. Its quality depends on the language resource used to train the compiled trie.
- Light, minimal, plural, and possessive filters are narrow baselines. They can be fast because they intentionally perform less linguistic work.
- Lucene TokenFilter rows include TokenStream, attribute, and required normalization overhead. Direct rows measure exposed direct APIs.
- Morfologik rows are dictionary-based and can emit multiple terms for one input token. Quality rows use the first returned term when no ranking weight is available.
- Snowball rows are rule-based generated suffix stemmers; they are useful algorithmic baselines, not dictionary-root equivalence guarantees.

<!-- STEMMING-QUALITY:START -->

## Stemming Quality

Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `DE_DE` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.

`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).

### Evaluation Scope and Key Findings

The dictionary resource is `src/main/resources/de_de/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.

- **ALL_WORDS:** `Radixor` ranks first by balanced accuracy at **0.907901** among 8 deterministic stemmers. The runner-up is `GERMAN CISTEM` at 0.880770, a difference of 0.027131. This rank does not imply leadership in throughput or every secondary metric.
- **LOWERCASE_GROUPS_ONLY:** `Radixor` ranks first by balanced accuracy at **0.966157** among 8 deterministic stemmers. The runner-up is `GERMAN CISTEM` at 0.915288, a difference of 0.050869. This rank does not imply leadership in throughput or every secondary metric.
### `ALL_WORDS`

This mode contains **12 result rows**, **8 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.907901|98192 / 44095245979 (0.000223%)|254903 / 1383872 (18.419550%)|0.897073|0.864768|0.866326|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.880770|477122 / 44095245979 (0.001082%)|329983 / 1383872 (23.844908%)|0.701852|0.723109|0.724023|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.778614|190680 / 44095245979 (0.000432%)|612734 / 1383872 (44.276783%)|0.737064|0.657494|0.668394|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.771357|295701 / 44095245979 (0.000671%)|632816 / 1383872 (45.727929%)|0.674089|0.617993|0.624014|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.756258|205740 / 44095245979 (0.000467%)|674609 / 1383872 (48.747933%)|0.703092|0.617052|0.630292|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.723772|331871 / 44095245979 (0.000753%)|764518 / 1383872 (55.244849%)|0.596821|0.530474|0.539809|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.641579|203883 / 44095245979 (0.000462%)|992010 / 1383872 (71.683653%)|0.520145|0.395897|0.431563|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.598139|110840 / 44095245979 (0.000251%)|1112246 / 1383872 (80.372029%)|0.466113|0.307558|0.373350|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.919984|0.815804|0.999998|0.907901|0.999992|0.000008|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.688361|0.761551|0.999989|0.880770|0.999982|0.000018|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.801750|0.557232|0.999996|0.778614|0.999982|0.000018|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.717508|0.542721|0.999993|0.771357|0.999979|0.000021|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.775148|0.512521|0.999995|0.756258|0.999980|0.000020|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.651112|0.447552|0.999992|0.723772|0.999975|0.000025|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.657768|0.283163|0.999995|0.641579|0.999973|0.000027|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.710196|0.196280|0.999997|0.598139|0.999972|0.000028|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.897073|0.864768|0.834709|0.761755|0.866330|0.866326|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.701852|0.723109|0.745694|0.566304|0.724032|0.724023|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.737064|0.657494|0.593429|0.489751|0.668402|0.668394|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.674089|0.617993|0.570517|0.447171|0.624024|0.624014|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.703092|0.617052|0.549774|0.446186|0.630301|0.630292|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.596821|0.530474|0.477402|0.360983|0.539820|0.539809|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.520145|0.395897|0.319562|0.246803|0.431574|0.431563|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.466113|0.307558|0.229493|0.181725|0.373359|0.373350|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.864764|0.989946|0.975085|0.982460|0.982460|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.723100|0.974048|0.975147|0.974597|0.974597|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.657485|0.983725|0.949324|0.966218|0.966218|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.617983|0.975845|0.942925|0.959102|0.959102|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.617043|0.980753|0.936533|0.958133|0.958133|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.530462|0.975550|0.942890|0.958942|0.958942|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.395885|0.980463|0.886873|0.931322|0.931322|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.307549|0.983615|0.896264|0.937910|0.937910|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|1128969|98192|254903|44095147787|98192 / 44095245979|254903 / 1383872|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|1053889|477122|329983|44094768857|477122 / 44095245979|329983 / 1383872|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|771138|190680|612734|44095055299|190680 / 44095245979|612734 / 1383872|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|751056|295701|632816|44094950278|295701 / 44095245979|632816 / 1383872|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|709263|205740|674609|44095040239|205740 / 44095245979|674609 / 1383872|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|619354|331871|764518|44094914108|331871 / 44095245979|764518 / 1383872|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|391862|203883|992010|44095042096|203883 / 44095245979|992010 / 1383872|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|271626|110840|1112246|44095135139|110840 / 44095245979|1112246 / 1383872|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.959835|1375 / 44095245979 (0.000003%)|111167 / 1383872 (8.033041%)|0.981996|0.957658|0.958475|
|2|HUNSPELL GERMAN LUCENE FILTER|ANY_CANDIDATE|0.647474|158403 / 44095245979 (0.000359%)|975697 / 1383872 (70.504859%)|0.559116|0.418544|0.460956|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.998921|0.919670|1.000000|0.959835|0.999997|0.000003|
|2|HUNSPELL GERMAN LUCENE FILTER|ANY_CANDIDATE|0.720422|0.294951|0.999996|0.647474|0.999974|0.000026|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|0.981996|0.957658|0.934498|0.918757|0.958476|0.958475|
|2|HUNSPELL GERMAN LUCENE FILTER|ANY_CANDIDATE|0.559116|0.418544|0.334456|0.264658|0.460966|0.460956|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL GERMAN LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1272705|1375|111167|44095244604|1375 / 44095245979|111167 / 1383872|
|2|HUNSPELL GERMAN LUCENE FILTER|ANY_CANDIDATE|408175|158403|975697|44095087576|158403 / 44095245979|975697 / 1383872|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.959832|244817 / 44095245979 (0.000555%)|111167 / 1383872 (8.033041%)|0.853711|0.877306|0.878234|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|0.647473|242551 / 44095245979 (0.000550%)|975697 / 1383872 (70.504859%)|0.511911|0.401234|0.430118|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.838673|0.919670|0.999994|0.959832|0.999992|0.000008|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|0.627261|0.294951|0.999994|0.647473|0.999972|0.000028|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.853711|0.877306|0.902242|0.781429|0.878238|0.878234|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|0.511911|0.401234|0.329907|0.250965|0.430130|0.430118|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|1272705|244817|111167|44095001162|244817 / 44095245979|111167 / 1383872|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|408175|242551|975697|44095003428|242551 / 44095245979|975697 / 1383872|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|143736|96817|146625|48574|16.356314%|8|361016|
|HUNSPELL GERMAN LUCENE FILTER|16313|45480|38668|7891|2.657135%|3|305052|

### `LOWERCASE_GROUPS_ONLY`

This mode contains **12 result rows**, **8 evaluated stemmers**, and **3 output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.

#### `PRIMARY_OUTPUT` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.966157|47898 / 11263756342 (0.000425%)|59114 / 873411 (6.768177%)|0.941996|0.938343|0.938358|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.915288|156784 / 11263756342 (0.001392%)|147964 / 873411 (16.940936%)|0.823934|0.826418|0.826415|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.795926|87697 / 11263756342 (0.000779%)|356475 / 873411 (40.814118%)|0.785153|0.699487|0.711329|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.775641|77653 / 11263756342 (0.000689%)|391910 / 873411 (44.871200%)|0.774111|0.672222|0.688986|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.769953|55477 / 11263756342 (0.000493%)|401846 / 873411 (46.008809%)|0.790797|0.673446|0.695023|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.716810|78723 / 11263756342 (0.000699%)|494677 / 873411 (56.637368%)|0.700519|0.569153|0.599149|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.659196|84679 / 11263756342 (0.000752%)|595318 / 873411 (68.160122%)|0.598178|0.449922|0.494019|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.575691|21214 / 11263756342 (0.000188%)|741190 / 873411 (84.861537%)|0.444545|0.257528|0.361168|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.944446|0.932318|0.999996|0.966157|0.999991|0.000009|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.822287|0.830591|0.999986|0.915288|0.999973|0.000027|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.854958|0.591859|0.999992|0.795926|0.999961|0.000039|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.861124|0.551288|0.999993|0.775641|0.999958|0.000042|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.894739|0.539912|0.999995|0.769953|0.999959|0.000041|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.827912|0.433626|0.999993|0.716810|0.999949|0.000051|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.766578|0.318399|0.999992|0.659196|0.999940|0.000060|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.861739|0.151385|0.999998|0.575691|0.999932|0.000068|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.941996|0.938343|0.934719|0.883848|0.938363|0.938358|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.823934|0.826418|0.828917|0.704184|0.826428|0.826415|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.785153|0.699487|0.630675|0.537854|0.711347|0.711329|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.774111|0.672222|0.594035|0.506276|0.689005|0.688986|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.790797|0.673446|0.586424|0.507666|0.695040|0.695023|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.700519|0.569153|0.479277|0.397774|0.599170|0.599149|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.598178|0.449922|0.360559|0.290258|0.494042|0.494019|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.444545|0.257528|0.181270|0.147795|0.361184|0.361168|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|0.938338|0.994062|0.990664|0.992360|0.992360|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|0.826404|0.985936|0.973570|0.979714|0.979714|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|0.699468|0.988418|0.932452|0.959619|0.959619|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.672202|0.989021|0.919542|0.953017|0.953017|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|0.673427|0.991320|0.915070|0.951670|0.951670|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|0.569130|0.988584|0.918718|0.952371|0.952371|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|0.449897|0.988041|0.865581|0.922766|0.922766|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|0.257511|0.992643|0.854403|0.918349|0.918349|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|PRIMARY_OUTPUT|814297|47898|59114|11263708444|47898 / 11263756342|59114 / 873411|
|2|GERMAN CISTEM|PRIMARY_OUTPUT|725447|156784|147964|11263599558|156784 / 11263756342|147964 / 873411|
|3|SNOWBALL GERMAN DIRECT|PRIMARY_OUTPUT|516936|87697|356475|11263668645|87697 / 11263756342|356475 / 873411|
|4|SNOWBALL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|481501|77653|391910|11263678689|77653 / 11263756342|391910 / 873411|
|5|GERMAN LUCENE GERMAN LIGHT STEM FILTER|PRIMARY_OUTPUT|471565|55477|401846|11263700865|55477 / 11263756342|401846 / 873411|
|6|GERMAN LUCENE GERMAN STEM FILTER|PRIMARY_OUTPUT|378734|78723|494677|11263677619|78723 / 11263756342|494677 / 873411|
|7|HUNSPELL GERMAN LUCENE FILTER|PRIMARY_OUTPUT|278093|84679|595318|11263671663|84679 / 11263756342|595318 / 873411|
|8|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|PRIMARY_OUTPUT|132221|21214|741190|11263735128|21214 / 11263756342|741190 / 873411|

</details>

#### `ANY_CANDIDATE` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|0 / 11263756342 (0.000000%)|0 / 873411 (0.000000%)|1.000000|1.000000|1.000000|
|2|HUNSPELL GERMAN LUCENE FILTER|ANY_CANDIDATE|0.665363|60996 / 11263756342 (0.000542%)|584547 / 873411 (66.926911%)|0.635466|0.472281|0.522540|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|0.000000|
|2|HUNSPELL GERMAN LUCENE FILTER|ANY_CANDIDATE|0.825656|0.330731|0.999995|0.665363|0.999943|0.000057|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|1.000000|1.000000|1.000000|1.000000|1.000000|1.000000|
|2|HUNSPELL GERMAN LUCENE FILTER|ANY_CANDIDATE|0.635466|0.472281|0.375782|0.309142|0.522561|0.522540|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL GERMAN LUCENE FILTER|ANY_CANDIDATE|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ANY_CANDIDATE|873411|0|0|11263756342|0 / 11263756342|0 / 873411|
|2|HUNSPELL GERMAN LUCENE FILTER|ANY_CANDIDATE|288864|60996|584547|11263695346|60996 / 11263756342|584547 / 873411|

</details>

#### `ALL_CANDIDATES` ranking

<div class="quality-table quality-table--compact" role="region" aria-label="Compact stemming-quality ranking; scroll horizontally for additional columns" tabindex="0" markdown="1">

| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.999996|97544 / 11263756342 (0.000866%)|0 / 873411 (0.000000%)|0.917983|0.947112|0.948436|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|0.665361|96545 / 11263756342 (0.000857%)|584547 / 873411 (66.926911%)|0.598050|0.458944|0.497855|

</div>

<details class="quality-details" markdown="1"><summary>Classification metrics</summary>

| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.899538|1.000000|0.999991|0.999996|0.999991|0.000009|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|0.749500|0.330731|0.999991|0.665361|0.999940|0.000060|

</details>

<details class="quality-details" markdown="1"><summary>Pair-relation metrics</summary>

| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|0.917983|0.947112|0.978152|0.899538|0.948440|0.948436|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|0.598050|0.458944|0.372338|0.297811|0.497878|0.497855|

</details>

<details class="quality-details" markdown="1"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>

| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |
|---:|---|---|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|n/a|n/a|n/a|n/a|n/a|

</details>

<details class="quality-details" markdown="1"><summary>Raw pair counts</summary>

| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |
|---:|---|---|---:|---:|---:|---:|---:|---:|
|1|Radixor|ALL_CANDIDATES|873411|97544|0|11263658798|97544 / 11263756342|0 / 873411|
|2|HUNSPELL GERMAN LUCENE FILTER|ALL_CANDIDATES|288864|96545|584547|11263659797|96545 / 11263756342|584547 / 873411|

</details>

#### Multi-output analysis

Alternative candidates are capability analyses, not replacements for the deterministic comparison.

| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |
|---|---:|---:|---:|---:|---:|---:|---:|
|Radixor|59114|47898|49646|14978|9.978814%|8|167157|
|HUNSPELL GERMAN LUCENE FILTER|10771|23683|11866|4989|3.323828%|3|155207|

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
- Dictionary language: `DE_DE`
- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`
- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`
- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV

<!-- STEMMING-QUALITY:END -->
