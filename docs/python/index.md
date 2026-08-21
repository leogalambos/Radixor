# Choose a Python Runtime

Radixor provides two native CPython packages over the same standard models and
stemming semantics. They are complementary, not successive package names.

| | Python (PyO3) | Python-C |
|---|---|---|
| Distribution | `radixor` | `radixor-c` |
| Native implementation | Rust/PyO3 | CPython C API |
| Primary strength | High throughput through batch processing | Low overhead for individual word calls |
| Text dictionary compilation | Yes | No; load a prepared compiled model |
| [Compiled model](../data-formats.md) loading | Yes | Yes |
| Direction of future expansion | Progressively toward the Java flagship API | Basic runtime first; trie modification will come later |
| Import | `from radixor import Stemmer` | `from radixor_c import Stemmer` |

Use [Python-C](../python-c/index.md) for simple, fast scalar stemming from
prepared models. Use the `radixor` package described below for batch-oriented
processing and Python-side model compilation. Java remains the primary and most
complete implementation.

## Python (PyO3)

The **`radixor`** package is Radixor's native Python implementation. It is not
a wrapper around the Java library and does not require a JVM: it is a
compiled extension (Rust, via [PyO3](https://pyo3.rs/) and
[maturin](https://www.maturin.rs/)) that loads precompiled patch-command tries
derived from the same canonical UniMorph data as the Java models.

```python
from radixor import Stemmer

s = Stemmer("en")
s.stem("running")               # 'run'
s.stem_batch(["cats", "ran"])   # ['cat', 'run']
```

- [Fast Track](fast-track.md) — install and produce the first stem.
- [Quick Start](quick-start.md) — the complete application-oriented learning path.
- [Installation and building](installation.md) — Linux, Windows, macOS.
- [Usage and examples](usage.md) — batch API, caching, and custom models.
- [Migration methods](usage.md) — `Stemmer`, `stemWord`, `stemWords`, `algorithms`, and `version`; optional compatibility `import Stemmer` when legacy import migration is used.
- [Dictionary compilation](model-compilation.md) — prepare a version 7 binary
  once and share it with Python or Java.
- [Performance](performance.md) — fair, reproducible comparisons vs PyStemmer,
  snowballstemmer, NLTK Porter, and CISTEM.

The language and model-ID mapping is shared with Java and maintained on the
[Built-in Languages](../built-in-languages.md) page. Installing `radixor`
also resolves the separate pure `radixor-models-standard` distribution containing
the 20 default compiled models; Java applications select independently
versioned model JARs.

!!! note "Same models, same results, different runtime"
    The standard Python models are compiled from the identical canonical
    dictionaries with the identical production reduction configuration
    (`MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS`, 75 % / 3×,
    uniform-subtree contraction, `LOWERCASE_WITH_LOCALE_ROOT`, `AS_IS`
    diacritics, `storeOriginal=true`). For a word present in a model, both
    implementations return the same dominant stem. The compiled **binary format
    is shared** (see below), so a model compiled by one side loads in the other.

## Java vs. `radixor`: detailed capabilities

The two implementations solve the same problem but make different runtime
trade-offs. Mixing their mental models causes confusion, so the differences are
stated explicitly. **Neither is “better”** — they target different runtimes.

| Aspect | Java (`org.egothor:radixor`) | Python (`radixor`) |
|---|---|---|
| Runtime | JVM library | Compiled extension (Rust/PyO3), no JVM |
| Distribution | Maven JAR + model JARs | `abi3` wheel (one wheel per OS/arch, Python ≥ 3.9) |
| Hot-path data structure | `CompiledNode` graph; routines operate on caller-owned **`char[]`** with zero-copy normalized lookups and visitor sinks (`EntrySink`) | Flat **CSR arrays** (no per-node objects); reused UTF‑16 scratch buffers |
| Result cache | **None** — `get()` is stateless and re-stems every call | **Bounded**, 10,000 entries by default (matching PyStemmer); `Stemmer(cache_size=0)` disables it (`maxCacheSize` is a supported alias) |
| Batch API | Not a batch call; you loop and reuse `char[]`/visitors to avoid allocation | **`stem_batch()` / `stem_all_batch()`** — one Python↔Rust crossing amortized over the whole list |
| Reduction modes | All three modes selectable at compile time | Fixed to the production `DOMINANT` mode |
| Extending a compiled trie | **Supported** — add words/transformations to an already-compiled trie without recompiling | **Not exposed** — compile from a dictionary (or load a compiled binary) |
| Model resolution | `ServiceLoader` registry, descriptors, SHA‑256 integrity checks | Separate standard data package; catalog/format/SHA‑256 validation before synchronous native load |
| Normalization control | Case and diacritic modes fully configurable | `lowercase` toggle; diacritics `AS_IS` (models are built this way) |
| Binary format | `StemmerPatchTrieBinaryIO` v7 read/write (versioned, fingerprinted) | v7 read/write, **inner stream byte-identical** to Java; **v7 only** (no legacy v1–v6) |
| Multiple stems | `getAll(...)` | `stem_all()` / `stem_all_batch()` |

### Runtime capabilities that differ

To avoid surprises, these Java capabilities are **not** in the Python package:

- **Extending / incrementally growing a compiled trie.** Python compiles from a
  source dictionary (or loads a compiled binary); it does not add words to an
  existing compiled trie at runtime.
- **Selectable reduction modes.** Only the production `DOMINANT` mode is used.
- **Pluggable provider discovery.** Python currently resolves one known
  standard provider directly; entry-point plugins are not yet exposed.
- **Legacy binary versions.** Only stream version 7 is read/written.
- **Diacritic-removal modes** beyond `AS_IS` (the bundled models are `AS_IS`).

### Python-specific capabilities

- A **batch API** (`stem_batch`) that amortizes the Python↔native boundary — the
  single most important call for throughput from Python.
- A **bounded result cache** (`cache_size=10_000` by default; `maxCacheSize` is
  a supported alias) for workloads with repeated tokens. It is shared by
  `stem()`, `stemWord()`, `stem_batch()`, and `stemWords()`; pass
  `cache_size=0` to disable it. The `stem_all*()` methods are not cached.
- A `lowercase=False` mode to skip per-lookup lowercasing when the caller
  guarantees already-lowercased input.

## Interoperability

The compiled binary is Radixor's **v7 trie stream**, and the Python runtime writes
the *inner stream byte-for-byte identically to the Java*
`StemmerPatchTrieBinaryIO`. Consequently:

- a model compiled by **Java** (`org.egothor.stemmer.Compile` /
  `StemmerPatchTrieBinaryIO.write`) loads in **Python**, and
- a model compiled by **Python** (`radixor.compile(...)`) loads in **Java**.

(The outer gzip wrapper bytes differ between the two gzip implementations; this
is irrelevant — both sides decompress to the same v7 stream.)
