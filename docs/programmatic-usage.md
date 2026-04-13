# Programmatic Usage

> ← Back to [README.md](../README.md)

This document describes how to use **Radixor** programmatically from Java.

It covers:

- building a trie from dictionary data
- compiling it into an immutable structure
- loading compiled stemmers
- querying for stems
- working with multiple candidates
- modifying existing compiled stemmers



## Overview

Radixor separates the stemming lifecycle into three stages:

1. **Build** – collect word–stem mappings in a mutable structure  
2. **Compile** – reduce and convert to an immutable trie  
3. **Query** – perform fast runtime lookups  

These stages are represented by:

- `FrequencyTrie.Builder` (mutable)
- `FrequencyTrie` (immutable, compiled)
- `StemmerPatchTrieLoader` / `StemmerPatchTrieBinaryIO` (I/O)



## Building a trie programmatically

You can construct a trie directly without using the CLI.

```java
import org.egothor.stemmer.*;

public final class BuildExample {

    public static void main(String[] args) {
        ReductionSettings settings = ReductionSettings.withDefaults(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
        );

        FrequencyTrie.Builder<String> builder =
                new FrequencyTrie.Builder<>(String[]::new, settings);

        PatchCommandEncoder encoder = new PatchCommandEncoder();

        builder.put("running", encoder.encode("running", "run"));
        builder.put("runs", encoder.encode("runs", "run"));
        builder.put("ran", encoder.encode("ran", "run"));

        FrequencyTrie<String> trie = builder.build();
    }
}
```



## Loading from dictionary files

To parse dictionary files directly:

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.*;

public final class LoadFromDictionaryExample {

    public static void main(String[] args) throws IOException {
        FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(
                Path.of("data/stemmer.txt"),
                true,
                ReductionSettings.withDefaults(
                        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
                )
        );
    }
}
```



## Loading a compiled binary trie

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.*;

public final class LoadBinaryExample {

    public static void main(String[] args) throws IOException {
        FrequencyTrie<String> trie =
                StemmerPatchTrieLoader.loadBinary(Path.of("english.radixor.gz"));
    }
}
```

This is the **preferred production approach**.



## Querying for stems

### Preferred result

```java
String word = "running";
String patch = trie.get(word);
String stem = PatchCommandEncoder.apply(word, patch);
```

### All candidates

```java
String[] patches = trie.getAll(word);

for (String patch : patches) {
    String stem = PatchCommandEncoder.apply(word, patch);
}
```



## Accessing value frequencies

For diagnostic or advanced use cases:

```java
import org.egothor.stemmer.ValueCount;

java.util.List<ValueCount<String>> entries = trie.getEntries("axes");

for (ValueCount<String> entry : entries) {
    String patch = entry.value();
    int count = entry.count();
}
```

This allows:

* inspecting ambiguity
* understanding ranking decisions
* debugging dictionary quality



## Using bundled language resources

```java
FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(
        StemmerPatchTrieLoader.Language.US_UK_PROFI,
        true,
        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
);
```

Bundled dictionaries are useful for:

* quick integration
* testing
* reference behavior



## Persisting a compiled trie

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.*;

public final class SaveExample {

    public static void main(String[] args) throws IOException {
        StemmerPatchTrieBinaryIO.write(trie, Path.of("english.radixor.gz"));
    }
}
```



## Modifying an existing trie

A compiled trie can be reopened into a builder, extended, and rebuilt.

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.*;

public final class ModifyExample {

    public static void main(String[] args) throws IOException {
        FrequencyTrie<String> compiled =
                StemmerPatchTrieBinaryIO.read(Path.of("english.radixor.gz"));

        ReductionSettings settings = ReductionSettings.withDefaults(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
        );

        FrequencyTrie.Builder<String> builder =
                FrequencyTrieBuilders.copyOf(compiled, String[]::new, settings);

        builder.put("microservices", PatchCommandEncoder.NOOP_PATCH);

        FrequencyTrie<String> updated = builder.build();

        StemmerPatchTrieBinaryIO.write(updated,
                Path.of("english-custom.radixor.gz"));
    }
}
```



## Thread safety

* `FrequencyTrie` (compiled):

  * **thread-safe**
  * safe for concurrent reads

* `FrequencyTrie.Builder`:

  * **not thread-safe**
  * intended for single-threaded construction



## Performance characteristics

### Querying

* O(length of word)
* minimal allocations
* suitable for high-throughput pipelines

### Loading

* binary loading is fast
* no preprocessing required

### Building

* depends on dictionary size
* reduction phase may be CPU-intensive



## Best practices

### Reuse compiled trie instances

* load once
* share across threads

### Prefer binary loading in production

* avoid rebuilding at runtime
* treat compiled files as deployable artifacts

### Use `getAll()` only when needed

* `get()` is faster and sufficient for most use cases

### Keep builders short-lived

* build → compile → discard



## Integration patterns

### Search systems

* apply stemming during indexing and querying
* ensure consistent dictionary usage

### Text normalization pipelines

* integrate as a transformation step
* combine with tokenization and filtering

### Domain adaptation

* extend dictionaries with domain-specific vocabulary
* rebuild compiled artifacts



## Next steps

* [Dictionary format](dictionary-format.md)
* [CLI compilation](cli-compilation.md)
* [Architecture and reduction](architecture-and-reduction.md)



## Summary

Programmatic usage of Radixor follows a clear pattern:

* build or load a trie
* query using patch commands
* apply transformations

The API is intentionally simple at the surface, while providing deeper control when needed for:

* ambiguity handling
* diagnostics
* dictionary evolution
