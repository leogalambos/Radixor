# Python-C Quick Start

## 1. Install

```bash
python -m pip install --only-binary=:all: radixor-c
```

The dependency `radixor-models-standard` supplies the 20 compiled standard
models. Radixor-C supports CPython 3.10 through 3.14.

## 2. Stem one word

```python
from radixor_c import Stemmer

english = Stemmer("en")
print(english.stem("running"))       # run
print(english.stem("unknown_word"))  # None when no patch applies
```

Construct a stemmer once and reuse it. Scalar calls are the principal reason
to choose this C runtime.

## 3. Stem a collection

```python
words = ["running", "studies", "cars"]
print(english.stem_batch(words))
```

Batch and PyStemmer-compatible calls are available for API portability:

```python
english.stemWord("unknown_word")
english.stemWords(words)
```

`stemWord()` and `stemWords()` return unmatched inputs unchanged; `stem()` and
`stem_batch()` return `None` for them.

## 4. Load a custom compiled model

```python
custom = Stemmer(compiled="models/domain-english.rxc")
```

Radixor-C accepts a prepared [compiled Radixor model](../data-formats.md) only.
To create it, use Java or install
`radixor` in the preparation environment:

```python
import radixor

radixor.compile("domain.tsv.gz", "models/domain-english.rxc", language="en")
```

See [Data Formats](../data-formats.md) for the complete workflow.
