# Venetian Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 8,595 distinct usable word forms" title="Relative dictionary size 3 of 5; 8,595 distinct usable word forms">★★★☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `vec-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/vec). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 8,595 distinct usable word forms" title="Relative dictionary size 3 of 5; 8,595 distinct usable word forms">★★★☆☆</span>. The exact count is **8,595 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `vec-default` | `1.0.0` | `VEC` | 368 | 8,595 | 9,056 | 736 | 8,320 | changed tokens | 8,320 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 339 |
| `BackwardCompoundCommand` | 5,519 |
| `DeleteSuffixCommand` | 2,456 |
| `PreserveCommand` | 736 |
| `ReplaceLastCharacterCommand` | 6 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 98.973% | 98.882% | 100.000% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 36834813 | 0.000000% | 596 / 97902 | 0.608772% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 36834813 | 0.000000% | 596 / 97902 | 0.608772% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 8,122 | 74.313% | 72.488% | 99.396% |
| 20% | 7,191 | 78.056% | 76.162% | 99.320% |
| 30% | 6,315 | 80.223% | 78.450% | 99.225% |
| 40% | 5,374 | 80.387% | 78.649% | 99.095% |
| 50% | 4,440 | 79.602% | 77.753% | 100.000% |
| 60% | 3,537 | 79.405% | 77.573% | 100.000% |
| 70% | 2,663 | 79.948% | 78.149% | 100.000% |
| 80% | 1,793 | 81.434% | 79.770% | 100.000% |
| 90% | 903 | 83.864% | 82.437% | 100.000% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.391 | 0.113 | 46.9 |
