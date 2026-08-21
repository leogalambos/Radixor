# Extending and Persisting Compiled Tries

This document explains how compiled Radixor tries can be reopened, extended
with domain vocabulary, rebuilt, and stored for deployment. This is currently
a Java capability. The resulting [compiled Radixor model](data-formats.md) can
be loaded by Java, Python (PyO3), and Python-C.

## Reopen and extend a compiled trie

`FrequencyTrieBuilders.copyOf(...)` reconstructs a mutable builder from a compiled trie. The reconstructed builder preserves the key-local value counts of the compiled trie as currently stored, making it suitable for subsequent modification and recompilation. Reconstruction is performed from the compiled state, not from the original unreduced insertion history.

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.FrequencyTrieBuilders;
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
        final String patch = PatchCommandEncoder.builder().build().encode(word, stem);
        builder.put(word, patch);

        final FrequencyTrie<String> updatedTrie = builder.build();

        final String storedPatch = updatedTrie.get(word);
        final String actualStem = PatchCommandEncoder.apply(
                word,
                storedPatch,
                updatedTrie.traversalDirection());
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
