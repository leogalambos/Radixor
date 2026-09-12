# Khanty Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,175 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,175 distinct usable word forms">★★☆☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `kca-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/kca). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 2,175 distinct usable word forms" title="Relative dictionary size 2 of 5; 2,175 distinct usable word forms">★★☆☆☆</span>. The exact count is **2,175 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `kca-default` | `1.0.0` | `KCA` | 661 | 2,175 | 2,859 | 1,322 | 1,537 | changed tokens | 5,000 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 2 |
| `BackwardCompoundCommand` | 986 |
| `DeleteSuffixCommand` | 557 |
| `PreserveCommand` | 1,306 |
| `ReplaceLastCharacterCommand` | 8 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 98.846% | 99.154% | 98.487% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 2355344 | 0.000000% | 147 / 8881 | 1.655219% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 2355344 | 0.000000% | 147 / 8881 | 1.655219% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 2,584 | 54.450% | 20.559% | 93.086% |
| 20% | 2,308 | 56.438% | 25.391% | 93.359% |
| 30% | 2,011 | 58.309% | 28.168% | 94.143% |
| 40% | 1,732 | 59.204% | 29.575% | 94.924% |
| 50% | 1,390 | 58.911% | 29.808% | 95.719% |
| 60% | 1,132 | 59.018% | 28.289% | 95.057% |
| 70% | 826 | 59.951% | 30.000% | 94.271% |
| 80% | 537 | 63.451% | 31.228% | 95.276% |
| 90% | 300 | 60.729% | 33.520% | 93.939% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.314 | 0.083 | 62.9 |
