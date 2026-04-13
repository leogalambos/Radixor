<img src="Radixor.png" width="30%" align="right" alt="Radixor logo" />

# Radixor

*Fast algorithmic stemming with compact patch-command tries.*

**Radixor** is a fast, algorithmic stemming toolkit for Java, built around compact **patch-command tries** in the tradition of the original **Egothor** stemmer.

It is designed for production search and text-processing systems that need stemming which is:

- fast at runtime
- compact in memory and on disk
- deterministic in behavior
- driven by dictionary data rather than hardcoded language rules
- practical to maintain, extend, and test

Radixor keeps the valuable core of the original Egothor idea, modernizes the implementation, and adds capabilities that make it more useful in real software systems today.

## Table of Contents

- [Why Radixor](#why-radixor)
- [Heritage](#heritage)
- [What Radixor adds](#what-radixor-adds)
- [Key features](#key-features)
- [Documentation](#documentation)
- [Project philosophy](#project-philosophy)
- [Historical note](#historical-note)

## Why Radixor

The central idea behind Radixor is simple: learn how to transform a word form into its stem, encode that transformation as a compact patch command, store it in a trie, and make runtime lookup extremely fast.

This gives you a stemmer that is:

- data-driven rather than rule-hardcoded
- reusable across languages
- compact enough for deployment-friendly binary artifacts
- suitable for both offline compilation and runtime loading

Radixor is especially attractive when you want something more adaptable than simple suffix stripping, but much smaller and easier to operate than a full morphological analyzer.

## Heritage

Radixor stands in the line of the original **Egothor** stemming work and its later **Stempel** packaging.

Historical Stempel documentation describes the stemmer code as taken virtually unchanged from the Egothor project, and Elasticsearch still documents the Stempel analysis plugin as integrating Lucene’s Stempel module for Polish.

Useful historical references:

- [Egothor project](http://www.egothor.org/)
- [Stempel overview](https://www.getopt.org/stempel/)
- [Lucene Stempel overview](https://lucene.apache.org/core/5_3_0/analyzers-stempel/index.html)
- [Elasticsearch Stempel plugin](https://www.elastic.co/docs/reference/elasticsearch/plugins/analysis-stempel)

Radixor is not just a repackaging of legacy code. It is a practical modernization of the approach for current Java development and long-term maintainability.

## What Radixor adds

Radixor keeps the patch-command trie model, but improves the engineering around it.

Compared with the historical baseline, Radixor emphasizes:

- **simplification to the most practical core**  
  The implementation focuses on the parts of the original approach that are most useful in production.

- **immutable compiled tries**  
  Runtime lookup uses compact read-only structures optimized for efficient access.

- **support for more than one stemming result**  
  Radixor can expose both a preferred result and multiple candidate results where the data is ambiguous.

- **frequency-aware deterministic ordering**  
  Candidate results are ordered consistently and reproducibly.

- **practical subtree reduction modes**  
  Reduction can be tuned toward stronger compression or more conservative behavioral preservation.

- **reconstruction of writable builders from compiled tables**  
  Existing compiled stemmer tables can be reopened, modified, and compiled again.

- **better tests and implementation stability**  
  Stronger coverage improves confidence during refactoring and further development.

## Key features

- Fast algorithmic stemming
- Compact compiled binary artifacts
- Patch-command based transformation model
- Dictionary-driven language adaptation
- Single-result and multi-result lookup
- Deterministic result ordering
- Compressed binary persistence
- Programmatic compilation and loading
- CLI compilation tool
- Bundled language resources
- Support for extending compiled stemmer tables

## Documentation

The repository keeps the front page concise and places detailed documentation under `docs/`.

Start here:

- [Quick Start](docs/quick-start.md)  
  A practical first guide to loading, compiling, and using Radixor.

- [Dictionary Format](docs/dictionary-format.md)  
  How to write stemming dictionaries.

- [Compilation (CLI tool)](docs/cli-compilation.md)  
  How to compile dictionaries with the `Compile` CLI.

- [Programmatic Usage](docs/programmatic-usage.md)  
  How to build, load, modify, and query Radixor from Java code.

- [Built-in Languages](docs/built-in-languages.md)  
  How to use integrated language resources such as `US_UK_PROFI`.

- [Architecture and Reduction](docs/architecture-and-reduction.md)  
  Internal model, compiled trie design, and reduction strategies.

- [Quality and Operations](docs/quality-and-operations.md)  
  Testing, persistence, deployment, and operational guidance.

## Project philosophy

Radixor does not preserve historical complexity for its own sake.

It preserves the valuable idea:

- compact learned transformations
- trie-based lookup
- language-data driven stemming
- practical runtime speed

Then it improves the parts modern users care about:

- maintainability
- testability
- modification workflows
- persistence
- determinism
- clearer APIs

The goal is to keep the Egothor/Stempel lineage useful as a serious contemporary software component.

## Historical note

Egothor showed that stemming could be both algorithmic and compact. Stempel proved that the approach was practical enough to survive inside major search ecosystems. Radixor continues that tradition with a modernized implementation focused on production use, maintainability, and controlled evolution.
