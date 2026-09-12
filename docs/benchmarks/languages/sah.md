# Yakut Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 526,431 distinct usable word forms" title="Relative dictionary size 5 of 5; 526,431 distinct usable word forms">★★★★★</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `sah-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/sah). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 526,431 distinct usable word forms" title="Relative dictionary size 5 of 5; 526,431 distinct usable word forms">★★★★★</span>. The exact count is **526,431 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `sah-default` | `1.0.0` | `SAH` | 5,622 | 526,431 | 534,266 | 11,244 | 523,022 | changed tokens | 523,022 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 4 |
| `BackwardCompoundCommand` | 56,855 |
| `DeleteSuffixCommand` | 466,523 |
| `PreserveCommand` | 10,839 |
| `ReplaceLastCharacterCommand` | 45 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.543% | 99.621% | 95.927% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 138536905333 | 0.000000% | 191890 / 27630332 | 0.694490% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 138536905333 | 0.000000% | 191890 / 27630332 | 0.694490% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 480,477 | 83.100% | 83.033% | 86.527% |
| 20% | 426,550 | 84.648% | 84.599% | 87.169% |
| 30% | 374,212 | 85.461% | 85.399% | 88.043% |
| 40% | 321,147 | 86.440% | 86.376% | 87.693% |
| 50% | 266,076 | 85.954% | 85.895% | 88.727% |
| 60% | 212,275 | 86.059% | 85.997% | 88.803% |
| 70% | 158,838 | 86.873% | 86.827% | 89.066% |
| 80% | 105,711 | 86.849% | 86.813% | 89.620% |
| 90% | 52,680 | 87.409% | 87.310% | 90.485% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 59.747 | 11.961 | 114.2 |
