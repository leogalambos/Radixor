# Java Programmatic Usage

Radixor code and model data are separate runtime components. Every example on
this page requires `org.egothor:radixor:<latest-java-version>` as an
`implementation` dependency and at least one compatible model JAR as a runtime
dependency. Resolve the placeholders from the
[Maven Central artifact page](https://central.sonatype.com/artifact/org.egothor/radixor)
and the [model catalog](stemmer-model-catalog.md), respectively. The core JAR
contains no `stemmer.gz`.

The Python implementation has its own native API. `pip install radixor` also
installs the separate standard data package containing 20 precompiled models.
See the [Python Quick Start](python/quick-start.md) and
[Python Usage and API](python/usage.md).

For complete dependency patterns, lifecycle guidance, and troubleshooting, use [Model Selection and Loading](model-selection-and-loading.md). The generated [model catalog](stemmer-model-catalog.md) records the current artifacts, versions, checksums, and provenance.

## 1. Minimal use: the Polish default

Dependency prerequisite:

```groovy
implementation 'org.egothor:radixor:<latest-java-version>'
runtimeOnly 'org.egothor:radixor-model-pl-pl-unimorph:<compatible-model-version>'
```

```java
import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

final FrequencyTrie<CompiledPatchCommand> trie =
        StemmerPatchTrieLoader.loadCompiled(
                StemmerPatchTrieLoader.Language.PL_PL,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

final String word = "koty";
final CompiledPatchCommand patch = trie.get(word);
final String stem = patch == null ? word : patch.apply(word);
```

`Language.PL_PL` resolves to `pl-pl-unimorph`. The loader creates the registry internally through the thread context class loader.

## 2. Explicit model selection

Dependency prerequisite: replace or supplement the default dependency with
`runtimeOnly 'org.egothor:radixor-model-pl-pl-polimorf:<compatible-model-version>'`.

```java
final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
final StemmerModelDescriptor polimorf = registry.require("pl-pl-polimorf");
final FrequencyTrie<CompiledPatchCommand> trie =
        StemmerPatchTrieLoader.loadCompiled(
                polimorf,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
```

The stable model-ID overload performs the same exact selection without a separately retained registry:

```java
final FrequencyTrie<CompiledPatchCommand> trie =
        StemmerPatchTrieLoader.loadCompiled(
                "pl-pl-polimorf",
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
```

## 3. Multiple variants for one language

Dependency prerequisite: compatible releases of both
`radixor-model-pl-pl-unimorph` and `radixor-model-pl-pl-polimorf` at runtime.

```java
final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
final StemmerModelDescriptor unimorph = registry.require("pl-pl-unimorph");
final StemmerModelDescriptor polimorf = registry.require("pl-pl-polimorf");

final FrequencyTrie<CompiledPatchCommand> unimorphTrie =
        StemmerPatchTrieLoader.loadCompiled(unimorph, true, reductionMode);
final FrequencyTrie<CompiledPatchCommand> polimorfTrie =
        StemmerPatchTrieLoader.loadCompiled(polimorf, true, reductionMode);

final StemmerModelDescriptor defaultPolish =
        registry.requireDefault(StemmerPatchTrieLoader.Language.PL_PL);
if (!"pl-pl-unimorph".equals(defaultPolish.id())) {
    throw new IllegalStateException(
            "Unexpected default Polish model: " + defaultPolish.id());
}
```

The tries remain independent. Radixor does not merge models or infer an alternative default from classpath order.

## 4. Discovery

Dependency prerequisite: whichever model artifacts the application intends to discover.

```java
final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();

for (final StemmerModelDescriptor descriptor : registry.models()) {
    System.out.printf("%s %s %s %s/%d%n",
            descriptor.id(), descriptor.language(), descriptor.version(),
            descriptor.format(), descriptor.formatVersion());
}

final java.util.List<StemmerModelDescriptor> polish =
        registry.findByLanguage(StemmerPatchTrieLoader.Language.PL_PL);
```

Results use deterministic model-ID order. See [Built-in Languages](built-in-languages.md) for default interpretation and the generated [catalog](stemmer-model-catalog.md) for provenance.

## 5. Advanced ClassLoader selection

Dependency prerequisite: the model JAR must be visible to the selected loader.

```java
final ClassLoader applicationLoader = application.getClass().getClassLoader();
final StemmerModelRegistry isolatedRegistry =
        StemmerModelRegistry.fromClassLoader(applicationLoader);
```

This form is useful for plugin containers, isolated application servers, and tests. It can discover a different set from the thread context loader. See [ClassLoader troubleshooting](model-selection-and-loading.md#troubleshooting).

## 6. Error handling

Dependency prerequisite: none beyond core; this example demonstrates an absent optional model.

```java
try {
    StemmerModelRegistry.fromContextClassLoader().require("pl-pl-polimorf");
} catch (final StemmerModelNotFoundException exception) {
    System.err.println(exception.getMessage());
}
```

Missing models never produce an empty trie or arbitrary fallback. Duplicate IDs, unsupported formats, malformed descriptors, missing resources, and checksum mismatches are also fatal. The full exception mapping and remediation table are in [Model Selection and Loading](model-selection-and-loading.md#error-handling).

## Continue into the trie API

- [Loading and Building Stemmers](programmatic-loading-and-building.md)
- [Querying and Ambiguity Handling](programmatic-querying-and-ambiguity.md)
- [Extending and Persisting Compiled Tries](programmatic-extending-and-persistence.md)
- [Architecture](architecture.md)
