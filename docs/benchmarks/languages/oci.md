# Occitan Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 7,465 distinct usable word forms" title="Relative dictionary size 3 of 5; 7,465 distinct usable word forms">★★★☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `oci-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/oci). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 7,465 distinct usable word forms" title="Relative dictionary size 3 of 5; 7,465 distinct usable word forms">★★★☆☆</span>. The exact count is **7,465 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `oci-default` | `1.0.0` | `OCI` | 174 | 7,465 | 7,784 | 348 | 7,436 | changed tokens | 7,436 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 133 |
| `BackwardCompoundCommand` | 4,898 |
| `DeleteSuffixCommand` | 1,842 |
| `PreserveCommand` | 349 |
| `ReplaceLastCharacterCommand` | 562 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 98.137% | 98.050% | 100.000% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 27698807 | 0.000000% | 1736 / 160573 | 1.081128% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 27698807 | 0.000000% | 1736 / 160573 | 1.081128% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 7,020 | 64.336% | 62.899% | 100.000% |
| 20% | 6,184 | 73.735% | 72.630% | 100.000% |
| 30% | 5,407 | 78.550% | 77.540% | 100.000% |
| 40% | 4,598 | 80.483% | 79.838% | 99.038% |
| 50% | 3,790 | 80.132% | 79.187% | 100.000% |
| 60% | 3,024 | 81.005% | 80.078% | 100.000% |
| 70% | 2,256 | 80.026% | 79.096% | 100.000% |
| 80% | 1,529 | 82.674% | 81.805% | 100.000% |
| 90% | 731 | 82.639% | 81.778% | 100.000% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.372 | 0.088 | 50.1 |
