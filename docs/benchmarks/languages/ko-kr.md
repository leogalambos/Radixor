# Korean Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 192,673 distinct usable word forms" title="Relative dictionary size 5 of 5; 192,673 distinct usable word forms">★★★★★</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `ko-kr-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Selected data file lacks file-applicable license evidence](https://github.com/unimorph/kor). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 5 of 5; 192,673 distinct usable word forms" title="Relative dictionary size 5 of 5; 192,673 distinct usable word forms">★★★★★</span>. The exact count is **192,673 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `ko-kr-default` | `1.0.0` | `KO_KR` | 2,672 | 192,673 | 196,302 | 5,344 | 190,958 | changed tokens | 190,958 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `AppendCharacterCommand` | 247 |
| `BackwardCompoundCommand` | 166,837 |
| `DeleteSuffixCommand` | 6 |
| `PreserveCommand` | 5,346 |
| `ReplaceLastCharacterCommand` | 23,866 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 99.512% | 99.499% | 99.963% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 18554200635 | 0.000000% | 29214 / 7145493 | 0.408845% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 18554200635 | 0.000000% | 29214 / 7145493 | 0.408845% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 176,512 | 81.085% | 80.561% | 99.792% |
| 20% | 156,840 | 81.037% | 80.512% | 99.766% |
| 30% | 136,808 | 82.501% | 82.016% | 99.786% |
| 40% | 117,008 | 81.947% | 81.444% | 99.813% |
| 50% | 97,497 | 82.828% | 82.353% | 99.775% |
| 60% | 78,230 | 83.945% | 83.502% | 99.719% |
| 70% | 58,487 | 82.242% | 81.745% | 99.625% |
| 80% | 38,779 | 81.688% | 81.167% | 99.812% |
| 90% | 19,450 | 82.959% | 82.480% | 100.000% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 21.239 | 3.762 | 111.2 |
