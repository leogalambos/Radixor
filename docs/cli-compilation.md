# CLI Compilation

Radixor provides a command-line compiler for turning line-oriented dictionary files into compact binary stemmer artifacts.

This is the preferred preparation workflow when stemming should run against an already compiled artifact rather than against raw dictionary input. The CLI reads the dictionary, derives patch commands, builds a mutable trie, applies the selected subtree reduction strategy, and writes the final compiled trie in the project binary format under GZip compression. The result is a deployment-ready `.radixor.gz` file that can be loaded directly by application code.

## What the CLI does

The `Compile` tool performs the following steps:

1. reads the input dictionary in the standard Radixor stemmer format,
2. parses each line into a canonical stem column and its known variant columns,
3. converts variants into patch commands,
4. builds a mutable trie of patch-command values,
5. applies the configured reduction mode,
6. writes the compiled trie as a GZip-compressed binary artifact.

This workflow is intentionally aligned with the same dictionary semantics used elsewhere in the library. Remarks introduced by `#` or `//` are supported through the shared dictionary parser.

## Basic usage

```bash
java org.egothor.stemmer.Compile \
    --input ./data/stemmer.tsv \
    --output ./build/english.radixor.gz \
    --reduction-mode MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS \
    --store-original \
    --overwrite
```

## Supported arguments

The CLI supports the following arguments:

```text
--input <file>
--output <file>
--reduction-mode <mode>
[--store-original]
[--dominant-winner-min-percent <1..100>]
[--dominant-winner-over-second-ratio <1..n>]
[--overwrite]
[--help]
```

### `--input <file>`

Path to the source dictionary file.

The file must use the standard line-oriented tab-separated values dictionary format, meaning that columns are separated by the tab character. Each non-empty logical line starts with the canonical stem column and may contain zero or more variant columns. The parser expects UTF-8 input, processes case according to `CaseProcessingMode` (default: `LOWERCASE_WITH_LOCALE_ROOT`), ignores trailing remarks introduced by `#` or `//`, and currently ignores dictionary items containing embedded whitespace while reporting them through warning-level log entries.

Example:

```text
--input ./data/stemmer.tsv
```

### `--output <file>`

Path to the output binary artifact.

The output file is written as a GZip-compressed binary trie. Parent directories are created automatically when needed.

Example:

```text
--output ./build/english.radixor.gz
```

### `--reduction-mode <mode>`

Selects the subtree reduction strategy used during compilation.

Supported values are:

- `MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS`
- `MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS`
- `MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS`

Example:

```text
--reduction-mode MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
```

This argument is required.

### `--store-original`

When this flag is present, the canonical stem itself is inserted using the no-op patch command.

```text
--store-original
```

This is usually a sensible default for real dictionaries because it ensures that canonical forms are directly representable in the compiled trie rather than relying only on their variants.

### `--dominant-winner-min-percent <1..100>`

Sets the minimum winner percentage used by dominant-result reduction settings.

Example:

```text
--dominant-winner-min-percent 75
```

This option matters primarily when `--reduction-mode` is `MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS`. The default value is `75`.

### `--dominant-winner-over-second-ratio <1..n>`

Sets the minimum winner-over-second ratio used by dominant-result reduction settings.

Example:

```text
--dominant-winner-over-second-ratio 3
```

This option also matters primarily for `MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS`. The default value is `3`.

### `--overwrite`

Allows the CLI to replace an already existing output file.

```text
--overwrite
```

Without this flag, compilation fails when the output path already exists.

### `--help`

Prints usage help and exits successfully.

```text
--help
```

The short form `-h` is also supported.

## Reduction modes in practice

Reduction mode is not only a storage decision. It also influences what semantics are preserved when the mutable trie is compiled into its canonical read-only form.

### Ranked `getAll()` equivalence

`MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS` merges subtrees whose `getAll()` results remain equivalent for every reachable key suffix and whose local result ordering is the same.

This is the best general-purpose choice when result ordering and ambiguity handling matter. It preserves ranked multi-result semantics while still achieving useful structural reduction.

This is the recommended default for most users.

### Unordered `getAll()` equivalence

`MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS` also uses `getAll()`-level equivalence, but it ignores local ordering differences in addition to absolute frequencies.

This can yield stronger reduction, but it also weakens the precision of ordered multi-result semantics.

Choose this mode only when the application does not depend on the ordering of alternative results.

### Dominant `get()` equivalence

`MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS` focuses on preserving preferred-result semantics for `get()`, subject to dominance thresholds.

If a node does not satisfy the configured dominance constraints, compilation falls back to ranked `getAll()` semantics for that node to avoid unsafe over-reduction.

This mode is most suitable when the application primarily consumes the preferred result and does not rely on preserving richer ambiguity information.

## Recommended usage patterns

### Use offline preparation

The CLI is best used as a preparation step during packaging, deployment, or controlled artifact generation. This keeps compilation outside the runtime startup path and allows services to load only the finished binary trie.

### Treat compiled files as versioned assets

A `.radixor.gz` file should be handled as a versioned output artifact. It represents a specific dictionary state, a specific reduction mode, and, where relevant, specific dominant-result thresholds.

### Choose reduction mode deliberately

The ranked `getAll()` mode is the safest default. The unordered and dominant modes should be chosen only when their trade-offs are acceptable for the consuming application.

### Expect memory pressure during preparation, not runtime

Compilation is usually a one-time step and is generally fast. The more important operational consideration is memory usage during preparation, because the dictionary-derived mutable structure exists before reduction compacts it into the final read-only trie. This is especially relevant for very large source dictionaries.

## Example workflow

### 1. Prepare a dictionary

```text
run	running	runs	ran
connect	connected	connecting
```

### 2. Compile it

```bash
java org.egothor.stemmer.Compile \
    --input ./data/stemmer.tsv \
    --output ./build/english.radixor.gz \
    --reduction-mode MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS \
    --store-original
```

### 3. Load it in an application

```java
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.StemmerPatchTrieLoader;

final FrequencyTrie<String> trie =
        StemmerPatchTrieLoader.loadBinary("english.radixor.gz");
```

## Exit codes and error handling

The CLI uses three exit outcomes:

- `0` for success,
- `1` for processing failures such as I/O or compilation errors,
- `2` for invalid command-line usage.

When argument parsing fails, the CLI prints the error message, prints the usage summary, and exits with usage error status.

When compilation fails during processing, the CLI prints a `Compilation failed: ...` message to standard error and exits with processing error status.

Examples of failure conditions include:

- missing required arguments,
- unknown arguments,
- invalid integer values for dominant thresholds,
- missing input files,
- unreadable input,
- existing output file without `--overwrite`,
- general I/O failures during reading or writing.

## Relation to programmatic usage

The CLI and the programmatic API implement the same conceptual preparation step. The CLI is the operationally convenient choice when you want a ready-made binary artifact. The programmatic API is the better fit when compilation must be integrated directly into custom Java workflows.

## Next steps

- [Dictionary format](dictionary-format.md)
- [Quick start](quick-start.md)
- [Programmatic usage](programmatic-usage.md)
- [Architecture and reduction](architecture-and-reduction.md)
