# Upper Sorbian Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 662 distinct usable word forms" title="Relative dictionary size 1 of 5; 662 distinct usable word forms">★☆☆☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `hsb-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/hsb). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 662 distinct usable word forms" title="Relative dictionary size 1 of 5; 662 distinct usable word forms">★☆☆☆☆</span>. The exact count is **662 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `hsb-default` | `1.0.0` | `HSB` | 343 | 662 | 1,006 | 686 | 320 | changed tokens | 5,000 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 11 |
| `BackwardCompoundCommand` | 140 |
| `DeleteSuffixCommand` | 106 |
| `PreserveCommand` | 687 |
| `ReplaceLastCharacterCommand` | 62 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.901% | 99.688% | 100.000% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 218407 | 0.000000% | 3 / 384 | 0.781250% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 218407 | 0.000000% | 3 / 384 | 0.781250% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 906 | 67.257% | 10.616% | 94.498% |
| 20% | 807 | 69.716% | 13.492% | 97.436% |
| 30% | 710 | 70.183% | 14.286% | 96.667% |
| 40% | 605 | 70.984% | 14.706% | 95.122% |
| 50% | 504 | 71.230% | 17.901% | 95.906% |
| 60% | 403 | 71.216% | 19.492% | 95.620% |
| 70% | 300 | 70.530% | 18.750% | 95.146% |
| 80% | 204 | 69.268% | 19.048% | 94.203% |
| 90% | 101 | 70.297% | 20.588% | 94.118% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.199 | 0.064 | 39.7 |
