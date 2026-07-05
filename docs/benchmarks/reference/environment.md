# Benchmark Environment And Reports

The values below are environment-specific and must not be read as universal performance claims.

| Item | Value |
| --- | --- |
| Benchmark date | 2026-07-06 (Europe/Prague) |
| Focused comparison command family | `./gradlew jmh -Pjmh.includes='.*StemmerComparisonBenchmark.*' --no-daemon` |
| English coverage command | `./gradlew jmh -Pjmh.includes='.*EnglishRadixorDictionaryCoverageBenchmark.*' --no-daemon` |
| Speed result reports | `build/reports/jmh/stemmer-comparison-2026-07-06.csv`, `build/reports/jmh/stemmer-comparison-2026-07-06.txt`, `build/reports/jmh/english-coverage-2026-07-06.csv`, and `build/reports/jmh/english-coverage-2026-07-06.txt` |
| Accuracy result reports | `build/reports/jmh/stemmer-comparison-2026-07-06.csv`, `build/reports/jmh/english-coverage-2026-07-06.csv`, and deterministic Radixor exact-root accounting over the same bundled language corpora |
| Final comparison JMH scope | Stemmer comparison benchmarks only; internal `FrequencyTrie*` microbenchmarks were not run |
| Coverage JMH scope | English Radixor dictionary coverage benchmark only |
| JMH version | 1.37 |
| Speed benchmark mode | Average time, `time/op` |
| Score unit | `ns/op` |
| Speed warmup | 3 iterations, 1 s each |
| Speed measurement | 5 iterations, 1 s each |
| Accuracy warmup | 3 JMH warmup iterations were applied by the Gradle invocation; timing scores from quality methods are not interpreted |
| Accuracy measurement | 5 JMH measurement samples; documentation uses deterministic auxiliary counter ratios from the same report |
| Fork count in generated report files | 1 |
| Default fork policy for accuracy-only benchmark classes | `@Fork(0)` for future default runs because accuracy counters are deterministic and not interpreted as speed |
| Thread count | 1 |
| JVM reported by JMH | JDK 25.0.3, OpenJDK 64-Bit Server VM, 25.0.3+9 |
| Java runtime | OpenJDK Runtime Environment, Red Hat build 25.0.3+9 |
| JVM invoker | `/usr/lib/jvm/java-25-openjdk/bin/java` |
| Operating system | Fedora Linux 44 (MATE-Compiz) |
| Kernel | Linux 7.0.12-201.fc44.x86_64 |
| Architecture | x86_64 |
| CPU | AMD Ryzen 5 8600G w/ Radeon 760M Graphics |
| Physical cores | 6 |
| Logical CPUs | 12 |

## Contracted Trie Baseline

All Radixor rows in the refreshed benchmark tables use contracted compiled patch tries. During compilation, a subtree whose reachable entries all resolve to the same preferred patch command is represented as an accepting leaf. Runtime lookup can therefore stop as soon as that leaf is reached, which reduces depth in uniform regions while preserving the preferred result used by `get()`.

## Report Files

Generated local report files for this benchmark update:

- `build/reports/jmh/stemmer-comparison-2026-07-06.csv`
- `build/reports/jmh/stemmer-comparison-2026-07-06.txt`
- `build/reports/jmh/english-coverage-2026-07-06.csv`
- `build/reports/jmh/english-coverage-2026-07-06.txt`

JMH TXT and CSV reports are still published as benchmark artifacts. They are not converted into a Porter speed badge.

## Published Metrics

The historical English Radixor versus Porter performance badge is no longer generated. `tools/generate-pages-badges.py` now produces only coverage and mutation badge endpoint JSON files:

- `coverage-badge.json`
- `pitest-badge.json`

The README therefore no longer presents a single Porter speed ratio. Benchmark interpretation now uses both speed and quality, because a narrow or aggressive stemmer can be fast while disagreeing with the dictionary root much more often than Radixor.
