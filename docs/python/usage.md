# Usage and examples

## Creating a stemmer

```python
from radixor import Stemmer

s = Stemmer("en")                      # by language code (bundled model)
s = Stemmer("us-uk-default")           # by full model ID
s = Stemmer(path="my_dictionary.gz")   # a custom gzipped TSV source dictionary
s = Stemmer(compiled="en.rxc")         # a pre-compiled binary (instant load)
```

The default traversal is BACKWARD from the stored sequence end for suffix-oriented
data in every writing system. Override with `backward=False` only for a
deliberately prefix-oriented custom `path=`; writing direction does not reverse
the character order stored by Python or Java.

## Stemming a single word

```python
s.stem("running")     # 'run'
s.stem("cats")        # 'cat'
s.stem("zzzzz")       # None  -> not reducible / unknown
```

`stem()` returns the single **dominant** stem, or `None`.

!!! info "Why a known word may return itself"
    A surface form that is *also* a canonical headword (e.g. an English word
    that is both its own lemma and an inflection of another lemma) returns
    itself, because the dominant sense is “this word is its own stem”. The
    inflectional reading is still available via `stem_all()`.

## Batch stemming — the fast path

For anything beyond a handful of words, use the batch API. It crosses the
Python↔native boundary **once** for the whole list, which is the dominant cost
when stemming from Python.

```python
words = ["running", "cats", "stemming", "quickly"]
s.stem_batch(words)          # ['run', 'cat', 'stem', 'quick'] (None for unknowns)
```

```python
# Multiple candidate stems per word (ambiguity preserved):
s.stem_all("running")               # e.g. ['run', 'runn']
s.stem_all_batch(["running", "cats"])
```

## PyStemmer-compatible methods

Radixor also exposes PyStemmer's scalar and batch method names. It preserves
PyStemmer method compatibility while keeping Radixor's own stemmer internals.
The compatibility surface covers supported PyStemmer-native algorithms only.

| Method | Recognized word | Word without a patch command | Return type |
| --- | --- | --- | --- |
| `stem(word)` | dominant stem | `None` | `str | None` |
| `stem_batch(words)` | dominant stem at the same position | `None` at the same position | `list[str | None]` |
| `stemWord(word)` | dominant stem | original input word | `str \| bytes` |
| `stemWords(words)` | dominant stem at the same position | original input word at the same position | `list[str \| bytes]` |

Use `stemWord()` and `stemWords()` when migrating code that expects
PyStemmer's no-`None` contract:

```python
import radixor as Stemmer

# The rest of this common PyStemmer call pattern remains unchanged.
s = Stemmer.Stemmer("english")

s.stemWord("running")                  # 'run'
s.stemWord("unknown_word")             # 'unknown_word'
s.stemWords(["running", "unknown_word"])
# ['run', 'unknown_word']

s.stemWord(b"running")                 # b'run'
s.stemWords([b"running", "unknown", b"cars"])
# [b'run', 'unknown', b'car']
```

`stemWords()` accepts any iterable:

```python
s.stemWords(("running", b"running"))               # tuple input
s.stemWords(word for word in ["running", b"running"])  # generator input
```

It preserves input order and length, and returns typed output (`str` for
`str` inputs, `bytes` for `bytes` inputs).

```python
radixor.algorithms()        # canonical compatibility names only
radixor.algorithms(aliases=False) == radixor.algorithms()
radixor.algorithms(True)    # include aliases
radixor.version()           # installed package version string
```

`algorithms(True)` includes only supported aliases and does not add unsupported
Snowball identities (`porter`, `dutch_porter`, etc.).
`algorithms(False)` omits aliases and is deterministic.

PyStemmer's full language names, such as `"english"` and `"czech"`, are
accepted for bundled Radixor languages and supported aliases.

The compatibility contract covers these method names, full language aliases,
and unmatched-word fallback behavior. Radixor also accepts PyStemmer's cache
knob name as an alias: `maxCacheSize` is supported as an alias of
`cache_size`. Both libraries default to a cache capacity of 10,000 entries.

Use `from radixor import Stemmer` (or `import radixor as Stemmer`) for the primary import.
`import Stemmer` is optional and only valid for zero-source-change migration when Radixor is the only top-level `Stemmer` provider.

