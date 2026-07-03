# Migration and Backward Compatibility

This page describes the migration from repeated serialized patch-command application to compiled patch commands.

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
