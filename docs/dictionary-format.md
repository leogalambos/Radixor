# Dictionary Format

Radixor uses a simple line-oriented dictionary format designed for practical stemming workflows. The textual source format is tab-separated values, meaning that columns are separated by the tab character.

Each logical line describes one canonical stem and zero or more known word variants that should reduce to that stem. The format is intentionally lightweight, easy to maintain in source control, and directly consumable both by the programmatic loader and by the CLI compiler.

## Core structure

Each non-empty logical line has the following shape:

```text
<stem>	<variant1>	<variant2>	<variant3> ...
```

The first column is interpreted as the **canonical stem**. Every following token on the same line is interpreted as a **known variant** belonging to that stem.

Example:

```text
run	running	runs	ran
connect	connected	connecting	connection
```

In this example:

- `run` is the canonical stem for `running`, `runs`, and `ran`,
- `connect` is the canonical stem for `connected`, `connecting`, and `connection`.

## How the loader interprets a line

When a dictionary is loaded through `StemmerPatchTrieLoader`, the loader processes each parsed line as follows:

1. the first column becomes the canonical stem,
2. every following token is treated as a variant,
3. each variant is converted into a patch command that transforms the variant into the stem,
4. if `storeOriginal` is enabled, the stem itself is also inserted using the canonical no-op patch command.

This means the textual dictionary is not used directly at runtime. Instead, it is transformed into patch-command data and compiled into a reduced read-only trie.

## Minimal valid lines

A line may consist of the stem only:

```text
run
```

This is syntactically valid. It defines a stem entry with no explicit variants on that line.

Whether such a line is operationally useful depends on how the dictionary is loaded:

- if `storeOriginal` is enabled, the stem itself is inserted as a no-op mapping,
- if `storeOriginal` is disabled, the line contributes no explicit variant mappings.

## Column and whitespace rules

Columns are separated by the tab character. Leading and trailing whitespace around each column is ignored.

This is the canonical form:

```text
run	running	runs	ran
```

This is also accepted because the surrounding padding is removed before the item is processed:

```text
  run	 running 	 runs	 ran  
```

Embedded whitespace inside one dictionary item is currently not supported. A stem or variant such as `new york` therefore cannot yet be represented as one usable dictionary item in the textual source format. Such items are ignored during parsing and reported through a warning-level log entry together with the physical line number, the stem, and the ignored items from that line.

## Empty lines

Empty lines are ignored.

Example:

```text
run	running	runs	ran

connect	connected	connecting
```

The blank line between entries has no effect.

## Remarks and comments

The parser supports both full-line and trailing remarks.

Two remark markers are recognized:

- `#`
- `//`

The earliest occurrence of either marker terminates the logical content of the line, and the remainder of that line is ignored.

Examples:

```text
run	running	runs	ran # English verb forms
connect	connected	connecting // Common derived forms
```

This is also valid:

```text
# This line is ignored completely
// This line is also ignored completely
```

## Case normalization

Input-line case normalization is controlled by `CaseProcessingMode`; by default the parser uses `LOWERCASE_WITH_LOCALE_ROOT` before tab-separated columns are processed into dictionary entries.

That means dictionary authors should treat the format as **case-insensitive at load time**. If a file contains uppercase or mixed-case tokens, they will be normalized during parsing.

Example:

```text
Run	Running	Runs	Ran
```

is processed the same way as:

```text
run	running	runs	ran
```

## Character set, compression, and normalization

Dictionary files are read as UTF-8 text. Files loaded through `StemmerPatchTrieLoader.load(Path, ...)` may be either plain UTF-8 text or GZip-compressed UTF-8 text; the loader detects GZip input from the stream header instead of relying on the file extension. Bundled dictionaries are stored as GZip resources and are decoded as UTF-8 after decompression.

The parser and trie are not restricted to ASCII. Dictionary items are ordinary Java `String` values, and trie traversal works over Java `char` sequences. This supports Latin-script data with diacritics, Cyrillic data, Hebrew, Persian, Yiddish, and other scripts represented in UTF-8, subject to the normal Java `String` model and the project’s traversal configuration.

Case normalization is controlled by `CaseProcessingMode`. The default `LOWERCASE_WITH_LOCALE_ROOT` mode lowercases the line before columns are split into dictionary items. `AS_IS` preserves the original casing.

Diacritic normalization is controlled at trie-build and lookup time by `DiacriticProcessingMode`:

- `AS_IS` preserves dictionary and lookup keys exactly after case handling,
- `REMOVE` strips supported diacritics and common Latin ligatures on both insertion and lookup paths,
- `AS_IS_AND_STRIPPED_FALLBACK` is declared in the public model but is not implemented yet and raises `UnsupportedOperationException`.

For reliable production behavior, choose one normalization policy deliberately and apply it consistently. Normalized ASCII dictionaries remain a practical convention for some legacy stemming data, but they are not a format requirement.

## Distinct stem and variant semantics

The format expresses a one-line grouping of forms under a canonical stem. It does not encode linguistic metadata, part-of-speech information, weights, or explicit ambiguity markers.

For example:

```text
axis	axes
axe	axes
```

These are simply two independent lines. If both contribute mappings for the same surface form, the compiled trie may later expose one or more candidate patch commands depending on the accumulated local counts and the selected reduction mode.

In other words, the dictionary format itself is deliberately simple. Richer behavior such as preferred-result ranking or multiple candidate results emerges during trie construction and reduction rather than through extra syntax in the dictionary file.

## Duplicate forms and repeated entries

The format does not reserve any special syntax for duplicates. If the same mapping is inserted multiple times through repeated dictionary content, the builder accumulates local counts for the stored value at the addressed key.

This matters because compiled tries preserve local value frequencies and use them to determine preferred ordering for `get(...)`, `getAll(...)`, and `getEntries(...)`.

As a result, repeating the same mapping is not just redundant text. It can influence the ranking behavior of the compiled trie.

## Practical examples

### Simple English example

```text
run	running	runs	ran
connect	connected	connecting	connection
build	building	builds	built
```

### Dictionary with remarks

```text
run	running	runs	ran # canonical verb family
connect	connected	connecting // derived forms
build	building	builds	built
```

### Stem-only entries

```text
run
connect	connected	connecting
build
```

### Mixed case input

```text
Run	Running	Runs	Ran
CONNECT	Connected	Connecting
```

This is accepted. Under the default `LOWERCASE_WITH_LOCALE_ROOT` mode it is normalized to lower case during parsing; under `AS_IS` it is preserved.

## Format limitations

The current dictionary format intentionally stays minimal:

- no quoted tokens,
- no escaping rules,
- no multi-word entries,
- no inline weighting syntax,
- no explicit ambiguity syntax,
- no sectioning or nested structure.

Each dictionary item is simply one tab-separated word form after remark stripping and the configured case and diacritic normalization.

## Authoring guidance

For reliable results, keep dictionaries:

- consistent in normalization,
- free of accidental duplicates unless repeated weighting is intentional,
- focused on meaningful stem-to-variant groupings,
- encoded in UTF-8,
- easy to audit in plain text form.

For most deployments, it is sensible to choose either preserved UTF-8 forms or a normalized ASCII/diacritic-stripped convention and keep that choice consistent across dictionary authoring, compilation, and runtime lookup.

## Relationship to other documentation

This page describes only the textual source format.

To understand how those dictionary lines are transformed into compiled runtime artifacts, continue with:

- [CLI compilation](cli-compilation.md)
- [Programmatic usage](programmatic-usage.md)
- [Architecture and reduction](architecture-and-reduction.md)
