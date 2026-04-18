# Architecture and Reduction

This section explains how **Radixor** turns textual dictionary input into a compact compiled stemmer and how reduction affects the semantics preserved in the final runtime artifact.

Radixor is easiest to understand when separated into two related concerns:

- **architecture**: what structures exist, how data moves through them, and what runtime lookup actually does,
- **reduction semantics**: what it means for two subtrees to be considered equivalent and how that choice affects `get()` and `getAll()` behavior.

## The short version

Radixor does not keep a large flat table of final stems. Instead, it converts dictionary entries into **patch commands**, stores them in a trie, reduces equivalent subtrees, and freezes the result into an immutable compiled structure.

The build-time flow is:

```text
Dictionary -> Mutable trie -> Reduced trie -> Compiled trie
```

At runtime, the compiled trie does not directly return the final stem string. It returns one or more stored patch commands for the addressed key, and those commands are then applied to the original input word.

## Why this matters

This design gives Radixor several practical properties at once:

- compact deployable artifacts,
- deterministic runtime behavior,
- support for both preferred and multiple candidate results,
- separation of preparation-time complexity from runtime lookup.

It also explains why a large source dictionary can be transformed into a much smaller compiled artifact without discarding the operational behavior that matters to the caller.

## Reading guide

Use the following pages depending on what you need to understand:

- [Architecture](architecture.md) explains the data flow, core structures, patch-command lookup model, and why the compiled trie is efficient at runtime.
- [Reduction Semantics](reduction-semantics.md) explains how subtree equivalence is defined, what ranked, unordered, and dominant reduction preserve, and how those choices affect observable lookup behavior.

## Recommended reading order

For most readers, the best order is:

1. [Architecture](architecture.md)
2. [Reduction Semantics](reduction-semantics.md)

## Related documentation

- [Quick start](quick-start.md)
- [Programmatic usage](programmatic-usage.md)
- [CLI compilation](cli-compilation.md)
- [Dictionary format](dictionary-format.md)
