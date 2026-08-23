# Compiling Dictionaries in Python

The Python package can compile a textual Radixor dictionary into the shared
version 7 binary trie format. This moves dictionary parsing, patch-command
generation, trie construction, reduction, and serialization out of application
startup.

Use this workflow when the application owns its model file. Standard language
aliases already load validated, precompiled `.rxc` resources from
`radixor-models-standard`; they do not parse or compile textual dictionaries
when a `Stemmer` is constructed.

## Source format

The input is a plain UTF-8 or GZip-compressed UTF-8 tab-separated dictionary.
The first column is the canonical stem and the remaining columns are its known
surface forms:

```text
run	running	runs	ran
cat	cats
```

Remarks beginning with `#` or `//` are accepted. The complete syntax and
normalization rules are documented on the shared [Dictionary Format](../dictionary-format.md)
page.

## Compile a model

```python
import radixor

radixor.compile(
    "stemmer.tsv.gz",
    "english.rxc",
    language="en",
)
```

`backward` defaults to `True` when omitted. This processes suffixes from the end
of the stored character sequence for Persian, Hebrew, Yiddish, and every other
natural-language model. The optional `language` is retained for API compatibility,
is not written to the compiled artifact, and does not select traversal. Set `backward=False` explicitly only
for deliberately prefix-oriented custom data:

```python
radixor.compile(
    "custom.tsv",
    "custom.rxc",
    backward=True,
    store_original=True,
    lowercase=True,
)
```

The arguments are:

| Argument | Meaning |
|---|---|
| `source` | Plain or GZip-compressed textual dictionary. |
| `out_path` | Destination for the GZip-compressed version 7 trie. |
| `language` | Optional alias or model ID retained for API compatibility; it is not persisted. |
| `backward` | Traversal direction; defaults to `True` for suffix-oriented data. |
| `store_original` | Include a no-op mapping for every canonical stem. Defaults to `True`. |
| `lowercase` | Record lowercase lookup normalization in the compiled metadata. Defaults to `True`. |

Compilation refuses an input that is already a compiled trie. The destination
is written by the native extension; the caller is responsible for choosing its
location and for replacing an existing file only when that is intended.

## Load the compiled model

```python
from radixor import Stemmer

stemmer = Stemmer(compiled="english.rxc")
print(stemmer.stem("running"))
```

`Stemmer(path=...)` also auto-detects textual dictionaries and compiled version
7 streams, but `compiled=` communicates the deployment intent more clearly.
Traversal direction, `store_original`, and lookup normalization are already
stored in a compiled artifact; constructor build options do not rewrite them.

## Java interoperability

Python and Java share the inner version 7 trie stream. A binary produced by
`radixor.compile(...)` can be loaded by Java's
`StemmerPatchTrieLoader.loadBinaryCompiled(...)`, and Python can load a version
7 artifact written by `StemmerPatchTrieBinaryIO`.

The outer GZip bytes need not be identical because compressor implementations
may differ. Interoperability applies to the decompressed version 7 stream and
its persisted metadata.

## Differences from the Java compiler

Python compilation intentionally exposes the production dominant-result
configuration used by the Python runtime. Java additionally offers three selectable
reduction modes, more normalization controls, incremental extension, and a CLI
with explicit overwrite handling. Use [Java CLI Compilation](../cli-compilation.md)
when those controls are required.

For normal Python use, compile once during preparation, deploy the resulting
`.rxc` file as an application-owned asset, and reuse one loaded `Stemmer` at
runtime.
