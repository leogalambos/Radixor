# Radixor-C for Python

**`radixor-c`** is Radixor's focused CPython runtime. Its native engine is
implemented directly against the CPython C API, which minimizes the overhead of
stemming one word at a time. It needs no JVM or Rust runtime.

```python
from radixor_c import Stemmer

stemmer = Stemmer("en")
stemmer.stem("running")  # 'run'
```

Radixor-C uses the same standard models, stemming semantics and public stemming
surface as the `radixor` Python package. Its scope is intentionally narrower:
it loads prepared [compiled Radixor models](../data-formats.md) but does not currently compile text
dictionaries or modify tries.

## When to choose it

Choose `radixor-c` when:

- Python code calls the stemmer for individual words and native-call overhead matters;
- the application uses standard models or already prepared `.rxc` files;
- basic dominant and multi-result stemming APIs are sufficient.

Choose `radixor` when:

- large batches are the primary workload—its strongest throughput comes from
  amortizing the Python/native boundary with batch calls;
- Python must compile a text dictionary into a compiled Radixor model;
- you want the Python implementation that will progressively receive the
  broader Java feature set.

Choose Java for the flagship API, including reopening and extending compiled
tries, selectable reduction modes and the complete model toolchain. This is a
capability distinction, not a model-quality distinction.

## Documentation

- [Python-C Quick Start](quick-start.md)
- [Python-C Installation and Builds](installation.md)
- [Python-C Usage and API](usage.md)
- [Shared Python Performance](../python/performance.md)
- [Shared Data Formats](../data-formats.md)
