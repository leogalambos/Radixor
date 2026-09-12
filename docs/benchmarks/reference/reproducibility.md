# Reproducibility and Raw Data

## Published quality snapshot

- Active machine-readable CSV: [stemming-quality-2026-09-11.csv](../data/stemming-quality-2026-09-11.csv)
- SHA-256 record: [stemming-quality-2026-09-11.sha256](../data/stemming-quality-2026-09-11.sha256)
- SHA-256: `24bddfeed06a60bb3eeed58e1bfc93aed46c1d32e7bebd293aec2dacfddfef5b`
- Complete scenarios: 1,094
- Authoritative universe: 143 language defaults; optional PoliMorf is explicitly outside this snapshot
- Historical archive: [stemming-quality.csv](../data/stemming-quality.csv), preserved byte-for-byte with SHA-256 `85763189eab4d0fbb047c2d5d3554c66abf9732182bd0d8fd758d7aef680e66f`

The CSV contains the model ID, independent model version, descriptor SHA-256, raw pair counts, raw over/under numerators and denominators, candidate statistics, and relation metrics. Reserved partition-metric columns remain empty because the gold standard is an overlapping cover. Documentation is regenerated from this file rather than manually transcribed. Publication fails when any row uses a model other than the language's registered default. Empty metric fields with zero denominators render as `n/a`; the raw counts remain published. `ANY_CANDIDATE` is an oracle-bound policy and has no coherent confusion matrix.

The [active snapshot manifest](../data/active-snapshots.properties) selects the complete current
performance campaign (corpus, exact-root accuracy, speed, both English coverage inputs, and the
Python CSV/JSON pair),
quality, generalization, its measured-source manifest, and the comparator catalog. A refresh adds
dated files and changes the pointers; it never repurposes an archived file name.

## Published generalization snapshot

- Active machine-readable CSV: [dictionary-generalization-2026-09-11.csv](../data/dictionary-generalization-2026-09-11.csv)
- SHA-256 record: [dictionary-generalization-2026-09-11.sha256](../data/dictionary-generalization-2026-09-11.sha256)
- SHA-256: `187a9fe7b7bb292ac8d36b43784dc63a6e6e749942e86f20e7cb26364b7ea8d5`
- Continuation measured-source manifest: [dictionary-generalization-2026-09-11-sources.txt](../data/dictionary-generalization-2026-09-11-sources.txt)
- Continuation source-manifest checksum: [dictionary-generalization-2026-09-11-sources.sha256](../data/dictionary-generalization-2026-09-11-sources.sha256), SHA-256 `952399251517793ae381b419c7c917e9ea78bfe69b03db37b6be89173f8983fa`
- Complete scenarios: 7,150
- Matrix: 143 default models × 10 coverage levels × 5 frozen splits
- Measured source identities: the archived 20-language Radixor/Java `4.2.0-6-g84e57fb` campaign plus the separately recorded 123-language `4.4.0` continuation
- Historical archive: [dictionary-generalization.csv](../data/dictionary-generalization.csv), preserved byte-for-byte with SHA-256 `e6479840b9307ae03bd0873e55f397811e975125d621a8b8716d4c1a166b3ff2`
- Historical measured-source manifest: [dictionary-generalization-sources.sha256](../data/dictionary-generalization-sources.sha256)

This CSV retains integer numerators and denominators for complete-dictionary,
withheld-row, and unseen-surface scopes. It also records selected and total rows,
overlap exclusions, split seed, protocol version, and exact model provenance.
The [generalization methodology](generalization-methodology.md) defines the
frozen split and the limits of the claim.

The two campaign-specific manifests record the byte identity of the generator and the Java
implementation files that determine trie construction, traversal, patch encoding, and lookup.
Every row retains its campaign's base revision and measured source state; the active snapshot does
not infer a clean release tag for the pre-release continuation.

## Published edit-cost snapshot

