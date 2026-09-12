# Georgian Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 75,914 distinct usable word forms" title="Relative dictionary size 5 of 5; 75,914 distinct usable word forms">★★★★★</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `ka-ge-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/kat). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 75,914 distinct usable word forms" title="Relative dictionary size 5 of 5; 75,914 distinct usable word forms">★★★★★</span>. The exact count is **75,914 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `ka-ge-default` | `1.0.0` | `KA_GE` | 3,847 | 75,914 | 79,967 | 7,694 | 72,273 | changed tokens | 72,273 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 65 |
| `BackwardCompoundCommand` | 51,834 |
| `DeleteSuffixCommand` | 14,977 |
| `PreserveCommand` | 7,699 |
| `ReplaceLastCharacterCommand` | 5,392 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.740% | 99.718% | 99.948% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 2879997984 | 0.000000% | 8325 / 1431757 | 0.581453% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 2879997984 | 0.000000% | 8325 / 1431757 | 0.581453% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 72,078 | 58.682% | 55.055% | 92.634% |
| 20% | 64,063 | 59.363% | 55.777% | 93.695% |
| 30% | 55,893 | 59.660% | 56.044% | 93.717% |
| 40% | 47,636 | 60.340% | 56.727% | 94.138% |
| 50% | 39,638 | 60.534% | 56.989% | 93.438% |
| 60% | 31,515 | 61.094% | 57.596% | 94.010% |
| 70% | 23,546 | 61.938% | 58.502% | 94.353% |
| 80% | 15,428 | 62.341% | 58.874% | 93.742% |
| 90% | 7,659 | 64.552% | 61.199% | 95.561% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 6.323 | 1.866 | 87.5 |
