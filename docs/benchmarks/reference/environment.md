# Benchmark Environment And Reports

The values below are environment-specific and must not be read as universal performance claims.

## Multilingual Generalization Run

The generalization report is deterministic and publishes no runtime score, so
CPU frequency, background load, and elapsed time do not affect its accuracy
counters. The active snapshot combines the immutable 20-language campaign with
the separately measured 123-language continuation:

| Item | Value |
| --- | --- |
| Evaluation dates | Historical 20 languages: 2026-08-25; 123-language continuation: 2026-09-11 (Europe/Prague) |
| Continuation command | `./gradlew --no-daemon -PdictionaryGeneralizationReleaseVersion=4.4.0 dictionaryGeneralizationStandalone` |
| Merge/publication command | `./gradlew --no-daemon mergeDictionaryGeneralizationSnapshots publishDictionaryGeneralizationDocumentation` |
| Radixor/Java source identities | Historical rows: `4.2.0-6-g84e57fb`; continuation rows: `4.4.0` at base revision `14f61beb106bca21d78f828e2f8e35fb457d3d90` |
| Continuation report | `build/reports/generalization/dictionary-generalization-standalone-2026-09-11.csv` |
| Active published snapshot | `docs/benchmarks/data/dictionary-generalization-2026-09-11.csv` |
| Historical archive | `docs/benchmarks/data/dictionary-generalization.csv` |
| Scenarios | 7,150: 143 default models × 10 coverage levels × 5 frozen splits |
| Continuation runtime | 6 min 34 s; informational only |
| Evaluation type | Raw deterministic exact-root counters; no elapsed-time value is interpreted |
| Java runtime | OpenJDK 25.0.4, Red Hat build 25.0.4+7 |
| Operating system | Fedora Linux 44 (MATE-Compiz) |
| Kernel | Linux 7.1.8-200.fc44.x86_64 |
| CPU | AMD Ryzen 5 8600G, 6 physical / 12 logical CPUs |
| Architecture | x86_64 |
| Split protocol | `radixor-generalization-v1`; exact-size nested row prefixes |
| Provenance | Every CSV row records model ID, independent model version, compressed-resource SHA-256, source revision/state, and generator SHA-256; each campaign has its own source manifest |

The CPU was configured with the `performance` governor, but that fact is not
used to support any generalization claim. The new report deliberately excludes
speed; the Java timing environment below remains the provenance for the
published 2026-09-11 JMH tables.

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
| Benchmark date | 2026-09-11 (Europe/Prague) |
| Corpus command | `./gradlew benchmarkCorpusReport --no-daemon` |
| Exact-root accuracy command | `tools/run-published-accuracy-benchmarks.sh 2026-09-11`; the exact-root benchmark suite is selected and timing scores are discarded |
| Pairwise stemming quality | Complete 143-language default-model snapshot published as `stemming-quality-2026-09-11.csv`; the prior 20-language CSV remains an immutable archive |
| Published speed command | `tools/run-published-speed-benchmarks.sh 2026-09-11 4.4.0` |
| Speed campaign | 2026-09-11 14:51:53 to 17:47:25 Europe/Prague; the main CSV was complete before the final English coverage pass |
| Stabilization interval | 30 s before the main speed matrix |
| Corpus and command report | `build/reports/jmh/benchmark-corpora.csv` |
| Exact-root reports | `build/reports/jmh/stemmer-accuracy-2026-09-11.csv` and `.txt` |
| Speed reports | `build/reports/jmh/stemmer-speed-2026-09-11.csv` and `.txt` |
| English coverage accuracy reports | `build/reports/jmh/english-coverage-accuracy-2026-09-11.csv` and `.txt` |
| English coverage speed reports | `build/reports/jmh/english-coverage-speed-2026-09-11.csv` and `.txt` |
| Stemming-quality reports | `build/reports/stemming-quality/stemming-quality.csv` and `.md` |
| Environment report | `build/reports/jmh/performance-environment-2026-09-11.txt` |
| Selected speed methods | `build/reports/jmh/published-speed-benchmarks-2026-09-11.txt` |
| Comparison scope | Parameterized Radixor measurements for all 144 user-facing model IDs plus approved authoritative same-language comparator paths, including the separately identified optional PoliMorf Morfologik row; quality methods, the CISTEM gold-standard experiment, ambiguous duplicate baselines, and internal trie microbenchmarks are excluded |
| Model scope | All 144 user-facing IDs: 143 language defaults plus the optional `pl-pl-polimorf`; default and optional Polish results retain distinct model identities |
| Core base commit | `14f61beb106bca21d78f828e2f8e35fb457d3d90` |
| Release identity | Radixor/Java `4.4.0`; exact measured tracked changes are retained as [measured-source-2026-09-11.patch](../data/measured-source-2026-09-11.patch) (SHA-256 `42cc2cc09bf0f829f0b4db6ac068aae9b44d950e9798b434d33a6092bb5b83f1`), and untracked-source checksums as [measured-untracked-2026-09-11.sha256](../data/measured-untracked-2026-09-11.sha256) (SHA-256 `decdee03e4d14caa3e8a70443d65cee0cce439ddc801e7a2cdb912d5b25ca800`) |
| JMH version | 1.37 |
| Speed benchmark mode | Average time, `time/op` |
| Score unit | `ns/op`; language pages additionally derive `ms/op` and `ns/token` |
| Speed warmup | 3 iterations, 1 s each, independently in every fork |
| Speed measurement | 5 iterations, 1 s each, independently in every fork |
| Speed forks | 3 independent JVM forks |
| Speed threads | 1 |
| Speed fork heap | Fixed `-Xms6g -Xmx6g` |
| Reported uncertainty | JMH `Score Error (99.9%)` over 15 measured samples |
| Observed relative uncertainty | Main speed matrix: maximum 32.763%, with 183 of 281 rows above 10% and 89 above 20%; coverage-speed curve: maximum 17.681%, with 5 of 10 rows above 10% and none above 20% |
| Deterministic measurements | Corpus, patch-command distribution, exact-root counters, coverage accuracy, and pairwise stemming quality are evaluated without interpreting runtime scores; no warmup is required |
| JVM reported by JMH | JDK 25.0.4.1, OpenJDK 64-Bit Server VM, 25.0.4.1+1 |
| Java runtime | OpenJDK Runtime Environment, Red Hat build 25.0.4.1+1 |
| JVM invoker | `/usr/lib/jvm/java-25-openjdk/bin/java` |
| Operating system | Fedora Linux 44 (MATE-Compiz) |
| Kernel | Linux 7.1.13-200.fc44.x86_64 |
| Architecture | x86_64 |
| CPU | AMD Ryzen 5 8600G w/ Radeon 760M Graphics |
| Physical / logical CPUs | 6 / 12 |
| CPU frequency policy | `amd-pstate-epp`; governor `performance` on every logical CPU; EPP `performance`; boost enabled |
| CPU affinity | Scheduler default; no explicit pinning |
| Installed memory | 61 GiB reported by the operating system |
| Pre-run idle state | Load average 2.07 / 2.22 / 2.22 after the 30 s idle interval; CPU Tctl 52.8 degrees Celsius; swap unused |
| End of coverage run | Load average 1.83 / 1.67 / 1.56; CPU Tctl 64.2 degrees Celsius; swap unused |
| Power and idle policy | Developer workstation on stable power; screensaver, suspend, and hibernation disabled |
| Concurrent project work | None during the published speed and coverage-speed run |

