# Integration Deep Dive

This page explains how to integrate Radixor into a real Java application after the first
fast-track experiment works. It covers dependencies, bundled dictionaries, runtime lifecycle,
deployment artifacts, and the decisions that matter in search or text-processing systems.

## Integration Model

Radixor has two separate phases:

| Phase | Work | Typical location |
| --- | --- | --- |
| Preparation | Parse dictionaries, derive patch commands, reduce and contract the trie, optionally persist a binary artifact. | Build pipeline, packaging job, admin tool, or startup for small services. |
| Runtime | Load an immutable compiled trie, look up patch commands, apply them to tokens. | Search indexing, query processing, text normalization, enrichment pipelines. |

The practical rule is simple: compile rarely, stem often.

For production systems, prefer a startup-owned or dependency-injected singleton
`FrequencyTrie<CompiledPatchCommand>` per language/configuration. The trie is immutable after
construction and is suitable for concurrent reads.

## Dependency Coordinates

The Maven coordinates are:

```text
org.egothor:radixor
```

Gradle:

```kotlin
dependencies {
    implementation("org.egothor:radixor:3.0.0")
}
```

Maven:

```xml
<dependency>
    <groupId>org.egothor</groupId>
    <artifactId>radixor</artifactId>
    <version>3.0.0</version>
</dependency>
```

Replace `3.0.0` with the current release selected for your deployment.

The core Java module is:

```java
module org.egothor.radixor;
```

A named consuming module declares:

```java
module example.search {
    requires org.egothor.radixor;
}
```

## Bundled Dictionaries

Radixor ships bundled dictionaries inside the library artifact. The public API exposes them through:

```java
StemmerPatchTrieLoader.Language
```

The physical resources are packaged as compressed UTF-8 dictionaries under resource directories
such as:

```text
us_uk/stemmer.gz
de_de/stemmer.gz
fr_fr/stemmer.gz
pl_pl/stemmer.gz
```

Treat those resource paths as implementation details. Application code should load bundled
dictionaries through `StemmerPatchTrieLoader.Language`, because the enum also carries the language
metadata needed for correct traversal.

See [Built-in Languages](built-in-languages.md) for the complete language list, writing-direction
notes, and links to per-language benchmark pages.

## Minimal Service Wrapper

A small service wrapper keeps loading, null handling, and fallback behavior in one place.

```java
import java.io.IOException;
import java.util.Objects;

import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class RadixorStemmerService {

    private final FrequencyTrie<CompiledPatchCommand> trie;

    public RadixorStemmerService(final StemmerPatchTrieLoader.Language language) throws IOException {
        this.trie = StemmerPatchTrieLoader.loadCompiled(
                Objects.requireNonNull(language, "language"),
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
    }

    public String stem(final String token) {
        final String checkedToken = Objects.requireNonNull(token, "token");
        final CompiledPatchCommand command = trie.get(checkedToken);
        return command == null ? checkedToken : command.apply(checkedToken);
    }
}
```

The fallback behavior preserves the original token when the trie has no patch command for it. That
is usually the right default for search normalization, because unknown tokens should remain
searchable.

## Production Artifact Workflow

For a controlled deployment, compile once and deploy the binary artifact:

1. choose a bundled or custom dictionary,
2. optionally extend it with domain vocabulary,
3. compile a contracted trie,
4. persist it as `.radixor.gz`,
5. deploy that artifact with the application,
6. load it with `StemmerPatchTrieLoader.loadBinaryCompiled(...)`.

Runtime loading then avoids dictionary parsing and preparation-time memory pressure.

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class BinaryStemmerLoader {

    private BinaryStemmerLoader() {
        throw new AssertionError("No instances.");
    }

    public static FrequencyTrie<CompiledPatchCommand> loadEnglish() throws IOException {
        return StemmerPatchTrieLoader.loadBinaryCompiled(Path.of("stemmers", "english.radixor.gz"));
    }
}
```

Use [CLI Compilation](cli-compilation.md) for command-line artifact creation, or
[Extending and Persisting Compiled Tries](programmatic-extending-and-persistence.md) for
programmatic artifact generation.

## Search Pipeline Guidance

Use Radixor consistently across indexing and querying:

- choose one language dictionary per field or per analysis chain,
- apply the same token normalization before stemming on both sides,
- keep the compiled trie in memory and reuse it,
- use `get(...)` for a single preferred stem,
- use `getAll(...)` when a retrieval model benefits from preserving alternatives,
- version custom `.radixor.gz` artifacts with the application or index schema.

For multilingual content, do not run every token through every language. Route text by field,
document metadata, or language detection before stemming.

## Choosing Bundled Versus Custom Dictionaries

Start with bundled dictionaries when:

- the language is supported,
- the application needs a strong baseline quickly,
- the vocabulary is general-purpose,
- the team is evaluating Radixor or building an initial integration.

Use custom or extended dictionaries when:

- domain vocabulary changes search quality,
- product names, technical terms, legal terms, or biomedical terms must be preserved consistently,
- stemming behavior must be curated and reviewed,
- a release process needs a versioned lexical artifact.

The dictionary format is intentionally simple and documented in
[Dictionary Format](dictionary-format.md). Contribution standards are described in
[Contributing Dictionaries](contributing-dictionaries.md).

## Performance Practices

The hot path should be only:

```text
token -> trie lookup -> compiled command application -> stem
```

Avoid these patterns in production request paths:

- loading or compiling dictionaries per request,
- applying serialized patch strings repeatedly instead of `CompiledPatchCommand`,
- rebuilding tries for short-lived batches,
- mixing different stemmer configurations between indexing and querying,
- interpreting speed without checking exact-root quality.

The current benchmark documentation separates methodology, corpora, environment, and language
results so performance claims remain auditable. Start with [Benchmarking](benchmarking.md), then
use [Benchmark Results](benchmarks/index.md) for the detailed reference tree.

## Operational Checklist

Before production rollout:

- dependency version is pinned,
- language resource and reduction mode are documented,
- indexing and query pipelines use the same stemming configuration,
- custom artifacts are versioned and reproducible,
- fallback behavior for unknown tokens is explicit,
- benchmark expectations are read together with quality metrics,
- CI includes at least a smoke test that stems representative project vocabulary.

## Related Pages

- [Fast Track](fast-track.md)
- [Quick Start](quick-start.md)
- [Built-in Languages](built-in-languages.md)
- [Programmatic Usage](programmatic-usage.md)
- [CLI Compilation](cli-compilation.md)
- [Benchmarking](benchmarking.md)
