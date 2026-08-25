# English Dictionary Coverage Benchmark

`EnglishRadixorDictionaryCoverageBenchmark` builds Radixor from deterministic slices of the English dictionary rows and evaluates accuracy against the complete dictionary. The speed method then stems the full changed-token English timing corpus.

Because the accuracy denominator includes both selected training rows and
withheld rows, this page is a reduced-training quality/speed operating curve—not
isolated held-out evidence. The
[multilingual dictionary-family generalization benchmark](../generalization.md)
adds five frozen splits, separate withheld counters, and an unseen-surface scope
for English and every other default language.

This benchmark is the clearest demonstration of the Radixor quality/speed envelope after contracted-trie compilation. More dictionary knowledge still gives the strongest changed-form precision, but uniform-subtree contraction removes much of the historical lookup-depth penalty. The table should therefore be read as a measured operating curve rather than as a strictly monotonic function of dictionary size.

The speed cells are JMH point estimates with their 99.9% score errors. The 100% row has the widest relative interval in this run (22.028%); small differences between coverage levels, and the non-monotonic ordering of nearby rows, must not be treated as a precise rank.

<!-- ENGLISH-SPEED-SUITES:START -->
!!! note "Separate English speed suites"
    The full-knowledge `97.9 ns/token` point on this page belongs to the coverage suite. The [English language comparison](../languages/english.md#speed) reports `80.8 ns/token` from a separate JMH method and run. Their uncertainty intervals overlap; neither point estimate should replace the other.
<!-- ENGLISH-SPEED-SUITES:END -->

| Used rows | Actual row ratio | All exact | Changed exact | Root preserved | Speed ms/op | Error ms | ns/token |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100% | 100.000% | 97.668% | 98.110% | 97.552% | 20.425 | 3.636 | 97.9 |
| 90% | 90.000% | 97.239% | 95.821% | 97.612% | 17.779 | 1.827 | 85.3 |
| 80% | 80.000% | 96.827% | 93.673% | 97.656% | 15.343 | 1.321 | 73.6 |
| 70% | 70.000% | 96.392% | 91.430% | 97.695% | 16.444 | 2.027 | 78.9 |
| 60% | 60.000% | 95.935% | 89.244% | 97.693% | 14.330 | 1.350 | 68.7 |
| 50% | 50.000% | 95.453% | 86.979% | 97.678% | 14.953 | 2.625 | 71.7 |
| 40% | 40.000% | 94.939% | 84.667% | 97.638% | 12.919 | 1.155 | 62.0 |
| 30% | 30.000% | 94.398% | 82.443% | 97.538% | 12.166 | 1.305 | 58.3 |
| 20% | 20.000% | 93.821% | 80.174% | 97.406% | 11.549 | 1.535 | 55.4 |
| 10% | 10.000% | 93.057% | 77.327% | 97.190% | 14.360 | 3.524 | 68.9 |

## Column Meanings

- `Used rows`: requested deterministic percentage of English dictionary rows used to build the trie.
- `Actual row ratio`: selected rows divided by all parsed English dictionary rows.
- `All exact`: exact agreement over the complete dictionary.
- `Changed exact`: exact agreement over dictionary tokens where `token != expectedRoot`.
- `Root preserved`: percentage of already-root dictionary tokens that are left unchanged.
- `Speed ms/op`: JMH average time for one full changed-token English operation.
- `Error ms`: JMH score error converted to milliseconds.
- `ns/token`: `Speed ms/op` divided by 208,540 changed English tokens.

For non-English languages, the same principle applies: dictionary-driven Radixor quality depends on the amount and consistency of the language resource, while contracted tries reduce the cost of uniform regions in the compiled lookup graph. The English table is the clearest because the English resource is large and the benchmark can show gradual deterministic reductions from 100% to 10%.

## Why The Historical Porter Ratio Changed

The historical English benchmark in `HEAD` used synthetic lexical families. Its `familyCount=5000` parameter generated roughly 70,000 artificial tokens rather than measuring the complete real English dictionary resource. That older workload was useful as a low-level stress test, but it was not a dictionary-quality comparison. Many synthetic tokens were not present in the Radixor dictionary, so Radixor often executed a fast miss path where lookup returned `null` and no patch command was applied.

The current benchmark is intentionally based on real Radixor dictionary data. For English, the speed workload processes 208,540 changed token/root pairs where the dictionary token differs from the expected root, and the quality workload evaluates the complete 1,002,414-token dictionary. This is a hit-heavy workload that measures real lookup plus compiled patch-command application against known expected roots. It is therefore a different and more linguistically meaningful workload than the historical synthetic benchmark.

The result must be interpreted through both speed and exact-root quality. Non-Radixor stemmers have different transformation scopes and do not necessarily target the same dictionary root, so lower runtime can coexist with lower `All exact` and `Changed exact` scores.

Radixor uses the dictionary as training data for transformation rules. With the full English dictionary, it reaches much higher exact-root agreement than the Porter-family and other narrow baselines. Higher speed is still possible by reducing the amount or complexity of the input dictionary used to build the stemmer, but that is an explicit quality/speed trade-off rather than an accidental benchmark artifact.

The coverage table shows that contracted tries substantially improve the operating point. Reducing dictionary knowledge still primarily damages changed-form exactness, while root preservation remains high. Even when Radixor is trained from only 10% of the English dictionary rows, the complete-dictionary `All exact` score remains above 92%. This is why Radixor performance should be discussed as a configurable quality/speed point, not as a single fixed ratio against Porter.
