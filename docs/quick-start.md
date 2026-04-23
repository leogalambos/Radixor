# Quick Start

This guide introduces the fastest practical path to using **Radixor**.

Radixor separates preparation from runtime usage. Source dictionaries are used to derive patch commands and reduce them into a compact read-only trie. Runtime stemming then operates on that compiled structure rather than on the original dictionary text. A richer dictionary usually improves the quality and coverage of inferred transformations, including transformations that are applicable to words not explicitly present in the source material. The reduction step also removes a large amount of redundant lexical information, which is why very large dictionaries can still produce compact runtime artifacts. These artifacts can be persisted and loaded directly when needed.

A practical workflow usually consists of two independent phases:

1. obtain a compiled stemmer,
2. use the compiled stemmer.

## 1. Obtain a compiled stemmer

A compiled stemmer can be obtained in three common ways.

### Use a bundled language dictionary

Radixor ships with bundled dictionaries for a set of supported languages. These resources are line-oriented dictionaries stored with the library and compiled into a `FrequencyTrie<String>` when loaded. The loader can also store the canonical stem itself as a no-op patch command. Compiled trie artifacts now persist self-describing metadata, including the traversal direction and compilation reduction settings used to build the artifact.

```java
import java.io.IOException;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class BundledStemmerExample {

    private BundledStemmerExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(
                StemmerPatchTrieLoader.Language.US_UK_PROFI,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        System.out.println("Canonical node count: " + trie.size());
    }
}
```

### Load a previously compiled binary stemmer

Compiled stemmers can be stored as GZip-compressed binary artifacts and loaded directly. This is usually the most convenient production path because no dictionary parsing or recompilation is needed during application startup.

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class LoadBinaryStemmerExample {

    private LoadBinaryStemmerExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.loadBinary(
                Path.of("stemmers", "english.radixor.gz"));

        System.out.println("Canonical node count: " + trie.size());
    }
}
```

### Build or extend a stemmer from dictionary data

Radixor can also build a compiled trie from a custom dictionary. Dictionary lines consist of a canonical stem followed by zero or more variants. The parser applies `CaseProcessingMode` (default: `LOWERCASE_WITH_LOCALE_ROOT`), ignores leading and trailing whitespace, and supports line remarks introduced by `#` or `//`.

This path is also relevant when you extend an existing compiled stemmer with additional domain-specific entries and rebuild a new compact artifact.

A dedicated CLI compilation workflow deserves its own focused page and should remain separate from Quick Start, but conceptually it is simply another way to prepare the compiled artifact before runtime use.

## 2. Use the compiled stemmer

A compiled `FrequencyTrie<String>` stores patch commands, not final stems. Querying therefore has two steps:

1. retrieve one or more patch commands from the trie,
2. apply each patch command to the original input word.

The trie returns values associated with the exact addressed node. `get(...)` returns the locally preferred value, while `getAll(...)` returns all locally stored values ordered by descending frequency with deterministic tie-breaking.

### Get the preferred result

Use `get(...)` when the application needs a single preferred transformation.

```java
import java.io.IOException;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class SingleStemExample {

    private SingleStemExample() {
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

        System.out.println(word + " -> " + stem + " (" + patch + ")");
    }
}
```

### Get all candidate results

Use `getAll(...)` when the application should preserve ambiguity instead of collapsing everything into one result. The method is available on every compiled trie. What changes across reduction modes is the semantic strength with which multi-result behavior is preserved during reduction, not whether the method exists.

```java
final String word = "axes";
final String[] patches = trie.getAll(word);

for (final String patch : patches) {
    final String stem = PatchCommandEncoder.apply(word, patch);
    System.out.println(word + " -> " + stem + " (" + patch + ")");
}
```

### Inspect ranked values and counts

For diagnostics or advanced ranking logic, use `getEntries(...)` to obtain value-count pairs in the same deterministic order as `getAll(...)`.

```java
import java.util.List;

import org.egothor.stemmer.ValueCount;

final List<ValueCount<String>> entries = trie.getEntries("axes");

for (final ValueCount<String> entry : entries) {
    System.out.println(entry.value() + " -> " + entry.count());
}
```

## Extend an existing compiled stemmer

A compiled trie is read-only, but it is not permanently closed. Radixor can reconstruct a mutable builder from a compiled trie, preserve the currently stored local counts, accept additional insertions, and then compile a new read-only trie. Reconstruction operates on the compiled form, so if the source trie was already reduced by subtree merging, the reopened builder reflects that compiled state rather than the original unreduced insertion history.

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

## Operational note on memory and preparation

Dictionary compilation is usually a one-time preparation step and is generally fast. The more relevant operational constraint is memory consumption during preparation: before reduction, the mutable build-time structure keeps the full dictionary-derived content in RAM. Reduction then compacts it substantially, but very large source dictionaries can still require significant memory during the initial build phase. The best operational model is therefore to compile once, persist the resulting binary artifact, and load that artifact directly in runtime environments.

## Where to continue

- [Programmatic Usage](programmatic-usage.md)
- [Dictionary format](dictionary-format.md)
- [CLI compilation](cli-compilation.md)
- [Built-in languages](built-in-languages.md)
- [Architecture and reduction](architecture-and-reduction.md)


## Persisted trie metadata

Every compiled trie artifact stores a `TrieMetadata` descriptor together with the immutable trie payload. That metadata currently records the binary format version, the `WordTraversalDirection`, the `ReductionSettings` used during compilation, the declared `DiacriticProcessingMode`, and the selected `CaseProcessingMode`. Traversal, case processing, and diacritic processing are applied during runtime lookup (`get`, `getAll`), and case/diacritic processing are also applied during dictionary insertion when a trie is built.

`DiacriticProcessingMode.AS_IS` keeps dictionary keys and lookup keys unchanged. `DiacriticProcessingMode.REMOVE` strips diacritics from dictionary keys and lookup keys (for Czech diacritics and broad European Latin-script variants). `DiacriticProcessingMode.AS_IS_AND_STRIPPED_FALLBACK` is currently not supported and raises an `UnsupportedOperationException`.