- Compressed raw CSV: [edit-cost-sensitivity.csv.gz](../data/edit-cost-sensitivity.csv.gz)
- SHA-256 record: [edit-cost-sensitivity.csv.sha256](../data/edit-cost-sensitivity.csv.sha256)
- Physical observations: 16,700 exact command-equivalence representatives
- Logical observations: 234,000 after deterministic class expansion
- Matrix: 20 default models × 234 normalized cost points × 10 knowledge levels × 5 frozen splits
- Protocol: `radixor-cost-sensitivity-v4`

The checked-in derived CSV files preserve recommendations, macro and per-language knowledge curves,
macro and per-language within-stratum correlations, and dictionary sensitivity. In particular,
`edit-cost-language-knowledge-curve.csv` and `edit-cost-language-correlations.csv` are the direct
machine-readable support for the conclusions generated into all 20 language pages. The
[edit-cost methodology](edit-cost-methodology.md) defines exact full-dictionary
class membership, the frozen selection rule, and the limits of the exploratory claims.

## Published performance snapshots

The Java comparison tables, English coverage curve, Python runtime page, landing-page figures,
and technology summary are regenerated from the dated inputs below rather than from disposable
files under `build/reports/`:

- [Current Java corpus and command report](../data/java-benchmark-corpora-2026-09-11.csv)
- [Current Java exact-root report](../data/java-stemmer-accuracy-2026-09-11.csv)
- [Current Java speed report](../data/java-stemmer-speed-2026-09-11.csv)
- [Current English coverage accuracy report](../data/java-english-coverage-accuracy-2026-09-11.csv)
- [Current English coverage speed report](../data/java-english-coverage-speed-2026-09-11.csv)
- [Current Python all-language batch CSV](../data/python-all-languages-batch-2026-09-11.csv)
- [Current Python all-language provenance JSON](../data/python-all-languages-batch-2026-09-11.json)
- [Previous Java corpus and command report](../data/java-benchmark-corpora-2026-08-25.csv)
- [Previous Java exact-root report](../data/java-stemmer-accuracy-2026-08-25.csv)
- [Previous Java speed report](../data/java-stemmer-speed-2026-08-25.csv)
- [Previous English coverage accuracy report](../data/java-english-coverage-accuracy-2026-08-25.csv)
- [Previous English coverage speed report](../data/java-english-coverage-speed-2026-08-25.csv)
- [Previous Python all-language batch CSV](../data/python-all-languages-batch-2026-08-25.csv)
- [Previous Python all-language provenance JSON](../data/python-all-languages-batch-2026-08-25.json)
- [SHA-256 manifest](../data/performance-snapshots.sha256)

The Python JSON retains the measured environment, parameters, and detailed result provenance.
Machine-specific prefixes in `backing_file` values are normalized to `<repository>` before
publication; numeric results and environment values are unchanged. The Java and Python publishers
have separate `update` and non-writing `verify` modes. Gradle `check` runs both verifiers against
these checked-in inputs and verifies every checksum in the manifest.

## Commands

