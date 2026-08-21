# Python Fast Track

This is the shortest path from an empty Python environment to a working
Radixor stemmer. First choose the native runtime. Both installations resolve
the separate standard-model package with 20 precompiled language models.

## 1. Choose and install

=== "Python-C"

    ```bash
    python -m pip install --only-binary=:all: radixor-c
    ```

    Use this package for simple stemming from standard or prepared models.
    It supports CPython 3.10–3.14.

=== "Python (PyO3)"

    ```bash
    python -m pip install --only-binary=:all: radixor
    ```

    Use this package when processing batches or compiling text dictionaries.
    It supports CPython 3.9 and newer.

## 2. Stem words

=== "Python-C"

    ```python
    from radixor_c import Stemmer

    stemmer = Stemmer("en")
    print(stemmer.stem("running"))
    ```

=== "Python (PyO3)"

    ```python
    from radixor import Stemmer

    stemmer = Stemmer("en")

    print(stemmer.stem("running"))
    print(stemmer.stem_batch(["running", "studies", "cars"]))
    ```

Expected first output:

```text
run
```

`stem()` and `stem_batch()` preserve Radixor's original API: a word for which
the trie finds no patch command produces `None`.

## 3. Use PyStemmer-compatible fallback semantics

For a low-friction migration from PyStemmer, use the compatible method names:

```python
stemmer.stemWord("running")
stemmer.stemWords(["running", "unknown_word"])
```

These methods return the original input whenever no patch command is found, so
their results are always strings rather than `None`.

With Python (PyO3), `import Stemmer` is optional and only for zero-source-change
migration when PyStemmer is absent; otherwise use
`from radixor import Stemmer`. Python-C uses `from radixor_c import Stemmer`.

## Next

- Continue with the [Python (PyO3) Quick Start](quick-start.md) for model selection,
  batch processing, custom compiled models, and deployment guidance.
- Continue with the [Python-C Quick Start](../python-c/quick-start.md) for the
  scalar-oriented C runtime.
- Use [Python (PyO3) Usage and API](usage.md) as the method reference.
- Review the reproducible [Python performance results](performance.md).