The workstation is not a hard real-time system. Normal kernel and desktop background activity was not removed, so the three independent forks and the published 99.9% error interval remain essential parts of result interpretation. Initial/final load and temperature sensor readings are stored in the environment report.

## Contracted Trie Baseline

All Radixor rows use contracted compiled patch tries. During compilation, a subtree whose reachable entries all resolve to the same preferred patch command is represented as an accepting leaf. Runtime lookup can therefore stop as soon as that leaf is reached while preserving the preferred result used by `get()`.

## Model And Source Identity

`benchmark-corpora.csv` records the model ID, independent artifact version, and descriptor SHA-256 for all 144 user-facing models. Every active stemming-quality CSV row repeats the same three fields for its complete 143-default-language scope. The performance environment report additionally records checksums of the executable JMH JAR, runtime classpath manifest, corpus report, quality report, measured source patch, and untracked-source manifest.

The parameterized Radixor speed benchmark selects every one of the 144 model IDs, including optional PoliMorf under its separate identity. Pairwise stemming quality covers all 143 default languages; the prior 20-language file is retained only as an immutable archive. The contemporaneous 167-entry runtime-classpath content manifest has SHA-256 `482050ce83b50bae26dd894610871ca320a6953cbfe4238a88257fd3f20fafda`; the validated main speed CSV has SHA-256 `4dd2923a9f5cad599b5aee7146f155e9a76766714134647ce885d4db70839f37`.

## Report Files

Generated local report files for this benchmark update:

- `build/reports/generalization/dictionary-generalization-standalone-2026-09-11.csv`
- `build/reports/generalization/edit-cost-sensitivity.csv`
- `build/reports/jmh/benchmark-corpora.csv`
- `build/reports/jmh/stemmer-accuracy-2026-09-11.csv`
- `build/reports/jmh/stemmer-accuracy-2026-09-11.txt`
- `build/reports/jmh/stemmer-speed-2026-09-11.csv`
- `build/reports/jmh/stemmer-speed-2026-09-11.txt`
- `build/reports/jmh/english-coverage-accuracy-2026-09-11.csv`
- `build/reports/jmh/english-coverage-accuracy-2026-09-11.txt`
- `build/reports/jmh/english-coverage-speed-2026-09-11.csv`
- `build/reports/jmh/english-coverage-speed-2026-09-11.txt`
- `build/reports/jmh/performance-environment-2026-09-11.txt`
- `build/reports/stemming-quality/stemming-quality.csv`
- `build/reports/stemming-quality/stemming-quality.md`
- `build/reports/stemming-quality/metric-correlations-pearson.csv`
- `build/reports/stemming-quality/metric-correlations-spearman.csv`

The versioned documentation snapshots under `docs/benchmarks/data/` preserve the complete
stemming-quality matrix, all 7,150 active generalization scenarios, the compressed historical edit-cost raw matrix
plus derived analyses, and the dated CSV inputs for the published Java and Python performance
tables. The Python provenance JSON records its environment and run parameters. Detailed JMH TXT
logs and machine-state reports remain local build artifacts. The complete versioned input list and
checksums are published on the [reproducibility page](reproducibility.md#published-performance-snapshots).

## Published Metrics

The historical English Radixor versus Porter performance badge is retired. `tools/generate-pages-badges.py` produces only coverage and mutation badge endpoint JSON files. Benchmark interpretation uses both speed and quality because a narrow or aggressive stemmer can be fast while disagreeing with the dictionary root much more often than Radixor.