```bash
./gradlew --no-daemon stemmingQuality
./gradlew --no-daemon publishStemmingQualityDocumentation \
    verifyStemmingQualityDocumentation
./gradlew --no-daemon \
    -PdictionaryGeneralizationReleaseVersion=4.4.0 \
    dictionaryGeneralizationStandalone
./gradlew --no-daemon mergeDictionaryGeneralizationSnapshots \
    publishDictionaryGeneralizationDocumentation
python3 tools/update-generalization-documentation.py \
    docs/benchmarks/data/dictionary-generalization-2026-09-11.csv docs update \
    --corpus docs/benchmarks/data/java-benchmark-corpora-2026-09-10.csv \
    --snapshot-name dictionary-generalization-2026-09-11.csv
./gradlew --no-daemon \
    -PdictionaryGeneralizationReleaseVersion=4.2.0-6-g84e57fb \
    editCostSensitivity
python3 tools/update-edit-cost-documentation.py \
    build/reports/generalization/edit-cost-sensitivity.csv docs update
python3 tools/update-edit-cost-documentation.py \
    docs/benchmarks/data/edit-cost-sensitivity.csv.gz docs verify
./gradlew --no-daemon benchmarkCorpusReport writeJmhRuntimeClasspath
tools/run-published-accuracy-benchmarks.sh 2026-09-11
tools/run-published-speed-benchmarks.sh 2026-09-11 4.4.0
./gradlew --no-daemon pythonBenchmarkAllLanguagesBatch
cp build/reports/jmh/benchmark-corpora.csv \
    docs/benchmarks/data/java-benchmark-corpora-2026-09-11.csv
cp build/reports/jmh/stemmer-accuracy-2026-09-11.csv \
    docs/benchmarks/data/java-stemmer-accuracy-2026-09-11.csv
cp build/reports/jmh/stemmer-speed-2026-09-11.csv \
    docs/benchmarks/data/java-stemmer-speed-2026-09-11.csv
cp build/reports/jmh/english-coverage-accuracy-2026-09-11.csv \
    docs/benchmarks/data/java-english-coverage-accuracy-2026-09-11.csv
cp build/reports/jmh/english-coverage-speed-2026-09-11.csv \
    docs/benchmarks/data/java-english-coverage-speed-2026-09-11.csv
cp build/reports/python-benchmarks/all-languages-batch.csv \
    docs/benchmarks/data/python-all-languages-batch-2026-09-11.csv
cp build/reports/python-benchmarks/all-languages-batch.json \
    docs/benchmarks/data/python-all-languages-batch-2026-09-11.json
sed -i "s#${PWD}#<repository>#g" \
    docs/benchmarks/data/python-all-languages-batch-2026-09-11.json
(cd docs/benchmarks/data && sha256sum \
    java-benchmark-corpora-2026-08-25.csv \
    java-stemmer-accuracy-2026-08-25.csv \
    java-stemmer-speed-2026-08-25.csv \
    java-english-coverage-accuracy-2026-08-25.csv \
    java-english-coverage-speed-2026-08-25.csv \
    java-benchmark-corpora-2026-09-10.csv \
    java-stemmer-accuracy-2026-09-10.csv \
    java-stemmer-speed-2026-09-10.csv \
    java-english-coverage-accuracy-2026-09-10.csv \
    java-english-coverage-speed-2026-09-10.csv \
    java-benchmark-corpora-2026-09-11.csv \
    java-stemmer-accuracy-2026-09-11.csv \
    java-stemmer-speed-2026-09-11.csv \
    java-english-coverage-accuracy-2026-09-11.csv \
    java-english-coverage-speed-2026-09-11.csv \
    python-all-languages-batch-2026-08-25.csv \
    python-all-languages-batch-2026-08-25.json \
    python-all-languages-batch-2026-09-11.csv \
    python-all-languages-batch-2026-09-11.json \
    > performance-snapshots.sha256)
python3 tools/update-benchmark-documentation.py \
    --corpus docs/benchmarks/data/java-benchmark-corpora-2026-09-11.csv \
    --accuracy docs/benchmarks/data/java-stemmer-accuracy-2026-09-11.csv \
    --speed docs/benchmarks/data/java-stemmer-speed-2026-09-11.csv \
    --coverage-accuracy docs/benchmarks/data/java-english-coverage-accuracy-2026-09-11.csv \
    --coverage-speed docs/benchmarks/data/java-english-coverage-speed-2026-09-11.csv \
    --quality docs/benchmarks/data/stemming-quality-2026-09-11.csv \
    --snowball-catalog docs/benchmarks/data/snowball-language-cases.csv \
    --date 2026-09-11 \
    --release-version 4.4.0 \
    --mode update
python3 tools/update-python-benchmark-documentation.py \
    --csv docs/benchmarks/data/python-all-languages-batch-2026-09-11.csv \
    --json docs/benchmarks/data/python-all-languages-batch-2026-09-11.json \
    --quality docs/benchmarks/data/stemming-quality-2026-09-11.csv \
    --geography docs/benchmarks/data/homepage-language-geography.csv \
    --date 2026-09-11 \
    --release-version 4.4.0 \
    --model-package-version 3.0.0 \
    --base-commit 14f61be \
    --mode update
./gradlew --no-daemon verifyPublishedPerformanceSnapshotChecksums \
    verifyPublishedJavaBenchmarkDocumentation \
    verifyPublishedPythonBenchmarkDocumentation
./gradlew test
./gradlew prepareMkDocsSource
mkdocs build --strict --config-file build/mkdocs/mkdocs.yml
```

