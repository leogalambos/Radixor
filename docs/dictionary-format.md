# Dictionary Format

Radixor uses a simple, line-oriented dictionary format to define mappings between **word forms** and their **canonical stems**.

This format is intentionally minimal, language-agnostic, and easy to generate from existing linguistic resources or corpora.

## Overview

Each logical line defines:

- one **canonical stem**
- zero or more **word variants** belonging to that stem

```
stem variant1 variant2 variant3 ...
```

At compile time:

- each variant is converted into a **patch command** transforming the variant into the stem
- the stem itself may optionally be stored as a **no-op mapping**

## Basic example

```
run running runs ran
connect connected connecting connection
analyze analyzing analysed analyses
```

This defines:

| Stem     | Variants                              |
|----------|----------------------------------------|
| run      | running, runs, ran                     |
| connect  | connected, connecting, connection      |
| analyze  | analyzing, analysed, analyses          |

## Syntax rules

### 1. Tokenization

- Tokens are separated by **whitespace**
- Multiple spaces and tabs are treated as a single separator
- Leading and trailing whitespace is ignored

### 2. First token is the stem

- The **first token** on each line is always the canonical stem
- All following tokens are treated as variants of that stem

### 3. Case normalization

- All input is normalized to **lowercase using `Locale.ROOT`**
- Dictionaries should ideally already be lowercase to avoid ambiguity

### 4. Empty lines

- Empty lines are ignored

### 5. Duplicate variants

- Duplicate variants are allowed but have no additional effect
- Frequency is determined by occurrence across the entire dataset

## Remarks (comments)

The parser supports both full-line and trailing remarks.

### Supported remark markers

- `#`
- `//`

### Examples

```
run running runs ran   # English verb forms
connect connected connecting  // basic forms
```

Everything after the first occurrence of a remark marker is ignored.

### Important note

Remark markers are not escaped. If `#` or `//` appear in a token, they will terminate the line.

## Storing the original form

When compiling, you may enable:

```
--store-original
```

This causes the stem itself to be stored using a **no-op patch command**.

Example:

```
run running runs
```

With `--store-original`, this implicitly includes:

```
run -> run
```

This is useful when:

- the input may already be normalized
- you want stable identity mappings
- you want to avoid missing entries for canonical forms

## Frequency and ordering

Radixor tracks **local frequencies** of values.

Frequency is determined by:

- how many times a mapping appears during construction
- merging behavior during reduction

When multiple stems exist for a word:

- results are ordered by **descending frequency**
- ties are resolved deterministically:
  1. shorter textual representation wins
  2. lexicographically smaller value wins
  3. earlier insertion order wins

This guarantees **stable and reproducible results**.

## Ambiguity and multiple stems

A word may legitimately map to more than one stem:

```
axes ax axe
```

This allows Radixor to represent ambiguity explicitly.

At runtime:

- `get(word)` returns the **preferred result**
- `getAll(word)` returns **all candidates**

## Design guidelines

### Keep stems consistent

Use a single canonical form:

- `run` instead of mixing `run` / `running`
- `analyze` vs `analyse` — pick one convention

### Avoid noise

Do not include:

- typos
- extremely rare forms (unless required)
- inconsistent normalization

### Prefer completeness over clever rules

Radixor is data-driven:

- more complete dictionaries → better results
- no hidden rule system compensates for missing entries

### Handle domain-specific vocabulary

You can extend dictionaries with:

- product names
- technical terms
- organization-specific terminology

## Example: minimal dictionary

```
go goes going went
be is are was were being
have has having had
```

## Example: domain-specific extension

```
microservice microservices
container containers containerized
kubernetes kubernetes
```

## Common pitfalls

### Mixing cases

```
Run running Runs   ❌
```

→ normalized to lowercase, but inconsistent input is error-prone

### Multiple stems on one line

```
run running connect   ❌
```

→ `connect` becomes a variant of `run`, which is incorrect

### Hidden comments

```
run running //comment runs   ❌
```

→ everything after `//` is ignored

## When to use this format

This format is suitable for:

- curated linguistic datasets
- exported morphological dictionaries
- domain-specific vocabularies
- generated `(word, stem)` pairs from corpora

## Next steps

- [CLI compilation](cli-compilation.md)
- [Programmatic usage](programmatic-usage.md)
- [Quick start](quick-start.md)

## Summary

Radixor dictionaries are intentionally simple:

- one line per stem
- whitespace-separated tokens
- optional remarks
- no embedded rules

This simplicity enables:

- easy generation
- fast parsing
- deterministic behavior
- efficient compilation into compact patch-command tries
