# Benchmark Results

This section contains the published Radixor benchmark reference set. It is intentionally split into
two layers:

- **benchmark reference pages**, which explain methodology, corpora, environment, candidate
  selection, and the English dictionary coverage experiment;
- **language result pages**, which contain the actual same-language accuracy and throughput tables.
- **pairwise quality pages and generated sections**, which publish over-stemming, under-stemming,
  candidate-policy, classification, and partition measurements from one checked result snapshot.

This structure keeps methodology separate from per-language result pages, while preserving all
measured data and the command-class analysis for each Radixor language resource.

## Read This First

Start with [Benchmarking](../benchmarking.md) for the high-level interpretation model. The most
important rule is that speed and exact-root quality must be read together. Many competing stemmers
are intentionally light, minimal, or aggressive; they can be fast because they are not trying to
match dictionary roots with the same precision.

Radixor rows in the refreshed tables use contracted compiled patch tries. Contraction collapses
uniform preferred-command subtrees into accepting leaves, reducing hot lookup depth while preserving
the preferred result measured by the accuracy pass.

## Reference Pages

| Page | Purpose |
| --- | --- |
| [Methodology](reference/methodology.md) | Workload design, normalization, speed metrics, quality metrics, and interpretation rules. |
| [Linguistic quality methodology](reference/linguistic-quality.md) | Gold-standard groups, output policies, pairwise formulas, ranking rules, aggregation, and limitations. |
| [Tested stemmers](reference/tested-stemmers.md) | Versions, upstream attribution, evaluated coverage, adapters, preprocessing, and output capability. |
| [Reproducibility and raw data](reference/reproducibility.md) | Commands, versioned CSV snapshot, checksum, generated artifacts, and unavailable provenance. |
| [Corpora](reference/corpora.md) | Dictionary row counts, complete quality tokens, already-root tokens, changed speed tokens, and timing token counts. |
| [Environment and reports](reference/environment.md) | Hardware, JVM, JMH settings, report files, and badge/report policy. |
| [English dictionary coverage](reference/english-coverage.md) | Quality/speed operating curve for contracted Radixor tries built from 100% down to 10% of English dictionary rows. |
| [Candidate evaluation](reference/candidates.md) | Included benchmark families and evaluated candidates that were skipped. |

## Language Results

Each language page contains:

- the dictionary corpus size,
- the Radixor patch-command distribution,
- exact-root quality metrics,
- throughput metrics,
- interpretation notes for the compared stemmers.

Open [Language Benchmark Pages](languages/index.md) for the complete language list.

## Key Published Result

The English dictionary coverage benchmark shows the current contracted-trie operating curve. With
the full English dictionary, Radixor reaches `97.478%` all-token exactness and `97.197%`
changed-token exactness at `135.8 ns/token`. Even with a deterministic 10% dictionary slice, it
keeps `92.868%` all-token exactness and `76.516%` changed-token exactness at `86.0 ns/token`.

Those figures should not be reduced to a single speed badge. The professional interpretation is a
quality/speed envelope: the amount and quality of dictionary knowledge affect stemming precision,
while contracted tries reduce lookup cost in uniform regions of the compiled graph.

## Quality versus performance

Each language page keeps exact-root accuracy, JMH latency, and pairwise linguistic-quality results in separate tables. No undocumented scalar combines them. The current repository checkout does not contain the dated machine-readable JMH CSV files named by the performance provenance page, so this revision preserves the existing performance tables but does not regenerate a cross-language Pareto frontier from rounded Markdown values. A defensible Pareto analysis requires the original unrounded JMH snapshot on the same hardware and JVM. Readers can still inspect the quality and speed dimensions side by side on every language page.

<!-- STEMMING-QUALITY-OVERVIEW:START -->

## Pairwise Quality Findings

The validated snapshot is a broad multilingual comparison covering the complete 20-language Radixor dictionary universe; 19 languages have existing benchmark pages. The direct ranking below uses only deterministic `PRIMARY_OUTPUT` rows over identical per-language inputs. Candidate-aware rows are intentionally excluded from this claim.