For an immediate local preview, `mkdocs serve` works directly from the repository root. The checked-in
model catalog makes that source tree complete. Its refresh is an explicit author-controlled source
operation: after changing model metadata or model bytes, run
`./gradlew publishModelCatalogDocumentation`, inspect the diff, and commit it. Pages publication consumes
the checked-in catalog and must never rewrite it ad hoc.

`stemmingQuality` performs the expensive complete evaluation and is intentionally not attached to `test` or `check`. It prepares JMH third-party dependencies automatically and writes:

- `build/reports/stemming-quality/stemming-quality.csv`
- `build/reports/stemming-quality/stemming-quality.md`
- `build/reports/stemming-quality/metric-correlations-pearson.csv`
- `build/reports/stemming-quality/metric-correlations-spearman.csv`

Audit mode is enabled with `-PstemmingQualityAudit=true`. Language, stemmer, dictionary-mode, output-policy, and ranking filters are documented on the central [stemming-quality page](../../stemming-quality.md). Filtered reports use separate filenames and cannot be accepted as publication sources.

`publishStemmingQualityDocumentation` validates the complete build CSV, copies it to a new dated documentation snapshot, updates the active pointer, and replaces only marked generated sections. It never overwrites the historical `stemming-quality.csv`. `verifyStemmingQualityDocumentation` re-renders from the active checked-in snapshot and fails on changed values, ordering, missing pages, duplicate keys, arithmetic inconsistencies, policy violations, or stale sections.

The staged site under `build/` is disposable output. The canonical model catalog is the reviewed,
checked-in `docs/stemmer-model-catalog.md`; the staged copy must remain identical to it.

When refreshing the dated performance snapshot, copy the five generated Java CSV files and the
two Python batch files to correspondingly dated names under `docs/benchmarks/data/`, normalize only
the Python `backing_file` repository prefix, update `performance-snapshots.sha256`, run both
publishers in `update` mode, inspect every generated documentation diff, and then run the three
Gradle verification tasks above. These source snapshots and their manifest are the manual files
that must accompany an accepted benchmark documentation refresh. Finally, update the dated input
paths and the `--date`, `--release-version`, and `--base-commit` arguments of
`verifyPublishedJavaBenchmarkDocumentation` and `verifyPublishedPythonBenchmarkDocumentation` in
`build.gradle`. A verifier must never read mutable files from `build/reports/`.

For new measurements, record language, stable model ID, model artifact version, descriptor checksum, source dictionary identity/version, core revision, and benchmark configuration. JMH resolves the required default models and optional PoliMorf directly from their individual model JARs; these benchmark-only dependencies are not transitive to ordinary users.

Current model descriptors also record the official repository, dataset, license, attribution,
verification date, transformations, and source-revision status. Exact historical revisions were
not recorded for the legacy UniMorph imports; that limitation is disclosed with
`not-recorded-in-legacy-import` rather than reconstructed. Future imports must record the exact
upstream revision and source-archive checksum. This reproducibility limitation does not replace or
weaken the packaged license and attribution requirements.

