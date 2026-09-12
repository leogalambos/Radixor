# Slovenian Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 25,100 distinct usable word forms" title="Relative dictionary size 4 of 5; 25,100 distinct usable word forms">★★★★☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `sl-si-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/slv). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 4 of 5; 25,100 distinct usable word forms" title="Relative dictionary size 4 of 5; 25,100 distinct usable word forms">★★★★☆</span>. The exact count is **25,100 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `sl-si-default` | `1.0.0` | `SL_SI` | 2,531 | 25,100 | 27,734 | 5,062 | 22,672 | changed tokens | 22,672 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 4 |
| `BackwardCompoundCommand` | 22,226 |
| `DeleteSuffixCommand` | 412 |
| `PreserveCommand` | 5,062 |
| `ReplaceLastCharacterCommand` | 30 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.629% | 99.546% | 100.000% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 314869560 | 0.000000% | 794 / 122890 | 0.646106% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 314869560 | 0.000000% | 794 / 122890 | 0.646106% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 24,916 | 32.884% | 18.983% | 93.766% |
| 20% | 22,138 | 38.322% | 25.738% | 95.012% |
| 30% | 19,417 | 40.223% | 27.893% | 94.865% |
| 40% | 16,660 | 43.381% | 31.616% | 95.787% |
| 50% | 13,863 | 45.005% | 33.575% | 96.206% |
| 60% | 11,077 | 47.147% | 36.122% | 95.949% |
| 70% | 8,333 | 47.954% | 37.124% | 96.574% |
| 80% | 5,516 | 49.226% | 38.384% | 97.233% |
| 90% | 2,810 | 47.260% | 36.111% | 96.838% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 2.132 | 0.363 | 94.0 |
