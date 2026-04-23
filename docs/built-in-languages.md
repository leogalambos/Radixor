# Built-in Languages

Radixor ships with a curated set of bundled stemmer dictionaries that can be loaded directly from the library distribution. These resources are intended to provide an immediately usable baseline for evaluation, prototyping, integration, and general-purpose stemming workloads, while still fitting naturally into workflows where the bundled baseline is later refined, extended, or replaced with custom lexical data.

## Overview

Bundled dictionaries are exposed through:

```java
org.egothor.stemmer.StemmerPatchTrieLoader.Language
```

Each bundled dictionary is packaged with the library as a compressed UTF-8 text resource. When loaded, the resource is parsed by `StemmerDictionaryParser`, transformed into patch-command mappings, and compiled into a read-only `FrequencyTrie<String>` by `StemmerPatchTrieLoader`.

The bundled language definition also carries a language-level right-to-left flag. That flag is used by the loader to derive the `WordTraversalDirection` used for both trie-key construction and patch-command generation. In practice, left-to-right bundled languages use historical backward Egothor traversal, while right-to-left bundled languages use forward traversal over the stored form.

## Supported bundled languages

The following bundled language identifiers are currently available:

| Language | Enum constant | Writing direction | Notes |
|---|---|---:|---|
| Czech | `CS_CZ` | LTR | Bundled general-purpose dictionary |
| Danish | `DA_DK` | LTR | Bundled general-purpose dictionary |
| German | `DE_DE` | LTR | Bundled general-purpose dictionary |
| Spanish | `ES_ES` | LTR | Bundled general-purpose dictionary |
| Persian | `FA_IR` | RTL | Bundled dictionary uses forward traversal over the stored form |
| Finnish | `FI_FI` | LTR | Bundled general-purpose dictionary |
| French | `FR_FR` | LTR | Bundled general-purpose dictionary |
| Hebrew | `HE_IL` | RTL | Bundled dictionary uses forward traversal over the stored form |
| Hungarian | `HU_HU` | LTR | Bundled general-purpose dictionary |
| Italian | `IT_IT` | LTR | Bundled general-purpose dictionary |
| Norwegian Bokmål | `NB_NO` | LTR | Bundled general-purpose dictionary |
| Dutch | `NL_NL` | LTR | Bundled general-purpose dictionary |
| Norwegian Nynorsk | `NN_NO` | LTR | Bundled general-purpose dictionary |
| Polish | `PL_PL` | LTR | Bundled general-purpose dictionary |
| Portuguese | `PT_PT` | LTR | Bundled general-purpose dictionary |
| Russian | `RU_RU` | LTR | Bundled general-purpose dictionary |
| Swedish | `SV_SE` | LTR | Bundled general-purpose dictionary |
| Ukrainian | `UK_UA` | LTR | Bundled general-purpose dictionary |
| English | `US_UK` | LTR | Bundled general-purpose dictionary |
| Yiddish | `YI` | RTL | Bundled dictionary uses forward traversal over the stored form |

## Basic usage

Load a bundled dictionary like this:

```java
import java.io.IOException;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class BuiltInExample {

    private BuiltInExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(
                StemmerPatchTrieLoader.Language.US_UK,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        System.out.println(trie.traversalDirection());
    }
}
```

This call loads the bundled dictionary resource for the selected language, parses its lexical entries, derives patch-command mappings, and compiles the result into a read-only trie.

## Example: stemming with a bundled dictionary

```java
import java.io.IOException;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class EnglishExample {

    private EnglishExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(
                StemmerPatchTrieLoader.Language.US_UK,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final String word = "running";
        final String patch = trie.get(word);
        final String stem = PatchCommandEncoder.apply(word, patch, trie.traversalDirection());

        System.out.println(word + " -> " + stem);
    }
}
```

Passing `trie.traversalDirection()` to `PatchCommandEncoder.apply(...)` is the correct general contract. It ensures that the patch is applied using the same logical traversal model that was used when the trie and its patch commands were produced.

## Traversal behavior and right-to-left languages

Bundled dictionaries are not all processed identically.

For traditional left-to-right suffix-oriented resources, Radixor preserves historical Egothor behavior and traverses logical word characters backward. That means trie paths are constructed from the logical end of the stored word toward its beginning, and patch commands are interpreted with the same backward traversal model.

For bundled right-to-left languages such as Persian, Hebrew, and Yiddish, Radixor uses forward traversal over the stored form. In those cases:

- trie keys are traversed from the logical beginning of the stored form,
- patch commands are generated in that same forward direction,
- patch application must use `WordTraversalDirection.FORWARD`, which is naturally obtained from `trie.traversalDirection()`.

