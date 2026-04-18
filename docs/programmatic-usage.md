# Programmatic Usage

This document provides the programmatic entry point to **Radixor**.

Radixor follows a clear lifecycle:

1. acquire a compiled stemmer,
2. query it for patch commands,
3. apply those commands to produce stems,
4. reopen and extend the compiled structure when needed.

## Conceptual model

Radixor is dictionary-driven, but runtime stemming does not operate by scanning raw dictionary files. A source dictionary is parsed as a sequence of canonical stems and their known variants. Each variant is converted into a compact patch command that transforms the variant into the stem, while the stem itself may optionally be stored as a canonical no-op patch. The mutable trie is then reduced into a compiled read-only structure that stores ordered values and their counts at addressed nodes.

Two consequences matter for developers:

- the quality and coverage of stemming behavior depend on dictionary richness,
- runtime usage is based on compiled patch-command lookup rather than on direct dictionary traversal.

This is why Radixor can generalize beyond explicitly listed forms and why compiled artifacts are well suited for deployment.

## Documentation map

The programmatic API is easier to understand when split by developer task:

- [Loading and Building Stemmers](programmatic-loading-and-building.md) explains how to acquire a compiled stemmer from bundled resources, textual dictionaries, binary artifacts, or direct builder usage.
- [Querying and Ambiguity Handling](programmatic-querying-and-ambiguity.md) explains `get(...)`, `getAll(...)`, `getEntries(...)`, patch application, and the practical meaning of reduction modes.
- [Extending and Persisting Compiled Tries](programmatic-extending-and-persistence.md) explains how to reopen compiled tries, add new lexical data, rebuild them, and store them as binary artifacts.

## Core types

The main types involved in programmatic usage are:

- `FrequencyTrie.Builder<V>` for mutable construction and extension,
- `FrequencyTrie<V>` for the compiled read-only trie,
- `PatchCommandEncoder` for creating and applying patch commands,
- `StemmerPatchTrieLoader` for loading bundled or textual dictionaries,
- `StemmerPatchTrieBinaryIO` for reading and writing compressed binary artifacts,
- `FrequencyTrieBuilders` for reconstructing a mutable builder from a compiled trie,
- `ReductionMode` and `ReductionSettings` for controlling compilation semantics.

## Recommended reading order

For most developers, the best order is:

1. [Loading and Building Stemmers](programmatic-loading-and-building.md)
2. [Querying and Ambiguity Handling](programmatic-querying-and-ambiguity.md)
3. [Extending and Persisting Compiled Tries](programmatic-extending-and-persistence.md)

## Next steps

- [Quick Start](quick-start.md)
- [CLI compilation](cli-compilation.md)
- [Dictionary format](dictionary-format.md)
- [Architecture and reduction](architecture-and-reduction.md)
