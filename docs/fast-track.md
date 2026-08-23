# Java Fast Track

This page is the shortest path from an empty Java project to a working Radixor stemmer.
It deliberately uses an external model artifact and the preferred compiled-command runtime API, so the
first result does not require writing a dictionary, running the CLI compiler, or understanding
reduction internals.

For the native Python package, start with the [Python Fast Track](python/fast-track.md)
instead; Python does not use Maven model artifacts or the Java loader API.

Use this page when the goal is:

- add the dependency,
- load a registered language model,
- stem a token,
- know where to go next.

For deeper production guidance, see [Integration Deep Dive](integration-deep-dive.md).

## 1. Add The Dependency

Radixor is published as:

```text
groupId:    org.egothor
artifactId: radixor
```

Use the latest published Radixor/Java release. Resolve the runtime and model
placeholders below from the
[Maven Central artifact page](https://central.sonatype.com/artifact/org.egothor/radixor)
and the [model catalog](stemmer-model-catalog.md), respectively.

For a Gradle project:

```kotlin
dependencies {
    implementation("org.egothor:radixor:<latest-java-version>")
    runtimeOnly("org.egothor:radixor-model-us-uk-default:<compatible-model-version>")
}
```

For a Maven project:

```xml
<dependency>
    <groupId>org.egothor</groupId>
    <artifactId>radixor</artifactId>
    <version>REPLACE_WITH_LATEST_JAVA_VERSION</version>
</dependency>
<dependency>
    <groupId>org.egothor</groupId>
    <artifactId>radixor-model-us-uk-default</artifactId>
    <version>REPLACE_WITH_COMPATIBLE_MODEL_VERSION</version>
    <scope>runtime</scope>
</dependency>
```

Radixor targets modern Java and has a dependency-light runtime core. The project documentation and
benchmarks assume a current JDK; Java 21 or newer is the practical baseline for current releases.

## 2. Load An External Model Dictionary

The fastest path is to use a registered model through `StemmerPatchTrieLoader.Language`.
This example uses `US_UK`, whose default ID is `us-uk-default`; the runtime model dependency above must be present.

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

The loaded `FrequencyTrie<CompiledPatchCommand>` has no mutating API. Load it once during application startup, publish it safely through application-owned lifecycle code, and reuse it for indexing and query processing.

## 3. Choose a Language Default or Explicit Model

Language defaults are exposed as enum constants. Common examples:

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

Polish has two models. `Language.PL_PL` selects `pl-pl-unimorph`; load the alternative explicitly with `StemmerPatchTrieLoader.loadCompiled("pl-pl-polimorf", true, reductionMode)`, or retain a registry and pass `registry.require("pl-pl-polimorf")` to the descriptor overload. See [Model Selection and Loading](model-selection-and-loading.md). Full PoliMorf construction requires substantially more startup heap than ordinary models; the repository verifies it in a dedicated 6 GiB test JVM.

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

The fast path parses and compiles a registered model dictionary during startup. That is convenient for evaluation and
small services. For larger deployments, compile once, persist a `.radixor.gz` artifact, and load
that binary artifact at runtime.

Continue with:

- [Integration Deep Dive](integration-deep-dive.md) for production lifecycle guidance.
- [Loading and Building Stemmers](programmatic-loading-and-building.md) for all loading APIs.
- [Model Selection and Loading](model-selection-and-loading.md) for model dependencies, variants, and failures.
- [Built-in Languages](built-in-languages.md) for defaults and optional variants.
- [Benchmarking](benchmarking.md) for speed and quality interpretation.
