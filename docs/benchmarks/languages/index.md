# Language Benchmark Pages

This section splits Radixor stemmer benchmark results by language. Each language page lists accuracy first and speed second.

## Reference Pages

| Page | Purpose |
| --- | --- |
| [Methodology](../reference/methodology.md) | Workload design, normalization, speed metrics, and quality metrics. |
| [Corpora](../reference/corpora.md) | Dictionary sizes and changed-token timing workloads. |
| [Environment and reports](../reference/environment.md) | Hardware, JVM, JMH settings, report files, and badge policy. |
| [English dictionary coverage](../reference/english-coverage.md) | Quality/speed operating curve for contracted Radixor tries built from 100% down to 10% of English dictionary rows. |
| [Candidate evaluation](../reference/candidates.md) | Included and skipped stemmer candidates. |

## Languages

| Language | Resource | Benchmark page |
| --- | --- | --- |
| Czech | `CS_CZ` | [Czech](czech.md) |
| Danish | `DA_DK` | [Danish](danish.md) |
| Dutch | `NL_NL` | [Dutch](dutch.md) |
| English | `US_UK` | [English](english.md) |
| Finnish | `FI_FI` | [Finnish](finnish.md) |
| French | `FR_FR` | [French](french.md) |
| German | `DE_DE` | [German](german.md) |
| Hungarian | `HU_HU` | [Hungarian](hungarian.md) |
| Italian | `IT_IT` | [Italian](italian.md) |
| Norwegian Bokmal | `NB_NO` | [Norwegian Bokmal](norwegian-bokmal.md) |
| Norwegian Nynorsk | `NN_NO` | [Norwegian Nynorsk](norwegian-nynorsk.md) |
| Persian | `FA_IR` | [Persian](persian.md) |
| Polish | `PL_PL` | [Polish](polish.md) |
| Portuguese | `PT_PT` | [Portuguese](portuguese.md) |
| Russian | `RU_RU` | [Russian](russian.md) |
| Spanish | `ES_ES` | [Spanish](spanish.md) |
| Swedish | `SV_SE` | [Swedish](swedish.md) |
| Ukrainian | `UK_UA` | [Ukrainian](ukrainian.md) |
| Yiddish | `YI` | [Yiddish](yiddish.md) |

## Methodology Notes

- Speed benchmarks process only changed dictionary tokens where the surface form differs from the expected root.
- Accuracy benchmarks process the complete dictionary and report `All exact`, `Changed exact`, and `Root preserved`.
- Radixor speed must be interpreted together with exact-root quality. A slower Radixor row must not be read as a simple performance weakness when Radixor is also the row with accuracy close to 100% and competing stemmers are much lower. Many fast light, minimal, possessive, or aggressive rule-based stemmers are fast because they do much less linguistic work. The measured Radixor cost buys dictionary-trained precision, and that precision is what improves search quality when queries and indexed text are reduced to the same intended roots. The [EnglishRadixorDictionaryCoverageBenchmark](../reference/english-coverage.md) table shows this contracted-trie operating curve explicitly.
- Results are comparable only within the same language and benchmark family.
- The historical Porter badge is retired; no JMH badge JSON is generated.
