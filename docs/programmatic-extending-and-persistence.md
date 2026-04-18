# Extending and Persisting Compiled Tries

This document explains how compiled Radixor tries can be reopened, extended, rebuilt, and stored for deployment.

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

        builder.put("microservices", "Na");

        final FrequencyTrie<String> updatedTrie = builder.build();

        StemmerPatchTrieBinaryIO.write(
                updatedTrie,
                Path.of("stemmers", "english-custom.radixor.gz"));
    }
}
```

This enables a layered workflow:

1. start from a bundled or already compiled stemmer,
2. reconstruct a builder,
3. add custom lexical data,
4. compile and persist a new binary artifact.

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
