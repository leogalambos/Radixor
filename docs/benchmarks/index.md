# Benchmark Results

This section contains the published Radixor benchmark reference set. It is intentionally split into
two layers:

- **benchmark reference pages**, which explain methodology, corpora, environment, candidate
  selection, multilingual generalization, and the English coverage-speed deep dive;
- **language result pages**, which contain the actual same-language accuracy and throughput tables.
- **pairwise quality pages and generated sections**, which publish over-stemming, under-stemming,
  candidate-policy, classification, and partition measurements from one checked result snapshot.

This structure keeps methodology separate from per-language result pages, while preserving all
measured data and the command-class analysis for each Radixor default model.

## Read This First

Start with [Benchmarking](../benchmarking.md) for the high-level interpretation model. Three
separate dimensions must be read together: pairwise linguistic quality, exact-root agreement, and
runtime. The principal quality benchmark tests whether forms in the same annotated dictionary
group—a morphological family, not a semantic or synonym set—receive the same stem, while forms with
no shared group membership remain separated. It does not require the resulting stem to equal one
prescribed dictionary root; exact-root agreement is reported separately. Many competing stemmers
are intentionally light, minimal, or aggressive, and can be fast because they perform a narrower or
different linguistic transformation.

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
| [Dictionary-family generalization](generalization.md) | All-language, five-split held-out-family results from 10% through 100% Java model training coverage. |
| [Generalization methodology](reference/generalization-methodology.md) | Frozen nested splits, unseen-surface leakage control, formulas, reproduction, and limitations. |
| [English dictionary coverage](reference/english-coverage.md) | Quality/speed operating curve for contracted Radixor tries built from 100% down to 10% of English dictionary rows. |
| [Candidate evaluation](reference/candidates.md) | Included benchmark families and evaluated candidates that were skipped. |

## Language Results

Each language page contains:

- the dictionary corpus size,
- the Radixor patch-command distribution,
- exact-root quality metrics,
- throughput metrics,
- pairwise linguistic-quality metrics showing whether same-group forms share a stem and forms with
  no shared dictionary-group membership remain separated,
- interpretation notes for the compared stemmers.

Open [Language Benchmark Pages](languages/index.md) for the complete language list.

## Key Published Result

The English dictionary coverage benchmark shows the current contracted-trie operating curve. With
the full English dictionary, Radixor reaches `97.478%` all-token exactness and `97.197%`
changed-token exactness at `87.2 ns/token`. Even with a deterministic 10% dictionary slice, it
keeps `92.868%` all-token exactness and `76.516%` changed-token exactness at `51.8 ns/token`.

Those figures should not be reduced to a single speed badge. The professional interpretation is a
quality/speed envelope: the amount and quality of dictionary knowledge affect stemming precision,
while contracted tries reduce lookup cost in uniform regions of the compiled graph.

## Quality versus performance

Each language page keeps exact-root accuracy, JMH latency, and pairwise linguistic-quality results in separate tables. No undocumented scalar combines them. The 2026-08-23 language tables are generated from the current corpus/command report, exact-root and speed JMH reports, and pairwise-quality snapshot produced for this refresh. The Snowball 3.1.0 matrix includes direct Czech, Persian, and Polish stemmers; all published Java stemmers were measured in the same run. Readers should inspect the quality and speed dimensions side by side; no cross-language Pareto ranking is inferred from workloads with different dictionaries and token counts.

### New Snowball 3.1.0 rows

| New direct stemmer | All exact | Changed exact | Root preserved | Speed | Relative to same-language Radixor |
| --- | ---: | ---: | ---: | ---: | ---: |
| Czech | 19.865% | 18.186% | 27.645% | 85.2 ns/token | 1.242× |
| Persian | 3.660% | 0.000% | 100.000% | 301.4 ns/token | 5.746× |
| Polish | 22.315% | 20.225% | 34.078% | 86.1 ns/token | 1.194× |

