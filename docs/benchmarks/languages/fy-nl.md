# West Frisian Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 696 distinct usable word forms" title="Relative dictionary size 1 of 5; 696 distinct usable word forms">★☆☆☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `fy-nl-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/fry). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 1 of 5; 696 distinct usable word forms" title="Relative dictionary size 1 of 5; 696 distinct usable word forms">★☆☆☆☆</span>. The exact count is **696 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `fy-nl-default` | `1.0.0` | `FY_NL` | 85 | 696 | 784 | 170 | 614 | changed tokens | 5,000 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 26 |
| `BackwardCompoundCommand` | 421 |
| `DeleteSuffixCommand` | 140 |
| `PreserveCommand` | 170 |
| `ReplaceLastCharacterCommand` | 27 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.617% | 99.511% | 100.000% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 239269 | 0.000000% | 16 / 2591 | 0.617522% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 239269 | 0.000000% | 16 / 2591 | 0.617522% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 702 | 35.846% | 22.162% | 80.263% |
| 20% | 626 | 44.338% | 30.390% | 92.647% |
| 30% | 546 | 46.154% | 33.178% | 93.220% |
| 40% | 474 | 48.734% | 37.366% | 92.157% |
| 50% | 393 | 53.671% | 42.484% | 90.476% |
| 60% | 317 | 57.547% | 46.800% | 94.118% |
| 70% | 233 | 57.522% | 47.541% | 92.000% |
| 80% | 159 | 56.604% | 46.281% | 94.118% |
| 90% | 73 | 59.459% | 48.276% | 100.000% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.231 | 0.053 | 46.2 |
