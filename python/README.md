# radixor — High-throughput Radixor for Python

**radixor** is the progressively expanded Python port of the
[Radixor](https://github.com/leogalambos/Radixor) Java flagship. It is built on
a Rust core via [PyO3](https://pyo3.rs/) and provides a batch API that amortises
the Python↔Rust bridge overhead across many words at once. Java remains the
primary and most complete implementation; new model-management capabilities
will be brought to this package over time.

For simple applications dominated by calls for individual words, also consider
**`radixor-c`**. Its direct CPython C implementation is designed for low scalar
call overhead, but currently loads only prepared models. Both packages use the
same standard model distribution and results; `radixor` is the Python choice
for batch throughput and text dictionary compilation.

| Runtime | Best fit | Current model capabilities |
|---|---|---|
| Java Radixor | Complete flagship API | Build, reduce, extend, persist and load tries |
| Python (PyO3) — `radixor` | Large batches and Python-side preparation | Compile text dictionaries and load [compiled Radixor models](https://leogalambos.github.io/Radixor/data-formats/); progressively converging on Java |
| Python-C — `radixor-c` | Fast individual calls | Load standard or prepared [compiled Radixor models](https://leogalambos.github.io/Radixor/data-formats/) |

## Why radixor?

| Library | Approach | Batch API |
|---|---|---|
| **radixor** | Compiled patch-command trie in Rust | ✅ `stem_batch()` |
| PyStemmer (Snowball) | C extension (`libstemmer`) | ✅ `stemWords()` |
| snowballstemmer | Pure-Python Snowball | ✅ (Python loop) |
| NLTK Porter / CISTEM | Pure Python | ❌ |

**Performance.** On the shared UniMorph gold-standard corpus, measuring runtime
stemming only (construction excluded) and with a fair, cache-disabled,
same-input methodology, radixor 4.2.0 recorded lower median processing time in
all **18 / 18** direct comparisons with PyStemmer 3.1.0 (Snowball's C
`libstemmer`) in the published 2026-08-23 run. At batch size 100, the
geometric-mean speedup was **2.25×**. The companion radixor-c 4.2.0 runtime did
the same in all 18 comparisons, with a **2.29×** geometric-mean speedup. The
complete
machine metadata and current results are in the [Python performance
documentation](https://leogalambos.github.io/Radixor/python/performance/);
benchmark implementation and fairness notes are in the
[`benchmarks/` source directory](https://github.com/leogalambos/Radixor/blob/main/python/benchmarks/README.md).

## Installation

From PyPI:

```bash
python -m pip install --only-binary=:all: radixor
```

The GitHub Releases-backed index is the independent alternative:

```bash
python -m pip install --only-binary=:all: \
  --index-url https://leogalambos.github.io/Radixor/python/simple/ radixor
```

The GitHub index points to the same immutable model and native release assets.
See the [installation guide](https://leogalambos.github.io/Radixor/python/installation/)
for provenance and source-checkout builds.

Wheels are provided for Linux, macOS, and Windows (Python 3.9+). The install
also resolves the mandatory pure `radixor-models-standard` dependency
with 20 precompiled standard models. Building the native source distribution
requires Rust ≥ 1.75 and [maturin](https://www.maturin.rs/).

## Quick start

```python
from radixor import Stemmer

s = Stemmer("en")          # English (us-uk-default model)
s.stem("running")          # → "run"
s.stem("cats")             # → "cat"
s.stem("unknown_word")     # → None
```

## Batch API — the fast path

```python
words = ["running", "cats", "stemming", "quickly"]

# Amortises the Python→Rust bridge cost across all words at once
stems = s.stem_batch(words)
# → ["run", "cat", "stem", "quick"]
```

For large corpora (tens of thousands of words) the batch call is the recommended interface. It avoids per-call Python frame overhead and keeps the hot loop entirely inside Rust.

## Migrating from PyStemmer

Radixor provides PyStemmer's `stemWord` and `stemWords` method names. These
compatibility methods also follow PyStemmer's fallback behavior: when the trie
has no patch command, they return the original word instead of `None`.

```python
from radixor import Stemmer

stemmer = Stemmer("english")
stemmer.stemWord("running")                 # → "run"
stemmer.stemWord("unknown_word")            # → "unknown_word"
stemmer.stemWords(["running", "unknown"])   # → ["run", "unknown"]
```

Prefer `from radixor import Stemmer` (or `import radixor as Stemmer`).
`import Stemmer` is only for zero-source-change migration when Radixor is the only provider of that top-level module name.

The original Radixor methods remain unchanged: `stem` and `stem_batch` return
`None` for words without a matching patch command. Radixor accepts PyStemmer's
full language names for the languages represented by its bundled models, as
well as its existing two-letter codes and model IDs.

## Supported languages

| Code | Language | Model ID |
|---|---|---|
| `cs` | Czech | `cs-cz-default` |
| `da` | Danish | `da-dk-default` |
| `de` | German | `de-de-default` |
| `en` | English | `us-uk-default` |
| `es` | Spanish | `es-es-default` |
| `fa` | Persian | `fa-ir-default` |
| `fi` | Finnish | `fi-fi-default` |
| `fr` | French | `fr-fr-default` |
| `he` | Hebrew | `he-il-default` |
| `hu` | Hungarian | `hu-hu-default` |
| `it` | Italian | `it-it-default` |
| `nb` | Norwegian Bokmål | `nb-no-default` |
| `nl` | Dutch | `nl-nl-default` |
| `nn` | Norwegian Nynorsk | `nn-no-default` |
| `pl` | Polish | `pl-pl-unimorph` |
| `pt` | Portuguese | `pt-pt-default` |
| `ru` | Russian | `ru-ru-default` |
| `sv` | Swedish | `sv-se-default` |
| `uk` | Ukrainian | `uk-ua-default` |
| `yi` | Yiddish | `yi-default` |

## API reference

### `Stemmer(language=None, *, path=None, compiled=None, backward=None, store_original=True, lowercase=True, cache_size=10_000)`

Create a stemmer for the given language code, model ID, custom textual
dictionary, or previously compiled version 7 trie. Textual dictionaries are
compiled in Rust; `compiled=` loads a prepared binary directly.

```python
s = Stemmer("de")                           # by language code
s = Stemmer("de-de-default")               # by model ID
s = Stemmer(path="/data/custom.gz")        # custom gzipped dictionary
s = Stemmer(compiled="/data/custom.rxc")   # prepared v7 binary
```

`backward` selects the traversal direction; when left as `None` it defaults to
BACKWARD for suffix-oriented data in every writing system. Set it to `False`
only for deliberately prefix-oriented custom data. `store_original` (default
`True`) maps each canonical stem to a no-op
patch so the stem itself is recognised. `lowercase=False` skips runtime
lowercasing for already-normalized input, and `cache_size` enables the bounded
result cache. The default holds up to 10,000 entries, matching PyStemmer;
`cache_size=0` disables it. One cache is shared by `stem()`, `stemWord()`,
`stem_batch()`, and `stemWords()`; the `stem_all*()` methods are not cached.
`maxCacheSize` is also available as a PyStemmer-compatible cache-size alias.

### `stem(word: str) → str | None`

Return the stem, or `None` when the compiled trie finds no applicable patch
command. This does not mean that lookup is restricted to exact training words.

### `stem_batch(words: list[str]) → list[str | None]`

Stem an entire list. Preferred for large inputs.

### `stemWord(word: str | bytes) → str | bytes`

PyStemmer-compatible scalar method. Return the original word if it cannot be
stemmed.

### `stemWords(words) → list[str | bytes]`

PyStemmer-compatible batch method. Accepts arbitrary iterables of `str` and
`bytes`, returns list output with per-element type preservation, and returns
each unrecognized word unchanged.

### `algorithms(aliases: bool = False) → list[str]`

Return supported PyStemmer-compatible algorithms.

`algorithms(True)` includes aliases from supported PyStemmer algorithms; unsupported
algorithm names are intentionally not surfaced.

### `version() → str`

Return the installed `radixor` package version.

### `stem_all(word: str) → list[str]`

Return all stems ordered by descending corpus frequency. Useful when multiple valid stems exist.

### `stem_all_batch(words: list[str]) → list[list[str]]`

Return all stems for each word in a batch.

## Compiling a model (compile once, load instantly)

Compiling the trie from a textual dictionary takes time for large languages
(seconds). You can compile it **once** to Radixor's binary format and then load
it near-instantly — the same workflow Java users have:

```python
import radixor
radixor.compile("stemmer.gz", "en.rxc", language="en")   # or backward=True/False
s = radixor.Stemmer(compiled="en.rxc")                    # instant load, no re-compile
```

The compiled file uses Radixor's **v7 trie format and is byte-compatible with
the Java `StemmerPatchTrieBinaryIO`** (the inner stream is identical), so a file
compiled by Java can be loaded by Python and vice versa. `Stemmer(path=...)`
auto-detects whether it was given a compiled trie or a textual dictionary.

## Using a custom model

Provide your own gzipped source dictionary (tab-separated
`stem<TAB>variant1<TAB>variant2…` per line, `#` / `//` line remarks allowed) and
load it directly:

```python
s = Stemmer(path="my_dictionary.gz")            # BACKWARD by default
s = Stemmer(path="my_prefix_dictionary.gz", backward=False)  # prefix-oriented
```

The dictionary is compiled to a patch-command trie in Rust at construction time.

## Building from source

```bash
cd Radixor/
pip install maturin build setuptools wheel pytest
./gradlew pythonBuildStandardModels
pip install --no-deps build/python/dist/standard/radixor_models_standard-0.0.0-py3-none-any.whl
cd python/
maturin develop --release    # editable install with release optimisations
```

From the repository root, Gradle builds the native wheel/sdist and pure
standard-model wheel/sdist without installing them globally:

```bash
./gradlew pythonBuild
```

The convenience tasks `pythonBuildLinux`, `pythonBuildWindows`, and
`pythonBuildMacos` use the host build when the requested platform matches the
current system. Other platforms are cross-compiled with the corresponding Rust
target and therefore require that target and its linker/SDK to be installed.
Override a default target with, for example,
`-PpythonWindowsTarget=x86_64-pc-windows-gnu`. Build artifacts are written below
`build/python/dist/`.

The complete batch benchmark runs Radixor for all bundled languages and every
available comparison engine for the languages it supports, using batch sizes
10, 20, 50, and 100:

Comparison engines are auto-detected in the environment of `pythonExecutable`.
Install `python/benchmarks/requirements-bench.txt` there to enable the complete
comparison set.

```bash
./gradlew pythonBenchmarkAllLanguagesBatch
```

Use `pythonBenchmarkWords`, `pythonBenchmarkRepeats`, and
`pythonBenchmarkWarmup` Gradle properties to tune the run. CSV and JSON reports
are written below `build/reports/python-benchmarks/`.

Neither runtime distribution contains textual dictionaries. The standard data
sdist contains build-ready gzip v7 `.rxc` files, a checksummed provenance
manifest, and per-model CC BY-SA 3.0 notices. They are generated below `build/`
from canonical `models/*/src/modelInput/stemmer.gz` inputs and are never stored
in Git. `./gradlew regeneratePythonStandardModels` performs this deterministic
generation; repository topology selects the 20 defaults and excludes optional
`pl-pl-polimorf`.

`radixor` requires `radixor-models-standard>=2.0,<3.0`. The Python distribution
version is independent of the Java model-catalog identity and of the individual
model versions recorded in the manifest.

## License

The native/API package is BSD-3-Clause — see the
[license text](https://github.com/leogalambos/Radixor/blob/main/python/LICENSE).
Model data is separately licensed under CC BY-SA 3.0 in its packaged notices.
