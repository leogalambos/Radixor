# Benchmark Results

This section contains the published Radixor benchmark reference set. It is intentionally split into
three layers:

- **benchmark reference pages**, which explain methodology, corpora, environment, candidate
  selection, multilingual generalization, and the English coverage-speed deep dive;
- **language result pages**, which contain the actual same-language accuracy and throughput tables;
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
| [Edit-cost sensitivity](edit-cost-sensitivity.md) | Language-specific edit-cost recommendations, exact command-equivalence classes, trie structure, and generalization associations. |
| [Edit-cost methodology](reference/edit-cost-methodology.md) | Normalized cost grid, exact equivalence verification, frozen splits, outcomes, analysis rules, and reproduction. |
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
- for the historical 20-language cohort only, the separately retained edit-cost and dictionary-knowledge analysis,
- interpretation notes for the compared stemmers.

Open [Language Benchmark Pages](languages/index.md) for the complete language list.

## Key Published Result

The English dictionary coverage benchmark shows the current contracted-trie operating curve. With
the full English dictionary, Radixor reaches `97.668%` all-token exactness and `98.110%`
changed-token exactness at `107.1 ns/token`. Even with a deterministic 10% dictionary slice, it
keeps `93.057%` all-token exactness and `77.327%` changed-token exactness at `78.1 ns/token`.

Those figures should not be reduced to a single speed badge. The professional interpretation is a
quality/speed envelope: the amount and quality of dictionary knowledge affect stemming precision,
while contracted tries reduce lookup cost in uniform regions of the compiled graph.

## Quality versus performance

Each language page keeps exact-root accuracy, JMH latency, and pairwise linguistic-quality results in separate tables. No undocumented scalar combines them. The 2026-09-10 Java tables are generated from the published corpus/command, exact-root, and speed reports; the richer pairwise-quality sections on the original 20 pages remain bound to their separately frozen snapshot. The Snowball 3.1.0 rows appear only where the implementation exactly matches the model language. Readers should inspect quality and speed side by side; no cross-language Pareto ranking is inferred from workloads with different dictionaries and token counts.

### New Snowball 3.1.0 rows

| New direct stemmer | All exact | Changed exact | Root preserved | Speed | Relative to same-language Radixor |
| --- | ---: | ---: | ---: | ---: | ---: |
| Czech | 19.865% | 18.186% | 27.645% | 85.2 ns/token | 1.242× |
| Persian | 3.660% | 0.000% | 100.000% | 301.4 ns/token | 5.746× |
| Polish | 22.315% | 20.225% | 34.078% | 86.1 ns/token | 1.194× |

These rows describe exact agreement with each Radixor model dictionary and the measured direct API workload; they are not a universal linguistic ranking. In this dataset the three new Snowball stemmers are both less exact and slower than their same-language Radixor baseline. Lucene 10.5.0 does not expose the three new algorithms through `SnowballFilter`, so no synthetic Lucene wrapper rows were added.

<!-- STEMMING-QUALITY-OVERVIEW:START -->

## Pairwise Quality Findings

The validated snapshot is a broad multilingual comparison covering the complete 143-language Radixor default-model universe, with one benchmark page per language. The direct ranking below uses only deterministic `PRIMARY_OUTPUT` rows over identical per-language inputs. Candidate-aware rows are intentionally excluded from this claim.

!!! success "Evidence-based primary-output result"
    Radixor achieved the highest balanced accuracy among the evaluated deterministic stemmers for every documented language in both `ALL_WORDS` and `LOWERCASE_GROUPS_ONLY`: **first place in all 282 evaluated language-mode matrices, with no exact first-place ties**. 60 matrices include at least one direct comparator; the two Hebrew modes report Radixor independently because no same-language adapter is configured. This statement is limited to the evaluated implementations, versions, dictionaries, adapters, and balanced-accuracy metric; it is not a universal claim about every stemming use case.

### Per-language winner matrix

