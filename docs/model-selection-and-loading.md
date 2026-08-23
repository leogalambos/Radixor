# Model Selection and Loading

Radixor separates executable stemming code from language data. The core artifact supplies dictionary parsing, trie construction, patch commands, lookup, and the model registry. A model artifact supplies one indexed descriptor, one GZip-compressed Radixor dictionary, and its licensing material. The core JAR contains no language dictionary.

```text
Application
  -> org.egothor:radixor (algorithmic core)
  -> StemmerModelRegistry
  -> indexed model descriptor
  -> namespaced stemmer.gz resource
  -> checksum verification and dictionary parsing
  -> FrequencyTrie construction
  -> patch lookup and stemming
```

## Language and model ID

These identifiers answer different questions:

| Concept | Example | Meaning |
|---|---|---|
| Language | `Language.PL_PL` | Polish as a linguistic identity |
| Model ID | `pl-pl-unimorph` | One concrete Polish model configuration |
| Model ID | `pl-pl-polimorf` | A different concrete Polish model configuration |
| Default model | `PL_PL -> pl-pl-unimorph` | The model selected by the language convenience API |

One language can have several models. `Language.PL_PL` is neither UniMorph nor PoliMorf. `loadCompiled(Language.PL_PL, ...)` resolves the stable default ID declared by `Language.defaultModelId()`. An explicit lookup requests exactly one ID. Registry ordering never changes either decision.

Licensing follows the selected artifact. Radixor Java software is BSD-3-Clause; UniMorph-derived
model data carries a model-specific CC BY-SA 3.0 notice, while PoliMorf carries its separate
BSD-2-Clause license. The UniMorph notice preserves upstream attribution and identifies the
Radixor transformations and limited protectable contributions without claiming the underlying data.

## Choose runtime dependencies

Use the latest published Radixor/Java release together with compatible model
versions from the [published model catalog](stemmer-model-catalog.md). Runtime,
individual model and model-catalog versions evolve independently. The examples
below use descriptive placeholders so that this page does not become stale when
one release stream advances.

### Core plus the default Polish model

```groovy
dependencies {
    implementation 'org.egothor:radixor:<latest-java-version>'
    runtimeOnly 'org.egothor:radixor-model-pl-pl-unimorph:<compatible-model-version>'
}
```

### Core plus optional PoliMorf

```groovy
dependencies {
    implementation 'org.egothor:radixor:<latest-java-version>'
    runtimeOnly 'org.egothor:radixor-model-pl-pl-polimorf:<compatible-model-version>'
}
```

This dependency makes `pl-pl-polimorf` discoverable; it does not change the default for `PL_PL`.

### Both Polish models

```groovy
dependencies {
    implementation 'org.egothor:radixor:<latest-java-version>'
    runtimeOnly 'org.egothor:radixor-model-pl-pl-unimorph:<compatible-model-version>'
    runtimeOnly 'org.egothor:radixor-model-pl-pl-polimorf:<compatible-model-version>'
}
```

### Standard defaults

```groovy
dependencies {
    implementation 'org.egothor:radixor:<latest-java-version>'
    runtimeOnly 'org.egothor:radixor-models-standard:<compatible-catalog-version>'
}
```

The standard aggregate is POM-only. Its POM supplies exactly one default model per supported language as transitive runtime dependencies and excludes optional PoliMorf. It publishes no empty binary JAR.

### BOM-managed versions

```groovy
dependencies {
    implementation 'org.egothor:radixor:<latest-java-version>'
    implementation platform('org.egothor:radixor-models-bom:<compatible-catalog-version>')
    runtimeOnly 'org.egothor:radixor-model-pl-pl-unimorph'
    runtimeOnly 'org.egothor:radixor-model-pl-pl-polimorf'
}
```

Equivalent Maven dependencies use ordinary runtime scope:

```xml
<dependency>
  <groupId>org.egothor</groupId>
  <artifactId>radixor</artifactId>
  <version>REPLACE_WITH_LATEST_JAVA_VERSION</version>
</dependency>
<dependency>
  <groupId>org.egothor</groupId>
  <artifactId>radixor-model-pl-pl-unimorph</artifactId>
  <version>REPLACE_WITH_COMPATIBLE_MODEL_VERSION</version>
  <scope>runtime</scope>
</dependency>
```

Use `implementation` for the core because application code imports its API. Models normally use `runtimeOnly` because they provide resources rather than Java types. Tests with a deliberately isolated model set use `testRuntimeOnly`. The repository attaches every default model and optional PoliMorf directly to `jmhRuntimeOnly`; test and quality configurations likewise use direct non-production model dependencies. No benchmark aggregate artifact exists, and no model dependency enters the root published POM.