!!! success "Evidence-based primary-output result"
    Radixor achieved the highest balanced accuracy among the evaluated deterministic stemmers for every documented language in both `ALL_WORDS` and `LOWERCASE_GROUPS_ONLY`: **38 wins in 38 language-mode comparisons, with no exact first-place ties**. This statement is limited to the evaluated implementations, versions, dictionaries, adapters, and balanced-accuracy metric; it is not a universal claim about every stemming use case.

### Per-language winner matrix

| Language | Dictionary mode | Winner | Balanced accuracy | Runner-up | Difference | Exact tie | Deterministic stemmers |
|---|---|---|---:|---|---:|---|---:|
|Czech (`CS_CZ`)|ALL_WORDS|Radixor|0.996565|HUNSPELL CZECH LUCENE FILTER|0.142812638|no|3|
|Czech (`CS_CZ`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997139|HUNSPELL CZECH LUCENE FILTER|0.144369049|no|3|
|Danish (`DA_DK`)|ALL_WORDS|Radixor|0.996066|SNOWBALL DANISH LUCENE FILTER|0.058096771|no|3|
|Danish (`DA_DK`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996305|SNOWBALL DANISH DIRECT|0.058230346|no|3|
|Dutch (`NL_NL`)|ALL_WORDS|Radixor|0.988661|SNOWBALL DUTCH DIRECT|0.261574077|no|4|
|Dutch (`NL_NL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.989040|SNOWBALL DUTCH DIRECT|0.258544404|no|4|
|English (`US_UK`)|ALL_WORDS|Radixor|0.965159|ENGLISH LUCENE PORTER COPIED|0.010532535|no|11|
|English (`US_UK`)|LOWERCASE_GROUPS_ONLY|Radixor|0.965820|ENGLISH LUCENE PORTER COPIED|0.010920064|no|11|
|Finnish (`FI_FI`)|ALL_WORDS|Radixor|0.984594|SNOWBALL FINNISH LUCENE FILTER|0.244241861|no|4|
|Finnish (`FI_FI`)|LOWERCASE_GROUPS_ONLY|Radixor|0.988068|SNOWBALL FINNISH DIRECT|0.249668284|no|4|
|French (`FR_FR`)|ALL_WORDS|Radixor|0.956992|SNOWBALL FRENCH DIRECT|0.111730673|no|6|
|French (`FR_FR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.957224|SNOWBALL FRENCH DIRECT|0.111809799|no|6|
|German (`DE_DE`)|ALL_WORDS|Radixor|0.907901|GERMAN CISTEM|0.027131083|no|8|
|German (`DE_DE`)|LOWERCASE_GROUPS_ONLY|Radixor|0.966157|GERMAN CISTEM|0.050868631|no|8|
|Hungarian (`HU_HU`)|ALL_WORDS|Radixor|0.995491|SNOWBALL HUNGARIAN LUCENE FILTER|0.172884951|no|4|
|Hungarian (`HU_HU`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996163|SNOWBALL HUNGARIAN DIRECT|0.174455479|no|4|
|Italian (`IT_IT`)|ALL_WORDS|Radixor|0.996507|SNOWBALL ITALIAN DIRECT|0.130318040|no|4|
|Italian (`IT_IT`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996512|SNOWBALL ITALIAN DIRECT|0.130307087|no|4|
|Norwegian Bokmal (`NB_NO`)|ALL_WORDS|Radixor|0.974783|SNOWBALL NORWEGIAN BOKMAL DIRECT|0.099819340|no|5|
|Norwegian Bokmal (`NB_NO`)|LOWERCASE_GROUPS_ONLY|Radixor|0.975000|SNOWBALL NORWEGIAN BOKMAL DIRECT|0.100008544|no|5|
|Norwegian Nynorsk (`NN_NO`)|ALL_WORDS|Radixor|0.935777|SNOWBALL NORWEGIAN NYNORSK DIRECT|0.076868986|no|3|
|Norwegian Nynorsk (`NN_NO`)|LOWERCASE_GROUPS_ONLY|Radixor|0.935853|SNOWBALL NORWEGIAN NYNORSK DIRECT|0.076816096|no|3|
|Persian (`FA_IR`)|ALL_WORDS|Radixor|0.974922|PERSIAN LUCENE PERSIAN STEM FILTER|0.472751327|no|2|
|Persian (`FA_IR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.974922|PERSIAN LUCENE PERSIAN STEM FILTER|0.472751327|no|2|
|Polish (`PL_PL`)|ALL_WORDS|Radixor|0.990388|POLISH LUCENE MORFOLOGIK FILTER|0.042233990|no|5|
|Polish (`PL_PL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.990579|POLISH LUCENE MORFOLOGIK FILTER|0.042401633|no|5|
|Portuguese (`PT_PT`)|ALL_WORDS|Radixor|0.998502|SNOWBALL PORTUGUESE DIRECT|0.059701750|no|6|
|Portuguese (`PT_PT`)|LOWERCASE_GROUPS_ONLY|Radixor|0.998502|SNOWBALL PORTUGUESE DIRECT|0.059701750|no|6|
|Russian (`RU_RU`)|ALL_WORDS|Radixor|0.989827|SNOWBALL RUSSIAN LUCENE FILTER|0.154951419|no|4|
|Russian (`RU_RU`)|LOWERCASE_GROUPS_ONLY|Radixor|0.989852|SNOWBALL RUSSIAN DIRECT|0.154997931|no|4|
|Spanish (`ES_ES`)|ALL_WORDS|Radixor|0.989295|SNOWBALL SPANISH LUCENE FILTER|0.336680479|no|7|
|Spanish (`ES_ES`)|LOWERCASE_GROUPS_ONLY|Radixor|0.989429|SNOWBALL SPANISH DIRECT|0.336708826|no|7|
|Swedish (`SV_SE`)|ALL_WORDS|Radixor|0.974636|SNOWBALL SWEDISH DIRECT|0.167101450|no|5|
|Swedish (`SV_SE`)|LOWERCASE_GROUPS_ONLY|Radixor|0.974584|SNOWBALL SWEDISH DIRECT|0.166984893|no|5|
|Ukrainian (`UK_UA`)|ALL_WORDS|Radixor|0.995343|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.066574583|no|4|
|Ukrainian (`UK_UA`)|LOWERCASE_GROUPS_ONLY|Radixor|0.995342|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.066590889|no|4|
|Yiddish (`YI`)|ALL_WORDS|Radixor|0.988241|SNOWBALL YIDDISH DIRECT|0.097253207|no|3|
|Yiddish (`YI`)|LOWERCASE_GROUPS_ONLY|Radixor|0.988241|SNOWBALL YIDDISH DIRECT|0.097253207|no|3|

### Secondary-metric trade-offs

Balanced-accuracy leadership does not imply leadership on every error trade-off. The table below lists all **15** deterministic primary-output language-mode-metric cases where a non-Radixor adapter has the best displayed value. Equal values are resolved by the authoritative row ordering and should be read as ties when the unrounded values are equal. Throughput leadership remains in the separate performance tables.

<details class="quality-details" markdown="1"><summary>Non-Radixor secondary-metric leaders</summary>

| Language | Dictionary mode | Metric | Leader | Value |
|---|---|---|---|---:|
|English|ALL_WORDS|Over-stemming percentage|ENGLISH LUCENE POSSESSIVE FILTER|0.000604|
|English|LOWERCASE_GROUPS_ONLY|Over-stemming percentage|ENGLISH LUCENE POSSESSIVE FILTER|0.000653|
|French|ALL_WORDS|Over-stemming percentage|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|0.000177|
|French|LOWERCASE_GROUPS_ONLY|Over-stemming percentage|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|0.000166|
|German|LOWERCASE_GROUPS_ONLY|Over-stemming percentage|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|0.000188|
|Italian|ALL_WORDS|Over-stemming percentage|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|0.000020|
|Italian|LOWERCASE_GROUPS_ONLY|Over-stemming percentage|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|0.000020|
|Persian|ALL_WORDS|Over-stemming percentage|PERSIAN LUCENE PERSIAN STEM FILTER|0.002652|
|Persian|LOWERCASE_GROUPS_ONLY|Over-stemming percentage|PERSIAN LUCENE PERSIAN STEM FILTER|0.002652|
|Portuguese|ALL_WORDS|Over-stemming percentage|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|0.000003|
|Portuguese|LOWERCASE_GROUPS_ONLY|Over-stemming percentage|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|0.000003|
|Spanish|ALL_WORDS|Over-stemming percentage|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|0.000013|
|Spanish|LOWERCASE_GROUPS_ONLY|Over-stemming percentage|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|0.000012|
|Ukrainian|ALL_WORDS|Over-stemming percentage|HUNSPELL UKRAINIAN LUCENE FILTER|0.000783|
|Ukrainian|LOWERCASE_GROUPS_ONLY|Over-stemming percentage|HUNSPELL UKRAINIAN LUCENE FILTER|0.000784|

</details>

### Win, tie, and placement summary

Counts use `PRIMARY_OUTPUT` only and retain each adapter configuration as a separate stemmer except that language-specific Radixor identifiers are combined as Radixor. Coverage is displayed explicitly; unsupported languages are absent, not losses.

<details class="quality-details" markdown="1"><summary>ALL_WORDS placements</summary>

| Stemmer | Evaluated languages | Wins | Exact first-place ties | Top-three placements | Average rank | Median rank |
|---|---:|---:|---:|---:|---:|---:|
|Radixor|19|19|0|19|1.000|1.000|
|CZECH LUCENE CZECH STEM FILTER|1|0|0|1|3.000|3.000|
|ENGLISH LUCENE KSTEM FILTER|1|0|0|0|8.000|8.000|
|ENGLISH LUCENE MINIMAL FILTER|1|0|0|0|9.000|9.000|
|ENGLISH LUCENE PORTER COPIED|1|0|0|1|2.000|2.000|
|ENGLISH LUCENE PORTER FILTER|1|0|0|1|3.000|3.000|
|ENGLISH LUCENE POSSESSIVE FILTER|1|0|0|0|11.000|11.000|
|ENGLISH OPENNLP PORTER|1|0|0|0|4.000|4.000|
|ENGLISH PAICE HUSK LANCASTER|1|0|0|0|7.000|7.000|
|ENGLISH SNOWBALL ORIGINAL PORTER|1|0|0|0|6.000|6.000|
|ENGLISH SNOWBALL PORTER2|1|0|0|0|5.000|5.000|
|FINNISH LUCENE FINNISH LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|FRENCH LUCENE FRENCH LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|1|0|0|0|6.000|6.000|
|GERMAN CISTEM|1|0|0|1|2.000|2.000|
|GERMAN LUCENE GERMAN LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|1|0|0|0|8.000|8.000|
|GERMAN LUCENE GERMAN STEM FILTER|1|0|0|0|6.000|6.000|
|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|HUNSPELL CZECH LUCENE FILTER|1|0|0|1|2.000|2.000|
|HUNSPELL DUTCH LUCENE FILTER|1|0|0|1|3.000|3.000|
|HUNSPELL ENGLISH LUCENE FILTER|1|0|0|0|10.000|10.000|
|HUNSPELL FRENCH LUCENE FILTER|1|0|0|0|4.000|4.000|
|HUNSPELL GERMAN LUCENE FILTER|1|0|0|0|7.000|7.000|
|HUNSPELL POLISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|HUNSPELL SPANISH LUCENE FILTER|1|0|0|0|4.000|4.000|
|HUNSPELL UKRAINIAN LUCENE FILTER|1|0|0|0|4.000|4.000|
|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|1|0|0|0|5.000|5.000|
|PERSIAN LUCENE PERSIAN STEM FILTER|1|0|0|1|2.000|2.000|
|POLISH LUCENE MORFOLOGIK FILTER|1|0|0|1|2.000|2.000|
|POLISH LUCENE STEMPEL DIRECT|1|0|0|0|4.000|4.000|
|POLISH LUCENE STEMPEL FILTER|1|0|0|0|5.000|5.000|
|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|1|0|0|0|6.000|6.000|
|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|1|0|0|0|4.000|4.000|
|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL DANISH DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL DANISH LUCENE FILTER|1|0|0|1|2.000|2.000|
|SNOWBALL DUTCH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL DUTCH LUCENE FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL FINNISH DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL FINNISH LUCENE FILTER|1|0|0|1|2.000|2.000|
|SNOWBALL FRENCH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL FRENCH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL GERMAN DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL GERMAN LUCENE FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL HUNGARIAN DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL HUNGARIAN LUCENE FILTER|1|0|0|1|2.000|2.000|
|SNOWBALL ITALIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ITALIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL NORWEGIAN BOKMAL DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL NORWEGIAN NYNORSK DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL NORWEGIAN NYNORSK LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL PORTUGUESE DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL PORTUGUESE LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL RUSSIAN DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL RUSSIAN LUCENE FILTER|1|0|0|1|2.000|2.000|
|SNOWBALL SPANISH DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL SPANISH LUCENE FILTER|1|0|0|1|2.000|2.000|
|SNOWBALL SWEDISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL SWEDISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL YIDDISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL YIDDISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SPANISH LUCENE SPANISH LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|1|0|0|0|7.000|7.000|
|SPANISH LUCENE SPANISH PLURAL STEM FILTER|1|0|0|0|6.000|6.000|
|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|1|0|0|0|4.000|4.000|
|UKRAINIAN LUCENE MORFOLOGIK FILTER|1|0|0|1|2.000|2.000|
|UKRAINIAN MORFOLOGIK DIRECT|1|0|0|1|3.000|3.000|

</details>

<details class="quality-details" markdown="1"><summary>LOWERCASE_GROUPS_ONLY placements</summary>

| Stemmer | Evaluated languages | Wins | Exact first-place ties | Top-three placements | Average rank | Median rank |
|---|---:|---:|---:|---:|---:|---:|
|Radixor|19|19|0|19|1.000|1.000|
|CZECH LUCENE CZECH STEM FILTER|1|0|0|1|3.000|3.000|
|ENGLISH LUCENE KSTEM FILTER|1|0|0|0|8.000|8.000|
|ENGLISH LUCENE MINIMAL FILTER|1|0|0|0|9.000|9.000|
|ENGLISH LUCENE PORTER COPIED|1|0|0|1|2.000|2.000|
|ENGLISH LUCENE PORTER FILTER|1|0|0|1|3.000|3.000|
|ENGLISH LUCENE POSSESSIVE FILTER|1|0|0|0|11.000|11.000|
|ENGLISH OPENNLP PORTER|1|0|0|0|4.000|4.000|
|ENGLISH PAICE HUSK LANCASTER|1|0|0|0|7.000|7.000|
|ENGLISH SNOWBALL ORIGINAL PORTER|1|0|0|0|6.000|6.000|
|ENGLISH SNOWBALL PORTER2|1|0|0|0|5.000|5.000|
|FINNISH LUCENE FINNISH LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|FRENCH LUCENE FRENCH LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|FRENCH LUCENE FRENCH MINIMAL STEM FILTER|1|0|0|0|6.000|6.000|
|GERMAN CISTEM|1|0|0|1|2.000|2.000|
|GERMAN LUCENE GERMAN LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|GERMAN LUCENE GERMAN MINIMAL STEM FILTER|1|0|0|0|8.000|8.000|
|GERMAN LUCENE GERMAN STEM FILTER|1|0|0|0|6.000|6.000|
|HUNGARIAN LUCENE HUNGARIAN LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|HUNSPELL CZECH LUCENE FILTER|1|0|0|1|2.000|2.000|
|HUNSPELL DUTCH LUCENE FILTER|1|0|0|1|3.000|3.000|
|HUNSPELL ENGLISH LUCENE FILTER|1|0|0|0|10.000|10.000|
|HUNSPELL FRENCH LUCENE FILTER|1|0|0|0|4.000|4.000|
|HUNSPELL GERMAN LUCENE FILTER|1|0|0|0|7.000|7.000|
|HUNSPELL POLISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|HUNSPELL SPANISH LUCENE FILTER|1|0|0|0|4.000|4.000|
|HUNSPELL UKRAINIAN LUCENE FILTER|1|0|0|0|4.000|4.000|
|ITALIAN LUCENE ITALIAN LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|NORWEGIAN BOKMAL LUCENE NORWEGIAN LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|NORWEGIAN BOKMAL LUCENE NORWEGIAN MINIMAL STEM FILTER|1|0|0|0|5.000|5.000|
|PERSIAN LUCENE PERSIAN STEM FILTER|1|0|0|1|2.000|2.000|
|POLISH LUCENE MORFOLOGIK FILTER|1|0|0|1|2.000|2.000|
|POLISH LUCENE STEMPEL DIRECT|1|0|0|0|4.000|4.000|
|POLISH LUCENE STEMPEL FILTER|1|0|0|0|5.000|5.000|
|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|1|0|0|0|6.000|6.000|
|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|1|0|0|0|4.000|4.000|
|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL DANISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL DANISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL DUTCH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL DUTCH LUCENE FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL FINNISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL FINNISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL FRENCH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL FRENCH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL GERMAN DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL GERMAN LUCENE FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL HUNGARIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL HUNGARIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL ITALIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ITALIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL NORWEGIAN BOKMAL DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL NORWEGIAN NYNORSK DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL NORWEGIAN NYNORSK LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL PORTUGUESE DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL PORTUGUESE LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL RUSSIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL RUSSIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL SPANISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL SPANISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL SWEDISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL SWEDISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL YIDDISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL YIDDISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SPANISH LUCENE SPANISH LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|SPANISH LUCENE SPANISH MINIMAL STEM FILTER|1|0|0|0|7.000|7.000|
|SPANISH LUCENE SPANISH PLURAL STEM FILTER|1|0|0|0|6.000|6.000|
|SWEDISH LUCENE SWEDISH LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|SWEDISH LUCENE SWEDISH MINIMAL STEM FILTER|1|0|0|0|4.000|4.000|
|UKRAINIAN LUCENE MORFOLOGIK FILTER|1|0|0|1|2.000|2.000|
|UKRAINIAN MORFOLOGIK DIRECT|1|0|0|1|3.000|3.000|

</details>


### Radixor full-coverage aggregates

These aggregates cover all 19 documented languages. Macro balanced accuracy gives each language equal weight. Micro metrics first sum raw pair counts across languages. Unsupported third-party languages are never inserted as zero results, so this full-coverage table is not presented as a cross-stemmer common-language ranking.

| Dictionary mode | Languages | Macro balanced accuracy | Micro balanced accuracy | Micro precision | Micro recall | Micro F1 |
|---|---:|---:|---:|---:|---:|---:|
|ALL_WORDS|19|0.978929|0.987664|0.975113|0.975328|0.975221|
|LOWERCASE_GROUPS_ONLY|19|0.982354|0.989366|0.975322|0.978734|0.977025|

### Reproducible data

- [Machine-readable quality snapshot](data/stemming-quality.csv)
- SHA-256: `5a93a6ab60e46489737cd649eb1ac48182114b9038f7f20195ab9d1c1fc0dd28`
- [Linguistic quality methodology](reference/linguistic-quality.md)
- [Tested stemmer inventory](reference/tested-stemmers.md)
- [Reproducibility and raw data](reference/reproducibility.md)
- Pearson and Spearman correlation files are generated under `build/reports/stemming-quality/`; they are separated by dictionary mode and output policy. Correlation does not establish metric equivalence.

<!-- STEMMING-QUALITY-OVERVIEW:END -->