| Language | Dictionary mode | Winner | Balanced accuracy | Runner-up | Difference | Exact tie | Deterministic stemmers |
|---|---|---|---:|---|---:|---|---:|
|Adyghe (`ADY`)|ALL_WORDS|Radixor|0.999051|n/a|n/a|no|1|
|Adyghe (`ADY`)|LOWERCASE_GROUPS_ONLY|Radixor|0.999051|n/a|n/a|no|1|
|Afrikaans (`AF_ZA`)|ALL_WORDS|Radixor|0.982452|n/a|n/a|no|1|
|Afrikaans (`AF_ZA`)|LOWERCASE_GROUPS_ONLY|Radixor|0.982452|n/a|n/a|no|1|
|Aimele (`AIL`)|ALL_WORDS|Radixor|0.965734|n/a|n/a|no|1|
|Aimele (`AIL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.965734|n/a|n/a|no|1|
|Akan (`AK`)|ALL_WORDS|Radixor|0.997522|n/a|n/a|no|1|
|Akan (`AK`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997522|n/a|n/a|no|1|
|Albanian (`SQ_AL`)|ALL_WORDS|Radixor|0.996960|n/a|n/a|no|1|
|Albanian (`SQ_AL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996960|n/a|n/a|no|1|
|Alsatian (`GSW`)|ALL_WORDS|Radixor|0.995913|n/a|n/a|no|1|
|Alsatian (`GSW`)|LOWERCASE_GROUPS_ONLY|Radixor|0.995913|n/a|n/a|no|1|
|Amharic (`AM_ET`)|ALL_WORDS|Radixor|0.980052|n/a|n/a|no|1|
|Amharic (`AM_ET`)|LOWERCASE_GROUPS_ONLY|Radixor|0.980052|n/a|n/a|no|1|
|Amuzgo (`AZG`)|ALL_WORDS|Radixor|0.994697|n/a|n/a|no|1|
|Amuzgo (`AZG`)|LOWERCASE_GROUPS_ONLY|Radixor|0.994697|n/a|n/a|no|1|
|Ancient Greek (`GRC`)|ALL_WORDS|Radixor|0.995540|n/a|n/a|no|1|
|Ancient Greek (`GRC`)|LOWERCASE_GROUPS_ONLY|Radixor|0.995540|n/a|n/a|no|1|
|Anglo-Norman (`XNO`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Anglo-Norman (`XNO`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Arabic (`AR`)|ALL_WORDS|Radixor|0.987101|SNOWBALL ARABIC DIRECT|0.315268705|no|3|
|Arabic (`AR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.987101|SNOWBALL ARABIC DIRECT|0.315268705|no|3|
|Armenian (`HY_AM`)|ALL_WORDS|Radixor|0.993984|SNOWBALL ARMENIAN DIRECT|0.427825813|no|3|
|Armenian (`HY_AM`)|LOWERCASE_GROUPS_ONLY|Radixor|0.993984|SNOWBALL ARMENIAN DIRECT|0.427825813|no|3|
|Ashaninka (`CNI`)|ALL_WORDS|Radixor|0.998172|n/a|n/a|no|1|
|Ashaninka (`CNI`)|LOWERCASE_GROUPS_ONLY|Radixor|0.998172|n/a|n/a|no|1|
|Assamese (`AS_IN`)|ALL_WORDS|Radixor|0.991853|n/a|n/a|no|1|
|Assamese (`AS_IN`)|LOWERCASE_GROUPS_ONLY|Radixor|0.991853|n/a|n/a|no|1|
|Asturian (`AST`)|ALL_WORDS|Radixor|0.994745|n/a|n/a|no|1|
|Asturian (`AST`)|LOWERCASE_GROUPS_ONLY|Radixor|0.994745|n/a|n/a|no|1|
|Aymara (`AYM`)|ALL_WORDS|Radixor|0.996564|n/a|n/a|no|1|
|Aymara (`AYM`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996564|n/a|n/a|no|1|
|Azerbaijani (`AZ_AZ`)|ALL_WORDS|Radixor|0.999948|n/a|n/a|no|1|
|Azerbaijani (`AZ_AZ`)|LOWERCASE_GROUPS_ONLY|Radixor|0.999948|n/a|n/a|no|1|
|Bashkir (`BAK`)|ALL_WORDS|Radixor|0.998782|n/a|n/a|no|1|
|Bashkir (`BAK`)|LOWERCASE_GROUPS_ONLY|Radixor|0.998782|n/a|n/a|no|1|
|Belarusian (`BE_BY`)|ALL_WORDS|Radixor|0.996350|n/a|n/a|no|1|
|Belarusian (`BE_BY`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996350|n/a|n/a|no|1|
|Bengali (`BN_BD`)|ALL_WORDS|Radixor|0.997025|n/a|n/a|no|1|
|Bengali (`BN_BD`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997025|n/a|n/a|no|1|
|Bininj Kun-wok (`GUP`)|ALL_WORDS|Radixor|0.997625|n/a|n/a|no|1|
|Bininj Kun-wok (`GUP`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997625|n/a|n/a|no|1|
|Braj (`BRA`)|ALL_WORDS|Radixor|0.989171|n/a|n/a|no|1|
|Braj (`BRA`)|LOWERCASE_GROUPS_ONLY|Radixor|0.989171|n/a|n/a|no|1|
|Breton (`BRE`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Breton (`BRE`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Bulgarian (`BG_BG`)|ALL_WORDS|Radixor|0.998003|n/a|n/a|no|1|
|Bulgarian (`BG_BG`)|LOWERCASE_GROUPS_ONLY|Radixor|0.998003|n/a|n/a|no|1|
|Catalan (`CA_ES`)|ALL_WORDS|Radixor|0.987704|SNOWBALL CATALAN DIRECT|0.076784766|no|3|
|Catalan (`CA_ES`)|LOWERCASE_GROUPS_ONLY|Radixor|0.987704|SNOWBALL CATALAN DIRECT|0.076784766|no|3|
|Cebuano (`CEB`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Cebuano (`CEB`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Chichewa (`NY_MW`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Chichewa (`NY_MW`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Chichicapan Zapotec (`ZPV`)|ALL_WORDS|Radixor|0.992863|n/a|n/a|no|1|
|Chichicapan Zapotec (`ZPV`)|LOWERCASE_GROUPS_ONLY|Radixor|0.992863|n/a|n/a|no|1|
|Chukchi (`CKT`)|ALL_WORDS|Radixor|0.990506|n/a|n/a|no|1|
|Chukchi (`CKT`)|LOWERCASE_GROUPS_ONLY|Radixor|0.990506|n/a|n/a|no|1|
|Church Slavonic (`CHU`)|ALL_WORDS|Radixor|0.918731|n/a|n/a|no|1|
|Church Slavonic (`CHU`)|LOWERCASE_GROUPS_ONLY|Radixor|0.918731|n/a|n/a|no|1|
|Classical Armenian (`XCL`)|ALL_WORDS|Radixor|0.949840|n/a|n/a|no|1|
|Classical Armenian (`XCL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.949840|n/a|n/a|no|1|
|Classical Syriac (`SYC`)|ALL_WORDS|Radixor|0.993956|n/a|n/a|no|1|
|Classical Syriac (`SYC`)|LOWERCASE_GROUPS_ONLY|Radixor|0.993956|n/a|n/a|no|1|
|Congo Swahili (`SWC`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Congo Swahili (`SWC`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Copala Triqui (`CPA`)|ALL_WORDS|Radixor|0.895664|n/a|n/a|no|1|
|Copala Triqui (`CPA`)|LOWERCASE_GROUPS_ONLY|Radixor|0.895664|n/a|n/a|no|1|
|Cornish (`COR`)|ALL_WORDS|Radixor|0.997378|n/a|n/a|no|1|
|Cornish (`COR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997378|n/a|n/a|no|1|
|Cree (`CRE`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Cree (`CRE`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Crimean Tatar (`CRH`)|ALL_WORDS|Radixor|0.999524|n/a|n/a|no|1|
|Crimean Tatar (`CRH`)|LOWERCASE_GROUPS_ONLY|Radixor|0.999524|n/a|n/a|no|1|
|Czech (`CS_CZ`)|ALL_WORDS|Radixor|0.996617|HUNSPELL CZECH LUCENE FILTER|0.142485045|no|4|
|Czech (`CS_CZ`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997195|HUNSPELL CZECH LUCENE FILTER|0.144045088|no|4|
|Dakota (`DAK`)|ALL_WORDS|Radixor|0.994385|n/a|n/a|no|1|
|Dakota (`DAK`)|LOWERCASE_GROUPS_ONLY|Radixor|0.994385|n/a|n/a|no|1|
|Danish (`DA_DK`)|ALL_WORDS|Radixor|0.996243|SNOWBALL DANISH DIRECT|0.053760569|no|3|
|Danish (`DA_DK`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996482|SNOWBALL DANISH DIRECT|0.054099342|no|3|
|Dutch (`NL_NL`)|ALL_WORDS|Radixor|0.988733|SNOWBALL DUTCH DIRECT|0.261639748|no|4|
|Dutch (`NL_NL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.989114|SNOWBALL DUTCH DIRECT|0.258605347|no|4|
|Eastern Chatino (`CLY`)|ALL_WORDS|Radixor|0.924863|n/a|n/a|no|1|
|Eastern Chatino (`CLY`)|LOWERCASE_GROUPS_ONLY|Radixor|0.924863|n/a|n/a|no|1|
|Egyptian Arabic (`ARZ`)|ALL_WORDS|Radixor|0.978579|n/a|n/a|no|1|
|Egyptian Arabic (`ARZ`)|LOWERCASE_GROUPS_ONLY|Radixor|0.978579|n/a|n/a|no|1|
|English (`US_UK`)|ALL_WORDS|Radixor|0.976120|ENGLISH LUCENE PORTER COPIED|0.010985401|no|11|
|English (`US_UK`)|LOWERCASE_GROUPS_ONLY|Radixor|0.976863|ENGLISH LUCENE PORTER COPIED|0.011393230|no|11|
|Estonian (`ET_EE`)|ALL_WORDS|Radixor|0.997030|SNOWBALL ESTONIAN DIRECT|0.255774903|no|3|
|Estonian (`ET_EE`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997030|SNOWBALL ESTONIAN DIRECT|0.255774903|no|3|
|Evenki (`EVN`)|ALL_WORDS|Radixor|0.996169|n/a|n/a|no|1|
|Evenki (`EVN`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996169|n/a|n/a|no|1|
|Faroese (`FO_FO`)|ALL_WORDS|Radixor|0.979257|n/a|n/a|no|1|
|Faroese (`FO_FO`)|LOWERCASE_GROUPS_ONLY|Radixor|0.979257|n/a|n/a|no|1|
|Finnish (`FI_FI`)|ALL_WORDS|Radixor|0.984838|SNOWBALL FINNISH LUCENE FILTER|0.244558928|no|4|
|Finnish (`FI_FI`)|LOWERCASE_GROUPS_ONLY|Radixor|0.988242|SNOWBALL FINNISH DIRECT|0.249699076|no|4|
|French (`FR_FR`)|ALL_WORDS|Radixor|0.958627|SNOWBALL FRENCH DIRECT|0.109964908|no|6|
|French (`FR_FR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.958856|SNOWBALL FRENCH DIRECT|0.110030565|no|6|
|Friulian (`FUR`)|ALL_WORDS|Radixor|0.997938|n/a|n/a|no|1|
|Friulian (`FUR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997938|n/a|n/a|no|1|
|Ga (`GAA`)|ALL_WORDS|Radixor|0.997872|n/a|n/a|no|1|
|Ga (`GAA`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997872|n/a|n/a|no|1|
|Galolen (`GAL`)|ALL_WORDS|Radixor|0.983508|n/a|n/a|no|1|
|Galolen (`GAL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.983508|n/a|n/a|no|1|
|German (`DE_DE`)|ALL_WORDS|Radixor|0.910445|GERMAN CISTEM|0.031918024|no|8|
|German (`DE_DE`)|LOWERCASE_GROUPS_ONLY|Radixor|0.966959|GERMAN CISTEM|0.052231588|no|8|
|Gothic (`GOT`)|ALL_WORDS|Radixor|0.981471|n/a|n/a|no|1|
|Gothic (`GOT`)|LOWERCASE_GROUPS_ONLY|Radixor|0.981471|n/a|n/a|no|1|
|Greek (`EL_GR`)|ALL_WORDS|Radixor|0.978296|SNOWBALL GREEK DIRECT|0.106884319|no|3|
|Greek (`EL_GR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.978296|SNOWBALL GREEK DIRECT|0.106884319|no|3|
|Gulf Arabic (`AFB`)|ALL_WORDS|Radixor|0.986901|n/a|n/a|no|1|
|Gulf Arabic (`AFB`)|LOWERCASE_GROUPS_ONLY|Radixor|0.986901|n/a|n/a|no|1|
|Haida (`HAI`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Haida (`HAI`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Hebrew (`HE_IL`)|ALL_WORDS|Radixor|0.986253|n/a|n/a|no|1|
|Hebrew (`HE_IL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.986253|n/a|n/a|no|1|
|Hiligaynon (`HIL`)|ALL_WORDS|Radixor|0.985981|n/a|n/a|no|1|
|Hiligaynon (`HIL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.985981|n/a|n/a|no|1|
|Hsilimo (`HSI`)|ALL_WORDS|Radixor|0.996914|n/a|n/a|no|1|
|Hsilimo (`HSI`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996914|n/a|n/a|no|1|
|Hungarian (`HU_HU`)|ALL_WORDS|Radixor|0.995555|SNOWBALL HUNGARIAN LUCENE FILTER|0.172591951|no|4|
|Hungarian (`HU_HU`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996227|SNOWBALL HUNGARIAN DIRECT|0.174150583|no|4|
|Icelandic (`IS_IS`)|ALL_WORDS|Radixor|0.981831|n/a|n/a|no|1|
|Icelandic (`IS_IS`)|LOWERCASE_GROUPS_ONLY|Radixor|0.981831|n/a|n/a|no|1|
|Indonesian (`ID_ID`)|ALL_WORDS|Radixor|0.999914|SNOWBALL INDONESIAN DIRECT|0.128496605|no|3|
|Indonesian (`ID_ID`)|LOWERCASE_GROUPS_ONLY|Radixor|0.999914|SNOWBALL INDONESIAN DIRECT|0.128496605|no|3|
|Ingrian (`IZH`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Ingrian (`IZH`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Irish (`GA_IE`)|ALL_WORDS|Radixor|0.975677|SNOWBALL IRISH DIRECT|0.462889616|no|3|
|Irish (`GA_IE`)|LOWERCASE_GROUPS_ONLY|Radixor|0.975677|SNOWBALL IRISH DIRECT|0.462889616|no|3|
|Italian (`IT_IT`)|ALL_WORDS|Radixor|0.996651|SNOWBALL ITALIAN DIRECT|0.130360651|no|4|
|Italian (`IT_IT`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996656|SNOWBALL ITALIAN DIRECT|0.130349693|no|4|
|Itelmen (`ITL`)|ALL_WORDS|Radixor|0.999389|n/a|n/a|no|1|
|Itelmen (`ITL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.999389|n/a|n/a|no|1|
|Japanese (`JA_JP`)|ALL_WORDS|Radixor|0.919503|n/a|n/a|no|1|
|Japanese (`JA_JP`)|LOWERCASE_GROUPS_ONLY|Radixor|0.919503|n/a|n/a|no|1|
|Kabardian (`KBD`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Kabardian (`KBD`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Kalaallisut (`KL_GL`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Kalaallisut (`KL_GL`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Kannada (`KN_IN`)|ALL_WORDS|Radixor|0.898695|n/a|n/a|no|1|
|Kannada (`KN_IN`)|LOWERCASE_GROUPS_ONLY|Radixor|0.898695|n/a|n/a|no|1|
|Karelian (`KRL`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Karelian (`KRL`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Kashubian (`CSB`)|ALL_WORDS|Radixor|0.995801|n/a|n/a|no|1|
|Kashubian (`CSB`)|LOWERCASE_GROUPS_ONLY|Radixor|0.995801|n/a|n/a|no|1|
|Kazakh (`KK_KZ`)|ALL_WORDS|Radixor|0.998095|n/a|n/a|no|1|
|Kazakh (`KK_KZ`)|LOWERCASE_GROUPS_ONLY|Radixor|0.998095|n/a|n/a|no|1|
|Khakas (`KJH`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Khakas (`KJH`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Khaling (`KLR`)|ALL_WORDS|Radixor|0.944247|n/a|n/a|no|1|
|Khaling (`KLR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.944247|n/a|n/a|no|1|
|Kodi (`KOD`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Kodi (`KOD`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Kongo (`KON`)|ALL_WORDS|Radixor|0.998208|n/a|n/a|no|1|
|Kongo (`KON`)|LOWERCASE_GROUPS_ONLY|Radixor|0.998208|n/a|n/a|no|1|
|Kyrgyz (`KY_KG`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Kyrgyz (`KY_KG`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Ladin (`LLD`)|ALL_WORDS|Radixor|0.997844|n/a|n/a|no|1|
|Ladin (`LLD`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997844|n/a|n/a|no|1|
|Latin (`LA`)|ALL_WORDS|Radixor|0.963898|n/a|n/a|no|1|
|Latin (`LA`)|LOWERCASE_GROUPS_ONLY|Radixor|0.963898|n/a|n/a|no|1|
|Latvian (`LV_LV`)|ALL_WORDS|Radixor|0.941147|n/a|n/a|no|1|
|Latvian (`LV_LV`)|LOWERCASE_GROUPS_ONLY|Radixor|0.941147|n/a|n/a|no|1|
|Lingala (`LIN`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Lingala (`LIN`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Lithuanian (`LT_LT`)|ALL_WORDS|Radixor|0.998532|SNOWBALL LITHUANIAN DIRECT|0.212439399|no|3|
|Lithuanian (`LT_LT`)|LOWERCASE_GROUPS_ONLY|Radixor|0.998532|SNOWBALL LITHUANIAN DIRECT|0.212439399|no|3|
|Livonian (`LIV`)|ALL_WORDS|Radixor|0.997482|n/a|n/a|no|1|
|Livonian (`LIV`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997482|n/a|n/a|no|1|
|Low German (`NDS`)|ALL_WORDS|Radixor|0.980207|n/a|n/a|no|1|
|Low German (`NDS`)|LOWERCASE_GROUPS_ONLY|Radixor|0.980207|n/a|n/a|no|1|
|Lower Sorbian (`DSB`)|ALL_WORDS|Radixor|0.992702|n/a|n/a|no|1|
|Lower Sorbian (`DSB`)|LOWERCASE_GROUPS_ONLY|Radixor|0.992702|n/a|n/a|no|1|
|Luganda (`LG_UG`)|ALL_WORDS|Radixor|0.994127|n/a|n/a|no|1|
|Luganda (`LG_UG`)|LOWERCASE_GROUPS_ONLY|Radixor|0.994127|n/a|n/a|no|1|
|Macedonian (`MK_MK`)|ALL_WORDS|Radixor|0.996059|n/a|n/a|no|1|
|Macedonian (`MK_MK`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996059|n/a|n/a|no|1|
|Magahi (`MAG`)|ALL_WORDS|Radixor|0.986749|n/a|n/a|no|1|
|Magahi (`MAG`)|LOWERCASE_GROUPS_ONLY|Radixor|0.986749|n/a|n/a|no|1|
|Malagasy (`MG_MG`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Malagasy (`MG_MG`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Maltese (`MT_MT`)|ALL_WORDS|Radixor|0.996750|n/a|n/a|no|1|
|Maltese (`MT_MT`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996750|n/a|n/a|no|1|
|Manx (`GV_IM`)|ALL_WORDS|n/a|n/a|n/a|n/a|n/a|0|
|Manx (`GV_IM`)|LOWERCASE_GROUPS_ONLY|n/a|n/a|n/a|n/a|n/a|0|
|Maori (`MI_NZ`)|ALL_WORDS|Radixor|0.995192|n/a|n/a|no|1|
|Maori (`MI_NZ`)|LOWERCASE_GROUPS_ONLY|Radixor|0.995192|n/a|n/a|no|1|
|Mapudungun (`ARN`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Mapudungun (`ARN`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Middle French (`FRM`)|ALL_WORDS|Radixor|0.996418|n/a|n/a|no|1|
|Middle French (`FRM`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996418|n/a|n/a|no|1|
|Middle High German (`GMH`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Middle High German (`GMH`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Middle Low German (`GML`)|ALL_WORDS|Radixor|0.978543|n/a|n/a|no|1|
|Middle Low German (`GML`)|LOWERCASE_GROUPS_ONLY|Radixor|0.978543|n/a|n/a|no|1|
|Mongolian (`MN_MN`)|ALL_WORDS|Radixor|0.979541|n/a|n/a|no|1|
|Mongolian (`MN_MN`)|LOWERCASE_GROUPS_ONLY|Radixor|0.979541|n/a|n/a|no|1|
|Murrinh-Patha (`MWF`)|ALL_WORDS|Radixor|0.861762|n/a|n/a|no|1|
|Murrinh-Patha (`MWF`)|LOWERCASE_GROUPS_ONLY|Radixor|0.861762|n/a|n/a|no|1|
|Navajo (`NAV`)|ALL_WORDS|Radixor|0.976188|n/a|n/a|no|1|
|Navajo (`NAV`)|LOWERCASE_GROUPS_ONLY|Radixor|0.976188|n/a|n/a|no|1|
|Neapolitan (`NAP`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Neapolitan (`NAP`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|North Frisian (`FRR`)|ALL_WORDS|Radixor|0.954614|n/a|n/a|no|1|
|North Frisian (`FRR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.954614|n/a|n/a|no|1|
|Northern Sami (`SME`)|ALL_WORDS|Radixor|0.991171|n/a|n/a|no|1|
|Northern Sami (`SME`)|LOWERCASE_GROUPS_ONLY|Radixor|0.991171|n/a|n/a|no|1|
|Norwegian Bokmål (`NB_NO`)|ALL_WORDS|Radixor|0.976021|SNOWBALL NORWEGIAN BOKMAL DIRECT|0.101762107|no|5|
|Norwegian Bokmål (`NB_NO`)|LOWERCASE_GROUPS_ONLY|Radixor|0.976240|SNOWBALL NORWEGIAN BOKMAL DIRECT|0.101954266|no|5|
|Norwegian Nynorsk (`NN_NO`)|ALL_WORDS|Radixor|0.950991|SNOWBALL NORWEGIAN NYNORSK DIRECT|0.082896791|no|3|
|Norwegian Nynorsk (`NN_NO`)|LOWERCASE_GROUPS_ONLY|Radixor|0.951104|SNOWBALL NORWEGIAN NYNORSK DIRECT|0.082851757|no|3|
|Old English (`ANG`)|ALL_WORDS|Radixor|0.970779|n/a|n/a|no|1|
|Old English (`ANG`)|LOWERCASE_GROUPS_ONLY|Radixor|0.970779|n/a|n/a|no|1|
|Old French (`FRO`)|ALL_WORDS|Radixor|0.965650|n/a|n/a|no|1|
|Old French (`FRO`)|LOWERCASE_GROUPS_ONLY|Radixor|0.965650|n/a|n/a|no|1|
|Old High German (`GOH`)|ALL_WORDS|Radixor|0.992668|n/a|n/a|no|1|
|Old High German (`GOH`)|LOWERCASE_GROUPS_ONLY|Radixor|0.992668|n/a|n/a|no|1|
|Old Irish (`SGA`)|ALL_WORDS|Radixor|0.997869|n/a|n/a|no|1|
|Old Irish (`SGA`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997869|n/a|n/a|no|1|
|Old Norse (`NON`)|ALL_WORDS|Radixor|0.940817|n/a|n/a|no|1|
|Old Norse (`NON`)|LOWERCASE_GROUPS_ONLY|Radixor|0.940817|n/a|n/a|no|1|
|Old Saxon (`OSX`)|ALL_WORDS|Radixor|0.987927|n/a|n/a|no|1|
|Old Saxon (`OSX`)|LOWERCASE_GROUPS_ONLY|Radixor|0.987927|n/a|n/a|no|1|
|Oodham (`OOD`)|ALL_WORDS|Radixor|0.988769|n/a|n/a|no|1|
|Oodham (`OOD`)|LOWERCASE_GROUPS_ONLY|Radixor|0.988769|n/a|n/a|no|1|
|Otomi (ote) (`OTE`)|ALL_WORDS|Radixor|0.953277|n/a|n/a|no|1|
|Otomi (ote) (`OTE`)|LOWERCASE_GROUPS_ONLY|Radixor|0.953277|n/a|n/a|no|1|
|Pashto (`PS_AF`)|ALL_WORDS|Radixor|0.955597|n/a|n/a|no|1|
|Pashto (`PS_AF`)|LOWERCASE_GROUPS_ONLY|Radixor|0.955597|n/a|n/a|no|1|
|Persian (`FA_IR`)|ALL_WORDS|Radixor|0.975610|SNOWBALL PERSIAN DIRECT|0.440486794|no|3|
|Persian (`FA_IR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.975610|SNOWBALL PERSIAN DIRECT|0.440486794|no|3|
|Polish (`PL_PL`)|ALL_WORDS|Radixor|0.991105|POLISH LUCENE MORFOLOGIK FILTER|0.042712804|no|6|
|Polish (`PL_PL`)|LOWERCASE_GROUPS_ONLY|Radixor|0.991301|POLISH LUCENE MORFOLOGIK FILTER|0.042883749|no|6|
|Portuguese (`PT_PT`)|ALL_WORDS|Radixor|0.998542|SNOWBALL PORTUGUESE DIRECT|0.059619854|no|6|
|Portuguese (`PT_PT`)|LOWERCASE_GROUPS_ONLY|Radixor|0.998542|SNOWBALL PORTUGUESE DIRECT|0.059619854|no|6|
|Quechua (`QUE`)|ALL_WORDS|Radixor|0.986835|n/a|n/a|no|1|
|Quechua (`QUE`)|LOWERCASE_GROUPS_ONLY|Radixor|0.986835|n/a|n/a|no|1|
|Romanian (`RO_RO`)|ALL_WORDS|Radixor|0.994415|SNOWBALL ROMANIAN DIRECT|0.145283621|no|3|
|Romanian (`RO_RO`)|LOWERCASE_GROUPS_ONLY|Radixor|0.994415|SNOWBALL ROMANIAN DIRECT|0.145283621|no|3|
|Russian (`RU_RU`)|ALL_WORDS|Radixor|0.990188|SNOWBALL RUSSIAN LUCENE FILTER|0.155623602|no|4|
|Russian (`RU_RU`)|LOWERCASE_GROUPS_ONLY|Radixor|0.990213|SNOWBALL RUSSIAN DIRECT|0.155670422|no|4|
|Seneca (`SEE`)|ALL_WORDS|Radixor|0.998027|n/a|n/a|no|1|
|Seneca (`SEE`)|LOWERCASE_GROUPS_ONLY|Radixor|0.998027|n/a|n/a|no|1|
|Serbo-Croatian (`HBS`)|ALL_WORDS|Radixor|0.987756|n/a|n/a|no|1|
|Serbo-Croatian (`HBS`)|LOWERCASE_GROUPS_ONLY|Radixor|0.987756|n/a|n/a|no|1|
|Shipibo-Conibo (`SHP`)|ALL_WORDS|Radixor|0.978699|n/a|n/a|no|1|
|Shipibo-Conibo (`SHP`)|LOWERCASE_GROUPS_ONLY|Radixor|0.978699|n/a|n/a|no|1|
|Shona (`SN_ZW`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Shona (`SN_ZW`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Sotho, Southern (`ST_ZA`)|ALL_WORDS|Radixor|1.000000|SNOWBALL SESOTHO DIRECT|0.496582532|no|2|
|Sotho, Southern (`ST_ZA`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|SNOWBALL SESOTHO DIRECT|0.496582532|no|2|
|Southern Kurdish (`SDH`)|ALL_WORDS|n/a|n/a|n/a|n/a|n/a|0|
|Southern Kurdish (`SDH`)|LOWERCASE_GROUPS_ONLY|n/a|n/a|n/a|n/a|n/a|0|
|Spanish (`ES_ES`)|ALL_WORDS|Radixor|0.989448|SNOWBALL SPANISH LUCENE FILTER|0.337009985|no|7|
|Spanish (`ES_ES`)|LOWERCASE_GROUPS_ONLY|Radixor|0.989580|SNOWBALL SPANISH DIRECT|0.337037572|no|7|
|Swedish (`SV_SE`)|ALL_WORDS|Radixor|0.977619|SNOWBALL SWEDISH DIRECT|0.169075635|no|5|
|Swedish (`SV_SE`)|LOWERCASE_GROUPS_ONLY|Radixor|0.977573|SNOWBALL SWEDISH DIRECT|0.168961385|no|5|
|Tagalog (`TL_PH`)|ALL_WORDS|Radixor|0.997116|n/a|n/a|no|1|
|Tagalog (`TL_PH`)|LOWERCASE_GROUPS_ONLY|Radixor|0.997116|n/a|n/a|no|1|
|Turkish (`TR_TR`)|ALL_WORDS|Radixor|0.991555|SNOWBALL TURKISH DIRECT|0.378971891|no|3|
|Turkish (`TR_TR`)|LOWERCASE_GROUPS_ONLY|Radixor|0.991555|SNOWBALL TURKISH DIRECT|0.378971891|no|3|
|Ukrainian (`UK_UA`)|ALL_WORDS|Radixor|0.995816|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.066909929|no|4|
|Ukrainian (`UK_UA`)|LOWERCASE_GROUPS_ONLY|Radixor|0.995815|UKRAINIAN LUCENE MORFOLOGIK FILTER|0.066926372|no|4|
|Uyghur (`UG_CN`)|ALL_WORDS|Radixor|0.996210|n/a|n/a|no|1|
|Uyghur (`UG_CN`)|LOWERCASE_GROUPS_ONLY|Radixor|0.996210|n/a|n/a|no|1|
|Uzbek (`UZ_UZ`)|ALL_WORDS|Radixor|0.999762|n/a|n/a|no|1|
|Uzbek (`UZ_UZ`)|LOWERCASE_GROUPS_ONLY|Radixor|0.999762|n/a|n/a|no|1|
|Voro (`VRO`)|ALL_WORDS|Radixor|0.998340|n/a|n/a|no|1|
|Voro (`VRO`)|LOWERCASE_GROUPS_ONLY|Radixor|0.998340|n/a|n/a|no|1|
|Western Highland Chatino (`CTP`)|ALL_WORDS|Radixor|0.971433|n/a|n/a|no|1|
|Western Highland Chatino (`CTP`)|LOWERCASE_GROUPS_ONLY|Radixor|0.971433|n/a|n/a|no|1|
|Xibe (`SJO`)|ALL_WORDS|Radixor|0.970067|n/a|n/a|no|1|
|Xibe (`SJO`)|LOWERCASE_GROUPS_ONLY|Radixor|0.970067|n/a|n/a|no|1|
|Yanesha’ (`AME`)|ALL_WORDS|Radixor|0.991071|n/a|n/a|no|1|
|Yanesha’ (`AME`)|LOWERCASE_GROUPS_ONLY|Radixor|0.991071|n/a|n/a|no|1|
|Yiddish (`YI`)|ALL_WORDS|Radixor|0.989079|SNOWBALL YIDDISH DIRECT|0.097960961|no|3|
|Yiddish (`YI`)|LOWERCASE_GROUPS_ONLY|Radixor|0.989079|SNOWBALL YIDDISH DIRECT|0.097960961|no|3|
|Yoloxóchitl Mixtec (`XTY`)|ALL_WORDS|Radixor|0.990824|n/a|n/a|no|1|
|Yoloxóchitl Mixtec (`XTY`)|LOWERCASE_GROUPS_ONLY|Radixor|0.990824|n/a|n/a|no|1|
|Zarma (`DJE`)|ALL_WORDS|Radixor|1.000000|n/a|n/a|no|1|
|Zarma (`DJE`)|LOWERCASE_GROUPS_ONLY|Radixor|1.000000|n/a|n/a|no|1|
|Zenzontepec Chatino (`CZN`)|ALL_WORDS|Radixor|0.973968|n/a|n/a|no|1|
|Zenzontepec Chatino (`CZN`)|LOWERCASE_GROUPS_ONLY|Radixor|0.973968|n/a|n/a|no|1|
|Zulu (`ZU_ZA`)|ALL_WORDS|Radixor|0.993145|n/a|n/a|no|1|
|Zulu (`ZU_ZA`)|LOWERCASE_GROUPS_ONLY|Radixor|0.993145|n/a|n/a|no|1|

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
|Radixor|141|141|0|141|1.000|1.000|
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
|SNOWBALL ARABIC DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ARABIC LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL ARMENIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ARMENIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL CATALAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL CATALAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL CZECH DIRECT|1|0|0|0|4.000|4.000|
|SNOWBALL DANISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL DANISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL DUTCH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL DUTCH LUCENE FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL ESTONIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ESTONIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL FINNISH DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL FINNISH LUCENE FILTER|1|0|0|1|2.000|2.000|
|SNOWBALL FRENCH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL FRENCH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL GERMAN DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL GERMAN LUCENE FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL GREEK DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL GREEK LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL HUNGARIAN DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL HUNGARIAN LUCENE FILTER|1|0|0|1|2.000|2.000|
|SNOWBALL INDONESIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL INDONESIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL IRISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL IRISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL ITALIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ITALIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL LITHUANIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL LITHUANIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL NORWEGIAN BOKMAL DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL NORWEGIAN NYNORSK DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL NORWEGIAN NYNORSK LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL PERSIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL POLISH DIRECT|1|0|0|0|6.000|6.000|
|SNOWBALL PORTUGUESE DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL PORTUGUESE LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL ROMANIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ROMANIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL RUSSIAN DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL RUSSIAN LUCENE FILTER|1|0|0|1|2.000|2.000|
|SNOWBALL SESOTHO DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL SPANISH DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL SPANISH LUCENE FILTER|1|0|0|1|2.000|2.000|
|SNOWBALL SWEDISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL SWEDISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL TURKISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL TURKISH LUCENE FILTER|1|0|0|1|3.000|3.000|
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
|Radixor|141|141|0|141|1.000|1.000|
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
|SNOWBALL ARABIC DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ARABIC LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL ARMENIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ARMENIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL CATALAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL CATALAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL CZECH DIRECT|1|0|0|0|4.000|4.000|
|SNOWBALL DANISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL DANISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL DUTCH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL DUTCH LUCENE FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL ESTONIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ESTONIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL FINNISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL FINNISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL FRENCH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL FRENCH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL GERMAN DIRECT|1|0|0|1|3.000|3.000|
|SNOWBALL GERMAN LUCENE FILTER|1|0|0|0|4.000|4.000|
|SNOWBALL GREEK DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL GREEK LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL HUNGARIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL HUNGARIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL INDONESIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL INDONESIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL IRISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL IRISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL ITALIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ITALIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL LITHUANIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL LITHUANIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL NORWEGIAN BOKMAL DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL NORWEGIAN BOKMAL LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL NORWEGIAN NYNORSK DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL NORWEGIAN NYNORSK LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL PERSIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL POLISH DIRECT|1|0|0|0|6.000|6.000|
|SNOWBALL PORTUGUESE DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL PORTUGUESE LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL ROMANIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL ROMANIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL RUSSIAN DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL RUSSIAN LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL SESOTHO DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL SPANISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL SPANISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL SWEDISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL SWEDISH LUCENE FILTER|1|0|0|1|3.000|3.000|
|SNOWBALL TURKISH DIRECT|1|0|0|1|2.000|2.000|
|SNOWBALL TURKISH LUCENE FILTER|1|0|0|1|3.000|3.000|
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

These aggregates cover all 143 documented languages. Macro balanced accuracy gives each language equal weight. Micro metrics first sum raw pair counts across languages. Unsupported third-party languages are never inserted as zero results, so this full-coverage table is not presented as a cross-stemmer common-language ranking.

| Dictionary mode | Languages | Macro balanced accuracy | Micro balanced accuracy | Micro precision | Micro recall | Micro F1 |
|---|---:|---:|---:|---:|---:|---:|
|ALL_WORDS|141 / 143|0.984680|0.987120|0.999993|0.974239|0.986948|
|LOWERCASE_GROUPS_ONLY|141 / 143|0.985130|0.988026|0.999996|0.976053|0.987879|

### Reproducible data

- [Machine-readable quality snapshot](data/stemming-quality-2026-09-11.csv)
- SHA-256: `24bddfeed06a60bb3eeed58e1bfc93aed46c1d32e7bebd293aec2dacfddfef5b`
- [Linguistic quality methodology](reference/linguistic-quality.md)
- [Tested stemmer inventory](reference/tested-stemmers.md)
- [Reproducibility and raw data](reference/reproducibility.md)
- Pearson and Spearman correlation files are generated under `build/reports/stemming-quality/`; they are separated by dictionary mode and output policy. Correlation does not establish metric equivalence.

<!-- STEMMING-QUALITY-OVERVIEW:END -->

## Java Radixor and Snowball runtime comparison

<!-- JAVA-SNOWBALL-COMPARISON:START -->
<div class="rx2-chart-title"><strong>Java Radixor and official direct Snowball</strong> <span>(exact-language cases)</span></div>
<div class="rx2-throughput"><b>2.104×</b> <span>Snowball / Radixor geometric-mean runtime ratio</span></div>
<div class="rx2-ratio-chart" aria-label="Log-scaled Snowball divided by Radixor nanoseconds per token for 29 exact-language cases">
<div class="rx2-ratio-axis"><span>Radixor slower</span><b>1×</b><span>Snowball slower</span></div>
<div class="rx2-ratio-row"><span>Arabic</span><span class="rx2-ratio-track" role="img" aria-label="Arabic: Snowball to Radixor runtime ratio 2.780; 99.9 percent intervals do not overlap" title="Arabic: 2.780×"><i style="--left:50.00%;--width:28.22%"></i></span><b>2.780×</b></div>
<div class="rx2-ratio-row"><span>Armenian</span><span class="rx2-ratio-track" role="img" aria-label="Armenian: Snowball to Radixor runtime ratio 1.644; 99.9 percent intervals do not overlap" title="Armenian: 1.644×"><i style="--left:50.00%;--width:13.72%"></i></span><b>1.644×</b></div>
<div class="rx2-ratio-row"><span>Catalan</span><span class="rx2-ratio-track" role="img" aria-label="Catalan: Snowball to Radixor runtime ratio 2.895; 99.9 percent intervals do not overlap" title="Catalan: 2.895×"><i style="--left:50.00%;--width:29.34%"></i></span><b>2.895×</b></div>
<div class="rx2-ratio-row"><span>Czech</span><span class="rx2-ratio-track" role="img" aria-label="Czech: Snowball to Radixor runtime ratio 1.086; 99.9 percent intervals overlap — statistically tied" title="Czech: 1.086×"><i style="--left:50.00%;--width:2.27%"></i></span><b>1.086×</b></div>
<div class="rx2-ratio-row"><span>Danish</span><span class="rx2-ratio-track" role="img" aria-label="Danish: Snowball to Radixor runtime ratio 2.035; 99.9 percent intervals do not overlap" title="Danish: 2.035×"><i style="--left:50.00%;--width:19.61%"></i></span><b>2.035×</b></div>
<div class="rx2-ratio-row"><span>Dutch</span><span class="rx2-ratio-track" role="img" aria-label="Dutch: Snowball to Radixor runtime ratio 2.592; 99.9 percent intervals do not overlap" title="Dutch: 2.592×"><i style="--left:50.00%;--width:26.29%"></i></span><b>2.592×</b></div>
<div class="rx2-ratio-row"><span>Estonian</span><span class="rx2-ratio-track" role="img" aria-label="Estonian: Snowball to Radixor runtime ratio 3.185; 99.9 percent intervals do not overlap" title="Estonian: 3.185×"><i style="--left:50.00%;--width:31.98%"></i></span><b>3.185×</b></div>
<div class="rx2-ratio-row"><span>English</span><span class="rx2-ratio-track" role="img" aria-label="English: Snowball to Radixor runtime ratio 1.745; 99.9 percent intervals do not overlap" title="English: 1.745×"><i style="--left:50.00%;--width:15.37%"></i></span><b>1.745×</b></div>
<div class="rx2-ratio-row"><span>Finnish</span><span class="rx2-ratio-track" role="img" aria-label="Finnish: Snowball to Radixor runtime ratio 0.950; 99.9 percent intervals overlap — statistically tied" title="Finnish: 0.950×"><i style="--left:48.59%;--width:1.41%"></i></span><b>0.950×</b></div>
<div class="rx2-ratio-row"><span>French</span><span class="rx2-ratio-track" role="img" aria-label="French: Snowball to Radixor runtime ratio 1.980; 99.9 percent intervals do not overlap" title="French: 1.980×"><i style="--left:50.00%;--width:18.86%"></i></span><b>1.980×</b></div>
<div class="rx2-ratio-row"><span>German</span><span class="rx2-ratio-track" role="img" aria-label="German: Snowball to Radixor runtime ratio 2.167; 99.9 percent intervals do not overlap" title="German: 2.167×"><i style="--left:50.00%;--width:21.35%"></i></span><b>2.167×</b></div>
<div class="rx2-ratio-row"><span>Greek</span><span class="rx2-ratio-track" role="img" aria-label="Greek: Snowball to Radixor runtime ratio 6.119; 99.9 percent intervals do not overlap" title="Greek: 6.119×"><i style="--left:50.00%;--width:50.00%"></i></span><b>6.119×</b></div>
<div class="rx2-ratio-row"><span>Hungarian</span><span class="rx2-ratio-track" role="img" aria-label="Hungarian: Snowball to Radixor runtime ratio 2.634; 99.9 percent intervals do not overlap" title="Hungarian: 2.634×"><i style="--left:50.00%;--width:26.74%"></i></span><b>2.634×</b></div>
<div class="rx2-ratio-row"><span>Indonesian</span><span class="rx2-ratio-track" role="img" aria-label="Indonesian: Snowball to Radixor runtime ratio 1.178; 99.9 percent intervals overlap — statistically tied" title="Indonesian: 1.178×"><i style="--left:50.00%;--width:4.53%"></i></span><b>1.178×</b></div>
<div class="rx2-ratio-row"><span>Irish</span><span class="rx2-ratio-track" role="img" aria-label="Irish: Snowball to Radixor runtime ratio 1.340; 99.9 percent intervals do not overlap" title="Irish: 1.340×"><i style="--left:50.00%;--width:8.08%"></i></span><b>1.340×</b></div>
<div class="rx2-ratio-row"><span>Italian</span><span class="rx2-ratio-track" role="img" aria-label="Italian: Snowball to Radixor runtime ratio 3.788; 99.9 percent intervals do not overlap" title="Italian: 3.788×"><i style="--left:50.00%;--width:36.76%"></i></span><b>3.788×</b></div>
<div class="rx2-ratio-row"><span>Lithuanian</span><span class="rx2-ratio-track" role="img" aria-label="Lithuanian: Snowball to Radixor runtime ratio 1.194; 99.9 percent intervals overlap — statistically tied" title="Lithuanian: 1.194×"><i style="--left:50.00%;--width:4.90%"></i></span><b>1.194×</b></div>
<div class="rx2-ratio-row"><span>Norwegian Bokmal</span><span class="rx2-ratio-track" role="img" aria-label="Norwegian Bokmal: Snowball to Radixor runtime ratio 1.383; 99.9 percent intervals do not overlap" title="Norwegian Bokmal: 1.383×"><i style="--left:50.00%;--width:8.95%"></i></span><b>1.383×</b></div>
<div class="rx2-ratio-row"><span>Norwegian Nynorsk</span><span class="rx2-ratio-track" role="img" aria-label="Norwegian Nynorsk: Snowball to Radixor runtime ratio 1.785; 99.9 percent intervals do not overlap" title="Norwegian Nynorsk: 1.785×"><i style="--left:50.00%;--width:16.00%"></i></span><b>1.785×</b></div>
<div class="rx2-ratio-row"><span>Persian</span><span class="rx2-ratio-track" role="img" aria-label="Persian: Snowball to Radixor runtime ratio 4.282; 99.9 percent intervals do not overlap" title="Persian: 4.282×"><i style="--left:50.00%;--width:40.15%"></i></span><b>4.282×</b></div>
<div class="rx2-ratio-row"><span>Polish</span><span class="rx2-ratio-track" role="img" aria-label="Polish: Snowball to Radixor runtime ratio 0.982; 99.9 percent intervals overlap — statistically tied" title="Polish: 0.982×"><i style="--left:49.50%;--width:0.50%"></i></span><b>0.982×</b></div>
<div class="rx2-ratio-row"><span>Portuguese</span><span class="rx2-ratio-track" role="img" aria-label="Portuguese: Snowball to Radixor runtime ratio 3.888; 99.9 percent intervals do not overlap" title="Portuguese: 3.888×"><i style="--left:50.00%;--width:37.48%"></i></span><b>3.888×</b></div>
<div class="rx2-ratio-row"><span>Russian</span><span class="rx2-ratio-track" role="img" aria-label="Russian: Snowball to Radixor runtime ratio 1.228; 99.9 percent intervals overlap — statistically tied" title="Russian: 1.228×"><i style="--left:50.00%;--width:5.68%"></i></span><b>1.228×</b></div>
<div class="rx2-ratio-row"><span>Romanian</span><span class="rx2-ratio-track" role="img" aria-label="Romanian: Snowball to Radixor runtime ratio 3.517; 99.9 percent intervals do not overlap" title="Romanian: 3.517×"><i style="--left:50.00%;--width:34.71%"></i></span><b>3.517×</b></div>
<div class="rx2-ratio-row"><span>Southern Sotho</span><span class="rx2-ratio-track" role="img" aria-label="Southern Sotho: Snowball to Radixor runtime ratio 1.061; 99.9 percent intervals overlap — statistically tied" title="Southern Sotho: 1.061×"><i style="--left:50.00%;--width:1.64%"></i></span><b>1.061×</b></div>
<div class="rx2-ratio-row"><span>Spanish</span><span class="rx2-ratio-track" role="img" aria-label="Spanish: Snowball to Radixor runtime ratio 2.164; 99.9 percent intervals do not overlap" title="Spanish: 2.164×"><i style="--left:50.00%;--width:21.30%"></i></span><b>2.164×</b></div>
<div class="rx2-ratio-row"><span>Swedish</span><span class="rx2-ratio-track" role="img" aria-label="Swedish: Snowball to Radixor runtime ratio 1.340; 99.9 percent intervals do not overlap" title="Swedish: 1.340×"><i style="--left:50.00%;--width:8.07%"></i></span><b>1.340×</b></div>
<div class="rx2-ratio-row"><span>Turkish</span><span class="rx2-ratio-track" role="img" aria-label="Turkish: Snowball to Radixor runtime ratio 3.765; 99.9 percent intervals do not overlap" title="Turkish: 3.765×"><i style="--left:50.00%;--width:36.60%"></i></span><b>3.765×</b></div>
<div class="rx2-ratio-row"><span>Yiddish</span><span class="rx2-ratio-track" role="img" aria-label="Yiddish: Snowball to Radixor runtime ratio 5.829; 99.9 percent intervals do not overlap" title="Yiddish: 5.829×"><i style="--left:50.00%;--width:48.66%"></i></span><b>5.829×</b></div>
</div>
<p class="rx2-note">The 2026-09-11 Java JMH snapshot charts all <b>29</b> supported official direct Snowball exact-language cases against the exact generic Radixor model row. The geometric mean is <b>2.104× over all 29 comparable changed-token cases</b>. A ratio above 1 means Snowball used more time per token; ratios are point estimates, not categorical winners. The 99.9% JMH intervals overlap in <b>7</b> cases, which are labelled statistically tied. All rows use 3 warmup iterations, 5 measurement iterations, 3 forks, one thread, and the same per-language timing population.</p>
<details class="quality-details"><summary>Accessible raw Java comparison table</summary>
<table><thead><tr><th>Language</th><th>Model</th><th>Timing corpus</th><th>Radixor ns/token</th><th>Radixor JMH error</th><th>Snowball ns/token</th><th>Snowball JMH error</th><th>Snowball / Radixor</th><th>99.9% intervals</th><th>Aggregate</th></tr></thead><tbody><tr><td>Arabic</td><td><code>ar-default</code></td><td>changed tokens</td><td>159.8</td><td>±33.8</td><td>444.1</td><td>±18.7</td><td>2.780×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Armenian</td><td><code>hy-am-default</code></td><td>changed tokens</td><td>88.9</td><td>±19.6</td><td>146.2</td><td>±19.2</td><td>1.644×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Catalan</td><td><code>ca-es-default</code></td><td>changed tokens</td><td>85.9</td><td>±12.6</td><td>248.5</td><td>±12.4</td><td>2.895×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Czech</td><td><code>cs-cz-default</code></td><td>changed tokens</td><td>91.4</td><td>±16.5</td><td>99.2</td><td>±15.8</td><td>1.086×</td><td>overlap — statistically tied</td><td>yes</td></tr><tr><td>Danish</td><td><code>da-dk-default</code></td><td>changed tokens</td><td>59.3</td><td>±9.0</td><td>120.7</td><td>±13.1</td><td>2.035×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Dutch</td><td><code>nl-nl-default</code></td><td>changed tokens</td><td>78.4</td><td>±14.1</td><td>203.2</td><td>±15.1</td><td>2.592×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Estonian</td><td><code>et-ee-default</code></td><td>changed tokens</td><td>61.5</td><td>±12.6</td><td>195.9</td><td>±13.3</td><td>3.185×</td><td>do not overlap</td><td>yes</td></tr><tr><td>English</td><td><code>us-uk-default</code></td><td>changed tokens</td><td>131.3</td><td>±9.3</td><td>229.0</td><td>±4.7</td><td>1.745×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Finnish</td><td><code>fi-fi-default</code></td><td>changed tokens</td><td>181.9</td><td>±21.2</td><td>172.8</td><td>±8.7</td><td>0.950×</td><td>overlap — statistically tied</td><td>yes</td></tr><tr><td>French</td><td><code>fr-fr-default</code></td><td>changed tokens</td><td>162.1</td><td>±4.9</td><td>320.9</td><td>±17.5</td><td>1.980×</td><td>do not overlap</td><td>yes</td></tr><tr><td>German</td><td><code>de-de-default</code></td><td>changed tokens</td><td>189.2</td><td>±16.4</td><td>410.1</td><td>±15.8</td><td>2.167×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Greek</td><td><code>el-gr-default</code></td><td>changed tokens</td><td>121.0</td><td>±24.2</td><td>740.5</td><td>±28.7</td><td>6.119×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Hungarian</td><td><code>hu-hu-default</code></td><td>changed tokens</td><td>73.5</td><td>±10.2</td><td>193.5</td><td>±17.2</td><td>2.634×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Indonesian</td><td><code>id-id-default</code></td><td>changed tokens</td><td>92.0</td><td>±12.1</td><td>108.4</td><td>±12.1</td><td>1.178×</td><td>overlap — statistically tied</td><td>yes</td></tr><tr><td>Irish</td><td><code>ga-ie-default</code></td><td>changed tokens</td><td>103.7</td><td>±12.8</td><td>139.0</td><td>±13.4</td><td>1.340×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Italian</td><td><code>it-it-default</code></td><td>changed tokens</td><td>97.6</td><td>±19.4</td><td>369.7</td><td>±16.6</td><td>3.788×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Lithuanian</td><td><code>lt-lt-default</code></td><td>changed tokens</td><td>128.7</td><td>±16.7</td><td>153.7</td><td>±12.5</td><td>1.194×</td><td>overlap — statistically tied</td><td>yes</td></tr><tr><td>Norwegian Bokmal</td><td><code>nb-no-default</code></td><td>changed tokens</td><td>71.2</td><td>±10.5</td><td>98.5</td><td>±12.6</td><td>1.383×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Norwegian Nynorsk</td><td><code>nn-no-default</code></td><td>changed tokens</td><td>54.2</td><td>±10.0</td><td>96.7</td><td>±12.9</td><td>1.785×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Persian</td><td><code>fa-ir-default</code></td><td>changed tokens</td><td>72.8</td><td>±22.8</td><td>311.9</td><td>±20.3</td><td>4.282×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Polish</td><td><code>pl-pl-unimorph</code></td><td>changed tokens</td><td>108.3</td><td>±19.6</td><td>106.3</td><td>±17.1</td><td>0.982×</td><td>overlap — statistically tied</td><td>yes</td></tr><tr><td>Portuguese</td><td><code>pt-pt-default</code></td><td>changed tokens</td><td>68.5</td><td>±16.3</td><td>266.2</td><td>±13.1</td><td>3.888×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Russian</td><td><code>ru-ru-default</code></td><td>changed tokens</td><td>142.2</td><td>±28.0</td><td>174.7</td><td>±16.1</td><td>1.228×</td><td>overlap — statistically tied</td><td>yes</td></tr><tr><td>Romanian</td><td><code>ro-ro-default</code></td><td>changed tokens</td><td>93.6</td><td>±13.3</td><td>329.2</td><td>±16.3</td><td>3.517×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Southern Sotho</td><td><code>st-za-default</code></td><td>changed tokens</td><td>50.1</td><td>±12.9</td><td>53.1</td><td>±12.3</td><td>1.061×</td><td>overlap — statistically tied</td><td>yes</td></tr><tr><td>Spanish</td><td><code>es-es-default</code></td><td>changed tokens</td><td>106.3</td><td>±18.8</td><td>229.9</td><td>±16.7</td><td>2.164×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Swedish</td><td><code>sv-se-default</code></td><td>changed tokens</td><td>77.4</td><td>±10.1</td><td>103.7</td><td>±12.8</td><td>1.340×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Turkish</td><td><code>tr-tr-default</code></td><td>changed tokens</td><td>117.4</td><td>±13.1</td><td>442.0</td><td>±19.3</td><td>3.765×</td><td>do not overlap</td><td>yes</td></tr><tr><td>Yiddish</td><td><code>yi-default</code></td><td>changed tokens</td><td>55.0</td><td>±17.0</td><td>320.4</td><td>±21.0</td><td>5.829×</td><td>do not overlap</td><td>yes</td></tr></tbody></table>
</details>
<!-- JAVA-SNOWBALL-COMPARISON:END -->
