# Extending and Persisting Compiled Tries

This document explains how compiled Radixor tries can be reopened, extended
with domain vocabulary, rebuilt, and stored for deployment. The Java API is
described here; the Python (PyO3) runtime offers the equivalent through
`radixor.TrieBuilder` (see
[Customizing a Dictionary](python/customization.md)). The resulting
[compiled Radixor model](data-formats.md) can be loaded by Java, Python (PyO3),
and Python-C.

## Reopen and extend a compiled trie

`FrequencyTrieBuilders.copyOf(...)` reconstructs a mutable builder from a compiled trie. The reconstructed builder preserves the key-local value counts of the compiled trie as currently stored, making it suitable for subsequent modification and recompilation. Reconstruction is performed from the compiled state, not from the original unreduced insertion history.

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.FrequencyTrieBuilders;
import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.egothor.stemmer.StemmerPatchTrieBinaryIO;

public final class ExtendCompiledStemmerExample {

    private ExtendCompiledStemmerExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final FrequencyTrie<String> compiledTrie = StemmerPatchTrieBinaryIO.read(
                Path.of("stemmers", "english.radixor.gz"));

        final ReductionSettings settings = ReductionSettings.withDefaults(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final FrequencyTrie.Builder<String> builder = FrequencyTrieBuilders.copyOf(
                compiledTrie,
                String[]::new,
                settings);

        final String word = "microservices";
        final String stem = "microservice";
        final PatchCommandEncoder encoder = PatchCommandEncoder.builder()
                .traversalDirection(compiledTrie.traversalDirection())
                .build();
        final String patch = encoder.encode(word, stem);
        builder.putDominant(word, patch);

        final FrequencyTrie<String> updatedTrie = builder.build();

        final String storedPatch = updatedTrie.get(word);
        final String actualStem = CompiledPatchCommand
                .compile(storedPatch, updatedTrie.traversalDirection())
                .apply(word);
        System.out.println(word + " -> " + actualStem);

        StemmerPatchTrieBinaryIO.write(
                updatedTrie,
                Path.of("stemmers", "english-custom.radixor.gz"));
    }
}
```

This enables a layered workflow:

1. start from a bundled or already compiled stemmer,
2. reconstruct a builder,
3. encode each new word-to-stem relationship as a patch command and add it,
4. compile and persist a new binary artifact.

The insertion key is the observed word (`microservices`), while the stored
value is the encoded transformation to its desired stem (`microservice`). Do
not copy a patch string from another word: generate it with
`PatchCommandEncoder` so offsets and traversal direction remain correct. Add
the canonical stem as a no-op relationship too when the custom vocabulary must
recognize it as an input in its own right.

The compiled representation is a directed acyclic graph (DAG): semantically
equivalent subtrees may share one physical node, whose frequencies have already
been aggregated. Reconstruction retains that provenance. Consequently, an
unmodified rebuild counts a shared node once instead of multiplying its counts
by the number of incoming logical paths. Modifying one expanded path creates a
copy-on-write reduction boundary, so its new values and frequencies cannot leak
into unchanged paths that previously shared the node. The original unreduced
per-path insertion history is not recoverable; the compiled aggregate is the
authoritative starting state.

## Choosing how an added rule wins

A compiled model generalizes suffixes: the English model contracts the `-s`
plural into one rule that strips a trailing `s` from any word, so a rule added
for one specific word can be shadowed by that generalization. Two things control
whether your rule takes effect — the update method you use on the builder, and
the lookup mode you read with. `copyOf` reconstructs the contracted
generalizations faithfully, so an unmodified round-trip reproduces the original
stemmer and your additions do not weaken the model's coverage of other words.

### Update methods

`put(key, value, count)` accumulates a frequency. Because the returned stem is
the highest-frequency value at a node, a word that already has a rule can
out-rank a single added occurrence. The additional update methods make the
intent explicit; all return the builder for chaining.

| Method | Effect at the key's node |
| --- | --- |
| `put(key, value, count)` | accumulate a raw frequency |
| `putDominant(key, value)` | make `value` the dominant result, keeping other values as lower-ranked alternatives |
| `set(key, value)` | replace every value at the node with `value` |
| `putIfAbsent(key, value)` | store `value` only when the node has no value yet |
| `remove(key)` | delete every value at the exact node |
| `remove(key, value)` | delete one value, keeping the rest |

`putDominant` is the usual choice for overriding one rule while keeping the prior
candidate visible in `getAll`; `set` discards the alternatives entirely.

### Reading with the specific rule

An added rule sits at the word's own deep node, beneath the shallow
generalization. Under the default `LookupMode.FIRST` the generalization
short-circuits and the added rule is not seen; read with `LookupMode.LAST` so the
specific rule wins (see
[Querying and Ambiguity Handling](programmatic-querying-and-ambiguity.md)).

```java
import org.egothor.stemmer.LookupMode;

final String word = "windows";
final String patch = PatchCommandEncoder.builder()
        .traversalDirection(compiledTrie.traversalDirection())
        .build()
        .encode(word, word); // identity
builder.putDominant(word, patch);

final FrequencyTrie<String> updated = builder.build();
final String stored = updated.withLookupMode(LookupMode.LAST).get(word);
// 'stored' is the identity rule, so the word is not rewritten by the -s generalization.
```

`remove(key)` targets the word's exact node. A word that stems only through a
shorter contracted generalization has no value of its own to delete, so removing
the full word is a no-op — override it with `set` or `putDominant` and read with
`LookupMode.LAST` instead, or remove the shorter suffix key, which affects every
word it covers.

## Persist and deploy compiled tries

`StemmerPatchTrieBinaryIO` reads and writes patch-command tries as GZip-compressed binary files. `StemmerPatchTrieLoader` exposes convenience methods around the same persistence functionality.

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.StemmerPatchTrieBinaryIO;

StemmerPatchTrieBinaryIO.write(trie, Path.of("stemmers", "english.radixor.gz"));
```

In deployment terms, the cleanest model is usually:

- compile once,
- persist the binary artifact,
- load the artifact directly in runtime services.

## Binary-first operational model

For larger dictionaries or controlled deployment environments, a binary-first workflow is usually the most robust choice:

- prepare the compiled trie offline,
- keep the preparation step outside the runtime startup path,
- version and distribute the binary artifact,
- load the finished trie directly in production.

This model works especially well when domain-specific extensions are added in layers and then recompiled into a new read-only artifact.

## Continue with

- [Loading and Building Stemmers](programmatic-loading-and-building.md)
- [Querying and Ambiguity Handling](programmatic-querying-and-ambiguity.md)


## Inspecting persisted metadata

After loading a compiled artifact, applications can inspect the persisted build descriptor directly:

```java
final FrequencyTrie<CompiledPatchCommand> trie =
        StemmerPatchTrieLoader.loadBinaryCompiled("build/stemmers/cs_cz.dat.gz");
final TrieMetadata metadata = trie.metadata();

System.out.println(metadata.formatVersion());
System.out.println(metadata.traversalDirection());
System.out.println(metadata.reductionSettings().reductionMode());
System.out.println(metadata.diacriticProcessingMode());
```

This is especially useful when a deployment manages multiple artifacts compiled under different traversal or reduction regimes.
