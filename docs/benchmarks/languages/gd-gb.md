# Scottish Gaelic Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 265 distinct usable word forms" title="Relative dictionary size 1 of 5; 265 distinct usable word forms">★☆☆☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `gd-gb-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/gla). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 265 distinct usable word forms" title="Relative dictionary size 1 of 5; 265 distinct usable word forms">★☆☆☆☆</span>. The exact count is **265 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `gd-gb-default` | `1.0.0` | `GD_GB` | 70 | 265 | 335 | 140 | 195 | changed tokens | 5,000 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `BackwardCompoundCommand` | 132 |
| `DeleteSuffixCommand` | 63 |
| `PreserveCommand` | 140 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 100.000% | 100.000% | 100.000% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 34526 | 0.000000% | 0 / 454 | 0.000000% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 34526 | 0.000000% | 0 / 454 | 0.000000% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 302 | 45.946% | 14.118% | 92.063% |
| 20% | 272 | 46.324% | 13.750% | 92.857% |
| 30% | 238 | 49.378% | 17.143% | 95.918% |
| 40% | 200 | 47.917% | 14.815% | 95.238% |
| 50% | 169 | 48.734% | 12.791% | 97.143% |
| 60% | 129 | 48.837% | 14.773% | 96.429% |
| 70% | 95 | 51.648% | 16.981% | 100.000% |
| 80% | 62 | 49.254% | 13.793% | 100.000% |
| 90% | 30 | 48.276% | 8.333% | 100.000% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.238 | 0.053 | 47.5 |
