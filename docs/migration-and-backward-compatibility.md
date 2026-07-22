# Migration and Backward Compatibility

## Radixor 3.x to 4.x architecture migration

Radixor 3.x published algorithm classes and language dictionaries together as `org.egothor:radixor`. Radixor 4 keeps that established coordinate for the algorithmic core but removes every dictionary from the core JAR. Applications must now choose independently versioned model artifacts. This is deliberately source-compatible where practical and deliberately different at runtime.

### Before and after: dependencies

| Deployment | 3.x | 4.x |
|---|---|---|
| Core | `org.egothor:radixor:<3.x-version>` included dictionaries | `org.egothor:radixor:<radixor-version>` contains code only |
| Minimal Polish | No separate data dependency | Add `radixor-model-pl-pl-unimorph:1.0.0` |
| All defaults | Implicitly embedded | Add optional `radixor-models-standard:<catalog-version>` |
| Optional Polish variant | Not independently selectable | Add and explicitly select `radixor-model-pl-pl-polimorf:1.0.0` |

Gradle, preserving the previous Polish default:

```groovy
dependencies {
    implementation 'org.egothor:radixor:<radixor-version>'
    runtimeOnly 'org.egothor:radixor-model-pl-pl-unimorph:1.0.0'
}
```

Gradle, broad default coverage:

```groovy
dependencies {
    implementation 'org.egothor:radixor:<radixor-version>'
    runtimeOnly 'org.egothor:radixor-models-standard:<catalog-version>'
}
```

Maven, preserving the Polish default:

```xml
<dependency>
  <groupId>org.egothor</groupId>
  <artifactId>radixor</artifactId>
  <version>${radixor.version}</version>
</dependency>
<dependency>
  <groupId>org.egothor</groupId>
  <artifactId>radixor-model-pl-pl-unimorph</artifactId>
  <version>1.0.0</version>
  <scope>runtime</scope>
</dependency>
```

### Before and after: API behavior

Language-oriented calls remain source-compatible:

```java
final FrequencyTrie<CompiledPatchCommand> polish =
        StemmerPatchTrieLoader.loadCompiled(
                StemmerPatchTrieLoader.Language.PL_PL,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
```

In 4.x this call creates a registry and resolves `Language.PL_PL.defaultModelId()`, which is `pl-pl-unimorph`. Source compatibility does not imply runtime classpath compatibility: the call fails with `StemmerModelNotFoundException` unless that model is visible.

Explicit selection enables multiple variants:

```java
final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
final StemmerModelDescriptor polimorf = registry.require("pl-pl-polimorf");
final FrequencyTrie<CompiledPatchCommand> trie =
        StemmerPatchTrieLoader.loadCompiled(
                polimorf,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
```

The existing `load(String, ...)` overload means a filesystem path. The compiled `loadCompiled(String, boolean, ReductionMode)` overload now means a stable model ID; use the `Path` overload for a filesystem dictionary. Descriptor-based compiled loading avoids rediscovery when an application retains a registry. See [Model Selection and Loading](model-selection-and-loading.md) for complete examples.

### Polish migration scenarios

1. **Preserve previous default behavior:** add `radixor-model-pl-pl-unimorph` and keep using `Language.PL_PL`.
2. **Use PoliMorf:** add `radixor-model-pl-pl-polimorf` and call `registry.require("pl-pl-polimorf")`.
3. **Deploy both:** add both runtime artifacts and load each descriptor by ID. They are not merged.
4. **Verify selection:** compare `registry.requireDefault(Language.PL_PL).id()` with `pl-pl-unimorph` through normal application control flow or a JUnit assertion, and inspect `registry.findByLanguage(Language.PL_PL)`.
5. **Diagnose absence:** read the exact `StemmerModelNotFoundException` message, then inspect the production `runtimeClasspath` rather than changing dependency order.

UniMorph and PoliMorf are not interchangeable quality datasets. They can differ in vocabulary, provenance, licensing, and stemming outputs.

Model migration does not erase source obligations. Each migrated UniMorph artifact packages its
language-specific notice with upstream attribution, Radixor modifications and contribution
statement, ShareAlike terms, and the canonical CC BY-SA 3.0 URI. The original imports did not
record exact UniMorph commits, so descriptors use
`source.revision=not-recorded-in-legacy-import` and disclose that fact. Future model imports must
record an exact upstream revision and source-archive checksum.

### Compatibility table