## Bounded result cache

Real text repeats tokens. The default bounded cache returns the already-built
result object on a recognized-word hit (a reference-count bump — no
re-stemming, no new result string). Unknown words are cached as misses, so
`stemWord()` and `stemWords()` still create their required original-word
result. Its default capacity is **10,000 entries**, matching PyStemmer:

```python
s = Stemmer("en")                       # cache up to 10,000 distinct input words
s = Stemmer("en", cache_size=50_000)    # choose a custom capacity
s = Stemmer("en", cache_size=0)         # explicitly disable caching
s = Stemmer("english", 50_000)          # drop-in PyStemmer style positional cache size

# Equivalent PyStemmer-style cache control:
s.maxCacheSize = 25_000
```

`maxCacheSize` matches PyStemmer's behavior: assigning a non-`int` raises
`TypeError`, and assigning a negative value raises `ValueError`.

One cache is shared by `stem()`, `stemWord()`, `stem_batch()`, and
`stemWords()`. The `stem_all()` and `stem_all_batch()` methods are not cached.
Caching never changes results; it only avoids recomputation. Entries are
inserted until the configured capacity is reached; there is no eviction. For a
high-cardinality stream without useful token repetition, use `cache_size=0`.

## Skipping lowercasing for pre-normalized input

By default lookups lowercase the input (`LOWERCASE_WITH_LOCALE_ROOT`). If your
pipeline already lowercases tokens, skip the redundant work:

```python
s = Stemmer("en", lowercase=False)     # assume already-lowercased input
s.stem("running")                      # 'run'
s.stem("Running")                      # None  -> not lowercased, so no match
```

The model's keys are always lowercase; `lowercase=False` only turns off
per-lookup normalization. On already-lowercased input the results are identical.

## Compile once, load instantly

Compiling a trie from text costs a few seconds for large languages. Compile it
once to Radixor's binary format and load it directly afterwards:

```python
import radixor

radixor.compile("stemmer.gz", "en.rxc", language="en")
s = radixor.Stemmer(compiled="en.rxc")
```

See [Compiling Dictionaries in Python](model-compilation.md) for the source
format, traversal and normalization options, deployment guidance, Java
interoperability, and the controls that remain Java-only.

## Using a custom dictionary

A source dictionary is a gzipped (or plain) TSV file, one entry per line, the
first column the canonical stem and the rest its variants; `#` and `//` start
line remarks:

```
run	running	runs	ran
cat	cats
```

```python
s = Stemmer(path="custom.gz", backward=True, store_original=True)
```

`store_original=True` (default) maps each stem to itself (a no-op patch) so the
stem is recognised. See [Dictionary Format](../dictionary-format.md) for the
authoritative specification shared with the Java project.

## Thread-safety

A `Stemmer` is safe to share across threads. The bounded cache is guarded
internally; the compiled trie is immutable after construction.

## API summary

| Call | Returns | Notes |
|---|---|---|
| `Stemmer(language=None, maxCacheSize: int | None = None, *, path=..., compiled=..., backward, store_original, lowercase, cache_size=10_000)` | stemmer | PyStemmer-compatible positional cache argument via `maxCacheSize`; if set, `cache_size` is ignored; `cache_size=0` disables caching (`maxCacheSize` remains supported as alias) |
| `stem(word)` | `str \| None` | dominant stem |
| `stem_batch(words)` | `list[str \| None]` | **preferred** for many words |
| `stemWord(word)` | `str \| bytes` | PyStemmer-compatible; returns an unmatched word unchanged |
| `stemWords(words)` | `list[str \| bytes]` | PyStemmer-compatible batch call; accepts any iterable and preserves unmatched words and input order |
| `algorithms(aliases: bool = False)` | `list[str]` | Supported PyStemmer algorithm names |
| `version()` | `str` | Installed `radixor` version |
| `stem_all(word)` | `list[str]` | all candidate stems, best first |
| `stem_all_batch(words)` | `list[list[str]]` | |
| `radixor.compile(source, out, *, language, backward, store_original, lowercase)` | `None` | writes a v7 binary |
