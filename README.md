<img src="Radixor.png" width="30%" align="right" alt="Radixor logo" />

# Radixor

[![Quality gates](https://github.com/leogalambos/Radixor/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/leogalambos/Radixor/actions/workflows/build.yml)
[![Coverage](https://img.shields.io/endpoint?url=https://leogalambos.github.io/Radixor/builds/latest/metrics/coverage-badge.json)](https://leogalambos.github.io/Radixor/builds/latest/coverage/)
[![Published reports](https://img.shields.io/badge/reports-GitHub%20Pages-blue)](https://leogalambos.github.io/Radixor/builds/latest/)
[![Mutation score](https://img.shields.io/endpoint?url=https://leogalambos.github.io/Radixor/builds/latest/metrics/pitest-badge.json)](https://leogalambos.github.io/Radixor/builds/latest/pitest/)
[![English benchmark](https://img.shields.io/endpoint?url=https://leogalambos.github.io/Radixor/builds/latest/metrics/jmh-badge.json)](https://leogalambos.github.io/Radixor/builds/latest/jmh/jmh-results.txt)
[![Maven Central](https://img.shields.io/maven-central/v/org.egothor/radixor)](https://central.sonatype.com/artifact/org.egothor/radixor)
[![License](https://img.shields.io/github/license/leogalambos/Radixor)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-brightgreen)](#)

*Fast, deterministic, multi-language stemming for Java, built around compact patch-command tries and measured at roughly 4× to 6× the throughput of the Snowball Porter stemmer family on the current English benchmark workload.*

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

Radixor includes a JMH benchmark suite for both its own algorithmic core and a side-by-side English comparison against the Snowball Porter stemmer family.

On the current English comparison workload, Radixor with bundled `US_UK` reaches approximately **31 to 32 million tokens per second**. Snowball original Porter reaches approximately **8 million tokens per second**, and Snowball English (Porter2) approximately **5 to 5.5 million tokens per second**.

That places Radixor at approximately:

- **4× the throughput of Snowball original Porter**
- **6× the throughput of Snowball English (Porter2)**

on the current benchmark workload.

This is a throughput comparison on the same deterministic token stream. It is **not** a claim that the compared stemmers are linguistically equivalent or interchangeable.

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

- [Quick Start](docs/quick-start.md)  
  A practical first guide to loading, compiling, and using Radixor.

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

### Concepts and Internals

- [Architecture and Reduction Overview](docs/architecture-and-reduction.md)  
  High-level explanation of the build pipeline and compiled trie model.

- [Architecture](docs/architecture.md)  
  Structural model, data flow, and runtime lookup behavior.

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
  JMH benchmark methodology, Porter comparison, and result interpretation.

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
