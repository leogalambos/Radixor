# Python Quick Start

Radixor's Python implementation is a native Rust extension with a Python API.
It uses the same learned patch-command model and version 7 compiled-trie format
as the Java implementation, without requiring a JVM.

## 1. Install the runtime and standard models

Create an isolated environment and install Radixor:

```bash
python -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
```

=== "PyPI"

    ```bash
    python -m pip install --only-binary=:all: radixor
    ```

=== "GitHub Releases"

    ```bash
    python -m pip install --only-binary=:all: \
      --index-url https://leogalambos.github.io/Radixor/python/simple/ radixor
    ```

Both indexes provide the same released distributions. See
[Installation and Builds](installation.md) for provenance and source builds.

The `radixor` wheel contains code. Its required
`radixor-models-standard` dependency contains 20 precompiled models. The
standard package excludes textual source dictionaries and optional PoliMorf
data, which keeps startup on the direct compiled-model path.

## 2. Select and reuse a stemmer

Construct a stemmer once and retain it for the lifetime of the application:

```python
from radixor import Stemmer

english = Stemmer("en")
polish = Stemmer("pl")

print(english.stem("running"))  # 'run'
print(polish.stem("koty"))
```

Short aliases such as `en`, `de`, and `pl` resolve to the documented default
model IDs. A full ID such as `us-uk-default` selects the same model explicitly.
The complete mapping is listed under [Built-in Languages](../built-in-languages.md).

## 3. Prefer batch calls for collections

Crossing the Python/native boundary once per collection is substantially more
efficient than a Python loop of scalar calls:

```python
words = ["running", "studies", "better", "cars"]
stems = english.stem_batch(words)
```

`stem_batch()` preserves input order and returns one item per word. Entries can
be `None` when the trie has no applicable patch command.

Repeated natural-language tokens use a bounded result cache shared by the
scalar and batch APIs. Its default capacity is 10,000 entries, matching
PyStemmer; choose another bound or pass `0` to disable it:

```python
english = Stemmer("en", 10_000)  # positional second argument like PyStemmer
english = Stemmer("en", cache_size=10_000)
uncached = Stemmer("en", cache_size=0)

# PyStemmer-compatible cache alias:
english.maxCacheSize = 25_000
```

The cache covers `stem()`, `stemWord()`, `stem_batch()`, and `stemWords()`;
the `stem_all*()` methods are not cached.

## 4. Migrate from PyStemmer

Radixor exposes PyStemmer's familiar scalar and batch method names:

```python
stemmer = Stemmer("en")

stemmer.stemWord("running")
stemmer.stemWords(["running", "unknown_word"])
stemmer.stemWords((b"running", "cars"))
stemmer.stemWords(b_word for b_word in [b"running", b"cars"])
```

`stemWord()` and `stemWords()` return unmatched input unchanged. This removes
the `None` fallback checks required by Radixor's original `stem()` and
`stem_batch()` methods, so most migration work is limited to the package import
and dependency change.

Use `from radixor import Stemmer` (or `import radixor as Stemmer`) in migration code.
`import Stemmer` is optional and only for environments without PyStemmer (zero-source-change migration).


## 5. Load a custom compiled model

The standard installation covers the maintained default catalog. A custom
version 7 model can be loaded directly:

```python
custom = Stemmer(compiled="models/domain-english.rxc")
```

To compile a maintained textual dictionary during a preparation step:

```python
from radixor import compile

compile("dictionaries/domain.tsv.gz", "models/domain-english.rxc", language="en")
```

Deploy the resulting `.rxc` file and load it at application startup. See
[Dictionary Compilation](model-compilation.md) for format interoperability and
the production compilation profile.

## 6. Production checklist

- Pin compatible `radixor` and `radixor-models-standard` releases in the
  application's dependency lock.
- Construct and reuse stemmers instead of rebuilding them per request.
- Use batch calls for token collections.
- Choose `stem*` or `stemWord*` semantics deliberately for unmatched words.
- Treat custom dictionaries and compiled models as trusted application input.
- Regression-test representative vocabulary before changing model versions.

Continue with [Installation and Builds](installation.md) for wheel/platform
details, [Usage and API](usage.md) for the complete call surface, or
[Performance](performance.md) for benchmark methodology and results.
