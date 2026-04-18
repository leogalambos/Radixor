# Reports and Published Build Artifacts

Radixor publishes durable build outputs to GitHub Pages from qualifying runs of `.github/workflows/pages.yml`.

This page is the central entry point for published project artifacts, including build summaries, API documentation, test and quality reports, benchmark outputs, and software composition materials. It is intended both for routine project inspection and for linking stable report surfaces from external references such as the README, release notes, or development workflows.

## Stable entry points

The following links are the primary stable locations for the most recent published build outputs:

- [Latest build summary](https://leogalambos.github.io/Radixor/builds/latest/)
- [Browse historical build reports](https://leogalambos.github.io/Radixor/builds/)

Use `builds/latest/` when you want the current published report surface. Use `builds/` when you need to inspect or compare retained historical runs.

## API and developer documentation

These reports are primarily useful when reviewing the published API surface and generated developer-facing documentation:

- [Javadoc](https://leogalambos.github.io/Radixor/builds/latest/javadoc/)

## Verification and code quality reports

These reports describe the outcome of core verification and static-analysis stages for the latest published build:

- [Unit test report](https://leogalambos.github.io/Radixor/builds/latest/test/)
- [PMD report](https://leogalambos.github.io/Radixor/builds/latest/pmd/main.html)
- [JaCoCo coverage report](https://leogalambos.github.io/Radixor/builds/latest/coverage/)
- [PIT mutation testing report](https://leogalambos.github.io/Radixor/builds/latest/pitest/)
- [Dependency vulnerability report](https://leogalambos.github.io/Radixor/builds/latest/dependency-check/dependency-check-report.html)

Together, these reports provide the most direct published view of functional correctness, static quality signals, coverage, mutation resistance, and dependency-level security review outputs.

## Software composition artifacts

These artifacts expose the published software bill of materials for the latest build:

- [SBOM (JSON)](https://leogalambos.github.io/Radixor/builds/latest/sbom/radixor-sbom.json)
- [SBOM (XML)](https://leogalambos.github.io/Radixor/builds/latest/sbom/radixor-sbom.xml)

They are useful for dependency inspection, downstream integration, compliance-oriented workflows, and artifact traceability.

## Benchmark outputs and badge metadata

These resources expose benchmark results and generated badge metadata derived from the latest published build:

- [JMH benchmark results (TXT)](https://leogalambos.github.io/Radixor/builds/latest/jmh/jmh-results.txt)
- [JMH benchmark results (CSV)](https://leogalambos.github.io/Radixor/builds/latest/jmh/jmh-results.csv)
- [Coverage badge metadata](https://leogalambos.github.io/Radixor/builds/latest/metrics/coverage-badge.json)
- [Mutation badge metadata](https://leogalambos.github.io/Radixor/builds/latest/metrics/pitest-badge.json)
- [Benchmark badge metadata](https://leogalambos.github.io/Radixor/builds/latest/metrics/jmh-badge.json)

The benchmark outputs provide direct access to the published JMH result files, while the badge metadata endpoints are intended for status surfaces such as the project README or other generated dashboards.

## Practical usage

In most cases, the recommended entry path is:

1. start with the [Latest build summary](https://leogalambos.github.io/Radixor/builds/latest/),
2. open the specific report category relevant to your task,
3. use [Browse historical build reports](https://leogalambos.github.io/Radixor/builds/) when historical inspection is needed.
