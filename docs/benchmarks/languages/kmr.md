# Kurmanji Kurdish Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 191,104 distinct usable word forms" title="Relative dictionary size 5 of 5; 191,104 distinct usable word forms">★★★★★</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `kmr-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/kmr). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 191,104 distinct usable word forms" title="Relative dictionary size 5 of 5; 191,104 distinct usable word forms">★★★★★</span>. The exact count is **191,104 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `kmr-default` | `1.0.0` | `KMR` | 13,981 | 191,104 | 206,245 | 27,962 | 178,283 | changed tokens | 178,283 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 210 |
| `BackwardCompoundCommand` | 22,874 |
| `DeleteSuffixCommand` | 156,389 |
| `PreserveCommand` | 26,418 |
| `ReplaceLastCharacterCommand` | 354 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.057% | 99.790% | 94.385% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 18257845405 | 0.000000% | 38903 / 2428451 | 1.601968% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 18257845405 | 0.000000% | 38903 / 2428451 | 1.601968% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 185,551 | 80.030% | 78.635% | 88.473% |
| 20% | 164,656 | 80.160% | 78.751% | 89.250% |
| 30% | 143,856 | 80.296% | 78.745% | 90.024% |
| 40% | 123,264 | 80.473% | 78.972% | 90.313% |
| 50% | 102,558 | 80.652% | 79.066% | 91.064% |
| 60% | 81,598 | 81.059% | 79.587% | 91.219% |
| 70% | 60,771 | 81.842% | 80.365% | 91.493% |
| 80% | 40,432 | 82.215% | 80.660% | 92.302% |
| 90% | 20,264 | 82.905% | 81.254% | 92.803% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 10.406 | 2.057 | 58.4 |
