# Urdu Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 881 distinct usable word forms" title="Relative dictionary size 2 of 5; 881 distinct usable word forms">★★☆☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `ur-pk-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/urd). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 881 distinct usable word forms" title="Relative dictionary size 2 of 5; 881 distinct usable word forms">★★☆☆☆</span>. The exact count is **881 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `ur-pk-default` | `1.0.0` | `UR_PK` | 162 | 881 | 1,047 | 324 | 723 | changed tokens | 5,000 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `BackwardCompoundCommand` | 384 |
| `DeleteSuffixCommand` | 252 |
| `PreserveCommand` | 325 |
| `ReplaceLastCharacterCommand` | 86 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.618% | 99.447% | 100.000% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 384834 | 0.000000% | 40 / 2806 | 1.425517% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 384834 | 0.000000% | 40 / 2806 | 1.425517% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 938 | 59.190% | 49.071% | 90.411% |
| 20% | 825 | 58.667% | 45.254% | 86.154% |
| 30% | 734 | 59.772% | 48.632% | 84.956% |
| 40% | 632 | 58.902% | 47.955% | 87.500% |
| 50% | 528 | 59.799% | 49.560% | 90.000% |
| 60% | 439 | 59.169% | 49.462% | 87.500% |
| 70% | 342 | 63.312% | 54.000% | 87.755% |
| 80% | 227 | 62.009% | 52.121% | 87.500% |
| 90% | 115 | 64.103% | 55.294% | 87.500% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.205 | 0.076 | 41.1 |
