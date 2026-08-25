# Benchmark Environment And Reports

The values below are environment-specific and must not be read as universal performance claims.

## Multilingual Generalization Run

The generalization report is deterministic and publishes no runtime score, so
CPU frequency, background load, and elapsed time do not affect its accuracy
counters. It was nevertheless generated on the same identified workstation:

| Item | Value |
| --- | --- |
| Evaluation date | 2026-08-25 (Europe/Prague) |
| Command | `./gradlew --no-daemon -PdictionaryGeneralizationReleaseVersion=4.2.0-6-g84e57fb dictionaryGeneralization` |
| Radixor/Java source identity | `4.2.0-6-g84e57fb` |
| Report | `build/reports/generalization/dictionary-generalization.csv` |
| Published snapshot | `docs/benchmarks/data/dictionary-generalization.csv` |
| Scenarios | 1,000: 20 default models × 10 coverage levels × 5 frozen splits |
| Evaluation type | Raw deterministic exact-root counters; no elapsed-time value is interpreted |
| Java runtime | OpenJDK 25.0.4, Red Hat build 25.0.4+7 |
| Operating system | Fedora Linux 44 (MATE-Compiz) |
| Kernel | Linux 7.1.8-200.fc44.x86_64 |
| CPU | AMD Ryzen 5 8600G, 6 physical / 12 logical CPUs |
| Architecture | x86_64 |
| Split protocol | `radixor-generalization-v1`; exact-size nested row prefixes |
| Provenance | Every CSV row records model ID, independent model version, and compressed-resource SHA-256 |

The CPU was configured with the `performance` governor, but that fact is not
used to support any generalization claim. The new report deliberately excludes
speed; the Java timing environment below remains the provenance for the
published 2026-08-25 JMH tables.

## Edit-Cost Sensitivity Run

| Item | Value |
| --- | --- |
| Evaluation date | 2026-08-25 (Europe/Prague) |
| Command | `./gradlew --no-daemon -PdictionaryGeneralizationReleaseVersion=4.2.0-6-g84e57fb editCostSensitivity` |
| Radixor/Java source identity | `4.2.0-6-g84e57fb` |
| Report | `build/reports/generalization/edit-cost-sensitivity.csv` |
| Published raw snapshot | `docs/benchmarks/data/edit-cost-sensitivity.csv.gz` |
| Physical / logical observations | 16,700 representatives / 234,000 expanded grid points |
| Matrix | 20 default models × 234 normalized cost points × 10 knowledge levels × 5 frozen splits |
| Runtime | 5 h 35 min 13 s |
| Maximum resident set | approximately 9.0 GiB; swap remained unused |
| Protocol | `radixor-cost-sensitivity-v4`; exact full-dictionary command equality |

The edit-cost report publishes deterministic structure and quality counters, not timing scores.
Elapsed time and memory describe the cost of reproducing the experiment and are not stemmer outcomes.

## Java Accuracy and Performance Run

| Item | Value |
| --- | --- |
| Benchmark date | 2026-08-25 (Europe/Prague) |
| Corpus command | `./gradlew benchmarkCorpusReport --no-daemon` |
| Exact-root accuracy command | `tools/run-published-accuracy-benchmarks.sh 2026-08-25`; all four `*BenchmarkQuality` classes are selected and timing scores are discarded |
| Stemming-quality command | `./gradlew stemmingQuality --no-daemon` |
| Published speed command | `tools/run-published-speed-benchmarks.sh 2026-08-25 4.2.0-6-g84e57fb` |
| Published speed run interval | 2026-08-25 14:33:15 to 16:27:55 Europe/Prague (1 h 54 min 40 s, including idle intervals and both JMH suites) |
| Stabilization intervals | 30 s before the main speed matrix; 15 s between the main matrix and coverage-speed suite |
| Corpus and command report | `build/reports/jmh/benchmark-corpora.csv` |
| Exact-root reports | `build/reports/jmh/stemmer-accuracy-2026-08-25.csv` and `.txt` |
| Speed reports | `build/reports/jmh/stemmer-speed-2026-08-25.csv` and `.txt` |
| English coverage accuracy reports | `build/reports/jmh/english-coverage-accuracy-2026-08-25.csv` and `.txt` |
| English coverage speed reports | `build/reports/jmh/english-coverage-speed-2026-08-25.csv` and `.txt` |
| Stemming-quality reports | `build/reports/stemming-quality/stemming-quality.csv` and `.md` |
| Environment report | `build/reports/jmh/performance-environment-2026-08-25.txt` |
| Selected speed methods | `build/reports/jmh/published-speed-benchmarks-2026-08-25.txt` |
| Comparison scope | Same-language methods used by the 20 language pages; `PolishPolimorfStemmerComparisonBenchmark`, all quality methods, the separate CISTEM gold-standard experiment, and internal trie microbenchmarks are excluded |
| Model scope | Exactly the 20 IDs declared by `Language.defaultModelId()`; Polish uses `pl-pl-unimorph`, and `pl-pl-polimorf` is not measured |
| Core base commit | `84e57fb27ae40913569e826858a8ebb07cf2ea01` |
| Release identity | Radixor/Java `4.2.0-6-g84e57fb`; exact measured tracked changes and untracked-source checksums are retained as `measured-source-2026-08-25.patch` and `measured-untracked-2026-08-25.sha256` |
| JMH version | 1.37 |
| Speed benchmark mode | Average time, `time/op` |
| Score unit | `ns/op`; language pages additionally derive `ms/op` and `ns/token` |
| Speed warmup | 5 iterations, 1 s each, independently in every fork |
| Speed measurement | 7 iterations, 1 s each, independently in every fork |
| Speed forks | 3 independent JVM forks |
| Speed threads | 1 |
| Speed fork heap | Fixed `-Xms6g -Xmx6g` |
| Reported uncertainty | JMH `Score Error (99.9%)` over 21 measured samples |
| Observed relative uncertainty | Main speed matrix: maximum 15.542%, with 18 of 105 rows above 10% and none above 20%; coverage-speed curve: maximum 24.542%, with 7 of 10 rows above 10% and 1 above 20% |
| Deterministic measurements | Corpus, patch-command distribution, exact-root counters, coverage accuracy, and pairwise stemming quality are evaluated without interpreting runtime scores; no warmup is required |
| JVM reported by JMH | JDK 25.0.4, OpenJDK 64-Bit Server VM, 25.0.4+7 |
| Java runtime | OpenJDK Runtime Environment, Red Hat build 25.0.4+7 |
| JVM invoker | `/usr/lib/jvm/java-25-openjdk/bin/java` |
| Operating system | Fedora Linux 44 (MATE-Compiz) |
| Kernel | Linux 7.1.8-200.fc44.x86_64 |
| Architecture | x86_64 |
| CPU | AMD Ryzen 5 8600G w/ Radeon 760M Graphics |
| Physical / logical CPUs | 6 / 12 |
| CPU frequency policy | `amd-pstate-epp`; governor `performance` on every logical CPU; EPP `performance`; boost enabled |
| CPU affinity | Scheduler default; no explicit pinning |
| Installed memory | 61 GiB reported by the operating system |
| Pre-run idle state | Load average 0.32 / 0.56 / 1.14 after the 30 s idle interval; CPU Tctl 42.8 degrees Celsius; swap unused |
| End-of-run state | Load average 1.17 / 1.21 / 1.27; CPU Tctl 68.9 degrees Celsius |
| Power and idle policy | Developer workstation on stable power; screensaver, suspend, and hibernation disabled |
| Concurrent project work | None during the published speed and coverage-speed run |