## Load the documented default

Dependency prerequisite: core plus `radixor-model-pl-pl-unimorph` (or the standard pack).

```java
import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.egothor.stemmer.StemmerPatchTrieLoader;

final FrequencyTrie<CompiledPatchCommand> polish =
        StemmerPatchTrieLoader.loadCompiled(
                StemmerPatchTrieLoader.Language.PL_PL,
                true,
                ReductionSettings.withDefaults(
                        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));

final String word = "koty";
final CompiledPatchCommand patch = polish.get(word);
final String stem = patch == null ? word : patch.apply(word);
```

The loader creates a registry from the thread context class loader, resolves `PL_PL` to `pl-pl-unimorph`, verifies the compressed resource checksum, decompresses and parses the UTF-8 dictionary, constructs the trie, and compiles its patch commands. It does not load a serialized Java object. If the default artifact is absent, `StemmerModelNotFoundException` names the missing ID and suggested Maven artifact.

## Load PoliMorf explicitly

Dependency prerequisite: core plus `radixor-model-pl-pl-polimorf`.

```java
import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerModelDescriptor;
import org.egothor.stemmer.StemmerModelRegistry;
import org.egothor.stemmer.StemmerPatchTrieLoader;

final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
final StemmerModelDescriptor descriptor = registry.require("pl-pl-polimorf");

final FrequencyTrie<CompiledPatchCommand> polish =
        StemmerPatchTrieLoader.loadCompiled(
                descriptor,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

final String word = "koty";
final CompiledPatchCommand patch = polish.get(word);
final String stem = patch == null ? word : patch.apply(word);
```

The equivalent direct model-ID form is:

```java
final FrequencyTrie<CompiledPatchCommand> polimorf =
        StemmerPatchTrieLoader.loadCompiled(
                "pl-pl-polimorf",
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
```

`require("pl-pl-polimorf")` and the direct overload are deterministic because registry keys are stable model IDs. Discovery order is sorted, duplicate IDs are rejected, and no “first Polish model on the classpath” fallback exists. Both overloads return compiled patch-command values and perform complete integrity checking, parsing, reduction, and trie construction.

!!! warning "PoliMorf startup memory"
    Full construction of the PoliMorf model is memory-intensive. Radixor verifies it in one isolated JVM with a task-specific maximum heap of 6 GiB. Two measured verification runs completed full construction in 23.7 seconds and 23.5 seconds, producing 358,993 canonical trie nodes; the complete Gradle processes peaked at approximately 6.23 GiB resident memory. The compressed model is only 12,624,997 bytes (68,093,680 bytes decompressed), so JAR size is not a proxy for construction-time heap. Applications loading the complete model must provision sufficient startup heap. Radixor does not currently expose a measured retained-heap value, so do not infer one from the process peak.

## Use both Polish models

Dependency prerequisite: both Polish model artifacts.

```java
final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();

final StemmerModelDescriptor unimorph = registry.require("pl-pl-unimorph");
final StemmerModelDescriptor polimorf = registry.require("pl-pl-polimorf");
final StemmerModelDescriptor defaultPolish =
        registry.requireDefault(StemmerPatchTrieLoader.Language.PL_PL);

if (!"pl-pl-unimorph".equals(defaultPolish.id())) {
    throw new IllegalStateException(
            "Unexpected default Polish model: " + defaultPolish.id());
}

final FrequencyTrie<CompiledPatchCommand> unimorphTrie =
        StemmerPatchTrieLoader.loadCompiled(unimorph, true, reductionMode);
final FrequencyTrie<CompiledPatchCommand> polimorfTrie =
        StemmerPatchTrieLoader.loadCompiled(polimorf, true, reductionMode);
```

The descriptors and tries coexist independently. The models are not merged, and adding PoliMorf does not alter the language default. An application that compares, votes across, or merges model outputs must implement that higher-level policy explicitly.

## Discover available models

```java
final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();

for (final StemmerModelDescriptor model : registry.models()) {
    System.out.printf("%s %s %s %s/%d descriptor=%s%n",
            model.id(), model.language(), model.version(),
            model.format(), model.formatVersion(), model.source());
}

final java.util.List<StemmerModelDescriptor> polishModels =
        registry.findByLanguage(StemmerPatchTrieLoader.Language.PL_PL);
```

Both lists use stable model-ID order. The public descriptor API exposes ID, model artifact version, language, display name, runtime resource, default flag, format, format version, checksum, and descriptor source URL. Packaged provenance properties such as `source.name` and `source.version` are not currently exposed as typed descriptor accessors; consult the generated [model catalog](stemmer-model-catalog.md) for them.

