# Basque Stemmer Benchmarks <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 10,401 distinct usable word forms" title="Relative dictionary size 3 of 5; 10,401 distinct usable word forms">★★★☆☆</span>

!!! warning "Benchmark-only dictionary — not distributed"
    `eus-default` is privately retained for reproducible measurement only. Radixor does not distribute, install, publish, register, or support this dictionary because file-applicable redistribution rights are unresolved. It has no Maven coordinates, BOM/catalog entry, standard-package membership, or Python package.

Evidence: [Repository declares License n/a](https://github.com/unimorph/eus). Benchmarking these private bytes does not assert a right to redistribute them.

<!-- DICTIONARY-SIZE-RATING:START -->
Dictionary size: <span class="dictionary-rating" role="img" aria-label="Relative dictionary size 3 of 5; 10,401 distinct usable word forms" title="Relative dictionary size 3 of 5; 10,401 distinct usable word forms">★★★☆☆</span>. The exact count is **10,401 distinct usable word forms** after parser-compatible filtering and exact case-preserved deduplication. Stars rank size among all 167 benchmarked dictionaries; they do **not** measure linguistic quality, availability, or benchmark accuracy.
<!-- DICTIONARY-SIZE-RATING:END -->

The corpus, quality, generalization, and speed evidence below belongs to the private `2026-09-11` Radixor/Java `4.4.0` documentation snapshot. It is never placed on a production or release classpath.

## Dictionary Corpus

| Model ID | Private version | Language | Dictionary rows | Distinct usable forms | Complete tokens | Already-root | Changed | Timing workload | Timing tokens |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| `eus-default` | `1.0.0` | `EUS` | 26 | 10,401 | 11,535 | 52 | 11,483 | changed tokens | 11,483 |

## Radixor Patch Command Distribution

| Command class | Complete-corpus forms |
| --- | ---: |
| `BackwardCompoundCommand` | 11,445 |
| `DeleteSuffixCommand` | 30 |
| `PreserveCommand` | 52 |
| `ReplaceLastCharacterCommand` | 8 |

## Exact-root Accuracy

| Stemmer | All exact | Changed exact | Root preserved |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 90.394% | 90.351% | 100.000% |
| Official Snowball 3.1.0 direct | 0.416% | 0.070% | 76.923% |

## Pairwise Linguistic Quality

OI and UI report over-stemming and under-stemming with raw numerators and denominators. A zero denominator is `n/a`.

| Stemmer | Dictionary mode | OI raw | OI | UI raw | UI |
| --- | --- | ---: | ---: | ---: | ---: |
| Private Radixor filesystem model | `ALL_WORDS` | 0 / 50961037 | 0.000000% | 304328 / 3124163 | 9.741105% |
| Private Radixor filesystem model | `LOWERCASE_GROUPS_ONLY` | 0 / 50961037 | 0.000000% | 304328 / 3124163 | 9.741105% |
| Official Snowball 3.1.0 direct | `ALL_WORDS` | 10387 / 50961037 | 0.020382% | 3105390 / 3124163 | 99.399103% |
| Official Snowball 3.1.0 direct | `LOWERCASE_GROUPS_ONLY` | 10387 / 50961037 | 0.020382% | 3105390 / 3124163 | 99.399103% |

## Dictionary-family Generalization

This frozen five-seed curve measures transfer to withheld lexical families; it does not establish redistribution or support status.

| Training rows | Median unseen occurrences | Unseen all exact | Unseen changed exact | Unseen root preserved |
| ---: | ---: | ---: | ---: | ---: |
| 10% | 10,316 | 0.479% | 0.000% | 100.000% |
| 20% | 9,285 | 0.671% | 0.150% | 100.000% |
| 30% | 7,273 | 0.687% | 0.179% | 100.000% |
| 40% | 5,858 | 0.819% | 0.275% | 100.000% |
| 50% | 4,473 | 0.805% | 0.256% | 100.000% |
| 60% | 2,943 | 0.740% | 0.000% | 100.000% |
| 70% | 2,657 | 1.217% | 0.000% | 100.000% |
| 80% | 1,625 | 1.600% | 0.991% | 100.000% |
| 90% | 1,187 | 1.853% | 1.355% | 100.000% |
| 100% | 0 | n/a | n/a | n/a |

## Speed

JMH average time uses 3 one-second warmup iterations, 5 one-second measurement iterations, 3 independent forks, and 1 thread.

| Benchmark | Score ms/op | Error ms | ns/token |
| --- | ---: | ---: | ---: |
| Private Radixor filesystem model | 1.159 | 0.134 | 100.9 |
| Official Snowball 3.1.0 direct | 1.935 | 0.179 | 168.5 |
