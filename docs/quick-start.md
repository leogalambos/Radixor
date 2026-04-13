# Quick Start

> ← Back to [README.md](../README.md)

This guide shows the fastest way to start using **Radixor** and the most common next steps.

## Hello world

```java
import java.io.IOException;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class HelloRadixor {

    private HelloRadixor() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(
                StemmerPatchTrieLoader.Language.US_UK_PROFI,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final String word = "running";
        final String patch = trie.get(word);
        final String stem = PatchCommandEncoder.apply(word, patch);

        System.out.println(word + " -> " + stem);
    }
}
```

This example shows the core workflow:

1. load a trie
2. get a patch command for a word
3. apply the patch
4. obtain the stem

## Retrieve multiple candidate stems

If you need more than one candidate result, use `getAll(...)` instead of `get(...)`.

```java
final String word = "axes";
final String[] patches = trie.getAll(word);

for (String patch : patches) {
    final String stem = PatchCommandEncoder.apply(word, patch);
    System.out.println(word + " -> " + stem + " (" + patch + ")");
}
```

## Load a compiled binary stemmer

For production systems, the preferred approach is usually to precompile the dictionary and load the compressed binary artifact at runtime.

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class BinaryStemmerExample {

    private BinaryStemmerExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final Path path = Path.of("stemmers", "english.radixor.gz");
        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.loadBinary(path);

        final String word = "connected";
        final String patch = trie.get(word);
        final String stem = PatchCommandEncoder.apply(word, patch);

        System.out.println(word + " -> " + stem);
    }
}
```

## Compile a dictionary from the command line

```bash
java org.egothor.stemmer.Compile \
    --input ./data/stemmer.txt \
    --output ./build/english.radixor.gz \
    --reduction-mode MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS \
    --store-original \
    --overwrite
```

## Modify an existing compiled stemmer

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.FrequencyTrieBuilders;
import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.egothor.stemmer.StemmerPatchTrieBinaryIO;

public final class ModifyCompiledExample {

    private ModifyCompiledExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final Path input = Path.of("stemmers", "english.radixor.gz");
        final Path output = Path.of("stemmers", "english-custom.radixor.gz");

        final FrequencyTrie<String> compiledTrie = StemmerPatchTrieBinaryIO.read(input);

        final ReductionSettings settings = ReductionSettings.withDefaults(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final FrequencyTrie.Builder<String> builder = FrequencyTrieBuilders.copyOf(
                compiledTrie,
                String[]::new,
                settings);

        builder.put("microservices", PatchCommandEncoder.NOOP_PATCH);

        final FrequencyTrie<String> updatedTrie = builder.build();
        StemmerPatchTrieBinaryIO.write(updatedTrie, output);
    }
}
```

## Where to continue

* [Dictionary format](dictionary-format.md)
* [CLI compilation](cli-compilation.md)
* [Programmatic usage](programmatic-usage.md)
* [Built-in languages](built-in-languages.md)
* [Architecture and reduction](architecture-and-reduction.md)
