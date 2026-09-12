# Welsh Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 6,240 distinct usable word forms" title="Relative dictionary size 3 of 5; 6,240 distinct usable word forms">★★★☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `cy-gb-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/cym). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 6,240 distinct usable word forms" title="Relative dictionary size 3 of 5; 6,240 distinct usable word forms">★★★☆☆</span>. The exact count is **6,240 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `cy-gb-default` | `1.0.0` | `CY_GB` | 183 | 6,240 | 6,425 | 366 | 6,059 | changed tokens | 6,059 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 56 |
| `BackwardCompoundCommand` | 4,278 |
| `DeleteSuffixCommand` | 1,406 |
| `PreserveCommand` | 366 |
| `ReplaceLastCharacterCommand` | 319 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.969% | 99.967% | 100.000% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 19354316 | 0.000000% | 78 / 111364 | 0.070041% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 19354316 | 0.000000% | 78 / 111364 | 0.070041% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 5,768 | 11.486% | 7.127% | 89.091% |
| 20% | 5,107 | 15.960% | 11.854% | 82.192% |
| 30% | 4,456 | 21.430% | 17.788% | 81.250% |
| 40% | 3,828 | 24.115% | 20.552% | 82.727% |
| 50% | 3,196 | 25.487% | 22.226% | 83.516% |
| 60% | 2,567 | 27.346% | 23.509% | 83.562% |
| 70% | 1,954 | 30.061% | 26.902% | 83.636% |
| 80% | 1,327 | 30.671% | 27.694% | 81.081% |
| 90% | 647 | 33.965% | 31.538% | 83.333% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 0.368 | 0.075 | 60.7 |