These rows describe exact agreement with each Radixor model dictionary and the measured direct API workload; they are not a universal linguistic ranking. In this dataset the three new Snowball stemmers are both less exact and slower than their same-language Radixor baseline. Lucene 10.5.0 does not expose the three new algorithms through `SnowballFilter`, so no synthetic Lucene wrapper rows were added.

<!-- STEMMING-QUALITY-OVERVIEW:START -->

## Pairwise Quality Findings

The validated snapshot is a broad multilingual comparison covering the complete 20-language Radixor default-model universe, with one benchmark page per language. The direct ranking below uses only deterministic `PRIMARY_OUTPUT` rows over identical per-language inputs. Candidate-aware rows are intentionally excluded from this claim.

!!! success "Evidence-based primary-output result"
    Radixor achieved the highest balanced accuracy among the evaluated deterministic stemmers for every documented language in both `ALL_WORDS` and `LOWERCASE_GROUPS_ONLY`: **first place in all 40 evaluated language-mode matrices, with no exact first-place ties**. 38 matrices include at least one direct comparator; the two Hebrew modes report Radixor independently because no same-language adapter is configured. This statement is limited to the evaluated implementations, versions, dictionaries, adapters, and balanced-accuracy metric; it is not a universal claim about every stemming use case.

### Per-language winner matrix

