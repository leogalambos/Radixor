# Python Fast Track

This is the shortest path from an empty Python environment to a working
Radixor stemmer. The installation includes the native runtime and the separate
standard-model package with 20 precompiled language models.

## 1. Install

=== "PyPI"

    ```bash
    python -m pip install --only-binary=:all: radixor
    ```

=== "GitHub Releases"

    ```bash
    python -m pip install --only-binary=:all: \
      --index-url https://leogalambos.github.io/Radixor/python/simple/ radixor
    ```

PyPI publication is pending, and the GitHub index becomes live with the first
Python releases. Until then, follow the source-checkout procedure on
[Installation and Builds](installation.md).

Radixor supports CPython 3.9 and newer. A JVM, Java dependency, and source
dictionary are not required.

## 2. Stem words

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

## Next

- Continue with the [Python Quick Start](quick-start.md) for model selection,
  batch processing, custom compiled models, and deployment guidance.
- Use [Python Usage and API](usage.md) as the method reference.
- Review the reproducible [Python performance results](performance.md).
