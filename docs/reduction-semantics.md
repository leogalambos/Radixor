# Reduction Semantics

This document explains how **Radixor** decides that two subtrees are equivalent, how the different reduction modes work, and how those choices affect observable runtime behavior.

## Why reduction exists

Without reduction, the trie would still work, but many subtrees that mean the same thing would remain duplicated. The result would be a much larger runtime artifact than necessary.

Reduction solves that by merging semantically equivalent subtrees into one canonical representative.

The key idea is simple:

> if two subtrees behave the same way under the semantic contract chosen for compilation, only one physical copy is needed.

## Reduction is semantic, not merely structural

Radixor does not reduce nodes merely because they look similar locally. It reduces subtrees only when their **meaning** matches according to the selected mode.

That is why reduction is based on a **signature** that captures both:

1. the local semantics of the current node,
2. the structure and semantics of all descendant edges.

Conceptually:

```text
Signature = (LocalDescriptor, SortedChildDescriptors)
```

Two subtrees are merged only if their signatures are equal.

## Local descriptors

The local descriptor defines what “equivalent” means for the values stored at one node.

Radixor supports three semantic views.

### Ranked descriptor

The ranked descriptor preserves the full ordered result semantics of `getAll()`.

That means:

- candidate membership is preserved,
- local ordering is preserved,
- observable ranked multi-result behavior remains stable.

This is the most semantically faithful mode when ambiguity handling matters.

### Unordered descriptor

The unordered descriptor preserves the set of reachable results, but not their local ordering.

That means:

- candidate membership is preserved,
- ordering differences may be ignored,
- more subtrees can be merged than in ranked mode.

This mode is useful when alternative candidates matter but exact ranking does not.

### Dominant descriptor

The dominant descriptor focuses on the preferred result returned by `get()`.

This mode is used only when the dominant local candidate is strong enough according to configured thresholds:

- minimum winner percentage,
- winner-over-second ratio.

If that local dominance is not strong enough, Radixor does not force dominant semantics anyway. It falls back to ranked semantics for that node to avoid unsafe over-reduction.

That fallback is one of the most important safeguards in the design.

## Child descriptors

A subtree is not defined only by the values stored at the current node. It is also defined by what behavior is reachable through its children.

Each child contributes:

```text
(edge character, child signature)
```

Children are sorted by edge character so that signatures remain deterministic and stable.

This matters because reduction must not depend on incidental map iteration order or other non-semantic implementation details.

## Canonicalization

Once a subtree signature is computed, the reduction process checks whether an equivalent canonical subtree already exists.

If yes, the existing reduced node is reused.

If no, a new canonical reduced node is created and registered.

This turns reduction into a canonicalization process:

- compute semantic identity,
- find canonical representative,
- reuse or create,
- continue bottom-up.

That is how Radixor eliminates duplicated equivalent subtrees.

## Count aggregation and compiled state

When multiple original build-time subtrees collapse into one canonical reduced node, local counts may be aggregated.

This is an important point for understanding compiled artifacts.

A compiled trie is not always a verbatim replay of original insertion history. It is a canonical runtime structure that preserves the semantics guaranteed by the chosen reduction mode.

This explains two things:

- why compiled artifacts can become dramatically smaller,
- why reconstructing a builder from a compiled trie reflects the compiled state rather than the full original unreduced history.

## Reduction modes

### `MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS`

This mode merges subtrees only when their `getAll()` results are equivalent for every reachable key suffix and when local ordering is preserved.

Use this mode when:

- ambiguity handling matters,
- `getAll()` ordering should remain meaningful,
- behavioral fidelity is more important than maximum compression.

This is the safest and most generally recommended mode.

### `MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS`

This mode also preserves `getAll()`-level membership equivalence for every reachable key suffix, but it ignores local ordering differences.

Use this mode when:

- alternative candidates still matter,
- exact ordering is less important,
- stronger reduction is acceptable.

This mode is more aggressive than ranked mode, but less semantically rich.

### `MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS`

This mode focuses on preserving dominant `get()` semantics for every reachable key suffix, subject to dominance thresholds.

Use this mode when:

- the main operational concern is the preferred result,
- richer alternative-result behavior is less important,
- stronger reduction is desirable.

Because non-dominant nodes fall back to ranked semantics, this mode is not simply “discard everything except the winner”. It is a controlled reduction strategy with a built-in safety condition.

## Practical effect on runtime behavior

Reduction mode is not just a storage optimization setting. It affects what distinctions remain visible after compilation.

### When ranked mode is used

You can rely on full ranked `getAll()` semantics being preserved.

### When unordered mode is used

You can rely on candidate membership, but not necessarily on preserving the same local ranking distinctions.

### When dominant mode is used

You optimize primarily for preferred-result semantics. Alternative-result behavior may still exist, but it is no longer the primary semantic contract of the reduction.

## Choosing a mode

A practical rule of thumb is:

- choose **ranked** if you are unsure,
- choose **unordered** if alternative membership matters but ranking does not,
- choose **dominant** only when your application is fundamentally driven by `get()` and you understand the trade-off.

## Why this design works well

The reduction model succeeds because it does not confuse “smaller” with “acceptable”.

Instead, it makes the semantic contract explicit:

- what exactly must be preserved,
- what differences may be ignored,
- when a more aggressive mode is safe,
- when the system must fall back to a stricter interpretation.

That explicitness is what makes the compression trustworthy.

## Mental model to keep

If you want one concise mental model for reduction, use this one:

- build-time insertion collects examples,
- reduction asks which subtrees mean the same thing,
- the answer depends on the chosen semantic contract,
- canonical representatives are shared,
- the compiled trie preserves the behavior promised by that contract.

## Continue with

- [Architecture](architecture.md)
- [Programmatic usage](programmatic-usage.md)
- [CLI compilation](cli-compilation.md)