Each UniMorph-derived model artifact carries its own notice with the exact audited license URI,
upstream attribution, transformations, applicable distribution statement, and Leo Galambos
contribution notice. Most are CC BY-SA 3.0; CC BY-SA 4.0, CC BY 4.0, and LGPLLR exceptions are
identified exactly. Khaling additionally packages the canonical LGPLLR text and legible dictionary
form. PoliMorf retains its separately packaged BSD-2-Clause license.

For a future full PoliMorf measurement, also record the startup heap separately from benchmark parameters. Complete runtime construction is currently verified with a dedicated 6 GiB maximum heap; this limit is neither a retained-trie measurement nor a setting applied to ordinary JMH runs.

The Pages workflow publishes that staged documentation together with Javadoc, JUnit, PMD, JaCoCo, PIT, representative JMH, SBOM, optional dependency-check output, badge metadata, and retained build history. Its filesystem merge explicitly preserves the `builds/` tree in the separate `gh-pages` publication branch, so documentation regeneration cannot erase durable report URLs.

## Performance benchmark reproduction

The current accuracy, speed, and coverage commands are:

```bash
./gradlew --no-daemon benchmarkCorpusReport writeJmhRuntimeClasspath
tools/run-published-accuracy-benchmarks.sh 2026-09-10
tools/run-published-speed-benchmarks.sh 2026-09-10 4.4.0
```

The speed runner refuses to start unless every CPU uses the `performance` governor, materializes the exact selected benchmark list, and rejects quality, gold-standard, and internal microbenchmark methods. It validates that the parameterized Radixor method covers all 144 user-facing model IDs and selects only the approved same-language comparator paths, including the separately identified optional PoliMorf comparator. It records hardware, JVM, source state, executable JAR, the complete runtime-classpath content manifest, corpus, quality, load, temperature, and governor provenance. After the main CSV passes model-coverage validation, the runner also records its SHA-256 before the coverage run. The accuracy runner evaluates the exact-root benchmark suite and verifies the complete counter rows for every selected Snowball 3.1.0 candidate. The exact JMH configuration is listed in [Environment and reports](environment.md). Quality and performance reports are separate datasets and are not combined into an undocumented scalar.

## Prohibited-model documentation benchmark

Prohibited dictionaries are not part of the public benchmark classpath or the
supported-model campaign above. They can be measured only from a locally
available, complete `models.prohibited/` store by the closed wrapper:

```bash
tools/run-prohibited-model-benchmarks.sh prepare 2026-09-11 4.4.0
tools/run-prohibited-model-benchmarks.sh corpus 2026-09-11 4.4.0
tools/run-prohibited-model-benchmarks.sh quality 2026-09-11 4.4.0
tools/run-prohibited-model-benchmarks.sh generalization 2026-09-11 4.4.0
tools/run-prohibited-model-benchmarks.sh speed 2026-09-11 4.4.0
tools/run-prohibited-model-benchmarks.sh snowball 2026-09-11 4.4.0
```

Each invocation takes an exclusive lock, recovers a stale verified stage if
necessary, checks the closed quarantine ID and source-hash manifests, stages
the private directories, enables the internal Gradle include property, and
restores the directories in reverse order while preserving the command exit
status. Only private compile/resource/JAR prerequisites are enabled. The speed
speed phases keep the canonical 3 warmup iterations, 5 measured iterations, 3 forks,
1 thread, and 1-second durations. Reports and runtime/source manifests remain
under `build/reports/prohibited-models/<date>/`; they never feed release tasks.
The `snowball` phase derives the exact comparator cohort from the guarded Java
authority; Snowball 3.1.0 currently contributes Basque, while it has no exact
Welsh or Slovenian algorithm.

After the base and comparator reports validate, the documentation-only publication command is:

