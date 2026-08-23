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

| Used rows | Actual row ratio | All exact | Changed exact | Root preserved | Speed ms/op | Error ms | ns/token |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100% | 100.000% | 97.478% | 97.197% | 97.552% | 18.349 | 4.042 | 87.2 |
| 90% | 90.000% | 97.047% | 94.913% | 97.613% | 14.767 | 1.331 | 70.2 |
| 80% | 80.000% | 96.635% | 92.768% | 97.661% | 14.233 | 1.242 | 67.6 |
| 70% | 70.000% | 96.209% | 90.565% | 97.705% | 15.195 | 2.398 | 72.2 |
| 60% | 60.000% | 95.750% | 88.384% | 97.703% | 13.539 | 1.173 | 64.3 |
| 50% | 50.000% | 95.262% | 86.107% | 97.690% | 12.624 | 1.054 | 60.0 |
| 40% | 40.000% | 94.753% | 83.855% | 97.643% | 13.419 | 1.587 | 63.8 |
| 30% | 30.000% | 94.208% | 81.651% | 97.537% | 12.299 | 1.418 | 58.4 |
| 20% | 20.000% | 93.633% | 79.366% | 97.416% | 10.937 | 1.250 | 52.0 |
| 10% | 10.000% | 92.868% | 76.516% | 97.204% | 10.911 | 1.798 | 51.8 |

## Column Meanings

- `Used rows`: requested deterministic percentage of English dictionary rows used to build the trie.
- `Actual row ratio`: selected rows divided by all parsed English dictionary rows.
- `All exact`: exact agreement over the complete dictionary.
- `Changed exact`: exact agreement over dictionary tokens where `token != expectedRoot`.
- `Root preserved`: percentage of already-root dictionary tokens that are left unchanged.
- `Speed ms/op`: JMH average time for one full changed-token English operation.
- `Error ms`: JMH score error converted to milliseconds.
- `ns/token`: `Speed ms/op` divided by 210,500 changed English tokens.

For non-English languages, the same principle applies: dictionary-driven Radixor quality depends on the amount and consistency of the language resource, while contracted tries reduce the cost of uniform regions in the compiled lookup graph. The English table is the clearest because the English resource is large and the benchmark can show gradual deterministic reductions from 100% to 10%.

## Why The Historical Porter Ratio Changed

The historical English benchmark in `HEAD` used synthetic lexical families. Its `familyCount=5000` parameter generated roughly 70,000 artificial tokens rather than measuring the complete real English dictionary resource. That older workload was useful as a low-level stress test, but it was not a dictionary-quality comparison. Many synthetic tokens were not present in the Radixor dictionary, so Radixor often executed a fast miss path where lookup returned `null` and no patch command was applied.

The current benchmark is intentionally based on real Radixor dictionary data. For English, the speed workload processes 210,500 changed token/root pairs where the dictionary token differs from the expected root, and the quality workload evaluates the complete 1,004,374-token dictionary. This is a hit-heavy workload that measures real lookup plus compiled patch-command application against known expected roots. It is therefore a different and more linguistically meaningful workload than the historical synthetic benchmark.

The result must be interpreted through both speed and exact-root quality. Non-Radixor stemmers have different transformation scopes and do not necessarily target the same dictionary root, so lower runtime can coexist with lower `All exact` and `Changed exact` scores.

Radixor uses the dictionary as training data for transformation rules. With the full English dictionary, it reaches much higher exact-root agreement than the Porter-family and other narrow baselines. Higher speed is still possible by reducing the amount or complexity of the input dictionary used to build the stemmer, but that is an explicit quality/speed trade-off rather than an accidental benchmark artifact.

The coverage table shows that contracted tries substantially improve the operating point. Reducing dictionary knowledge still primarily damages changed-form exactness, while root preservation remains high. Even when Radixor is trained from only 10% of the English dictionary rows, the complete-dictionary `All exact` score remains above 92%. This is why Radixor performance should be discussed as a configurable quality/speed point, not as a single fixed ratio against Porter.
