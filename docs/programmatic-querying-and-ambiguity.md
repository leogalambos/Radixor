# Querying and Ambiguity Handling

This document explains how a compiled Radixor trie is queried and how ambiguity is represented.

## Query a compiled trie

### `get(...)`: preferred local value

`FrequencyTrie.get(String)` returns the most frequent value stored at the node addressed by the supplied key. If several values have the same local frequency, the winner is chosen deterministically by shorter `toString()` value first, then by lexicographically lower `toString()`, and finally by stable first-seen order. If the key does not exist or no value is stored at the addressed node, `null` is returned.

```java
final String word = "running";
final String patch = trie.get(word);
```

### `getAll(...)`: ordered local values

`FrequencyTrie.getAll(String)` returns all values stored at the addressed node, ordered by descending frequency using the same deterministic tie-breaking rules. The returned array is a defensive copy. If the key is missing or has no local values, an empty array is returned.

```java
final String[] patches = trie.getAll("axes");
```

### `getEntries(...)`: values with counts

`FrequencyTrie.getEntries(String)` returns immutable `ValueCount<V>` objects aligned with the same ordering used by `getAll(...)`.

```java
import java.util.List;

import org.egothor.stemmer.ValueCount;

final List<ValueCount<String>> entries = trie.getEntries("axes");
```

## Apply patch commands

A patch command is not the final stem. It must be applied to the original input token. `PatchCommandEncoder.apply(source, patchCommand)` performs that transformation directly on the serialized command format. If the source is `null`, the method returns `null`. If the patch is `null`, empty, or malformed in compatibility-relevant ways, the original source word is preserved. Equal source and target words are represented by the canonical no-op patch.

```java
import org.egothor.stemmer.PatchCommandEncoder;

final String word = "running";
final String patch = trie.get(word);
final String stem = PatchCommandEncoder.apply(word, patch);
```

For multiple candidates:

```java
final String word = "axes";
for (final String patch : trie.getAll(word)) {
    final String stem = PatchCommandEncoder.apply(word, patch);
    System.out.println(word + " -> " + stem + " (" + patch + ")");
}
```

## Understand reduction modes

Reduction mode determines how mutable subtrees are merged during compilation. All modes operate on full subtree semantics rather than only on local node content.

### `MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS`

This mode merges subtrees whose `getAll()` results are equivalent for every reachable key suffix and whose local result ordering is the same. It ignores absolute frequencies when comparing subtree signatures, but it preserves ranked multi-result ordering semantics.

### `MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS`

This mode also merges according to `getAll()` equivalence for every reachable key suffix, but it ignores local result ordering in addition to absolute frequencies. It is therefore more aggressive in what it considers equivalent.

### `MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS`

This mode focuses on `get()` equivalence for every reachable key suffix, subject to dominance constraints. If a node does not satisfy the configured dominance thresholds, the implementation falls back to ranked `getAll()` semantics for that node to avoid unsafe over-reduction. The thresholds are configured through `ReductionSettings`. Defaults are 75 percent minimum winner share and a winner-over-second ratio of 3.

## Practical guidance

- choose a ranked `getAll()` mode when downstream ambiguity handling matters,
- choose the dominant `get()` mode when the primary operational concern is the preferred result,
- treat reduction mode as part of observable lookup semantics, not merely as an internal compression setting.

## Continue with

- [Extending and Persisting Compiled Tries](programmatic-extending-and-persistence.md)
- [Loading and Building Stemmers](programmatic-loading-and-building.md)
