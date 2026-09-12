# Votic Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,392 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,392 distinct usable word forms">★★☆☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `vot-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/vot). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 2 of 5; 1,392 distinct usable word forms" title="Relative dictionary size 2 of 5; 1,392 distinct usable word forms">★★☆☆☆</span>. The exact count is **1,392 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `vot-default` | `1.0.0` | `VOT` | 55 | 1,392 | 1,448 | 110 | 1,338 | changed tokens | 5,000 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `BackwardCompoundCommand` | 913 |
| `DeleteSuffixCommand` | 422 |
| `PreserveCommand` | 110 |
| `ReplaceLastCharacterCommand` | 3 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.931% | 99.925% | 100.000% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 951179 | 0.000000% | 23 / 16957 | 0.135637% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 951179 | 0.000000% | 23 / 16957 | 0.135637% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 1,289 | 15.839% | 9.580% | 85.714% |
| 20% | 1,159 | 19.655% | 13.790% | 90.909% |
| 30% | 1,001 | 19.000% | 12.554% | 94.737% |
| 40% | 869 | 23.645% | 18.102% | 93.939% |
| 50% | 710 | 24.930% | 19.665% | 92.593% |
| 60% | 579 | 23.276% | 17.757% | 90.909% |
| 70% | 421 | 21.378% | 15.938% | 87.500% |
| 80% | 288 | 23.368% | 17.844% | 81.818% |
| 90% | 132 | 25.385% | 22.500% | 80.000% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.246 | 0.060 | 49.1 |
