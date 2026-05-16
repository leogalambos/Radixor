# Lookup Edge Optimization

Compiled trie nodes (`CompiledNode`) use three lookup strategies when resolving child edges:

1. dense array direct lookup,
2. linear scan for very small child counts,
3. binary search over sorted edge labels.

This page explains the dense path, what `maxExpandedIndex` controls, and how to tune it.

## Runtime model of one node

For a node with sorted edge labels `char[] edges`, the implementation can materialize an
index-aligned dense table when labels occupy a small compact code-point interval:

```text
span = maxEdge - minEdge
use dense table iff (span <= maxExpandedIndex) and (maxExpandedIndex > 0)
```

When dense lookup is used, lookup is constant-time indexing:

```text
denseIndex = requestedEdge - minEdge
return denseChildren[denseIndex]  // or null if outside interval
```

When dense lookup is not active (interval is too wide or the configured
`maxExpandedIndex` is `0`), `CompiledNode` still chooses between two fallback
strategies:

- **linear scan** for very small child counts (`4` or fewer children),
- **binary search** for larger child counts.

This means the fallback method is selected by child count, not by “distance” alone.
`linear scan` is therefore used when there are only a few edges even if those edges are
spread across very distant code points.

### Example: few edges, wide Unicode span

```text
edges = ['a', '中', '你']
edge count = 3
minEdge = 'a' (U+0061)
maxEdge = '你' (U+4F60)
span = 20319
```

- If `maxExpandedIndex = 512`, dense indexing is not used because `span > maxExpandedIndex`.
- Because `edge count = 3` (<= 4), lookup falls back to a tiny linear scan of the
  three labels.
- This is exactly the case where you get benefit from the threshold even though the interval is wide.

This is useful for non-Latin scripts as well: what matters is interval width in Unicode
code points, not script name. A compact Arabic-range block can still benefit from dense
lookups when keys stay in a tight code-point interval.

## Why this is configurable

`maxExpandedIndex` is only a performance/paging choice:

- higher value:
  - more compact intervals qualify for dense tables,
  - more constant-time child lookup,
  - more memory for dense tables in qualifying nodes.
- lower value (or `0`):
  - less dense-table allocation,
  - fewer branches into constant-time path,
  - lower materialization memory.

The value never changes lookup semantics. It only changes the in-memory structure shape.

## Persistence and loading model

This threshold is **not** stored in `TrieMetadata`.

- The binary format stores only trie payload and semantic metadata (`reduction`, `traversal`,
  case/diacritic settings, and stream version).
- `maxExpandedIndex` is chosen when materializing nodes in memory.
- You can therefore keep one persisted artifact and load it with different in-memory
  trade-offs depending on deployment constraints.

## Default

- `FrequencyTrie.DEFAULT_MAX_EXPANDED_INDEX == 512`
- `CompiledNode.DEFAULT_MAX_EXPANDED_INDEX == 512`

These are practical defaults for mixed-language text and Latin-like scripts where edge labels
often cluster.

## Tune during build (writable phase)

Use the full `FrequencyTrie.Builder` constructor when you are compiling from source data.
The builder threshold is applied while freezing reduced nodes into the immutable form.

```java
import org.egothor.stemmer.CaseProcessingMode;
import org.egothor.stemmer.DiacriticProcessingMode;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.egothor.stemmer.WordTraversalDirection;

final ReductionSettings settings = ReductionSettings.withDefaults(
        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

final FrequencyTrie.Builder<String> fastBuilder =
        new FrequencyTrie.Builder<>(String[]::new,
                settings,
                WordTraversalDirection.BACKWARD,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT,
                DiacriticProcessingMode.AS_IS,
                1024); // prefer lookup speed

// ... put(...) ...
final FrequencyTrie<String> trie = fastBuilder.build();
```

Use `0` or `256` for lower memory while still building larger tries.

```java
final FrequencyTrie.Builder<String> compactBuilder =
        new FrequencyTrie.Builder<>(String[]::new,
                settings,
                WordTraversalDirection.BACKWARD,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT,
                DiacriticProcessingMode.AS_IS,
                256); // lower memory profile
```

## Tune when loading a binary artifact (runtime phase)

At artifact load time, you can tune the same trade-off independently of persisted metadata.

```java
import java.nio.file.Path;

import org.egothor.stemmer.StemmerPatchTrieLoader;

var defaultLookup = StemmerPatchTrieLoader.loadBinary(
        Path.of("stemmers", "english.radixor.gz"));

var fastLookup = StemmerPatchTrieLoader.loadBinary(
        Path.of("stemmers", "english.radixor.gz"), 1024);

var compactLookup = StemmerPatchTrieLoader.loadBinary(
        Path.of("stemmers", "english.radixor.gz"), 0);
```

You can also set the threshold directly with `FrequencyTrie.readFrom(...)` when reading streams:

```java
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import org.egothor.stemmer.FrequencyTrie;

public final class StreamLoadExample {

    private StreamLoadExample() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        try (InputStream fileInput = Files.newInputStream(Path.of("stemmers", "english.radixor.gz"));
                GZIPInputStream gzip = new GZIPInputStream(fileInput);
                DataInputStream dataInput = new DataInputStream(gzip)) {
            final FrequencyTrie<String> compactOnLoad = FrequencyTrie.readFrom(
                    dataInput,
                    String[]::new,
                    input -> input.readUTF(),
                    256);
        }
    }
}
```

Note: the string codec is intentionally inline in this snippet to keep it self-contained.

## Practical guidance

- Start with default (`512`) in production and profile before changing it.
- Use `0` when memory is the priority and query throughput is not the bottleneck.
- Use values around `1024` for workloads dominated by compact alphabets and very hot lookups.

Trade-off expectation:

- increasing `maxExpandedIndex` improves lookup speed when edges tend to occupy short spans,
- decreasing it reduces per-node auxiliary memory in dense-span nodes.
