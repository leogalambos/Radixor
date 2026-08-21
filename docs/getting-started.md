# Choose a Radixor Runtime

Radixor is a dictionary-trained transformation stemmer. It learns compact
word-to-stem operations from lexical evidence and applies them through a
reduced trie, allowing the runtime to generalize beyond word forms explicitly
listed in the training data. The result is deterministic multilingual stemming
with compact deployable models and independently reproducible quality and
performance measurements.

Radixor is available through three implementations. They share the standard
language catalog and interoperable [compiled Radixor models](data-formats.md),
but differ in integration surface and model-management capabilities.

| Implementation | Role | Best fit | Performance model | Model capabilities |
|---|---|---|---|---|
| **Java** (`org.egothor:radixor`) | Primary, flagship and reference implementation | JVM applications and model development | Direct JVM calls; allocation-conscious APIs | Complete functionality: selectable reduction and normalization, trie construction, extension and persistence |
| **Python (PyO3)** (`radixor`) | Native Python port, progressively converging on the Java API | Python pipelines that need model compilation, rich results or high batch throughput | Batch methods amortize the Python/native boundary | Loads and compiles text or binary models today; additional Java capabilities will be added here first |
| **Python-C** (`radixor-c`) | Focused runtime implemented against the CPython C API | Python applications that primarily consume prepared models | Low native-call overhead, including calls for individual words | Basic stemming from compiled models; trie compilation and modification are intentionally deferred |

Java remains the authority for new functionality. The `radixor` Python port is
the path toward Java-level capabilities. `radixor-c` is not a reduced-quality
stemmer: it uses the same compiled models and produces the same stemming
results. It deliberately has a smaller **management API**, so it can concentrate
on fast, simple runtime use.

## Fast track

=== "Python-C"

    ```bash
    python -m pip install radixor-c
    ```

    ```python
    from radixor_c import Stemmer

    stemmer = Stemmer("en")
    print(stemmer.stem("running"))  # run
    ```

    Continue with the [Python-C quick start](python-c/quick-start.md).

=== "Python (PyO3)"

    ```bash
    python -m pip install radixor
    ```

    ```python
    from radixor import Stemmer

    stemmer = Stemmer("en")
    print(stemmer.stem_batch(["running", "studies", "cars"]))
    ```

    Continue with the [Python quick start](python/quick-start.md).

=== "Java"

    Start with the [Java fast track](fast-track.md), then see how to
    [extend a compiled stemmer](programmatic-extending-and-persistence.md).

## Shared concepts

- [Data Formats](data-formats.md) explains source dictionaries, compiled tries,
  which runtime can create them, and how they move between implementations.
- [Built-in Languages](built-in-languages.md) documents aliases, model IDs and defaults.
- [Python Performance](python/performance.md) is the shared benchmark page for
  both Python runtimes; unmeasured Python-C cells are explicitly marked `N/A`.
- [Architecture](architecture.md) explains the common stemming semantics and
  runtime-specific data structures.
