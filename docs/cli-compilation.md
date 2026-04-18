# CLI Compilation

Radixor provides a command-line tool for compiling dictionary files into compact, production-ready binary stemmer tables.

This is the recommended workflow for deployment environments, as it separates:

- dictionary preparation (offline)
- stemming execution (runtime)



## Overview

The `Compile` tool:

1. reads a line-oriented dictionary file
2. converts word–stem pairs into patch commands
3. builds a trie structure
4. applies subtree reduction
5. writes a compressed binary artifact

The output is a `.radixor.gz` file suitable for fast runtime loading.



## Basic usage

```bash
java org.egothor.stemmer.Compile \
  --input ./data/stemmer.txt \
  --output ./build/english.radixor.gz \
  --reduction-mode MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS \
  --store-original \
  --overwrite
```



## Required arguments

### `--input`

Path to the source dictionary file.

* must be in the [dictionary format](dictionary-format.md)
* must be readable
* UTF-8 encoding is expected

```
--input ./data/stemmer.txt
```

### `--output`

Path to the output binary file.

* parent directories are created automatically
* output is written as **GZip-compressed binary**

```
--output ./build/english.radixor.gz
```



## Optional arguments

### `--reduction-mode`

Controls how aggressively the trie is reduced during compilation.

Available values:

* `MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS`
* `MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS`
* `MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS`

Example:

```
--reduction-mode MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
```

#### Recommendation

Use:

```
MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
```

This provides:

* safe behavior
* deterministic ordering
* good compression



### `--store-original`

Stores the stem itself as a no-op mapping.

```
--store-original
```

Effect:

* ensures that canonical forms are always resolvable
* improves robustness in real-world inputs

Recommended for most use cases.



### `--overwrite`

Allows overwriting an existing output file.

```
--overwrite
```

Without this flag:

* compilation fails if the output file already exists



## Reduction strategy explained

Reduction merges semantically equivalent subtrees to reduce memory and file size.

Trade-offs:

| Mode      | Compression | Behavioral fidelity |
| --------- | ----------- | ------------------- |
| Ranked    | Medium      | High                |
| Unordered | High        | Medium              |
| Dominant  | Highest     | Lower (heuristic)   |

### Ranked (recommended)

* preserves full `getAll()` ordering
* safest and most predictable

### Unordered

* ignores ordering differences
* higher compression, but less precise semantics

### Dominant

* focuses on the most frequent result
* useful when only `get()` is relevant
* may lose secondary candidates



## Output format

The compiled file:

* is a binary representation of the trie
* uses **GZip compression**
* is optimized for:

  * fast loading
  * minimal memory footprint

Typical properties:

* small file size
* fast deserialization
* no runtime preprocessing required



## Example workflow

### 1. Prepare dictionary

```
run running runs ran
connect connected connecting
```

### 2. Compile

```bash
java org.egothor.stemmer.Compile \
  --input ./data/stemmer.txt \
  --output ./build/english.radixor.gz \
  --reduction-mode MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS \
  --store-original
```

### 3. Use in application

```java
FrequencyTrie<String> trie =
    StemmerPatchTrieLoader.loadBinary("english.radixor.gz");
```



## Error handling

The CLI reports:

* missing input file
* invalid arguments
* I/O failures
* parsing errors

Typical exit codes:

* `0` – success
* non-zero – failure

Error details are printed to standard error.



## Performance considerations

### Compilation

* typically CPU-bound
* depends on dictionary size and reduction mode

### Output size

* depends on:

  * dictionary completeness
  * reduction strategy
* can vary significantly between modes

### Runtime impact

* compiled tries are optimized for:

  * fast lookup
  * low allocation
  * predictable latency



## Best practices

### Use offline compilation

* compile dictionaries during build or deployment
* do not compile on application startup

### Version your artifacts

* treat `.radixor.gz` files as versioned assets
* store them alongside application releases

### Choose reduction mode deliberately

* use **ranked** for correctness
* use **dominant** only if you fully understand the trade-offs

### Keep dictionaries clean

* better input → better compiled output
* avoid noise and inconsistencies



## Integration tips

* store compiled files under `resources/` or a dedicated directory
* load them once and reuse the trie instance
* avoid repeated loading in frequently executed code paths (for example, per-request processing)



## Next steps

* [Dictionary format](dictionary-format.md)
* [Programmatic usage](programmatic-usage.md)
* [Quick start](quick-start.md)



## Summary

The `Compile` CLI is the bridge between:

* human-readable dictionary data
* optimized runtime stemmer tables

It enables a clean separation between:

* data preparation
* runtime execution

and is the preferred way to prepare Radixor for production use.
