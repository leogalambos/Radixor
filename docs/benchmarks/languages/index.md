# Language Benchmark Pages

This section splits Radixor stemmer benchmark results by language. Each of the 20 registered default models has one language page containing the refreshed corpus, patch-command distribution, exact-root accuracy, runtime performance, and pairwise stemming-quality tables for both dictionary-processing modes.

## Reference Pages

| Page | Purpose |
| --- | --- |
| [Methodology](../reference/methodology.md) | Workload design, normalization, speed metrics, and exact-root quality metrics. Pairwise quality definitions are also reproduced on every language page. |
| [Corpora](../reference/corpora.md) | Dictionary sizes and changed-token timing workloads. |
| [Environment and reports](../reference/environment.md) | Hardware, JVM, JMH settings, report files, and badge policy. |
| [Dictionary-family generalization](../generalization.md) | Separate all-language evaluation of Java transformations on families withheld from model training. |
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
| Hebrew | `HE_IL` | [Hebrew](hebrew.md) |
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
- Runtime and exact-root agreement measure different properties. Light, minimal, possessive, and other rule-based filters intentionally have different transformation scopes, so compare every speed row with the adjacent quality table. The [English coverage benchmark](../reference/english-coverage.md) shows the quality/speed operating curve; the [multilingual generalization benchmark](../generalization.md) separately tests transformations on withheld families.
- Results are comparable only within the same language and benchmark family.
- The historical Porter badge is retired; no JMH badge JSON is generated.
