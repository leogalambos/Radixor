# radixor-c — Fast Scalar Stemming for Python

**radixor-c** is the C-backed Python runtime for
[Radixor](https://github.com/leogalambos/Radixor), a dictionary-trained
transformation stemmer supporting 20 languages. Its native engine is implemented
directly against the CPython C API to keep the overhead of stemming individual
words low.

It uses the same precompiled models and stemming semantics as the Java flagship
and the Python (PyO3) package, distributed as `radixor`. The distinction is scope: radixor-c focuses
on basic runtime stemming from prepared models. Text dictionary compilation,
trie modification and the broader model toolchain belong to Java and, as it
converges toward Java, the `radixor` Python package.

## Which Radixor package should I use?

| Package | Choose it for | Model operations |
|---|---|---|
| **Python-C** (`radixor-c`) | Fast calls for individual Python words; simple deployment | Load standard or [compiled Radixor models](https://leogalambos.github.io/Radixor/data-formats/) |
| **Python (PyO3)** (`radixor`) | High-throughput batch processing and Python-side compilation | Load compiled models and compile textual dictionaries; broader capabilities are added here first |
| **Java Radixor** | The complete, flagship API and model development | Full construction, reduction, extension and persistence |

Radixor-c is not a lower-quality stemmer. Given the same compiled trie it
produces the same results; it currently exposes fewer ways to create or modify
that trie.

## Installation

```bash
python -m pip install --only-binary=:all: radixor-c
```

Published wheels support CPython 3.10–3.14 on Linux, macOS and Windows. The
installation also resolves `radixor-models-standard`, the shared package of 20
precompiled standard models.

## Quick start

```python
from radixor_c import Stemmer

stemmer = Stemmer("en")

stemmer.stem("running")       # "run"
stemmer.stem("unknown_word")  # None
```

Construct the stemmer once and reuse it. Scalar calls are the primary reason to
select radixor-c, but batch and PyStemmer-compatible methods are also available:

```python
words = ["running", "studies", "cars"]

stemmer.stem_batch(words)
stemmer.stemWord("running")
stemmer.stemWords(words)
```

`stem()` and `stem_batch()` return `None` where no patch applies.
`stemWord()` and `stemWords()` instead return unmatched input unchanged.

## Custom models

Radixor-c loads [compiled Radixor models](https://leogalambos.github.io/Radixor/data-formats/):

```python
custom = Stemmer(compiled="models/domain-english.rxc")
```

It deliberately does not compile a text dictionary. Prepare the interoperable
`.rxc` file with Java or the `radixor` package:

```python
import radixor

radixor.compile("domain.tsv.gz", "models/domain-english.rxc", language="en")
```

The resulting model can be loaded by all three implementations.

## API at a glance

| API | Purpose |
|---|---|
| `Stemmer(language)` | Load a bundled model by alias or model ID |
| `Stemmer(compiled=path)` | Load an application-owned compiled Radixor model |
| `stem(word)` | Return the dominant stem or `None` |
| `stem_batch(words)` | Stem a list with positional `None` results |
| `stemWord(word)` / `stemWords(words)` | PyStemmer-compatible unmatched-word fallback |
| `stem_all(word)` / `stem_all_batch(words)` | Return ranked alternative stems |
| `algorithms()` / `version()` | Compatibility and package information |

The default bounded cache holds 10,000 results. Use `cache_size=0` to disable
it, or configure the PyStemmer-compatible `maxCacheSize` property.

## Documentation

- [Choose a Radixor runtime](https://leogalambos.github.io/Radixor/getting-started/)
- [Python-C quick start](https://leogalambos.github.io/Radixor/python-c/quick-start/)
- [Python-C usage and API](https://leogalambos.github.io/Radixor/python-c/usage/)
- [Shared Python benchmark](https://leogalambos.github.io/Radixor/python/performance/)
- [Shared data formats](https://leogalambos.github.io/Radixor/data-formats/)

Radixor software is available under the BSD 3-Clause License. Model data keeps
its separately documented provenance and licensing.