| Dimension | 4.x migration status |
|---|---|
| Source compatibility | Language-oriented loader signatures remain; external model dependencies are new |
| Binary compatibility | Removing resources is a major-version boundary; review all deployed artifacts |
| Runtime classpath | At least one selected model JAR is required |
| Model format | Descriptor format `radixor-dictionary-tsv-gzip` version `1` is validated by the registry |
| Model IDs | Stable runtime identities, independent of artifact discovery order |
| Core Maven coordinate | Remains `org.egothor:radixor` |
| Release versions | Core, each model, upstream source, format, and catalog versions evolve separately |

### Upgrade checklist

- Update the core dependency.
- Choose individual model artifacts or the standard pack.
- Put resource-only model dependencies on the production runtime classpath.
- Verify `Language.defaultModelId()` mappings used by the application.
- Inspect shaded, minimized, plugin, or modular packaging for indexes and resources.
- Run application-level vocabulary and output regression tests.
- Track model artifact versions and checksums separately from the core version.

### Roll back model choice

To return from optional PoliMorf to the default UniMorph behavior, add or retain `radixor-model-pl-pl-unimorph`, stop requesting `pl-pl-polimorf`, and load `Language.PL_PL` or explicitly request `pl-pl-unimorph`. Do not change the language constant. Remove the unused PoliMorf runtime dependency after verifying no explicit lookup still needs it.

Rolling the whole application back to 3.x instead requires restoring the reviewed 3.x core dependency and removing 4.x model assumptions. Do not combine 3.x embedded resources with the 4.x registry architecture.

Core, model, and catalog releases are independent:

```bash
git tag -a "release@4.0.0" -m "Release Radixor 4.0.0"
git tag -a "model/pl-pl-polimorf@1.0.0" -m "Release Polish PoliMorf model 1.0.0"
git tag -a "models-catalog@2026.1" -m "Release Radixor model catalog 2026.1"
```

A core tag publishes only the root `org.egothor:radixor` software artifacts, never model JARs. A model tag validates and publishes exactly its matching module, never core, standard, BOM, JMH, or the multilingual quality suite. A catalog tag publishes only BOM and standard aggregate metadata. Local model dry-run:

The catalog artifacts are POM-only: `radixor-models-standard` carries runtime dependencies on the 20 defaults, while `radixor-models-bom` carries dependency-management constraints for all 21 individual models. Neither publishes an empty binary, sources, or Javadoc JAR. This Maven BOM is distinct from the root CycloneDX SBOM report under `build/reports/sbom/`.

```bash
./tools/parse-model-release-tag.sh "model/pl-pl-polimorf@1.0.0" .
./gradlew --no-daemon :models:pl-pl-polimorf:check
./gradlew --no-daemon :models:pl-pl-polimorf:validateModelRelease -PmodelReleaseVersion=1.0.0
./gradlew --no-daemon :models:pl-pl-polimorf:packageModelReleaseCandidate -PmodelReleaseVersion=1.0.0
```

Model format compatibility is descriptor-level and does not alter migrated bytes. Version 1 is `radixor-dictionary-tsv-gzip`. Model versions come from each module's `model-version.txt` or the matching explicit release property; catalog version comes from `models/catalog-version.txt`; only core uses Git-derived `release@` versioning.

The model catalog used by the published documentation is generated under `build/mkdocs-source/`. Neither generated Markdown nor rendered MkDocs output belongs in Git.

The remainder of this page describes the earlier migration from repeated serialized patch-command application to compiled patch commands.

## Summary

Radixor patch commands are still encoded as compact strings when dictionaries are built and persisted. That serialized form remains the interchange format used by textual dictionaries, binary artifacts, and compilation tooling.

Runtime stemming should no longer repeatedly apply those serialized strings directly. Since 2.3.0, the String-based patch application API is deprecated. Code that stems live input should load or create `CompiledPatchCommand` values and reuse them. The deprecated API remains available for compatibility during the transition, but applications should migrate before 3.0.0.

The reason is performance. The old API parses the serialized P-command every time it is applied. `CompiledPatchCommand` parses it once and stores a concrete immutable command object, so repeated stemming avoids the same analysis work.

## Deprecated Runtime APIs

The following API family is kept for source compatibility but is no longer the preferred runtime path:

