# Reports and Published Build Artifacts

Radixor publishes durable build outputs to GitHub Pages from qualifying runs of `.github/workflows/pages.yml`.

The workflow stages maintained MkDocs documentation under `build/mkdocs-source/` and independently regenerates the model catalog there. `verifyModelCatalogDocumentation` requires that staged copy to be byte-identical to the reviewed, checked-in `docs/stemmer-model-catalog.md`; the workflow does not rewrite repository documentation. It then merges the rendered site into the separate `gh-pages` publication worktree while preserving `builds/`. Rendered site output remains untracked. The publication retains the ten newest numbered report sets and maintains `builds/latest/` as a stable alias.

This page is the central entry point for published project artifacts, including build summaries, API documentation, test and quality reports, benchmark outputs, and software composition materials. It is intended both for routine project inspection and for linking stable report surfaces from external references such as the README, release notes, or development workflows.

For the verification policy behind these artifacts, see [Quality and Operations](quality-and-operations.md). The local [Historical Builds](builds.md) route is replaced with the retained index during Pages staging.

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

- [Release verification test report (ciRelease)](https://leogalambos.github.io/Radixor/builds/latest/test/)
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

These resources expose benchmark results and generated badge metadata derived from the latest published build. JMH benchmark reports are published as TXT and CSV files; the historical Porter comparison badge is no longer generated.

- [JMH benchmark results (TXT)](https://leogalambos.github.io/Radixor/builds/latest/jmh/jmh-results.txt)
- [JMH benchmark results (CSV)](https://leogalambos.github.io/Radixor/builds/latest/jmh/jmh-results.csv)
- [Coverage badge metadata](https://leogalambos.github.io/Radixor/builds/latest/metrics/coverage-badge.json)
- [Mutation badge metadata](https://leogalambos.github.io/Radixor/builds/latest/metrics/pitest-badge.json)

These JMH files are rolling, representative CI measurements produced by the Pages workflow. They are not the source of the frozen all-language scientific tables dated 2026-08-25. For those reviewed snapshots, conclusions, exact inputs, and reproduction commands, start with the [benchmark results overview](benchmarks/index.md) and [reproducibility page](benchmarks/reference/reproducibility.md). Coverage and mutation badge metadata endpoints are intended for status surfaces such as the project README or other generated dashboards.

## Practical usage

In most cases, the recommended entry path is:

1. start with the [Latest build summary](https://leogalambos.github.io/Radixor/builds/latest/),
2. open the specific report category relevant to your task,
3. use [Browse historical build reports](https://leogalambos.github.io/Radixor/builds/) when historical inspection is needed.
