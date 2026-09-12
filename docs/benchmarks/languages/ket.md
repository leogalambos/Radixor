# Ket Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,100 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,100 distinct usable word forms">★★☆☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `ket-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/ket). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,100 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,100 distinct usable word forms">★★☆☆☆</span>. The exact count is **1,100 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `ket-default` | `1.0.0` | `KET` | 519 | 1,100 | 1,622 | 1,038 | 584 | changed tokens | 5,000 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 12 |
| `BackwardCompoundCommand` | 322 |
| `DeleteSuffixCommand` | 233 |
| `PreserveCommand` | 1,038 |
| `ReplaceLastCharacterCommand` | 17 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.753% | 99.658% | 99.807% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 603124 | 0.000000% | 8 / 1326 | 0.603318% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 603124 | 0.000000% | 8 / 1326 | 0.603318% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 1,453 | 59.986% | 7.407% | 89.700% |
| 20% | 1,290 | 62.308% | 8.405% | 92.029% |
| 30% | 1,129 | 63.240% | 10.864% | 92.798% |
| 40% | 971 | 63.237% | 10.541% | 93.204% |
| 50% | 811 | 64.555% | 10.169% | 93.411% |
| 60% | 632 | 63.278% | 10.550% | 94.203% |
| 70% | 483 | 64.361% | 9.259% | 94.194% |
| 80% | 313 | 67.219% | 11.290% | 94.231% |
| 90% | 158 | 65.161% | 9.375% | 94.231% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.313 | 0.071 | 62.6 |
