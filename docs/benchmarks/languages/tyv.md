# Tuvan Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 491,421 distinct usable word forms" title="Relative dictionary size 5 of 5; 491,421 distinct usable word forms">★★★★★</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `tyv-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/tyv). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 491,421 distinct usable word forms" title="Relative dictionary size 5 of 5; 491,421 distinct usable word forms">★★★★★</span>. The exact count is **491,421 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `tyv-default` | `1.0.0` | `TYV` | 5,032 | 491,421 | 499,024 | 10,064 | 488,960 | changed tokens | 488,960 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 1 |
| `BackwardCompoundCommand` | 45,265 |
| `DeleteSuffixCommand` | 443,520 |
| `PreserveCommand` | 9,839 |
| `ReplaceLastCharacterCommand` | 399 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.456% | 99.504% | 97.099% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 120720265288 | 0.000000% | 250846 / 26788622 | 0.936390% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 120720265288 | 0.000000% | 250846 / 26788622 | 0.936390% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 448,881 | 82.247% | 82.098% | 88.618% |
| 20% | 397,927 | 83.539% | 83.395% | 89.812% |
| 30% | 348,468 | 84.300% | 84.168% | 90.432% |
| 40% | 297,998 | 85.520% | 85.420% | 90.475% |
| 50% | 248,481 | 86.196% | 86.093% | 91.251% |
| 60% | 197,607 | 86.289% | 86.193% | 91.355% |
| 70% | 147,688 | 86.377% | 86.272% | 91.581% |
| 80% | 98,535 | 86.661% | 86.543% | 92.441% |
| 90% | 49,047 | 86.705% | 86.600% | 92.990% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 44.090 | 8.598 | 90.2 |