- `PatchCommandEncoder.apply(String, String)`
- `PatchCommandEncoder.apply(String, String, WordTraversalDirection)`
- `PatchCommandEncoder.applyTo(..., String, WordTraversalDirection, ...)`
- `PatchCommandEncoder.applyWithConfiguredDirection(String, String)`
- `StemmerPatchTrieLoader.load(...)` overloads returning `FrequencyTrie<String>`
- `StemmerPatchTrieLoader.loadBinary(...)` overloads returning `FrequencyTrie<String>`

Use the compiled equivalents for runtime stemming:

- `CompiledPatchCommand.compile(String, WordTraversalDirection)`
- `PatchCommandEncoder.compile(String)`
- `PatchCommandEncoder.compile(String, WordTraversalDirection)`
- `StemmerPatchTrieLoader.loadCompiled(...)`
- `StemmerPatchTrieLoader.loadBinaryCompiled(...)`

## Loading A Text Dictionary

Old runtime code:

```java
Path dictionary = Path.of("dictionary.txt");
ReductionSettings settings = ReductionSettings.withDefaults(
        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(dictionary, true, settings);

String word = "running";
String patch = trie.get(word);
String stem = patch == null
        ? word
        : PatchCommandEncoder.apply(word, patch, trie.traversalDirection());
```

New runtime code:

```java
Path dictionary = Path.of("dictionary.txt");
ReductionSettings settings = ReductionSettings.withDefaults(
        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(dictionary, true, settings);

String word = "running";
CompiledPatchCommand patch = trie.get(word);
String stem = patch == null ? word : patch.apply(word);
```

## Loading A Binary Artifact

Old runtime code:

```java
FrequencyTrie<String> trie = StemmerPatchTrieLoader.loadBinary(Path.of("us-uk.radixor.gz"));

String word = "studies";
String patch = trie.get(word);
String stem = patch == null
        ? word
        : PatchCommandEncoder.apply(word, patch, trie.traversalDirection());
```

New runtime code:

```java
FrequencyTrie<CompiledPatchCommand> trie =
        StemmerPatchTrieLoader.loadBinaryCompiled(Path.of("us-uk.radixor.gz"));

String word = "studies";
CompiledPatchCommand patch = trie.get(word);
String stem = patch == null ? word : patch.apply(word);
```

Existing binary artifacts remain readable. `loadBinaryCompiled(...)` reads the stored serialized patch strings and compiles them during load setup, before live stemming begins.

## Manual Patch Encoding

Encoding still produces a serialized patch command because that is the compact stored representation:

```java
PatchCommandEncoder encoder = PatchCommandEncoder.builder().build();
String patch = encoder.encode("running", "run");
```

Old repeated application:

```java
String stem = PatchCommandEncoder.apply("running", patch);
```

New repeated application:

```java
CompiledPatchCommand compiled = encoder.compile(patch);
String stem = compiled.apply("running");
```

## Caller-Owned Output Buffers

Old buffer-oriented code:

```java
char[] output = new char[32];
int length = PatchCommandEncoder.applyTo(
        "running",
        patch,
        WordTraversalDirection.BACKWARD,
        output,
        0,
        output.length);
```

New buffer-oriented code:

```java
CompiledPatchCommand compiled = CompiledPatchCommand.compile(patch, WordTraversalDirection.BACKWARD);
char[] output = new char[32];
int length = compiled.applyTo("running", output, 0, output.length);
```

Both APIs return `CompiledPatchCommand.APPLY_INSUFFICIENT_CAPACITY` when the caller-owned output range is too small.

## Compatibility Rules

Serialized patch strings remain part of the dictionary and artifact format. The deprecation is about repeated runtime application of serialized strings, not about the stored representation itself.

Compatibility tests may continue to exercise the deprecated API to prove that old artifacts and source code still work during the transition. New production code, examples, and benchmark runtime paths should use `CompiledPatchCommand`.

The command-line compiler still writes artifacts containing serialized patch commands. Runtime loaders can expose those commands as compiled immutable objects through `loadCompiled(...)` and `loadBinaryCompiled(...)`.

## Contracted Trie Artifacts

Current compiled loaders and freshly written binary artifacts can use contracted compiled tries.
Contraction replaces a subtree with an accepting leaf when every reachable entry below that subtree
selects the same preferred patch command. This changes the physical trie shape and the binary
stream version, but it does not change the serialized patch-command language.

Existing binary artifacts remain readable through the compatibility reader. To obtain the
contracted runtime representation, rebuild the artifact with the current compiler or load the
source dictionary through the current `loadCompiled(...)` APIs. Applications that only consume
`CompiledPatchCommand` values through `get()` and `apply(...)` do not need code changes for this
optimization.
