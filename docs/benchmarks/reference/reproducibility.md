# Reproducibility and Raw Data

## Published quality snapshot

- Machine-readable CSV: [stemming-quality.csv](../data/stemming-quality.csv)
- SHA-256 record: [stemming-quality.sha256](../data/stemming-quality.sha256)
- SHA-256: `85763189eab4d0fbb047c2d5d3554c66abf9732182bd0d8fd758d7aef680e66f`
- Complete scenarios: 314
- Authoritative language universe: 20 languages
- Language-page scenarios: 314 across 20 benchmark pages

The CSV contains the model ID, independent model version, descriptor SHA-256, raw pair counts, raw over/under numerators and denominators, candidate statistics, and relation metrics. Reserved partition-metric columns remain empty because the gold standard is an overlapping cover. Documentation is regenerated from this file rather than manually transcribed. Publication fails when any row uses a model other than the language's registered default.

## Published generalization snapshot

- Machine-readable CSV: [dictionary-generalization.csv](../data/dictionary-generalization.csv)
- SHA-256 record: [dictionary-generalization.sha256](../data/dictionary-generalization.sha256)
- SHA-256: `e6479840b9307ae03bd0873e55f397811e975125d621a8b8716d4c1a166b3ff2`
- Measured-source manifest: [dictionary-generalization-sources.sha256](../data/dictionary-generalization-sources.sha256)
- Complete scenarios: 1,000
- Matrix: 20 default models × 10 coverage levels × 5 frozen splits
- Measured source identity: Radixor/Java `4.2.0-6-g84e57fb`

This CSV retains integer numerators and denominators for complete-dictionary,
withheld-row, and unseen-surface scopes. It also records selected and total rows,
overlap exclusions, split seed, protocol version, and exact model provenance.
The [generalization methodology](generalization-methodology.md) defines the
frozen split and the limits of the claim.

The accompanying manifest records the byte identity of the generator and the
Java implementation files that determine trie construction, traversal, patch
encoding, and lookup. The base revision and measured source state are retained
instead of inferring a clean release tag for this experiment.

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

- [Java corpus and command report](../data/java-benchmark-corpora-2026-08-25.csv)
- [Java exact-root report](../data/java-stemmer-accuracy-2026-08-25.csv)
- [Java speed report](../data/java-stemmer-speed-2026-08-25.csv)
- [English coverage accuracy report](../data/java-english-coverage-accuracy-2026-08-25.csv)
- [English coverage speed report](../data/java-english-coverage-speed-2026-08-25.csv)
- [Python all-language batch CSV](../data/python-all-languages-batch-2026-08-25.csv)
- [Python all-language provenance JSON](../data/python-all-languages-batch-2026-08-25.json)
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
    -PdictionaryGeneralizationReleaseVersion=4.2.0-6-g84e57fb \
    dictionaryGeneralization
python3 tools/update-generalization-documentation.py \
    build/reports/generalization/dictionary-generalization.csv docs update
python3 tools/update-generalization-documentation.py \
    docs/benchmarks/data/dictionary-generalization.csv docs verify
./gradlew --no-daemon \
    -PdictionaryGeneralizationReleaseVersion=4.2.0-6-g84e57fb \
    editCostSensitivity
python3 tools/update-edit-cost-documentation.py \
    build/reports/generalization/edit-cost-sensitivity.csv docs update
python3 tools/update-edit-cost-documentation.py \
    docs/benchmarks/data/edit-cost-sensitivity.csv.gz docs verify
./gradlew --no-daemon benchmarkCorpusReport writeJmhRuntimeClasspath
tools/run-published-accuracy-benchmarks.sh 2026-08-25
tools/run-published-speed-benchmarks.sh 2026-08-25 4.2.0-6-g84e57fb
./gradlew --no-daemon pythonBenchmarkAllLanguagesBatch
cp build/reports/jmh/benchmark-corpora.csv \
    docs/benchmarks/data/java-benchmark-corpora-2026-08-25.csv
cp build/reports/jmh/stemmer-accuracy-2026-08-25.csv \
    docs/benchmarks/data/java-stemmer-accuracy-2026-08-25.csv
cp build/reports/jmh/stemmer-speed-2026-08-25.csv \
    docs/benchmarks/data/java-stemmer-speed-2026-08-25.csv
cp build/reports/jmh/english-coverage-accuracy-2026-08-25.csv \
    docs/benchmarks/data/java-english-coverage-accuracy-2026-08-25.csv
cp build/reports/jmh/english-coverage-speed-2026-08-25.csv \
    docs/benchmarks/data/java-english-coverage-speed-2026-08-25.csv
cp build/reports/python-benchmarks/all-languages-batch.csv \
    docs/benchmarks/data/python-all-languages-batch-2026-08-25.csv
cp build/reports/python-benchmarks/all-languages-batch.json \
    docs/benchmarks/data/python-all-languages-batch-2026-08-25.json
sed -i "s#${PWD}#<repository>#g" \
    docs/benchmarks/data/python-all-languages-batch-2026-08-25.json
