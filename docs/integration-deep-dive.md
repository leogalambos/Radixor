# Java Integration Deep Dive

This page explains how to integrate Radixor into a real Java application after the first
fast-track experiment works. It covers dependencies, external model artifacts, runtime lifecycle,
deployment artifacts, and the decisions that matter in search or text-processing systems.

## Integration Model

Radixor has two separate phases:

| Phase | Work | Typical location |
| --- | --- | --- |
| Preparation | Parse dictionaries, derive patch commands, reduce and contract the trie, optionally persist a binary artifact. | Build pipeline, packaging job, admin tool, or startup for small services. |
| Runtime | Load an immutable compiled trie, look up patch commands, apply them to tokens. | Search indexing, query processing, text normalization, enrichment pipelines. |

The practical rule is simple: compile rarely, stem often.

For production systems, prefer a startup-owned or dependency-injected
`FrequencyTrie<CompiledPatchCommand>` per language/configuration. The compiled structure has no
mutating API. The project does not currently publish a formal cross-thread safety guarantee, so
applications should use normal safe-publication practices when sharing a loaded trie.

## Dependency Coordinates

The Maven coordinates are:

```text
org.egothor:radixor
```

Gradle:

```kotlin
dependencies {
    implementation("org.egothor:radixor:<radixor-version>")
    runtimeOnly("org.egothor:radixor-models-standard:<catalog-version>")
}
```

Maven:

```xml
<dependency>
    <groupId>org.egothor</groupId>
    <artifactId>radixor</artifactId>
    <version>${radixor.version}</version>
</dependency>
<dependency>
    <groupId>org.egothor</groupId>
    <artifactId>radixor-models-standard</artifactId>
    <version>${model.catalog.version}</version>
    <scope>runtime</scope>
</dependency>
```

Replace the example versions with the independently selected core and catalog releases for your deployment.

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

## Runtime Model Artifacts

The core ships no language dictionary. Add one or more `radixor-model-<model-id>` artifacts, or the optional metadata-only standard pack. Each model JAR contains an indexed descriptor and a namespaced GZip dictionary. `StemmerPatchTrieLoader.Language` represents language properties and a stable default model ID; it does not own embedded data.

The standard option is specifically a POM-only runtime dependency aggregate, not an all-model binary JAR. It resolves one default model JAR per language and excludes optional PoliMorf. The separate POM-only `radixor-models-bom` manages recommended versions without adding runtime artifacts. Repository tests and JMH attach individual model projects directly to non-production configurations, so neither path changes the root publication's dependency graph.

For minimal deployments choose only required model artifacts. For multiple Polish variants add both `pl-pl-unimorph` and `pl-pl-polimorf`, retain UniMorph as the language default, and request PoliMorf explicitly. See [Model Selection and Loading](model-selection-and-loading.md) for complete dependencies and [Built-in Languages](built-in-languages.md) for mappings.

Use `loadCompiled("pl-pl-polimorf", true, reductionMode)` for direct exact selection, or discover once and call `loadCompiled(descriptor, true, reductionMode)`. Neither form caches the trie. Complete PoliMorf startup is memory-intensive and is verified with a dedicated 6 GiB heap; construct it once during application initialization and retain the immutable result.

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

1. choose a registered model resource or caller-owned custom dictionary,
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

## Choosing Registered Versus Custom Dictionaries

Start with registered model artifacts when:

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
- language, model ID, model artifact version, checksum, and reduction mode are documented,
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
- [Model Selection and Loading](model-selection-and-loading.md)
- [CLI Compilation](cli-compilation.md)
- [Benchmarking](benchmarking.md)
