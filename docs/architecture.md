# Architecture

This document explains the structural architecture of **Radixor**: what data is stored, how it flows through the build pipeline, and how runtime lookup works once a compiled trie has been produced.

## The central idea

Radixor does not store final stems directly as a large flat lookup table. Instead, it stores **patch commands** that describe how a word form should be transformed into a canonical stem.

For example, if a dictionary states that `running` should reduce to `run`, the final runtime artifact does not need to store a full redundant `running -> run` output string entry in the simplest possible form. It can store a compact transformation command that expresses how to turn the source form into the target form.

That matters because many words share similar transformation patterns. Once those mappings are organized in a trie and compiled into a canonical structure, the result is much smaller and more reusable than a naive direct-output table.

## End-to-end build flow

The full build-time flow is:

```text
Dictionary -> Mutable trie -> Reduced trie -> Compiled trie
```

Each stage has a different purpose.

### Dictionary input

The textual dictionary groups known word forms under a canonical stem:

```text
run	running	runs	ran
connect	connected	connecting	connection
```

The first column is the canonical stem. The following tab-separated columns are known variants.

### Patch-command generation

Each variant is converted into a patch command that transforms the variant into the stem.

Conceptually:

```text
running -> <patch> -> run
runs    -> <patch> -> run
ran     -> <patch> -> run
```

If `storeOriginal` is enabled, the stem itself is also inserted using a canonical no-op patch.

### Mutable trie construction

Those patch-command values are inserted into a mutable trie keyed by the source surface form.

### Reduction

Equivalent subtrees are merged into canonical reduced nodes.

### Compilation

The reduced structure is frozen into an immutable compiled trie optimized for runtime lookup.

## Why a trie is used

A trie is useful because many word forms share structural fragments. Instead of storing each word independently, the trie reuses paths and organizes lookup by character traversal.

A trie node can contain:

- outgoing edges,
- one or more ordered values,
- counts aligned with those values.

This is why the structure can represent both:

- a single preferred result,
- multiple competing results for the same key.

## Stage 1: Mutable construction

The mutable build-time structure is created by `FrequencyTrie.Builder`.

This stage is optimized for insertion rather than runtime lookup. As dictionary data is added, the builder accumulates:

- child edges,
- local values,
- local frequencies of those values.

Those frequencies are not incidental metadata. They later influence both result ordering and, depending on reduction mode, the semantic identity of subtrees during reduction.

### Why the build-time form is mutable

The builder must be easy to extend and easy to aggregate into. That is the opposite of what a runtime lookup structure needs.

Build-time priorities are:

- flexibility,
- accumulation of counts,
- structural growth.

Runtime priorities are:

- compactness,
- immutability,
- fast lookup.

Radixor therefore keeps construction and runtime representation strictly separate.

## What a compiled node contains

After reduction and freezing, the runtime structure uses immutable compiled nodes.

A compiled node stores:

- `char[] edgeLabels`
- child-node references aligned with those labels
- ordered value arrays
- aligned count arrays

This array-based form is compact and efficient for lookup.

## Runtime lookup model

At runtime, lookup is conceptually simple:

1. traverse the compiled trie by the input key,
2. reach the node addressed by that key,
3. retrieve one or more stored patch commands,
4. apply the chosen patch command to the original word.

The trie itself does not create the final stem string. It selects the stored transformation command. `PatchCommandEncoder.apply(...)` then performs the actual transformation.

That separation is architecturally important:

- the trie is responsible for **selection**,
- patch application is responsible for **transformation**.

## `get()` and `getAll()`

The runtime API exposes two complementary views of the addressed node.

### `get()`

`get()` returns the locally preferred value stored at that node.

Preference is deterministic:

1. higher local frequency wins,
2. shorter textual representation wins,
3. lexicographically lower textual representation wins,
4. stable first-seen order acts as the final tie-breaker.

### `getAll()`

`getAll()` returns all locally stored values in deterministic ranked order.

This is what allows Radixor to preserve ambiguity explicitly instead of forcing every key into a single answer.

## Why multiple results can exist

Some stemming systems discard ambiguity early because they insist on returning exactly one answer.

Radixor does not require that simplification. If multiple plausible patch commands exist for a key, the compiled trie can preserve them and the runtime API can expose them.

That is useful when downstream logic wants to:

- inspect ambiguity,
- preserve alternatives for retrieval,
- apply later ranking or domain-specific selection.

## Why compiled artifacts are compact

The final compiled trie can be much smaller than the original dictionary for several reasons working together:

- patch commands are compact,
- trie paths reuse shared structure,
- reduction merges equivalent subtrees,
- binary persistence stores the already reduced form,
- GZip compression is applied on top of the binary format.

This is why a very large dictionary can still produce a manageable deployable runtime artifact.

## Why preparation can still use more memory

The compactness of the final artifact should not be confused with the memory usage of preparation.

Before reduction has completed, the mutable build-time structure must exist in memory. For large dictionaries, that temporary preparation cost can be noticeably higher than the size of the final persisted artifact or the loaded compiled trie.

That is why the preferred operational model is usually:

- compile offline,
- persist the compiled artifact,
- load the finished artifact in runtime services.

## Determinism as a design principle

Radixor favors deterministic behavior throughout the pipeline.

This appears in:

- lowercased dictionary parsing,
- stable value ordering,
- sorted child descriptors,
- canonical reduction signatures,
- reproducible compiled lookup behavior.

Determinism matters not only for tests, but also for operational trust. It makes stemming behavior explainable and reproducible across builds and environments.

## Continue with

- [Reduction Semantics](reduction-semantics.md)
- [Programmatic usage](programmatic-usage.md)
- [CLI compilation](cli-compilation.md)
