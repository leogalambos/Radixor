# Benchmark Environment And Reports

The values below are environment-specific and must not be read as universal performance claims.

| Item | Value |
| --- | --- |
| Benchmark date | 2026-07-03 |
| Focused comparison command family | JMH jar runs limited to `EnglishStemmerComparisonBenchmark`, `MultiLanguageStemmerComparisonBenchmark`, and `SnowballLanguageStemmerComparisonBenchmark`; Radixor exact-root metrics were recomputed deterministically against the same contracted loaders |
| English coverage command | `./gradlew jmh -Pjmh.includes='.*EnglishRadixorDictionaryCoverageBenchmark.*' --no-daemon` |
| Speed result reports | `build/reports/jmh/contracted/english-comparison.csv`, `multilanguage-speed.csv`, `snowball-language-speed.csv` |
| Accuracy result reports | Deterministic Radixor exact-root pass over bundled dictionaries; non-Radixor quality rows retained from the existing published quality suite |
| Final comparison JMH scope | Stemmer comparison benchmarks only; internal `FrequencyTrie*` microbenchmarks were not run |
| Coverage JMH scope | English Radixor dictionary coverage benchmark only |
| JMH version | 1.37 |
| Speed benchmark mode | Average time, `time/op` |
| Score unit | `ns/op` |
| Speed warmup | 3 iterations, 1 s each |
| Speed measurement | 5 iterations, 1 s each |
| Accuracy warmup | none for deterministic exact-root accounting |
| Accuracy measurement | 1 deterministic measurement iteration; counters only, not speed interpretation |
| Fork count in generated report files | 1 |
| Default fork policy for accuracy-only benchmark classes | `@Fork(0)` for future default runs because accuracy counters are deterministic and not interpreted as speed |
| Thread count | 1 |
| JVM reported by JMH | OpenJDK 64-Bit Server VM, 25.0.3+9 |
| JVM invoker | `/usr/lib/jvm/java-25-openjdk/bin/java` |
| Operating system | Linux 7.0.13-200.fc44.x86_64 |
| CPU | AMD Ryzen 5 7600 6-Core Processor |
| Logical CPUs | 12 |

## Contracted Trie Baseline

All Radixor rows in the refreshed benchmark tables use contracted compiled patch tries. During compilation, a subtree whose reachable entries all resolve to the same preferred patch command is represented as an accepting leaf. Runtime lookup can therefore stop as soon as that leaf is reached, which reduces depth in uniform regions while preserving the preferred result used by `get()`.

## Report Files

Generated local report files for this benchmark update:

- `build/reports/jmh/contracted/english-comparison.csv`
- `build/reports/jmh/contracted/english-comparison.txt`
- `build/reports/jmh/contracted/multilanguage-speed.csv`
- `build/reports/jmh/contracted/multilanguage-speed.txt`
- `build/reports/jmh/contracted/snowball-language-speed.csv`
- `build/reports/jmh/contracted/snowball-language-speed.txt`

JMH TXT and CSV reports are still published as benchmark artifacts. They are not converted into a Porter speed badge.

## Published Metrics

The historical English Radixor versus Porter performance badge is no longer generated. `tools/generate-pages-badges.py` now produces only coverage and mutation badge endpoint JSON files:

- `coverage-badge.json`
- `pitest-badge.json`

The README therefore no longer presents a single Porter speed ratio. Benchmark interpretation now uses both speed and quality, because a narrow or aggressive stemmer can be fast while disagreeing with the dictionary root much more often than Radixor.
