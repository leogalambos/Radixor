# Oromo Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 656 distinct usable word forms" title="Relative dictionary size 1 of 5; 656 distinct usable word forms">★☆☆☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `om-et-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/orm). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 656 distinct usable word forms" title="Relative dictionary size 1 of 5; 656 distinct usable word forms">★☆☆☆☆</span>. The exact count is **656 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `om-et-default` | `1.0.0` | `OM_ET` | 84 | 656 | 740 | 168 | 572 | changed tokens | 5,000 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `BackwardCompoundCommand` | 2 |
| `DeleteSuffixCommand` | 570 |
| `PreserveCommand` | 168 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 100.000% | 100.000% | 100.000% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 212600 | 0.000000% | 0 / 2240 | 0.000000% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 212600 | 0.000000% | 0 / 2240 | 0.000000% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 669 | 83.259% | 78.337% | 100.000% |
| 20% | 591 | 85.956% | 81.838% | 100.000% |
| 30% | 522 | 91.522% | 89.027% | 100.000% |
| 40% | 441 | 88.036% | 84.548% | 100.000% |
| 50% | 371 | 91.129% | 88.542% | 100.000% |
| 60% | 301 | 89.597% | 86.522% | 100.000% |
| 70% | 221 | 92.727% | 90.588% | 100.000% |
| 80% | 150 | 92.667% | 90.517% | 100.000% |
| 90% | 71 | 94.286% | 92.593% | 100.000% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.118 | 0.031 | 23.7 |
