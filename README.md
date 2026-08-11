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

### Java dependencies

The core artifact contains the algorithm and registry, but no language dictionary. Add either one minimal model or the optional standard default pack:

```groovy
dependencies {
    implementation 'org.egothor:radixor:<radixor-version>'
    runtimeOnly 'org.egothor:radixor-model-pl-pl-unimorph:1.0.0'
    // Or: runtimeOnly 'org.egothor:radixor-models-standard:<catalog-version>'
}
```

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
| 100% | 100.000% | 97.478% | 97.197% | 97.552% | 15.064 | 0.658 | 71.6 |
| 90% | 90.000% | 97.047% | 94.913% | 97.613% | 17.798 | 2.161 | 84.6 |
| 80% | 80.000% | 96.635% | 92.768% | 97.661% | 13.900 | 0.941 | 66.0 |
| 70% | 70.000% | 96.209% | 90.565% | 97.705% | 14.809 | 1.376 | 70.3 |
| 60% | 60.000% | 95.750% | 88.384% | 97.703% | 13.186 | 0.930 | 62.6 |
| 50% | 50.000% | 95.262% | 86.107% | 97.690% | 12.852 | 0.943 | 61.1 |
| 40% | 40.000% | 94.753% | 83.855% | 97.643% | 12.358 | 0.831 | 58.7 |
| 30% | 30.000% | 94.208% | 81.651% | 97.537% | 11.657 | 0.921 | 55.4 |
| 20% | 20.000% | 93.633% | 79.366% | 97.416% | 11.494 | 1.256 | 54.6 |
| 10% | 10.000% | 92.868% | 76.516% | 97.204% | 9.895 | 0.925 | 47.0 |

Column meanings:

- `Used rows` is the requested deterministic percentage of English dictionary rows used to build the stemmer.
- `Actual row ratio` is the selected row count divided by the full parsed dictionary row count.
- `All exact` is exact agreement over every word/root pair in the full dictionary.
- `Changed exact` is exact agreement only where the word differs from its root.
- `Root preserved` is the share of already-root forms that remain unchanged.
- `Speed ms/op` is JMH average time for one changed-token benchmark operation.
- `Error ms` is the JMH score error converted to milliseconds.
- `ns/token` is average nanoseconds per changed token in that operation.

The contracted trie result is materially stronger than the older uncontracted profile: full English coverage reaches 97.478% all-token exactness and 97.197% changed-token exactness at 71.6 ns/token, while even a 10% deterministic dictionary slice remains at 92.868% all-token exactness and 76.516% changed-token exactness at 47.0 ns/token. This is why Radixor benchmark results are documented with both speed and quality instead of a single Porter speed badge.

For benchmark scope, workload design, environment, commands, report locations, and interpretation guidance, see [Benchmarking](docs/benchmarking.md).

## Heritage

Radixor stands in the line of the original **Egothor** stemming work and its later **Stempel** packaging.

Historical Stempel documentation describes the stemmer code as taken virtually unchanged from the Egothor project, and Elasticsearch still documents the Stempel analysis plugin as integrating Lucene’s Stempel module for Polish.

Useful historical references:

- [Egothor project](http://www.egothor.org/)
- [Stempel overview](https://www.getopt.org/stempel/)
- [Leo Galambos, *Lemmatizer for Document Information Retrieval Systems in JAVA* (SOFSEM 2001)](https://www.researchgate.net/publication/221512865_Lemmatizer_for_Document_Information_Retrieval_Systems_in_JAVA)
- [Lucene Stempel overview](https://lucene.apache.org/core/5_3_0/analyzers-stempel/index.html)
- [Elasticsearch Stempel plugin](https://www.elastic.co/docs/reference/elasticsearch/plugins/analysis-stempel)

The Galambos paper is a useful historical reference for the semi-automatic, transformation-based stemming idea that later informed the Egothor lineage and, in turn, the conceptual background of Radixor. It should be read as research and heritage context rather than as a description of Radixor's present-day implementation.

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

The Python installation installs the native package together with the pure
`radixor-models-standard` 1.x distribution of the 2026.1 catalog: 20 precompiled v7 models, excluding
the optional PoliMorf model. Python runtime distributions contain no textual
dictionaries.

- [Installation and Builds](docs/python/installation.md)
  Wheels, source builds, Gradle tasks, host builds, and cross-compilation requirements.

- [Usage and API](docs/python/usage.md)
  Single and batch stemming, caching, custom dictionaries, and compiled models.

- [Dictionary Compilation](docs/python/model-compilation.md)
  Compile a textual dictionary once, load it directly, or share its version 7 binary with Java.

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
  Structured reference for methodology, corpora, environment, English coverage, and per-language result pages.

- [Published Reports](docs/reports.md)  
  Entry points to CI-published reports and GitHub Pages artifacts.

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
    implementation 'org.egothor:radixor:4.0.0'
    runtimeOnly 'org.egothor:radixor-model-pl-pl-polimorf:1.0.0'
}
```