## Use an explicit ClassLoader

```java
final ClassLoader pluginLoader = plugin.getClass().getClassLoader();
final StemmerModelRegistry pluginModels =
        StemmerModelRegistry.fromClassLoader(pluginLoader);
final StemmerModelDescriptor model = pluginModels.require("pl-pl-polimorf");
```

`fromContextClassLoader()` uses the current thread context loader, falling back to Radixor's defining loader when the context loader is `null`. `fromClassLoader(loader)` searches only what that loader can expose through `getResources(...)` and ordinary resource lookup. Plugin containers, application servers, and isolated tests can therefore observe different model sets. Pass a non-null loader and retain the registry associated with that deployment scope.

## Error handling

```java
try {
    final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
    final StemmerModelDescriptor model = registry.require("pl-pl-polimorf");
    // Load and cache the trie during application startup.
} catch (final StemmerModelNotFoundException exception) {
    // Missing runtime dependency or model hidden from this ClassLoader.
    throw exception;
} catch (final DuplicateStemmerModelException exception) {
    // Conflicting artifacts or a fat JAR duplicated one stable ID.
    throw exception;
} catch (final UnsupportedStemmerModelFormatException exception) {
    // The model format or format version is not supported by this core.
    throw exception;
} catch (final StemmerModelIntegrityException exception) {
    // Malformed descriptor/index, missing resource, wrong language, or checksum failure.
    throw exception;
} catch (final java.io.IOException exception) {
    // Classpath enumeration or resource I/O failed.
    throw new java.io.UncheckedIOException(exception);
}
```

Malformed metadata does not have a separate public exception: it is reported as `StemmerModelIntegrityException`. Missing explicit and default models both use `StemmerModelNotFoundException`; the default diagnostic additionally names the language and expected default ID. Never swallow these failures or choose an arbitrary model.

## Lifecycle and concurrency

`StemmerModelRegistry` copies discovered descriptors into an unmodifiable map, returns immutable list copies, and has no mutating API. `StemmerModelDescriptor` is final with final fields. These objects are safe to retain after discovery. Registry discovery is not globally cached: every call enumerates indexes and parses descriptors again. Model loading is also not cached: every call reads, hashes, decompresses, parses, and builds a new trie.

Compiled tries are immutable and thread-safe for concurrent reads. Load a registry and the required tries once during application startup, publish them safely, and reuse them. The loader does not cache model tries; do not repeatedly discover and compile models per token. When comparing both Polish models, account for the memory of two independent tries and avoid constructing them concurrently unless the deployment is sized for that peak.

## Troubleshooting

| Symptom | Meaning | Action |
|---|---|---|
| `No default model '...' is available` | The default artifact is absent from the selected loader | Add the named model as a runtime dependency and inspect `runtimeClasspath` |
| `No model 'pl-pl-polimorf' is available` | Explicit optional model is absent or invisible | Add `radixor-model-pl-pl-polimorf` to runtime, not only tests |
| Duplicate model ID | Two resources declare one stable ID | Remove the duplicate artifact or fix fat-JAR resource duplication; do not reorder the classpath |
| Checksum mismatch | Descriptor and compressed bytes differ | Replace the corrupted or incorrectly repackaged artifact |
| Unsupported format | Core supports neither the format name nor version | Use a compatible core/model pair; do not bypass validation |
| Works in tests, fails in production | The model is probably `testRuntimeOnly` | Inspect `./gradlew dependencies --configuration runtimeClasspath` |
| Visible with one loader only | Class loaders expose different resources | Call `fromClassLoader(...)` with the loader that owns the model JAR |
| PoliMorf is installed but language loading uses UniMorph | Expected default behavior | Select `pl-pl-polimorf` explicitly |
| Dependency minimization removed the model | Resource-only dependency was treated as unused | Preserve the model JAR, index, descriptor, license, and dictionary |
| Shaded JAR fails or reports duplicates | Indexes/resources were dropped or duplicated | Inspect with `jar tf app.jar | grep -E 'models.index|stemmer.gz'`; configure deterministic resource merging without duplicating IDs |

Useful Gradle diagnostics include `./gradlew dependencyInsight --dependency radixor-model --configuration runtimeClasspath` and `./gradlew dependencies --configuration testRuntimeClasspath`. Classpath order is not a remediation mechanism.

Continue with [Programmatic Usage](programmatic-usage.md), [Stemmer Models](stemmer-models.md), [Built-in Languages](built-in-languages.md), the generated [model catalog](stemmer-model-catalog.md), and [Architecture](architecture.md).