This design keeps the traversal policy explicit and consistent across dictionary loading, trie lookup, binary persistence, builder reconstruction, and patch application.

## Reduction behavior

Bundled dictionaries can be compiled using any supported `ReductionMode`. The reduction configuration controls how semantically equivalent subtrees are merged during trie compilation, while preserving the contract of the selected mode.

Typical entry points are:

- `StemmerPatchTrieLoader.load(language, storeOriginal, reductionMode)`
- `StemmerPatchTrieLoader.load(language, storeOriginal, reductionSettings)`

For most users, `ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS` is the most conservative general-purpose choice because it preserves ranked `getAll(...)` behavior.

## Intended role of bundled dictionaries

Bundled dictionaries should be understood as practical default resources.

They are a good fit when:

- a supported language is already available,
- immediate usability matters,
- a reasonable baseline is sufficient,
- the goal is evaluation, prototyping, or straightforward integration.

They are also well suited to staged refinement workflows in which a bundled base is loaded first, then extended with domain-specific vocabulary, and finally persisted as a custom binary artifact.

## Character representation

Bundled dictionaries are ordinary UTF-8 lexical resources. The parser reads them as text, the trie stores standard Java strings, and the patch-command model operates on general character sequences.

This is important for two reasons:

1. the built-in resources are not limited to ASCII-only processing,
2. the traversal model is orthogonal to character encoding and script choice.

In other words, right-to-left handling in the loader is about logical traversal strategy, not about introducing a separate character model.

## When to prefer custom dictionaries

A custom dictionary is usually the better choice when:

- domain-specific vocabulary materially affects stemming quality,
- lexical coverage must be controlled more precisely,
- a stronger lexical resource is available than the bundled baseline,
- operational requirements demand an explicitly curated, versioned artifact.

Typical examples include:

- technical terminology,
- biomedical language,
- legal or financial vocabulary,
- organization-specific product and process names,
- dictionaries maintained with project-specific validation rules.

## Production recommendation

For production systems, the most robust workflow is usually:

1. start from a bundled dictionary when it is suitable,
2. extend it with domain-specific forms if needed,
3. rebuild it into a binary artifact,
4. deploy that compiled binary artifact,
5. load it at runtime through `loadBinary(...)`.

This avoids repeated startup parsing and makes the deployed stemming behavior explicit, reproducible, and versionable.

## Example refinement workflow

```java
import java.io.IOException;
import java.nio.file.Path;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.FrequencyTrieBuilders;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.egothor.stemmer.StemmerPatchTrieBinaryIO;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class BundledRefinementExample {

    private BundledRefinementExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final FrequencyTrie<String> base = StemmerPatchTrieLoader.load(
                StemmerPatchTrieLoader.Language.US_UK,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final FrequencyTrie.Builder<String> builder = FrequencyTrieBuilders.copyOf(
                base,
                String[]::new,
                ReductionSettings.withDefaults(
                        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));

        builder.put("microservices", "Na");

        final FrequencyTrie<String> compiled = builder.build();

        StemmerPatchTrieBinaryIO.write(compiled, Path.of("english-custom.radixor.gz"));
    }
}
```

The reconstructed builder preserves the traversal direction of the source trie, so refinements remain semantically aligned with the original bundled dictionary.

## Extending language support

The built-in set is intentionally a practical baseline rather than a closed catalog. Additional languages, stronger lexical coverage, and improved dictionaries for currently supported languages are all natural extension paths.

What matters most is not only the number of entries, but the quality, consistency, maintainability, and operational usefulness of the lexical resource being added.

## Related API surface

The following types are typically involved when working with bundled dictionaries:

- `StemmerPatchTrieLoader`
- `StemmerPatchTrieLoader.Language`
- `FrequencyTrie`
- `PatchCommandEncoder`
- `WordTraversalDirection`
- `ReductionMode`
- `ReductionSettings`
- `StemmerPatchTrieBinaryIO`
- `FrequencyTrieBuilders`

## Next steps

- [Quick start](quick-start.md)
- [Dictionary format](dictionary-format.md)
- [CLI compilation](cli-compilation.md)
- [Programmatic usage](programmatic-usage.md)

## Summary

Radixor’s built-in language support provides immediate usability, a professionally defined baseline API, and a practical starting point for custom refinement. The bundled set now includes both left-to-right and right-to-left languages, and the library models that distinction explicitly through `WordTraversalDirection` so that trie construction, lookup, and patch application remain consistent.