(cd docs/benchmarks/data && sha256sum \
    java-benchmark-corpora-2026-08-25.csv \
    java-stemmer-accuracy-2026-08-25.csv \
    java-stemmer-speed-2026-08-25.csv \
    java-english-coverage-accuracy-2026-08-25.csv \
    java-english-coverage-speed-2026-08-25.csv \
    python-all-languages-batch-2026-08-25.csv \
    python-all-languages-batch-2026-08-25.json \
    > performance-snapshots.sha256)
python3 tools/update-benchmark-documentation.py \
    --corpus docs/benchmarks/data/java-benchmark-corpora-2026-08-25.csv \
    --accuracy docs/benchmarks/data/java-stemmer-accuracy-2026-08-25.csv \
    --speed docs/benchmarks/data/java-stemmer-speed-2026-08-25.csv \
    --coverage-accuracy docs/benchmarks/data/java-english-coverage-accuracy-2026-08-25.csv \
    --coverage-speed docs/benchmarks/data/java-english-coverage-speed-2026-08-25.csv \
    --date 2026-08-25 \
    --release-version 4.2.0-6-g84e57fb \
    --mode update
python3 tools/update-python-benchmark-documentation.py \
    --csv docs/benchmarks/data/python-all-languages-batch-2026-08-25.csv \
    --json docs/benchmarks/data/python-all-languages-batch-2026-08-25.json \
    --date 2026-08-25 \
    --release-version 4.2.1 \
    --base-commit 84e57fb \
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

`publishStemmingQualityDocumentation` validates the complete build CSV, copies a versioned documentation snapshot, and replaces only marked generated sections. `verifyStemmingQualityDocumentation` re-renders from the checked-in snapshot and fails on changed values, ordering, missing pages, duplicate keys, arithmetic inconsistencies, policy violations, or stale sections.

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

Each UniMorph-derived model artifact carries its own notice with the canonical CC BY-SA 3.0 URI,
upstream attribution, transformations, ShareAlike statement, and Leo Galambos contribution notice.
The full CC legal text is not duplicated or presented as a root-project license. PoliMorf retains
its separately packaged BSD-2-Clause license.

For a future full PoliMorf measurement, also record the startup heap separately from benchmark parameters. Complete runtime construction is currently verified with a dedicated 6 GiB maximum heap; this limit is neither a retained-trie measurement nor a setting applied to ordinary JMH runs.

The Pages workflow publishes that staged documentation together with Javadoc, JUnit, PMD, JaCoCo, PIT, representative JMH, SBOM, optional dependency-check output, badge metadata, and retained build history. Its filesystem merge explicitly preserves the `builds/` tree in the separate `gh-pages` publication branch, so documentation regeneration cannot erase durable report URLs.

## Performance benchmark reproduction

The current accuracy, speed, and coverage commands are:

```bash
./gradlew --no-daemon benchmarkCorpusReport writeJmhRuntimeClasspath
tools/run-published-accuracy-benchmarks.sh 2026-08-25
tools/run-published-speed-benchmarks.sh 2026-08-25 4.2.0-6-g84e57fb
```

The speed runner refuses to start unless every CPU uses the `performance` governor, materializes the exact selected benchmark list, rejects quality/Polimorf/gold-standard methods, and requires the Hebrew speed path. It records hardware, JVM, source-state, JAR, classpath, corpus, quality, load, temperature, and governor provenance before running. The accuracy runner evaluates all four exact-root benchmark classes and verifies that every new Snowball 3.1.0 candidate exposes all six accuracy counters. The exact JMH configuration is listed in [Environment and reports](environment.md). Quality and performance reports are separate datasets and are not combined into an undocumented scalar.

## Recorded and unavailable provenance

The performance documentation records its 2026-08-25 environment, JDK, operating system, hardware, base revision, exact measured-source patch, untracked-source checksums, executable JMH JAR checksum, and model descriptor checksums. The quality CSV embeds model identity and checksum in every row; run date, core source state, JVM, OS, and hardware are shared provenance on the environment page.

Exact immutable upstream revisions were not recorded for every legacy UniMorph import. That limitation remains explicit in model descriptors and cannot be repaired from filesystem timestamps. Dependency versions reproducible from repository configuration include Apache Lucene 10.5.0, Morfologik 2.1.9, the Ukrainian dictionary artifact 4.9.1, and JMH 1.37.

## Correlation and audit data

Pearson and Spearman files are generated from unrounded metric values in cohorts separated by dictionary mode and output policy. A missing coefficient means too few observations, undefined input, or zero variance. Correlation is descriptive and does not demonstrate that two metrics are scientifically interchangeable.

Audit reports preserve original multilingual forms and identify high-contributing dictionary groups. They are build artifacts rather than checked-in publication data because of their size. No documentation value is manually altered after generation.

## JMH badge compatibility

The quality documentation generator does not invoke JMH, change JMH result formats, or modify badge tooling. Existing JMH result paths and historical badge-compatible inputs remain independent. The repository currently publishes coverage and mutation badge metadata and retains JMH TXT/CSV artifacts as documented in [Environment and reports](environment.md).
See [Model Selection and Loading](../../model-selection-and-loading.md), [Stemmer Models](../../stemmer-models.md), and the generated [model catalog](../../stemmer-model-catalog.md) for current model identities.