| Language | Dictionary mode | Winner | Balanced accuracy | Runner-up | Difference | Exact tie | Deterministic stemmers |
|---|---|---|---:|---|---:|---|---:|
|Czech (`CS_CZ`)|ALL_WORDS|Radixor|0.996617|HUNSPELL CZECH LUCENE FILTER|0.142485045|no|4|
|Czech (`CS_CZ`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997195|HUNSPELL CZECH LUCENE FILTER|0.144045088|no|4|
|Danish (`DA_DK`)|ALL_WORDS|Radixor|0.996243|SNOWBALL DANISH DIRECT|0.053760569|no|3|
|Danish (`DA_DK`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996482|SNOWBALL DANISH DIRECT|0.054099342|no|3|
|Dutch (`NL_NL`)|ALL_WORDS|Radixor|0.988733|SNOWBALL DUTCH DIRECT|0.261639748|no|4|
|Dutch (`NL_NL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.989114|SNOWBALL DUTCH DIRECT|0.258605347|no|4|
|English (`US_UK`)|ALL_WORDS|Radixor|0.965537|ENGLISH LUCENE PORTER COPIED|0.010741250|no|11|
|English (`US_UK`)|LOWERCASE_GROUPS_ONLY|Radixor|0.966202|ENGLISH LUCENE PORTER COPIED|0.011138557|no|11|
|Finnish (`FI_FI`)|ALL_WORDS|Radixor|0.984838|SNOWBALL FINNISH LUCENE FILTER|0.244558928|no|4|
|Finnish (`FI_FI`)|LOWERCASE_GROUPS_ONLY|Radixor|0.988242|SNOWBALL FINNISH DIRECT|0.249699076|no|4|
|French (`FR_FR`)|ALL_WORDS|Radixor|0.958627|SNOWBALL FRENCH DIRECT|0.109964908|no|6|
|French (`FR_FR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.958856|SNOWBALL FRENCH DIRECT|0.110030565|no|6|
|German (`DE_DE`)|ALL_WORDS|Radixor|0.910445|GERMAN CISTEM|0.031918024|no|8|
|German (`DE_DE`)|LOWERCASE_GROUPS_ONLY|Radixor|0.966959|GERMAN CISTEM|0.052231588|no|8|
|Hebrew (`HE_IL`)|ALL_WORDS|Radixor|0.986253|n/a|n/a|no|1|
|Hebrew (`HE_IL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.986253|n/a|n/a|no|1|
|Hungarian (`HU_HU`)|ALL_WORDS|Radixor|0.995555|SNOWBALL HUNGARIAN LUCENE FILTER|0.172591951|no|4|
|Hungarian (`HU_HU`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996227|SNOWBALL HUNGARIAN DIRECT|0.174150583|no|4|
|Italian (`IT_IT`)|ALL_WORDS|Radixor|0.996651|SNOWBALL ITALIAN DIRECT|0.130360651|no|4|
|Italian (`IT_IT`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996656|SNOWBALL ITALIAN DIRECT|0.130349693|no|4|
|Norwegian Bokmal (`NB_NO`)|ALL_WORDS|Radixor|0.976021|SNOWBALL NORWEGIAN BOKMAL DIRECT|0.101762107|no|5|
|Norwegian Bokmal (`NB_NO`)|LOWERCASE_GROUPS_ONLY|Radixor|0.976240|SNOWBALL NORWEGIAN BOKMAL DIRECT|0.101954266|no|5|
|Norwegian Nynorsk (`NN_NO`)|ALL_WORDS|Radixor|0.950991|SNOWBALL NORWEGIAN NYNORSK DIRECT|0.082896791|no|3|
|Norwegian Nynorsk (`NN_NO`)|LOWERCASE_GROUPS_ONLY|Radixor|0.951104|SNOWBALL NORWEGIAN NYNORSK DIRECT|0.082851757|no|3|
|Persian (`FA_IR`)|ALL_WORDS|Radixor|0.975610|SNOWBALL PERSIAN DIRECT|0.440486794|no|3|
|Persian (`FA_IR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.975610|SNOWBALL PERSIAN DIRECT|0.440486794|no|3|
|Polish (`PL_PL`)|ALL_WORDS|Radixor|0.991105|POLISH LUCENE MORFOLOGIK FILTER|0.042712804|no|6|
|Polish (`PL_PL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.991301|POLISH LUCENE MORFOLOGIK FILTER|0.042883749|no|6|
|Portuguese (`PT_PT`)|ALL_WORDS|Radixor|0.998542|SNOWBALL PORTUGUESE DIRECT|0.059619854|no|6|
|Portuguese (`PT_PT`)|LOWERCASE_GROUPS_ONLY|Radixor|0.998542|SNOWBALL PORTUGUESE DIRECT|0.059619854|no|6|
|Russian (`RU_RU`)|ALL_WORDS|Radixor|0.990188|SNOWBALL RUSSIAN LUCENE FILTER|0.155623602|no|4|
|Russian (`RU_RU`)|LOWERCASE_GROUPS_ONLY|Radixor|0.990213|SNOWBALL RUSSIAN DIRECT|0.155670422|no|4|
|Spanish (`ES_ES`)|ALL_WORDS|Radixor|0.989448|SNOWBALL SPANISH LUCENE FILTER|0.337009985|no|7|
|Spanish (`ES_ES`)|LOWERCASE_GROUPS_ONLY|Radixor|0.989580|SNOWBALL SPANISH DIRECT|0.337037572|no|7|
|Swedish (`SV_SE`)|ALL_WORDS|Radixor|0.977619|SNOWBALL SWEDISH DIRECT|0.169075635|no|5|
|Swedish (`SV_SE`)|LOWERCASE_GROUPS_ONLY|Radixor|0.977573|SNOWBALL SWEDISH DIRECT|0.168961385|no|5|
|Ukrainian (`UK_UA`)|ALL_WORDS|Radixor|0.995816|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.066909929|no|4|
|Ukrainian (`UK_UA`)|LOWERCASE_GROUPS_ONLY|Radixor|0.995815|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.066926372|no|4|
|Yiddish (`YI`)|ALL_WORDS|Radixor|0.989079|SNOWBALL YIDDISH DIRECT|0.097960961|no|3|
|Yiddish (`YI`)|LOWERCASE_GROUPS_ONLY|Radixor|0.989079|SNOWBALL YIDDISH DIRECT|0.097960961|no|3|

### Secondary-metric trade-offs

Balanced-accuracy leadership does not imply leadership on every error trade-off. The table below lists all **0** deterministic primary-output language-mode-metric cases where a non-Radixor adapter has the best displayed value. Equal values are resolved by the authoritative row ordering and should be read as ties when the unrounded values are equal. Throughput leadership remains in the separate performance tables.

<details class="quality-details" markdown="1"><summary>Non-Radixor secondary-metric leaders</summary>

| Language | Dictionary mode | Metric | Leader | Value |
|---|---|---|---|---:|

</details>

### Win, tie, and placement summary

Counts use `PRIMARY_OUTPUT` only and retain each adapter configuration as a separate stemmer except that language-specific Radixor identifiers are combined as Radixor. Coverage is displayed explicitly; unsupported languages are absent, not losses.

<details class="quality-details" markdown="1"><summary>ALL_WORDS placements</summary>

| Stemmer | Evaluated languages | Wins | Exact first-place ties | Top-three placements | Average rank | Median rank |
|---|---:|---:|---:|---:|---:|---:|
|Radixor|20|20|0|20|1.000|1.000|
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
|PERSIAN LUCENE PERSIAN STEM FILTER|1|0|0|1|3.000|3.000|
|POLISH LUCENE MORFOLOGIK FILTER|1|0|0|1|2.000|2.000|
|POLISH LUCENE STEMPEL DIRECT|1|0|0|0|4.000|4.000|
|POLISH LUCENE STEMPEL FILTER|1|0|0|0|5.000|5.000|
|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|1|0|0|0|6.000|6.000|
|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|1|0|0|0|4.000|4.000|
|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL CZECH DIRECT|1|0|0|0|4.000|4.000|
|SNOWBALL DANISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL DANISH LUCENE FILTER|1|0|0|1|3.000|3.000|
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
|SNOWBALL PERSIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL POLISH DIRECT|1|0|0|0|6.000|6.000|
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
|Radixor|20|20|0|20|1.000|1.000|
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
|PERSIAN LUCENE PERSIAN STEM FILTER|1|0|0|1|3.000|3.000|
|POLISH LUCENE MORFOLOGIK FILTER|1|0|0|1|2.000|2.000|
|POLISH LUCENE STEMPEL DIRECT|1|0|0|0|4.000|4.000|
|POLISH LUCENE STEMPEL FILTER|1|0|0|0|5.000|5.000|
|PORTUGUESE LUCENE PORTUGUESE LIGHT STEM FILTER|1|0|0|0|5.000|5.000|
|PORTUGUESE LUCENE PORTUGUESE MINIMAL STEM FILTER|1|0|0|0|6.000|6.000|
|PORTUGUESE LUCENE PORTUGUESE STEM FILTER|1|0|0|0|4.000|4.000|
|RUSSIAN LUCENE RUSSIAN LIGHT STEM FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL CZECH DIRECT|1|0|0|0|4.000|4.000|
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
|SNOWBALL PERSIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL POLISH DIRECT|1|0|0|0|6.000|6.000|
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

These aggregates cover all 20 documented languages. Macro balanced accuracy gives each language equal weight. Micro metrics first sum raw pair counts across languages. Unsupported third-party languages are never inserted as zero results, so this full-coverage table is not presented as a cross-stemmer common-language ranking.

| Dictionary mode | Languages | Macro balanced accuracy | Micro balanced accuracy | Micro precision | Micro recall | Micro F1 |
|---|---:|---:|---:|---:|---:|---:|
|ALL_WORDS|20|0.980696|0.987977|0.999988|0.975953|0.987824|
|LOWERCASE_GROUPS_ONLY|20|0.983862|0.989614|0.999992|0.979228|0.989501|

### Reproducible data

- [Machine-readable quality snapshot](data/stemming-quality.csv)
- SHA-256: `f15f8e653022e0333955b8b82f42944aa1c5a14a5ce54e628bb1a9c9aed42132`
- [Linguistic quality methodology](reference/linguistic-quality.md)
- [Tested stemmer inventory](reference/tested-stemmers.md)
- [Reproducibility and raw data](reference/reproducibility.md)
- Pearson and Spearman correlation files are generated under `build/reports/stemming-quality/`; they are separated by dictionary mode and output policy. Correlation does not establish metric equivalence.

<!-- STEMMING-QUALITY-OVERVIEW:END -->