The workstation is not a hard real-time system. Normal kernel and desktop background activity was not removed, so the three independent forks and the published 99.9% error interval remain essential parts of result interpretation. Initial/final load and temperature sensor readings are stored in the environment report.

## Contracted Trie Baseline

All Radixor rows use contracted compiled patch tries. During compilation, a subtree whose reachable entries all resolve to the same preferred patch command is represented as an accepting leaf. Runtime lookup can therefore stop as soon as that leaf is reached while preserving the preferred result used by `get()`.

## Model And Source Identity

`benchmark-corpora.csv` records the model ID, independent artifact version, and descriptor SHA-256 for every language. Every stemming-quality CSV row repeats the same three fields. The performance environment report additionally records checksums of the executable JMH JAR, runtime classpath manifest, corpus report, quality report, measured source patch, and untracked-source manifest.

The JMH runtime classpath contains the optional model artifact because it is a separately testable project dependency. It is not selected by any published benchmark. The selected-method manifest rejects `PolishPolimorf`, and the corpus/quality publication validators reject any non-default Polish model.

## Report Files

Generated local report files for this benchmark update:

- `build/reports/generalization/dictionary-generalization.csv`
- `build/reports/generalization/edit-cost-sensitivity.csv`
- `build/reports/jmh/benchmark-corpora.csv`
- `build/reports/jmh/stemmer-accuracy-2026-08-25.csv`
- `build/reports/jmh/stemmer-accuracy-2026-08-25.txt`
- `build/reports/jmh/stemmer-speed-2026-08-25.csv`
- `build/reports/jmh/stemmer-speed-2026-08-25.txt`
- `build/reports/jmh/english-coverage-accuracy-2026-08-25.csv`
- `build/reports/jmh/english-coverage-accuracy-2026-08-25.txt`
- `build/reports/jmh/english-coverage-speed-2026-08-25.csv`
- `build/reports/jmh/english-coverage-speed-2026-08-25.txt`
- `build/reports/jmh/performance-environment-2026-08-25.txt`
- `build/reports/stemming-quality/stemming-quality.csv`
- `build/reports/stemming-quality/stemming-quality.md`
- `build/reports/stemming-quality/metric-correlations-pearson.csv`
- `build/reports/stemming-quality/metric-correlations-spearman.csv`

The versioned documentation snapshots under `docs/benchmarks/data/` preserve the complete
stemming-quality matrix, all 1,000 generalization scenarios, the compressed edit-cost raw matrix
plus derived analyses, and the dated CSV inputs for the published Java and Python performance
tables. The Python provenance JSON records its environment and run parameters. Detailed JMH TXT
logs and machine-state reports remain local build artifacts. The complete versioned input list and
checksums are published on the [reproducibility page](reproducibility.md#published-performance-snapshots).

## Published Metrics

The historical English Radixor versus Porter performance badge is retired. `tools/generate-pages-badges.py` produces only coverage and mutation badge endpoint JSON files. Benchmark interpretation uses both speed and quality because a narrow or aggressive stemmer can be fast while disagreeing with the dictionary root much more often than Radixor.
