# Dictionary Format

Radixor uses a simple line-oriented dictionary format designed for practical stemming workflows.

Each logical line describes one canonical stem and zero or more known word variants that should reduce to that stem. The format is intentionally lightweight, easy to maintain in source control, and directly consumable both by the programmatic loader and by the CLI compiler.

## Core structure

Each non-empty logical line has the following shape:

```text
<stem> <variant1> <variant2> <variant3> ...
```

The first token is interpreted as the **canonical stem**. Every following token on the same line is interpreted as a **known variant** belonging to that stem.

Example:

```text
run running runs ran
connect connected connecting connection
```

In this example:

- `run` is the canonical stem for `running`, `runs`, and `ran`,
- `connect` is the canonical stem for `connected`, `connecting`, and `connection`.

## How the loader interprets a line

When a dictionary is loaded through `StemmerPatchTrieLoader`, the loader processes each parsed line as follows:

1. the first token becomes the canonical stem,
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

## Whitespace rules

Tokens are separated by whitespace. Leading and trailing whitespace is ignored.

These lines are equivalent:

```text
run running runs ran
```

```text
   run   running   runs   ran
```

Tabs and repeated spaces are both accepted because tokenization is whitespace-based.

## Empty lines

Empty lines are ignored.

Example:

```text
run running runs ran

connect connected connecting
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
run running runs ran # English verb forms
connect connected connecting // Common derived forms
```

This is also valid:

```text
# This line is ignored completely
// This line is also ignored completely
```

## Case normalization

Input lines are normalized to lower case using `Locale.ROOT` before tokenization is processed into dictionary entries.

That means dictionary authors should treat the format as **case-insensitive at load time**. If a file contains uppercase or mixed-case tokens, they will be normalized during parsing.

Example:

```text
Run Running Runs Ran
```

is processed the same way as:

```text
run running runs ran
```

## Character set and practical convention

Dictionary files are read as UTF-8 text.

From the perspective of the parser and the stemming algorithm, the format is not restricted to plain ASCII tokens. The parser accepts ordinary Java `String` data, and the trie itself works with general character sequences rather than with an ASCII-only internal model. In principle, this means the system could process diacritic and non-diacritic forms alike, and it could also store forms with inconsistently used diacritics.

In practice, however, the format is currently best understood as **primarily intended for classical basic ASCII lexical input**, especially in the traditional stemming style where language data is normalized into plain characters in the ASCII range up to character code 127. This convention is particularly relevant for languages whose original orthography includes diacritics but whose stemming dictionaries are commonly maintained in normalized non-diacritic form.

Future versions may expand the documentation and operational guidance for dictionaries that intentionally preserve diacritics. At present, that workflow is not the primary documented use case, not because the algorithm fundamentally forbids it, but because a concrete project requirement for such support has not yet emerged.

## Distinct stem and variant semantics

The format expresses a one-line grouping of forms under a canonical stem. It does not encode linguistic metadata, part-of-speech information, weights, or explicit ambiguity markers.

For example:

```text
axis axes
axe axes
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
run running runs ran
connect connected connecting connection
build building builds built
```

### Dictionary with remarks

```text
run running runs ran # canonical verb family
connect connected connecting // derived forms
build building builds built
```

### Stem-only entries

```text
run
connect connected connecting
build
```

### Mixed case input

```text
Run Running Runs Ran
CONNECT Connected Connecting
```

This is accepted, but it is normalized to lower case during parsing.

## Format limitations

The current dictionary format intentionally stays minimal:

- no quoted tokens,
- no escaping rules,
- no multi-word entries,
- no inline weighting syntax,
- no explicit ambiguity syntax,
- no sectioning or nested structure.

Each token is simply a whitespace-delimited word form after remark stripping and lowercasing.

## Authoring guidance

For reliable results, keep dictionaries:

- consistent in normalization,
- free of accidental duplicates unless repeated weighting is intentional,
- focused on meaningful stem-to-variant groupings,
- encoded in UTF-8,
- easy to audit in plain text form.

For most current deployments, it is sensible to keep dictionary content in normalized basic ASCII form unless there is a clear requirement to preserve diacritics end-to-end.

## Relationship to other documentation

This page describes only the textual source format.

To understand how those dictionary lines are transformed into compiled runtime artifacts, continue with:

- [CLI compilation](cli-compilation.md)
- [Programmatic usage](programmatic-usage.md)
- [Architecture and reduction](architecture-and-reduction.md)
