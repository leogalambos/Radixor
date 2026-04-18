# Built-in Languages

Radixor provides a set of bundled stemmer dictionaries that can be loaded directly without preparing custom lexical data first.

These resources are intended as practical default dictionaries for common use. They provide a solid starting point for evaluation, integration, and general-purpose stemming workloads, while still fitting naturally into workflows where the bundled baseline is later refined, extended, or replaced by a custom dictionary.

## Overview

Bundled dictionaries are exposed through:

```java
StemmerPatchTrieLoader.Language
```

They are packaged with the library as text resources and compiled into a `FrequencyTrie<String>` when loaded.

## Supported languages

The following bundled language identifiers are currently available:

| Language | Enum constant | Notes |
|---|---|---|
| Danish | `DA_DK` | Bundled general-purpose dictionary |
| German | `DE_DE` | Bundled general-purpose dictionary |
| Spanish | `ES_ES` | Bundled general-purpose dictionary |
| French | `FR_FR` | Bundled general-purpose dictionary |
| Italian | `IT_IT` | Bundled general-purpose dictionary |
| Dutch | `NL_NL` | Bundled general-purpose dictionary |
| Norwegian | `NO_NO` | Bundled general-purpose dictionary |
| Portuguese | `PT_PT` | Bundled general-purpose dictionary |
| Russian | `RU_RU` | Currently supplied in normalized transliterated form |
| Swedish | `SV_SE` | Bundled general-purpose dictionary |
| English | `US_UK` | Standard English dictionary |
| English | `US_UK_PROFI` | Extended English dictionary |

## Basic usage

Load a bundled stemmer like this:

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
                StemmerPatchTrieLoader.Language.US_UK_PROFI,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
    }
}
```

The loader reads the bundled dictionary resource, parses the textual entries, derives patch-command mappings, and compiles the result into a read-only trie.

## Example: stemming with `US_UK_PROFI`

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

## `US_UK` and `US_UK_PROFI`

Radixor currently provides two bundled English variants.

### `US_UK`

`US_UK` is the lighter-weight bundled English resource. It is suitable where a smaller default dictionary is preferred and maximal lexical coverage is not the primary goal.

### `US_UK_PROFI`

`US_UK_PROFI` is the more extensive bundled English resource. It offers broader lexical coverage and is the better default for most applications that want stronger out-of-the-box behavior.

### Recommendation

For most English-language deployments, prefer:

```text
US_UK_PROFI
```

Use `US_UK` when a smaller bundled baseline is more appropriate.

## Intended role of bundled dictionaries

Bundled dictionaries should be understood as **general-purpose default resources**.

They are a good fit when:

- a supported language is already available,
- immediate usability matters,
- a reasonable baseline is sufficient,
- the goal is evaluation, prototyping, or straightforward integration.

They are also well suited to staged refinement workflows in which the bundled base is loaded first, then extended with domain-specific vocabulary, and finally persisted as a custom binary artifact.

## Character representation

The current bundled resources follow a pragmatic normalization convention.

At present, bundled dictionaries are supplied in normalized plain-ASCII form. For some languages, this is simply a lightweight maintenance convention. For others, especially languages commonly written in another script, it reflects a transliterated lexical resource. Russian is the clearest example in the current bundled set.

This convention belongs to the supplied dictionary resources, not to the core stemming model. The parser reads UTF-8 text, the dictionary model works with ordinary Java strings, and the trie and patch-command mechanism operate on general character sequences. In practical terms, the architecture is compatible with native-script dictionaries when suitable lexical resources are available.

## When to prefer custom dictionaries

A custom dictionary is usually the better choice when:

- domain-specific vocabulary materially affects stemming quality,
- lexical coverage must be controlled more precisely,
- a stronger language resource is available than the bundled baseline,
- native-script support is needed beyond the currently bundled resources.

Typical examples include:

- technical terminology,
- biomedical language,
- legal or financial vocabulary,
- organization-specific product and process names,
- language resources maintained in native scripts.

## Production recommendation

For production systems, the most robust workflow is usually:

1. start from a bundled dictionary when it is suitable,
2. extend it with domain-specific forms if needed,
3. compile or rebuild it into a binary `.radixor.gz` artifact,
4. deploy that compiled artifact,
5. load it at runtime using `loadBinary(...)`.

This avoids repeated startup parsing and makes the deployed stemming behavior explicit and versionable.

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
                StemmerPatchTrieLoader.Language.US_UK_PROFI,
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

## Extending language support

The built-in set is intentionally a practical baseline rather than a closed catalog. High-quality dictionaries for additional languages, improved language coverage, and stronger native-script resources are all natural extension paths for the project.

What matters most is not only the number of entries, but the quality, consistency, and operational usefulness of the lexical resource being added.

## Next steps

- [Quick start](quick-start.md)
- [Dictionary format](dictionary-format.md)
- [CLI compilation](cli-compilation.md)
- [Programmatic usage](programmatic-usage.md)

## Summary

Radixor’s built-in language support provides immediate usability, practical default dictionaries, and a strong starting point for custom refinement. The current bundled resources follow a pragmatic normalization convention, while the underlying architecture remains well suited to richer language resources and future extensions.
