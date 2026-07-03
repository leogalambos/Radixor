# Fast Track

This page is the shortest path from an empty Java project to a working Radixor stemmer.
It deliberately uses a bundled dictionary and the preferred compiled-command runtime API, so the
first result does not require writing a dictionary, running the CLI compiler, or understanding
reduction internals.

Use this page when the goal is:

- add the dependency,
- load a bundled language resource,
- stem a token,
- know where to go next.

For deeper production guidance, see [Integration Deep Dive](integration-deep-dive.md).

## 1. Add The Dependency

Radixor is published as:

```text
groupId:    org.egothor
artifactId: radixor
```

Use the current published version from Maven Central. The snippets below use `3.0.0`; replace it
with the version you deploy if a newer release is available.

For a Gradle project:

```kotlin
dependencies {
    implementation("org.egothor:radixor:3.0.0")
}
```

For a Maven project:

```xml
<dependency>
    <groupId>org.egothor</groupId>
    <artifactId>radixor</artifactId>
    <version>3.0.0</version>
</dependency>
```

Radixor targets modern Java and has a dependency-light runtime core. The project documentation and
benchmarks assume a current JDK; Java 21 or newer is the practical baseline for current releases.

## 2. Load A Bundled Dictionary

The fastest path is to use a bundled dictionary through `StemmerPatchTrieLoader.Language`.
This example uses the bundled English resource, `US_UK`.

```java
import java.io.IOException;

import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

public final class RadixorFirstStem {

    private RadixorFirstStem() {
        throw new AssertionError("No instances.");
    }

    public static void main(final String[] arguments) throws IOException {
        final FrequencyTrie<CompiledPatchCommand> stemmer = StemmerPatchTrieLoader.loadCompiled(
                StemmerPatchTrieLoader.Language.US_UK,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final String token = "running";
        final CompiledPatchCommand command = stemmer.get(token);
        final String stem = command == null ? token : command.apply(token);

        System.out.println(token + " -> " + stem);
    }
}
```

The loaded `FrequencyTrie<CompiledPatchCommand>` is immutable and can be shared across request
threads. Load it once during application startup and reuse it for indexing and query processing.

## 3. Choose A Language Resource

Bundled dictionaries are exposed as enum constants. Common examples:

| Language | Enum constant |
| --- | --- |
| English | `US_UK` |
| German | `DE_DE` |
| French | `FR_FR` |
| Spanish | `ES_ES` |
| Italian | `IT_IT` |
| Polish | `PL_PL` |
| Russian | `RU_RU` |
| Czech | `CS_CZ` |

The full list, writing-direction notes, and benchmark links are in
[Built-in Languages](built-in-languages.md).

## 4. Use The Same Stemmer On Both Sides

For search, use the same Radixor configuration during indexing and query processing. A typical
minimal integration flow is:

1. tokenize text with your application or search platform,
2. normalize tokens consistently,
3. call `stemmer.get(token)`,
4. apply the returned `CompiledPatchCommand`,
5. index or query with the resulting stem.

Do not load the trie per token. The compiled trie is the runtime artifact; per-token work should be
limited to lookup and patch application.

## 5. Next Step For Production

The fast path compiles a bundled dictionary during startup. That is convenient for evaluation and
small services. For larger deployments, compile once, persist a `.radixor.gz` artifact, and load
that binary artifact at runtime.

Continue with:

- [Integration Deep Dive](integration-deep-dive.md) for production lifecycle guidance.
- [Loading and Building Stemmers](programmatic-loading-and-building.md) for all loading APIs.
- [Built-in Languages](built-in-languages.md) for bundled resources and dictionary locations.
- [Benchmarking](benchmarking.md) for speed and quality interpretation.
