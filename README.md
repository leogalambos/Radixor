<img src="docs/assets/images/banner.jpg" width="100%" alt="Radixor banner" />

[![License](https://img.shields.io/github/license/leogalambos/Radixor)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-brightgreen)](#)
[![Maven Central](https://img.shields.io/maven-central/v/org.egothor/radixor)](https://central.sonatype.com/artifact/org.egothor/radixor)
[![Published reports](https://img.shields.io/badge/reports-GitHub%20Pages-blue)](https://leogalambos.github.io/Radixor/builds/latest/)
[![Quality gates](https://github.com/leogalambos/Radixor/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/leogalambos/Radixor/actions/workflows/build.yml)
[![Coverage](https://img.shields.io/endpoint?url=https://leogalambos.github.io/Radixor/builds/latest/metrics/coverage-badge.json)](https://leogalambos.github.io/Radixor/builds/latest/coverage/)
[![Mutation score](https://img.shields.io/endpoint?url=https://leogalambos.github.io/Radixor/builds/latest/metrics/pitest-badge.json)](https://leogalambos.github.io/Radixor/builds/latest/pitest/)

*Deterministic, multi-language stemming for Java, built around compact dictionary-derived patch-command tries with an explicit quality/speed trade-off.*

**Radixor** is a modern multi-language stemming toolkit for Java in the tradition of the original **Egothor** approach. It learns compact word-to-stem transformations from dictionary data, stores them in compiled patch-command tries, and exposes a runtime model designed for speed, determinism, and operational simplicity. Unlike a closed-form dictionary lookup stemmer, Radixor can also generalize beyond explicitly listed word forms.

It is particularly well suited to systems that need stemming which is:

- fast at runtime,
- compact in memory and on disk,
- deterministic in behavior,
- adaptable through dictionary data rather than hardcoded language rules,
- practical to compile, persist, version, extend, and deploy.

It also retains the operational advantages of a compiled artifact model: predictable runtime behavior, direct binary loading, and clear separation between preparation-time compilation and live request processing.

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
| 100% | 100.000% | 97.478% | 97.197% | 97.552% | 23.113 | 7.065 | 109.8 |
| 90% | 90.000% | 97.047% | 94.913% | 97.613% | 21.270 | 9.914 | 101.0 |
| 80% | 80.000% | 96.635% | 92.768% | 97.661% | 19.170 | 6.609 | 91.1 |
| 70% | 70.000% | 96.209% | 90.565% | 97.705% | 20.857 | 6.734 | 99.1 |
| 60% | 60.000% | 95.750% | 88.384% | 97.703% | 14.975 | 1.215 | 71.1 |
| 50% | 50.000% | 95.262% | 86.107% | 97.690% | 15.249 | 1.078 | 72.4 |
| 40% | 40.000% | 94.753% | 83.855% | 97.643% | 15.323 | 2.340 | 72.8 |
| 30% | 30.000% | 94.208% | 81.651% | 97.537% | 16.778 | 2.643 | 79.7 |
| 20% | 20.000% | 93.633% | 79.366% | 97.416% | 18.929 | 3.241 | 89.9 |
| 10% | 10.000% | 92.868% | 76.516% | 97.204% | 19.124 | 1.883 | 90.9 |

Column meanings:

- `Used rows` is the requested deterministic percentage of English dictionary rows used to build the stemmer.
- `Actual row ratio` is the selected row count divided by the full parsed dictionary row count.
- `All exact` is exact agreement over every word/root pair in the full dictionary.
- `Changed exact` is exact agreement only where the word differs from its root.
- `Root preserved` is the share of already-root forms that remain unchanged.
- `Speed ms/op` is JMH average time for one changed-token benchmark operation.
- `Error ms` is the JMH score error converted to milliseconds.
- `ns/token` is average nanoseconds per changed token in that operation.

The contracted trie result is materially stronger than the older uncontracted profile: full English coverage reaches 97.478% all-token exactness and 97.197% changed-token exactness at 109.8 ns/token, while even a 10% deterministic dictionary slice remains at 92.868% all-token exactness and 76.516% changed-token exactness at 90.9 ns/token. This is why Radixor benchmark results are documented with both speed and quality instead of a single Porter speed badge.

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
- Bundled language resources
- Support for extending compiled stemmer tables
- Reproducible and auditable engineering posture

## Documentation

The repository keeps the front page concise and places detailed documentation under `docs/`.

### Getting Started

- [Fast Track](docs/fast-track.md)  
  The shortest path from adding the dependency to getting a first stem from a bundled dictionary.

- [Quick Start](docs/quick-start.md)  
  A broader developer walkthrough covering loading options, querying, extension, persistence, and metadata.

- [Integration Deep Dive](docs/integration-deep-dive.md)  
  Dependency setup, bundled dictionary selection, production lifecycle, search-pipeline guidance, and operational checklist.

- [Built-in Languages](docs/built-in-languages.md)  
  Overview of bundled language resources such as `US_UK`.

- [Dictionary Format](docs/dictionary-format.md)  
  How to write and normalize stemming dictionaries.

- [Compilation (CLI tool)](docs/cli-compilation.md)  
  How to compile dictionaries into deployable binary artifacts.

### Programmatic Usage

- [Programmatic Usage Overview](docs/programmatic-usage.md)  
  Entry point to the Java API and the overall usage model.

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
