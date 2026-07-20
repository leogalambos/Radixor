# Reproducibility and Raw Data

## Published quality snapshot

- Machine-readable CSV: [stemming-quality.csv](../data/stemming-quality.csv)
- SHA-256 record: [stemming-quality.sha256](../data/stemming-quality.sha256)
- SHA-256: `5a93a6ab60e46489737cd649eb1ac48182114b9038f7f20195ab9d1c1fc0dd28`
- Complete scenarios: 308
- Authoritative language universe: 20 languages
- Language-page scenarios: 302 across 19 existing benchmark pages

The six remaining scenarios are the three Radixor policies in two modes for `HE_IL`. Hebrew is present in the complete result snapshot but has no existing language benchmark page.

The CSV contains raw TP, FP, FN, and TN counts; raw over/under numerators and denominators; candidate statistics; relation metrics; and partition-only metrics. Documentation is regenerated from this file rather than manually transcribed.

## Commands

```bash
./gradlew stemmingQuality
./gradlew publishStemmingQualityDocumentation
./gradlew verifyStemmingQualityDocumentation
./gradlew test
mkdocs build --strict
```

`stemmingQuality` performs the expensive complete evaluation and is intentionally not attached to `test` or `check`. It prepares JMH third-party dependencies automatically and writes:

- `build/reports/stemming-quality/stemming-quality.csv`
- `build/reports/stemming-quality/stemming-quality.md`
- `build/reports/stemming-quality/metric-correlations-pearson.csv`
- `build/reports/stemming-quality/metric-correlations-spearman.csv`

Audit mode is enabled with `-PstemmingQualityAudit=true`. Language, stemmer, dictionary-mode, output-policy, and ranking filters are documented on the central [stemming-quality page](../../stemming-quality.md). Filtered reports use separate filenames and cannot be accepted as publication sources.

`publishStemmingQualityDocumentation` validates the complete build CSV, copies a versioned documentation snapshot, and replaces only marked generated sections. `verifyStemmingQualityDocumentation` re-renders from the checked-in snapshot and fails on changed values, ordering, missing pages, duplicate keys, arithmetic inconsistencies, policy violations, or stale sections.

## Performance benchmark reproduction

The JMH comparison command family is:

```bash
./gradlew jmh -Pjmh.includes='.*StemmerComparisonBenchmark.*' --no-daemon
```

The exact JMH configuration, hardware, operating system, and JDK captured for the published performance tables are listed in [Environment and reports](environment.md). Quality and performance reports are separate datasets and are not combined into an undocumented scalar.

## Recorded and unavailable provenance

The performance documentation records its 2026-07-06 environment, JDK 25.0.3, operating system, and hardware. The quality CSV records the evaluated identifiers and counts but does not embed the Radixor Git revision, generation date, JDK, operating system, dictionary content hash, or immutable upstream revisions for every downloaded source. These fields are explicitly unavailable for this snapshot and are not reconstructed from filesystem timestamps.

Dependency versions that are reproducible from repository configuration include Apache Lucene 10.5.0, Morfologik 2.1.9, the Ukrainian dictionary artifact 4.9.1, and JMH 1.37. Other upstream branches or downloaded dictionary revisions should be pinned and embedded in a future result schema.

## Correlation and audit data

Pearson and Spearman files are generated from unrounded metric values in cohorts separated by dictionary mode and output policy. A missing coefficient means too few observations, undefined input, or zero variance. Correlation is descriptive and does not demonstrate that two metrics are scientifically interchangeable.

Audit reports preserve original multilingual forms and identify high-contributing dictionary groups. They are build artifacts rather than checked-in publication data because of their size. No documentation value is manually altered after generation.

## JMH badge compatibility

The quality documentation generator does not invoke JMH, change JMH result formats, or modify badge tooling. Existing JMH result paths and historical badge-compatible inputs remain independent. The repository currently publishes coverage and mutation badge metadata and retains JMH TXT/CSV artifacts as documented in [Environment and reports](environment.md).