```bash
python3 tools/update-prohibited-benchmark-documentation.py \
    --active-corpus docs/benchmarks/data/java-benchmark-corpora-2026-09-11.csv \
    --corpus build/reports/prohibited-models/2026-09-11/prohibited-benchmark-corpora-2026-09-11.csv \
    --quality build/reports/prohibited-models/2026-09-11/prohibited-stemming-quality-2026-09-11.csv \
    --generalization build/reports/prohibited-models/2026-09-11/prohibited-dictionary-generalization-2026-09-11.csv \
    --speed build/reports/prohibited-models/2026-09-11/prohibited-stemmer-speed-2026-09-11.csv \
    --provenance build/reports/prohibited-models/2026-09-11/provenance.txt \
    --source-hashes build/reports/prohibited-models/2026-09-11/prohibited-source-inputs.sha256 \
    --runtime-classpath build/reports/prohibited-models/2026-09-11/runtime-classpath-content.sha256 \
    --model-manifest build/reports/prohibited-models/2026-09-11/models.tsv \
    --comparator-catalog build/reports/prohibited-models/2026-09-11/prohibited-snowball-cases-2026-09-11.tsv \
    --snowball-accuracy build/reports/prohibited-models/2026-09-11/prohibited-snowball-accuracy-2026-09-11.csv \
    --snowball-quality build/reports/prohibited-models/2026-09-11/prohibited-snowball-quality-2026-09-11.csv \
    --snowball-speed build/reports/prohibited-models/2026-09-11/prohibited-snowball-speed-2026-09-11.csv \
    --snowball-provenance build/reports/prohibited-models/2026-09-11/prohibited-snowball-provenance.txt \
    --snowball-source-hashes build/reports/prohibited-models/2026-09-11/prohibited-snowball-source-inputs.sha256 \
    --snowball-runtime-classpath build/reports/prohibited-models/2026-09-11/prohibited-snowball-runtime-classpath-content.sha256 \
    --date 2026-09-11 --release-version 4.4.0 --mode update
```

Run the same command with `--mode verify` after reviewing the generated diff.
The publisher checks provenance and source/classpath hashes, writes a separate
active pointer and checksum manifest, rebases size tiers across all benchmarked
dictionaries, and adds the benchmark-only pages under an explicit unavailable
warning. It does not add coordinates, registry entries, package claims, or
public model identities.

## Recorded and unavailable provenance

The performance documentation records its 2026-09-11 environment, JDK, operating system, hardware, base revision, exact measured-source patch, untracked-source checksums, executable JMH JAR checksum, complete runtime-classpath content manifest, main-report checksum, and model descriptor checksums. The quality CSV embeds model identity and checksum in every row; run date, core source state, JVM, OS, and hardware are shared provenance on the environment page.

Exact immutable upstream revisions were not recorded for every legacy UniMorph import. That limitation remains explicit in model descriptors and cannot be repaired from filesystem timestamps. Dependency versions reproducible from repository configuration include Apache Lucene 10.5.0, Morfologik 2.1.9, the Ukrainian dictionary artifact 4.9.1, and JMH 1.37.

## Correlation and audit data

Pearson and Spearman files are generated from unrounded metric values in cohorts separated by dictionary mode and output policy. A missing coefficient means too few observations, undefined input, or zero variance. Correlation is descriptive and does not demonstrate that two metrics are scientifically interchangeable.

Audit reports preserve original multilingual forms and identify high-contributing dictionary groups. They are build artifacts rather than checked-in publication data because of their size. No documentation value is manually altered after generation.

## JMH badge compatibility

The quality documentation generator does not invoke JMH, change JMH result formats, or modify badge tooling. Existing JMH result paths and historical badge-compatible inputs remain independent. The repository currently publishes coverage and mutation badge metadata and retains JMH TXT/CSV artifacts as documented in [Environment and reports](environment.md).
See [Model Selection and Loading](../../model-selection-and-loading.md), [Stemmer Models](../../stemmer-models.md), and the generated [model catalog](../../stemmer-model-catalog.md) for current model identities.
