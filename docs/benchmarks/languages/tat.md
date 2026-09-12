# Tatar Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 7,483 distinct usable word forms" title="Relative dictionary size 3 of 5; 7,483 distinct usable word forms">★★★☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `tat-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/tat). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 7,483 distinct usable word forms" title="Relative dictionary size 3 of 5; 7,483 distinct usable word forms">★★★☆☆</span>. The exact count is **7,483 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `tat-default` | `1.0.0` | `TAT` | 1,258 | 7,483 | 8,745 | 2,516 | 6,229 | changed tokens | 6,229 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `BackwardCompoundCommand` | 241 |
| `DeleteSuffixCommand` | 5,987 |
| `PreserveCommand` | 2,517 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.943% | 99.952% | 99.921% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 27975338 | 0.000000% | 17 / 18565 | 0.091570% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 27975338 | 0.000000% | 17 / 18565 | 0.091570% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 7,870 | 93.380% | 94.131% | 92.314% |
| 20% | 6,993 | 94.025% | 94.359% | 94.135% |
| 30% | 6,123 | 94.757% | 95.023% | 94.098% |
| 40% | 5,246 | 94.949% | 95.349% | 94.960% |
| 50% | 4,370 | 95.467% | 95.459% | 95.367% |
| 60% | 3,495 | 95.769% | 95.868% | 95.817% |
| 70% | 2,618 | 95.225% | 95.506% | 94.960% |
| 80% | 1,750 | 95.533% | 95.677% | 96.000% |
| 90% | 873 | 95.642% | 95.513% | 95.968% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.152 | 0.053 | 24.4 |
