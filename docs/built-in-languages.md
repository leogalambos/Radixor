# Built-in Languages

> ← Back to [README.md](../README.md)

Radixor provides a set of **bundled stemmer dictionaries** that can be loaded directly without preparing custom data.

These built-in resources are useful for:

- quick integration
- testing and evaluation
- reference behavior
- prototyping search pipelines



## Overview

Bundled dictionaries are exposed through:

```java
StemmerPatchTrieLoader.Language
```

They are packaged with the library and loaded from the classpath.



## Supported languages

The following language identifiers are currently available:

| Language | Enum constant     | Description                  |
|----------|------------------|------------------------------|
| Danish   | `DA_DK`          | Danish                       |
| German   | `DE_DE`          | German                       |
| Spanish  | `ES_ES`          | Spanish                      |
| French   | `FR_FR`          | French                       |
| Italian  | `IT_IT`          | Italian                      |
| Dutch    | `NL_NL`          | Dutch                        |
| Norwegian| `NO_NO`          | Norwegian                    |
| Portuguese| `PT_PT`         | Portuguese                   |
| Russian  | `RU_RU`          | Russian                      |
| Swedish  | `SV_SE`          | Swedish                      |
| English  | `US_UK`          | Standard English             |
| English  | `US_UK_PROFI`    | Extended English dictionary  |



## Basic usage

Load a bundled stemmer:

```java
import java.io.IOException;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class BuiltInExample {

    public static void main(String[] args) throws IOException {
        FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(
                StemmerPatchTrieLoader.Language.US_UK_PROFI,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
        );
    }
}
```



## Example: stemming with `US_UK_PROFI`

```java
import java.io.IOException;

import org.egothor.stemmer.*;

public final class EnglishExample {

    public static void main(String[] args) throws IOException {
        FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(
                StemmerPatchTrieLoader.Language.US_UK_PROFI,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
        );

        String word = "running";
        String patch = trie.get(word);
        String stem = PatchCommandEncoder.apply(word, patch);

        System.out.println(word + " -> " + stem);
    }
}
```



## `US_UK` vs `US_UK_PROFI`

### `US_UK`

* smaller dictionary
* faster load time
* suitable for lightweight use cases

### `US_UK_PROFI`

* larger and more complete dataset
* better coverage of word forms
* improved stemming quality
* slightly larger memory footprint

### Recommendation

Use:

````
US_UK_PROFI
```

for most applications unless memory constraints are strict.



## How bundled dictionaries are loaded

Internally:

- dictionaries are stored as text resources
- parsed using `StemmerDictionaryParser`
- compiled into a trie at load time

This means:

- first load includes parsing + compilation cost
- subsequent usage is fast



## When to use bundled languages

Bundled dictionaries are suitable when:

- you need quick results without preparing custom data
- you are prototyping or experimenting
- your language requirements match the provided datasets



## When to use custom dictionaries

You should prefer custom dictionaries when:

- domain-specific vocabulary is important
- accuracy requirements are high
- you need full control over stemming behavior

Typical examples:

- technical terminology
- product catalogs
- biomedical text
- legal or financial language



## Production recommendation

For production systems:

1. Load a bundled dictionary
2. Extend it with domain-specific terms (optional)
3. Compile it into a binary `.radixor.gz` file
4. Deploy the compiled artifact
5. Load it using `loadBinary(...)`

This avoids:

- runtime parsing overhead
- repeated compilation
- startup latency



## Example workflow

```java
// 1. Load bundled dictionary
FrequencyTrie<String> base = StemmerPatchTrieLoader.load(
        StemmerPatchTrieLoader.Language.US_UK_PROFI,
        true,
        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
);

// 2. Modify (optional)
FrequencyTrie.Builder<String> builder =
        FrequencyTrieBuilders.copyOf(
                base,
                String[]::new,
                ReductionSettings.withDefaults(
                        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
                )
        );

builder.put("microservices", PatchCommandEncoder.NOOP_PATCH);

// 3. Compile
FrequencyTrie<String> compiled = builder.build();

// 4. Save
StemmerPatchTrieBinaryIO.write(compiled, Path.of("english-custom.radixor.gz"));
```



## Limitations

* bundled dictionaries are **general-purpose**
* they may not reflect:

  * domain-specific usage
  * rare or specialized vocabulary
  * organization-specific terminology



## Next steps

* [Quick start](quick-start.md)
* [Dictionary format](dictionary-format.md)
* [CLI compilation](cli-compilation.md)
* [Programmatic usage](programmatic-usage.md)



## Summary

Radixor’s built-in language support provides:

* immediate usability
* reference datasets
* a starting point for customization

For production systems, they are best used as:

* a baseline
* a seed for further extension
* a source for compiled deployment artifacts

