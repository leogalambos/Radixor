# Python-C Usage and API

Radixor-C mirrors the public stemming API of `radixor` so an application can
usually switch imports without changing its stemming calls:

```python
from radixor_c import Stemmer

stemmer = Stemmer("en")
```

## Model selection

```python
Stemmer("en")                              # language alias
Stemmer("us-uk-default")                  # full standard model ID
Stemmer(compiled="domain-english.rxc")    # application-owned compiled model
```

`path=` is accepted as an alias for a compiled model path for API compatibility,
but it does not compile a text dictionary in Radixor-C.

## Stemming calls

| Call | Result for a recognized word | Result when no patch applies |
|---|---|---|
| `stem(word)` | dominant stem | `None` |
| `stem_batch(words)` | one dominant result per input | `None` at that position |
| `stemWord(word)` | dominant stem | original word |
| `stemWords(words)` | one result per input | original word at that position |
| `stem_all(word)` | ranked candidate stems | empty list |
| `stem_all_batch(words)` | candidates per input | empty list at that position |

The bounded result cache defaults to 10,000 entries. Set `cache_size=0` to
disable it or use the PyStemmer-compatible `maxCacheSize` name.

```python
Stemmer("en", cache_size=0)
Stemmer("english", 50_000)
```

Use `lowercase=False` only when inputs have already been normalized. Stemmer
instances are safe to share between Python threads.

## Scope boundary

Radixor-C does not expose `radixor.compile(...)`, textual dictionary loading,
or trie modification. Compile or extend models outside the serving process and
load the finished artifact. The shared [Data Formats](../data-formats.md) page
shows which tool creates each artifact; the [runtime chooser](../getting-started.md)
compares all three implementations.
