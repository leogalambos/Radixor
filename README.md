<p align="center">
  <img src="docs/assets/images/radixor-logo.png" width="160" alt="Radixor logo" />
</p>

[![License](https://img.shields.io/github/license/leogalambos/Radixor)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-brightgreen)](#)
[![Python](https://img.shields.io/badge/Python-3.9%2B-1769ef)](docs/python/fast-track.md)
[![Maven Central](https://img.shields.io/maven-central/v/org.egothor/radixor)](https://central.sonatype.com/artifact/org.egothor/radixor)
[![Published reports](https://img.shields.io/badge/reports-GitHub%20Pages-blue)](https://leogalambos.github.io/Radixor/builds/latest/)
[![Quality gates](https://github.com/leogalambos/Radixor/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/leogalambos/Radixor/actions/workflows/build.yml)
[![Coverage](https://img.shields.io/endpoint?url=https://leogalambos.github.io/Radixor/builds/latest/metrics/coverage-badge.json)](https://leogalambos.github.io/Radixor/builds/latest/coverage/)
[![Mutation score](https://img.shields.io/endpoint?url=https://leogalambos.github.io/Radixor/builds/latest/metrics/pitest-badge.json)](https://leogalambos.github.io/Radixor/builds/latest/pitest/)

*Deterministic, multi-language stemming for Java and Python, built around compact dictionary-trained patch-command tries with an explicit quality/speed trade-off.*

**Radixor** is a modern multi-language stemming toolkit for Java and Python in the tradition of the original **Egothor** approach. It learns compact word-to-stem transformations from dictionary data, stores them in compiled patch-command tries, and exposes native runtime implementations designed for speed, determinism, and operational simplicity. Unlike a closed-form dictionary lookup stemmer, Radixor can also generalize beyond explicitly listed word forms.

It is particularly well suited to systems that need stemming which is:

- fast at runtime,
- compact in memory and on disk,
- deterministic in behavior,
- adaptable through dictionary data rather than hardcoded language rules,
- practical to compile, persist, version, extend, and deploy.

It also retains the operational advantages of a compiled artifact model: predictable runtime behavior, direct binary loading, and clear separation between preparation-time compilation and live request processing.

## Choose a runtime

Radixor has three interoperable implementations. **Java Radixor is the primary,
flagship and most complete implementation.** The **Python (PyO3)** package,
distributed as `radixor`,
is the Python port that will progressively converge on Java; it reaches its
highest throughput by batching words across the Python/native boundary. The
direct C **`radixor-c`** package focuses on basic stemming from prepared tries
and keeps the overhead of individual Python calls low. Trie compilation and
modification will reach Python-C later.

| Runtime | Choose it for | Model capabilities |
|---|---|---|
| Java | Full API and model development | Build, configure, extend, persist and load |
| Python (PyO3) | Batch throughput and Python-side compilation | Compile text dictionaries and load [compiled Radixor models](docs/data-formats.md); expanding toward Java |
| Python-C | Fast scalar calls with basic stemming APIs | Load standard or prepared [compiled Radixor models](docs/data-formats.md) |

For Python, one installation provides the native runtime and the separate
standard package of 20 precompiled models:

From PyPI:

```bash
python -m pip install --only-binary=:all: radixor
```

Or from the GitHub Releases-backed index:

```bash
python -m pip install --only-binary=:all: \
  --index-url https://leogalambos.github.io/Radixor/python/simple/ radixor
```

```python
from radixor import Stemmer

english = Stemmer("en")
print(english.stemWord("running"))  # run
```

Continue with the [Python Fast Track](docs/python/fast-track.md) or
[Python Quick Start](docs/python/quick-start.md).

For the C runtime, install `radixor-c`, import `Stemmer` from `radixor_c`, and
continue with the [Python-C Quick Start](docs/python-c/quick-start.md).

### Java dependencies

The core artifact contains the algorithm and registry, but no language dictionary. Add either one minimal model or the optional standard default pack:

```groovy
dependencies {
    implementation 'org.egothor:radixor:<latest-java-version>'
    runtimeOnly 'org.egothor:radixor-model-pl-pl-unimorph:<compatible-model-version>'
    // Or: runtimeOnly 'org.egothor:radixor-models-standard:<compatible-catalog-version>'
}
```

Resolve the current Radixor/Java version from its
[Maven Central artifact page](https://central.sonatype.com/artifact/org.egothor/radixor)
and compatible model versions from the
[model catalog](docs/stemmer-model-catalog.md).

```java
final FrequencyTrie<CompiledPatchCommand> polish =
        StemmerPatchTrieLoader.loadCompiled(
                StemmerPatchTrieLoader.Language.PL_PL,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
```

`Language.PL_PL` selects the documented default `pl-pl-unimorph`. The optional `pl-pl-polimorf` model requires its own runtime artifact and explicit selection; adding it does not change the default. See [Model Selection and Loading](docs/model-selection-and-loading.md) for complete executable examples and [Stemmer Models](docs/stemmer-models.md) for artifact concepts.

`radixor-models-standard` is a POM-only runtime aggregate: it brings the 20 default model JARs transitively but publishes no empty aggregate JAR. `radixor-models-bom` is the separate POM-only Maven dependency BOM for version management; importing it alone adds no model. The root CycloneDX SBOM report is unrelated to that dependency BOM.

```java
final FrequencyTrie<CompiledPatchCommand> polimorf =
        StemmerPatchTrieLoader.loadCompiled(
                "pl-pl-polimorf",
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
```

Complete PoliMorf construction is supported but unusually memory-intensive: the dedicated verification task uses a 6 GiB maximum heap. Applications should load and retain the resulting immutable trie during startup rather than rebuilding it per request.

## Table of Contents

- [Why Radixor](#why-radixor)
- [Performance](#performance)
- [Heritage](#heritage)
- [What Radixor adds](#what-radixor-adds)
- [Key features](#key-features)
- [Documentation](#documentation)
- [Project philosophy](#project-philosophy)
- [Historical note](#historical-note)

## Why Radixor

The central idea behind Radixor is simple: learn how to transform a word form into its stem, encode that transformation as a compact patch command, store it in a trie, and make the runtime path as small and direct as possible.

That produces a stemmer that is:

- data-driven rather than rule-hardcoded,
- applicable across languages through compiled transformation models learned from dictionary data,
- compact enough for deployment-friendly binary artifacts,
- suitable for both offline compilation and direct runtime loading,
- capable of exposing either a preferred result or multiple candidate results when ambiguity matters.

Radixor is especially attractive when you want something more adaptable than simple suffix stripping, but much smaller and easier to operate than a full morphological analyzer.

## Performance

Radixor performance is best read together with stemming quality. The English dictionary coverage benchmark builds contracted compiled patch tries from deterministic slices of the `US_UK` dictionary and then measures both exact-root agreement and changed-token runtime.

| Used rows | Actual row ratio | All exact | Changed exact | Root preserved | Speed ms/op | Error ms | ns/token |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100% | 100.000% | 97.668% | 98.110% | 97.552% | 20.425 | 3.636 | 97.9 |
| 90% | 90.000% | 97.239% | 95.821% | 97.612% | 17.779 | 1.827 | 85.3 |
| 80% | 80.000% | 96.827% | 93.673% | 97.656% | 15.343 | 1.321 | 73.6 |
| 70% | 70.000% | 96.392% | 91.430% | 97.695% | 16.444 | 2.027 | 78.9 |
| 60% | 60.000% | 95.935% | 89.244% | 97.693% | 14.330 | 1.350 | 68.7 |
| 50% | 50.000% | 95.453% | 86.979% | 97.678% | 14.953 | 2.625 | 71.7 |
| 40% | 40.000% | 94.939% | 84.667% | 97.638% | 12.919 | 1.155 | 62.0 |
| 30% | 30.000% | 94.398% | 82.443% | 97.538% | 12.166 | 1.305 | 58.3 |
| 20% | 20.000% | 93.821% | 80.174% | 97.406% | 11.549 | 1.535 | 55.4 |
| 10% | 10.000% | 93.057% | 77.327% | 97.190% | 14.360 | 3.524 | 68.9 |

Column meanings:

- `Used rows` is the requested deterministic percentage of English dictionary rows used to build the stemmer.
- `Actual row ratio` is the selected row count divided by the full parsed dictionary row count.
- `All exact` is exact agreement over every word/root pair in the full dictionary.
- `Changed exact` is exact agreement only where the word differs from its root.
- `Root preserved` is the share of already-root forms that remain unchanged.
- `Speed ms/op` is JMH average time for one changed-token benchmark operation.
- `Error ms` is the JMH score error converted to milliseconds.
- `ns/token` is average nanoseconds per changed token in that operation.

The contracted trie result is materially stronger than the older uncontracted profile: full English coverage reaches 97.668% all-token exactness and 98.110% changed-token exactness at 97.9 ns/token, while even a 10% deterministic dictionary slice remains at 93.057% all-token exactness and 77.327% changed-token exactness at 68.9 ns/token. This is why Radixor benchmark results are documented with both speed and quality instead of a single Porter speed badge.

The English curve evaluates the complete dictionary, so it intentionally mixes
trained and withheld rows. The separate
[20-language dictionary-family generalization report](docs/benchmarks/generalization.md)
isolates held-out rows, removes surface forms duplicated in training, and reports
five frozen splits at every 10% coverage step. It contains 1,000 raw scenarios
with exact model provenance and makes clear where transfer is strong—and where a
small resource does not support a broad generalization claim.

The complementary [edit-cost sensitivity experiment](docs/benchmarks/edit-cost-sensitivity.md)
expands 16,700 physically measured exact command classes into a validated 234,000-observation
logical matrix. It finds that suitable relative edit costs and their structural effect are
language-dependent. Each [language benchmark page](docs/benchmarks/languages/index.md) therefore
publishes its own 10%–90% knowledge curve, command-equivalence evidence, selected-cost effect,
factor associations, and bounded conclusion; exploratory non-baseline settings are not presented
as production defaults without external validation.

For benchmark scope, workload design, environment, commands, report locations, and interpretation guidance, see [Benchmarking](docs/benchmarking.md).

## Heritage

Radixor stands in the line of the original **Egothor** stemming work and its later **Stempel** packaging.

Historical Stempel documentation describes the stemmer code as taken virtually unchanged from the Egothor project, and Elasticsearch still documents the Stempel analysis plugin as integrating Lucene’s Stempel module for Polish.

Useful historical references:

- [Egothor project](http://www.egothor.org/)
- [Stempel overview](https://www.getopt.org/stempel/)
- [Leo Galambos, *Lemmatizer for Document Information Retrieval Systems in JAVA* (SOFSEM 2001)](https://doi.org/10.1007/3-540-45627-9_21)
- [Lucene Stempel overview](https://lucene.apache.org/core/5_3_0/analyzers-stempel/index.html)
- [Elasticsearch Stempel plugin](https://www.elastic.co/docs/reference/elasticsearch/plugins/analysis-stempel)

The 2001 paper documents the general P-command method used by the Egothor/Radixor lineage: minimum-cost partial edit commands encode word-to-stem transformations and are organized in a trie. It is the historical method reference for this lineage rather than a description of Radixor's present-day implementation.

Radixor is not a repackaging of legacy code. It is a modern implementation that preserves the valuable core idea while reworking the engineering around maintainability, testing, persistence, and long-term operational use.

## What Radixor adds

Radixor keeps the patch-command trie model, but improves the engineering around it in ways that matter in real software systems.

Compared with the historical baseline, Radixor emphasizes:

- **a focused practical core**  
  The implementation concentrates on the parts of the original approach that are most useful in production.

- **immutable compiled tries**  
  Runtime lookup uses compact read-only structures optimized for efficient access.

- **support for more than one stemming result**  
  Radixor can expose both a preferred result and multiple candidate results when the underlying data is ambiguous.

- **frequency-aware deterministic ordering**  
  Candidate results are ordered consistently and reproducibly.

- **contracted compiled patch tries**  
  Uniform patch-command subtrees are collapsed into accepting leaves, reducing hot lookup depth while preserving preferred stemming results.

- **practical subtree reduction modes**  
  Reduction can be tuned toward stronger compression or more conservative semantic preservation.

- **reconstruction of writable builders from compiled artifacts**  
  Existing compiled stemmer tables can be reopened, modified, and compiled again.

- **strong validation discipline**  
  Coverage, mutation testing, benchmark visibility, and published reports are treated as part of the engineering standard rather than optional project decoration.

## Key features

- Fast algorithmic stemming
- Compact compiled binary artifacts
- Patch-command based transformation model
- Multi-language stemming through compiled transformation models
- Single-result and multi-result lookup
- Deterministic result ordering
- Compressed binary persistence
- Programmatic compilation and loading
- CLI compilation tool
- Independently versioned language-model resources
- Support for extending compiled stemmer tables
- Reproducible and auditable engineering posture

## Documentation

The repository keeps the front page concise and places detailed documentation under `docs/`.

### Getting Started

- [Python Fast Track](docs/python/fast-track.md)
  The shortest path from `pip install` to the first native Python stem.

- [Java Fast Track](docs/fast-track.md)
  The shortest Java path from adding core plus a model artifact to getting a first stem.

- [Python Quick Start](docs/python/quick-start.md)
  Installation, standard models, batch use, PyStemmer migration, and deployment guidance.

- [Java Quick Start](docs/quick-start.md)
  A broader Java walkthrough covering loading options, querying, extension, persistence, and metadata.

- [Python Overview](docs/python/index.md)
  Runtime architecture, model packaging, API capabilities, and Java interoperability.

- [Java Integration Deep Dive](docs/integration-deep-dive.md)
  Dependency setup, model selection, production lifecycle, search-pipeline guidance, and operational checklist.

- [Built-in Languages](docs/built-in-languages.md)  
  Language enum values, default model IDs, artifacts, and optional variants.

- [Dictionary Format](docs/dictionary-format.md)  
  How to write and normalize stemming dictionaries.

- [Java CLI Compilation](docs/cli-compilation.md)
  How to compile dictionaries into deployable binary artifacts from Java.

### Python

The Python installation installs the native package together with the
compatible pure-Python `radixor-models-standard` distribution: 20
[compiled Radixor models](docs/data-formats.md), excluding
the optional PoliMorf model. Python runtime distributions contain no textual
dictionaries.

- [Installation and Builds](docs/python/installation.md)
  Wheels, source builds, Gradle tasks, host builds, and cross-compilation requirements.

- [Usage and API](docs/python/usage.md)
  Single and batch stemming, caching, custom dictionaries, and compiled models.

- [Dictionary Compilation](docs/python/model-compilation.md)
  Compile a textual dictionary once, load it directly, or share its [compiled model](docs/data-formats.md) with Java.

- [Python Benchmarks](docs/python/performance.md)
  Batch methodology and comparisons with available Python stemmers.

### Java Programmatic Usage

- [Programmatic Usage Overview](docs/programmatic-usage.md)  
  Entry point to the Java API and the overall usage model.

- [Model Selection and Loading](docs/model-selection-and-loading.md)
  Default, explicit, dual-model, ClassLoader, dependency, and troubleshooting examples.

- [Loading and Building Stemmers](docs/programmatic-loading-and-building.md)  
  Loading bundled resources, textual dictionaries, binary artifacts, and direct builder usage.

- [Querying and Ambiguity Handling](docs/programmatic-querying-and-ambiguity.md)  
  `get()`, `getAll()`, `getEntries()`, patch application, and ambiguity behavior.

- [Extending and Persisting Compiled Tries](docs/programmatic-extending-and-persistence.md)  
  Reopening compiled tries, rebuilding them, and writing binary artifacts.

- [Migration and Backward Compatibility](docs/migration-and-backward-compatibility.md)  
  Migration from serialized String patch-command application to `CompiledPatchCommand`.

### Concepts and Internals

- [Architecture and Reduction Overview](docs/architecture-and-reduction.md)  
  High-level explanation of the build pipeline and compiled trie model.

- [Architecture](docs/architecture.md)  
  Structural model, data flow, and runtime lookup behavior.

- [Lookup Edge Optimization](docs/lookup-edge-optimization.md)  
  Speed/memory trade-off of dense child edge lookup in compiled tries.

- [Reduction Semantics](docs/reduction-semantics.md)  
  Ranked, unordered, and dominant reduction behavior.

- [Compatibility and Guarantees](docs/compatibility-and-guarantees.md)  
  Supported public API, internal API boundaries, and compatibility expectations.

### Dictionaries and Language Resources

- [Contributing Dictionaries](docs/contributing-dictionaries.md)  
  Guidance for high-quality lexical resource contributions.

### Quality and Operations

- [Quality and Operations](docs/quality-and-operations.md)  
  Engineering standards, validation posture, auditability, and operational model.

- [Benchmarking](docs/benchmarking.md)  
  JMH benchmark methodology, dictionary coverage trade-offs, speed, quality, and result interpretation.

- [Benchmark Results](docs/benchmarks/index.md)  
  Structured reference for methodology, corpora, environment, multilingual
  generalization, English coverage, and per-language result pages.

- [Published Reports](docs/reports.md)  
  Entry points to CI-published reports and GitHub Pages artifacts.

- [Trust, Security and Support](docs/trust-security-and-support.md)
  Supported releases, runtime versioning, public support, private vulnerability
  reporting, supply-chain evidence, and lifecycle expectations.

## Project philosophy

Radixor does not preserve historical complexity for its own sake.

It preserves the valuable idea:

- compact learned transformations,
- trie-based lookup,
- language-data driven stemming,
- practical runtime speed.

Then it improves the parts modern users care about:

- maintainability,
- testability,
- modification workflows,
- persistence,
- determinism,
- clearer APIs,
- explicit quality evidence.

The goal is to keep the Egothor/Stempel lineage useful as a serious contemporary software component.

## Historical note

Egothor showed that stemming could be both algorithmic and compact. Stempel proved that the approach was practical enough to survive inside major search ecosystems. Radixor continues that tradition with a modernized implementation focused on production use, maintainability, and controlled evolution.
# Radixor 4 artifact architecture

The established `org.egothor:radixor` artifact remains the algorithmic core and contains no language-model data. From version 4 onward, applications explicitly add individual `org.egothor:radixor-model-<model-id>` runtime artifacts or the optional metadata-only `org.egothor:radixor-models-standard` aggregate. Polish defaults to `pl-pl-unimorph`; `pl-pl-polimorf` is opt-in. See [Stemmer Models](docs/stemmer-models.md) and [Migration and Backward Compatibility](docs/migration-and-backward-compatibility.md).

Radixor Java software remains licensed under BSD-3-Clause. UniMorph-derived model data is
distributed under CC BY-SA 3.0, with upstream attribution, the canonical license URI, Radixor
transformations, and Leo Galambos's limited contribution notice carried by each model artifact.
PoliMorf model data retains its separate BSD-2-Clause license. There is no project-wide CC license
directory because the root artifact contains no model data.

```groovy
dependencies {
    implementation 'org.egothor:radixor:<latest-java-version>'
    runtimeOnly 'org.egothor:radixor-model-pl-pl-polimorf:<compatible-model-version>'
}
```
