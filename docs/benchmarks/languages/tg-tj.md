# Tajik Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 78 distinct usable word forms" title="Relative dictionary size 1 of 5; 78 distinct usable word forms">★☆☆☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `tg-tj-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/tgk). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 78 distinct usable word forms" title="Relative dictionary size 1 of 5; 78 distinct usable word forms">★☆☆☆☆</span>. The exact count is **78 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `tg-tj-default` | `1.0.0` | `TG_TJ` | 75 | 78 | 153 | 150 | 3 | changed tokens | 5,000 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `BackwardCompoundCommand` | 1 |
| `DeleteSuffixCommand` | 2 |
| `PreserveCommand` | 150 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 100.000% | 100.000% | 100.000% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 3000 | 0.000000% | 0 / 3 | 0.000000% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 3000 | 0.000000% | 0 / 3 | 0.000000% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 137 | 97.810% | 0.000% | 100.000% |
| 20% | 123 | 97.561% | 0.000% | 100.000% |
| 30% | 106 | 98.113% | 0.000% | 100.000% |
| 40% | 92 | 97.826% | 0.000% | 100.000% |
| 50% | 75 | 98.667% | 0.000% | 100.000% |
| 60% | 61 | 98.361% | 0.000% | 100.000% |
| 70% | 45 | 97.778% | 0.000% | 100.000% |
| 80% | 30 | 100.000% | 0.000% | 100.000% |
| 90% | 14 | 100.000% | 0.000% | 100.000% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.134 | 0.011 | 26.8 |
