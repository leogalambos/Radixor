# Architecture and Reduction

> ← Back to [README.md](../README.md)

This document describes the internal architecture of **Radixor** and the principles behind its **trie compilation and reduction model**.

It explains:

- how data flows from dictionary input to compiled trie
- how patch-command tries are structured
- how subtree reduction works
- how reduction modes affect behavior and size



## Overview

Radixor transforms dictionary data into an optimized runtime structure through three stages:

1. **Mutable construction**
2. **Reduction (canonicalization)**
3. **Compilation (freezing)**

```
Dictionary → Mutable trie → Reduced trie → Compiled trie
```

Each stage has a distinct purpose:

| Stage       | Purpose                         | Structure               |
|------------|----------------------------------|-------------------------|
| Build       | Collect mappings                 | `MutableNode`           |
| Reduction   | Merge equivalent subtrees        | `ReducedNode`           |
| Compilation | Optimize for runtime lookup      | `CompiledNode`          |



## Core data model

### Patch-command trie

Radixor stores **patch commands** instead of stems directly.

- keys: word forms
- values: transformation commands
- structure: trie (prefix tree)

At runtime:

1. the word is traversed through the trie
2. a patch command is retrieved
3. the patch is applied to reconstruct the stem



## Stage 1: Mutable construction

The builder (`FrequencyTrie.Builder`) constructs a trie using:

- `MutableNode`
- maps of children (`char → node`)
- maps of value counts (`value → frequency`)

Characteristics:

- insertion-order preserving
- mutable
- optimized for building, not querying

Example structure:

```
g
 └─ n
     └─ i
         └─ n
             └─ n
                 └─ u
                     └─ r
                         └─ (values: {
                               "<patch-command-1>": 3,
                               "<patch-command-2>": 1
                           })
```

This example represents the word "running", stored in reversed form.

- each edge corresponds to one character of the word
- the path is traversed from the end of the word toward the beginning
- the terminal node stores one or more patch commands together with their local frequencies

The values represent transformations from the word form to candidate stems, and the counts indicate how often each mapping was observed during construction.

Note: Radixor stores word forms in reversed order so that suffix-based transformations can be matched efficiently in a trie.


## Local value summary

Before reduction, each node is summarized using `LocalValueSummary`.

It computes:

- ordered values (by frequency)
- aligned counts
- total frequency
- dominant value (if any)
- second-best value

This summary is critical for:

- deterministic ordering
- reduction decisions
- dominance evaluation



## Stage 2: Reduction (canonicalization)

Reduction is the process of merging **semantically equivalent subtrees**.

### Why reduction exists

Without reduction:

- trie size grows linearly with input data
- repeated patterns are duplicated

With reduction:

- identical subtrees are shared
- memory footprint is reduced
- binary output becomes smaller



## Reduction signature

Each subtree is represented by a **ReductionSignature**.

A signature consists of:

1. **local descriptor** (node semantics)
2. **child descriptors** (structure)

```
Signature = (LocalDescriptor, SortedChildDescriptors)
```

Two subtrees are merged if their signatures are equal.



## Local descriptors

The local descriptor encodes how values at a node are interpreted.

Radixor supports three descriptor types:

### 1. Ranked descriptor

Preserves:

- full ordering of values (`getAll()`)

Uses:

- ordered value list

Best for:

- correctness
- deterministic multi-result behavior



### 2. Unordered descriptor

Preserves:

- only membership (set of values)

Ignores:

- ordering differences

Best for:

- higher compression
- use cases where ordering is irrelevant



### 3. Dominant descriptor

Preserves:

- only the dominant value (`get()`)

Condition:

- dominant value must satisfy thresholds:
  - minimum percentage
  - ratio over second-best

Fallback:

- if dominance is not strong enough → ranked descriptor is used

Best for:

- maximum compression
- single-result workflows



## Child descriptors

Each child is represented as:

```
(edge character, child signature)
```

Children are sorted by edge character to ensure:

- deterministic signatures
- stable equality comparisons



## Reduction context

`ReductionContext` maintains:

- mapping: `ReductionSignature → ReducedNode`
- canonical instances of subtrees

Workflow:

1. compute signature
2. check if already exists
3. reuse existing node or create new one

This ensures:

- structural sharing
- no duplicate equivalent subtrees



## Reduced nodes

`ReducedNode` represents:

- canonical subtree
- aggregated value counts
- canonical children

It supports:

- merging local counts
- verifying structural consistency

At this stage:

- structure is canonical
- still mutable (internally)



## Stage 3: Compilation (freezing)

The reduced trie is converted into a **CompiledNode** structure.

### CompiledNode characteristics

- immutable
- array-based storage
- optimized for fast lookup

Fields:

- `char[] edgeLabels`
- `CompiledNode[] children`
- `V[] orderedValues`
- `int[] orderedCounts`



## Lookup algorithm

Runtime lookup:

1. traverse trie using `edgeLabels` (matching characters from the end of the word toward the beginning)
2. binary search per node
3. retrieve values
4. apply patch command

Properties:

- O(length of word)
- low memory overhead
- minimal memory allocation during lookup; patch application produces the resulting string


## Deterministic ordering

Value ordering is deterministic and stable:

1. higher frequency first
2. shorter string first
3. lexicographically smaller
4. insertion order

This guarantees:

- reproducible builds
- stable query results
- predictable ranking



## Reduction modes

Reduction modes control how local descriptors are chosen.

### Ranked mode

```
MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS
```

- preserves full semantics
- safest option
- recommended default



### Unordered mode

```
MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS
```

- ignores ordering
- higher compression
- slightly weaker semantics



### Dominant mode

```
MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS
```

- keeps only dominant result
- highest compression
- may lose alternative candidates



## Trade-offs

| Aspect        | Ranked | Unordered | Dominant |
|---------------|--------|----------|----------|
| Compression   | Medium | High     | Highest  |
| Accuracy      | High   | Medium   | Lower    |
| getAll()      | Full   | Partial  | Limited  |
| get()         | Exact  | Exact    | Heuristic|



## Deserialization model

Binary loading uses:

- `NodeData` as intermediate representation
- reconstruction of `CompiledNode`

This separates:

- I/O format
- in-memory structure



## Why this architecture works

Radixor achieves:

### Compactness

- subtree sharing
- efficient encoding
- compressed binary output

### Performance

- array-based lookup
- no runtime reduction
- minimal branching

### Flexibility

- configurable reduction strategies
- multiple result support
- dictionary-driven behavior

### Determinism

- stable ordering
- canonical signatures
- reproducible builds



## Design philosophy

The architecture reflects a few key principles:

- separate build-time complexity from runtime simplicity
- encode semantics explicitly (not implicitly in code)
- favor deterministic behavior over heuristic shortcuts
- allow controlled trade-offs between size and fidelity



## When to tune reduction

You should consider changing reduction mode when:

- binary size is too large
- memory footprint must be minimized
- only single-result stemming is needed

Otherwise:

**use ranked mode by default**



## Next steps

- [Programmatic usage](programmatic-usage.md)
- [CLI compilation](cli-compilation.md)
- [Dictionary format](dictionary-format.md)



## Summary

Radixor’s architecture is built around:

- patch-command tries
- canonical subtree reduction
- immutable compiled structures

This design allows the system to remain:

- fast
- compact
- deterministic
- adaptable

while still supporting advanced use cases such as:

- ambiguity-aware stemming
- dictionary evolution
- controlled trade-offs between size and behavior
