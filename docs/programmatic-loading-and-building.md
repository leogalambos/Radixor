# Loading and Building Stemmers

This document explains how to acquire a compiled Radixor stemmer in Java.

## Load a bundled language dictionary

Bundled language resources are simple to use and compile directly into a `FrequencyTrie<String>` during loading.

```java
import java.io.IOException;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class BundledLanguageExample {

    private BundledLanguageExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(
                StemmerPatchTrieLoader.Language.US_UK_PROFI,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
    }
}
```

The `storeOriginal` flag controls whether the canonical stem is inserted as a no-op patch entry for the stem itself.

## Load a textual dictionary

Loading from a dictionary file follows the same preparation model as bundled resources, but the source comes from your own file or path. The textual format is tab-separated values, meaning that columns are separated by the tab character. Each non-empty logical line starts with the stem column and may contain zero or more variant columns. Input is normalized to lower case using `Locale.ROOT`, trailing remarks introduced by `#` or `//` are ignored, and dictionary items containing embedded whitespace are currently ignored with warning-level diagnostics.

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class LoadTextDictionaryExample {

    private LoadTextDictionaryExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(
                Path.of("data", "stemmer.tsv"),
                true,
                ReductionSettings.withDefaults(
                        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
    }
}
```

## Load a compiled binary artifact

Binary loading is typically the preferred runtime path because it avoids reparsing the textual source and skips the preparation step entirely.

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class LoadBinaryExample {

    private LoadBinaryExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.loadBinary(
                Path.of("stemmers", "english.radixor.gz"));
    }
}
```

The binary format is the native `FrequencyTrie` serialization wrapped in GZip compression.

## Build directly with a mutable builder

A `FrequencyTrie.Builder<V>` accepts repeated `put(key, value)` calls and compiles the final read-only trie through `build()`. Compilation performs bottom-up reduction and produces the compact immutable runtime representation.

```java
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;

public final class BuilderExample {

    private BuilderExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) {
        final ReductionSettings settings = ReductionSettings.withDefaults(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final FrequencyTrie.Builder<String> builder =
                new FrequencyTrie.Builder<>(String[]::new, settings);

        final PatchCommandEncoder encoder = new PatchCommandEncoder();

        builder.put("running", encoder.encode("running", "run"));
        builder.put("runs", encoder.encode("runs", "run"));
        builder.put("ran", encoder.encode("ran", "run"));
        builder.put("runner", encoder.encode("runner", "run"));

        final FrequencyTrie<String> trie = builder.build();
        System.out.println("Canonical node count: " + trie.size());
    }
}
```

## Preparation-time memory characteristics

Compilation is commonly a one-time preparation activity and is generally fast enough not to be the main operational concern. The more important constraint is memory usage while building from textual dictionary data. Before reduction produces the compact immutable structure, the mutable build-time representation keeps the inserted data in memory. This is precisely why very large source dictionaries may require noticeably more memory during preparation than after compilation. The resulting compiled trie, by contrast, is designed as the compact runtime form.

This makes offline preparation especially attractive for large dictionaries.

## Continue with

- [Querying and Ambiguity Handling](programmatic-querying-and-ambiguity.md)
- [Extending and Persisting Compiled Tries](programmatic-extending-and-persistence.md)
